package com.sidduttala.hypeclipper.ingest;

import com.sidduttala.hypeclipper.model.ChatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Reads a live channel's chat over Twitch's IRC-over-WebSocket gateway.
 *
 * No OAuth needed for reading: logging in as "justinfan<random>" with no
 * password gets you an anonymous read-only session.
 */
@Component
public class TwitchIrcClient {

    private static final Logger log = LoggerFactory.getLogger(TwitchIrcClient.class);

    private static final String IRC_WS = "wss://irc-ws.chat.twitch.tv:443";

    /** channel = lowercase login, e.g. "caedrel" (no leading #). */
    public void connect(String channel, Consumer<ChatEvent> onMessage) {
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(IRC_WS), new Listener(channel, onMessage))
                .join();
    }

    static final class Listener implements WebSocket.Listener {

        private final String channel;
        private final Consumer<ChatEvent> onMessage;

        /** Twitch can split a frame mid-line, so hold the remainder until last==true. */
        private final StringBuilder buffer = new StringBuilder();

        Listener(String channel, Consumer<ChatEvent> onMessage) {
            this.channel = channel;
            this.onMessage = onMessage;
        }

        @Override
        public void onOpen(WebSocket ws) {
            log.info("connected to twitch irc, joining #{}", channel);
            ws.sendText("NICK justinfan" + (int) (Math.random() * 100000) + "\r\n", true);
            ws.sendText("JOIN #" + channel + "\r\n", true);
            WebSocket.Listener.super.onOpen(ws);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                for (String line : buffer.toString().split("\r\n")) {
                    handleLine(ws, line);
                }
                buffer.setLength(0);
            }
            return WebSocket.Listener.super.onText(ws, data, last);
        }

        private void handleLine(WebSocket ws, String line) {
            if (line.isEmpty()) {
                return;
            }

            // Twitch pings every ~5 minutes and drops the connection if you
            // don't answer. Took me a while to work out that was why the
            // stream of messages just stopped dead.
            if (line.startsWith("PING")) {
                ws.sendText("PONG :tmi.twitch.tv\r\n", true);
                return;
            }

            // A chat message looks like:
            // :user!user@user.tmi.twitch.tv PRIVMSG #channel :the message text
            int privmsgIdx = line.indexOf(" PRIVMSG ");
            if (privmsgIdx > 0) {
                String user = line.substring(1, line.indexOf('!'));
                String msg = line.substring(line.indexOf(" :", privmsgIdx) + 2);
                onMessage.accept(new ChatEvent(channel, user, msg, System.currentTimeMillis()));
            }
        }
    }
}
