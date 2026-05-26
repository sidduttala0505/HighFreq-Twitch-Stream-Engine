package com.sidduttala.hypeclipper.detect;

import com.sidduttala.hypeclipper.audit.AuditLogService;
import com.sidduttala.hypeclipper.config.HypeProperties;
import com.sidduttala.hypeclipper.model.ChatEvent;
import com.sidduttala.hypeclipper.notify.DiscordNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The detection half of the pipeline. Reads chat back off the topic and decides
 * whether anything worth clipping just happened.
 */
@Component
public class ChatEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatEventConsumer.class);

    private final VelocityTrackingService velocity;
    private final DeduplicationService dedup;
    private final DiscordNotifier discord;
    private final AuditLogService audit;
    private final HypeProperties props;

    public ChatEventConsumer(VelocityTrackingService velocity,
                             DeduplicationService dedup,
                             DiscordNotifier discord,
                             AuditLogService audit,
                             HypeProperties props) {
        this.velocity = velocity;
        this.dedup = dedup;
        this.discord = discord;
        this.audit = audit;
        this.props = props;
    }

    @KafkaListener(topics = "${hype.topic}", groupId = "hype-detector")
    public void onChatEvent(ChatEvent event) {
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
