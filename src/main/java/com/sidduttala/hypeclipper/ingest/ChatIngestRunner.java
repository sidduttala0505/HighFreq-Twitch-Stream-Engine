package com.sidduttala.hypeclipper.ingest;

import com.sidduttala.hypeclipper.audit.AuditLogService;
import com.sidduttala.hypeclipper.config.HypeProperties;
import com.sidduttala.hypeclipper.detect.DeduplicationService;
import com.sidduttala.hypeclipper.detect.SpikeSignal;
import com.sidduttala.hypeclipper.detect.VelocityTrackingService;
import com.sidduttala.hypeclipper.model.ChatEvent;
import com.sidduttala.hypeclipper.notify.DiscordNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final DeduplicationService dedup;
    private final DiscordNotifier discord;
    private final AuditLogService audit;
    private final HypeProperties props;

    public ChatIngestRunner(TwitchIrcClient irc,
                            VelocityTrackingService velocity,
                            DeduplicationService dedup,
                            DiscordNotifier discord,
                            AuditLogService audit,
                            HypeProperties props) {
        this.irc = irc;
        this.velocity = velocity;
        this.dedup = dedup;
        this.discord = discord;
        this.audit = audit;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("watching #{}", props.getChannel());
        irc.connect(props.getChannel(), this::onChatMessage);
    }

    private void onChatMessage(ChatEvent event) {
        log.debug("[{}] {}: {}", event.channelId(), event.userId(), event.message());
        velocity.evaluate(event).ifPresent(this::onSpike);
    }

    private void onSpike(SpikeSignal spike) {
        // The whole burst looks like a spike, so only the first one through
        // the lock gets to do anything about it.
        if (!dedup.acquireClipLock(spike.channelId())) {
            return;
        }

        log.info("SPIKE in #{} - {} messages in {}s (baseline {})",
                spike.channelId(), spike.recentCount(), props.getRecentSeconds(), spike.baselineCount());

        discord.sendSpike(spike.channelId(), spike.recentCount(), props.getRecentSeconds());
        audit.record(spike, null);
    }
}
