package com.sidduttala.hypeclipper.ingest;

import com.sidduttala.hypeclipper.detect.VelocityTrackingService;
import com.sidduttala.hypeclipper.model.ChatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Kicks off the chat connection once the context is up.
 */
@Component
public class ChatIngestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatIngestRunner.class);

    private final TwitchIrcClient irc;
    private final VelocityTrackingService velocity;

    @Value("${hype.channel}")
    private String channel;

    public ChatIngestRunner(TwitchIrcClient irc, VelocityTrackingService velocity) {
        this.irc = irc;
        this.velocity = velocity;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("watching #{}", channel);
        irc.connect(channel, this::onChatMessage);
    }

    private void onChatMessage(ChatEvent event) {
        log.debug("[{}] {}: {}", event.channelId(), event.userId(), event.message());

        if (velocity.isSpike(event)) {
            log.info("SPIKE in #{}", event.channelId());
        }
    }
}
