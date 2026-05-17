package com.sidduttala.hypeclipper.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

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
    public void connect(String channel) {
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(IRC_WS), new Listener(channel))
                .join();
    }

    static final class Listener implements WebSocket.Listener {

        private final String channel;

        /** Twitch can split a frame mid-line, so hold the remainder until last==true. */
        private final StringBuilder buffer = new StringBuilder();

        Listener(String channel) {
            this.channel = channel;
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
                    handleLine(line);
                }
                buffer.setLength(0);
            }
            return WebSocket.Listener.super.onText(ws, data, last);
        }

        private void handleLine(String line) {
            if (line.isEmpty()) {
                return;
            }
            // Just dumping raw protocol lines for now so I can see what the
            // server actually sends before I write a parser for it.
            log.info("<< {}", line);
        }
    }
}
