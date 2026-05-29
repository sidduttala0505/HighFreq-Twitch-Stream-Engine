package com.sidduttala.hypeclipper.dispatch;

import com.sidduttala.hypeclipper.audit.AuditLogService;
import com.sidduttala.hypeclipper.config.HypeProperties;
import com.sidduttala.hypeclipper.detect.DeduplicationService;
import com.sidduttala.hypeclipper.detect.SpikeSignal;
import com.sidduttala.hypeclipper.notify.DiscordNotifier;
import com.sidduttala.hypeclipper.twitch.TwitchApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * What actually happens when chat pops off: clip it, post it, write it down.
 *
 * All of that is slow (a clip takes 5-15 seconds of polling), so none of it
 * runs on the consumer thread. If it did, we'd stop reading chat for the
 * length of every clip and walk straight past the next spike.
 */
@Component
public class SpikeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SpikeDispatcher.class);

    private final DeduplicationService dedup;
    private final TwitchApi twitch;
    private final DiscordNotifier discord;
    private final AuditLogService audit;
    private final HypeProperties props;

    public SpikeDispatcher(DeduplicationService dedup,
                           TwitchApi twitch,
                           DiscordNotifier discord,
                           AuditLogService audit,
                           HypeProperties props) {
        this.dedup = dedup;
        this.twitch = twitch;
        this.discord = discord;
        this.audit = audit;
        this.props = props;
    }

    public void onSpike(SpikeSignal spike) {
        // The whole burst looks like a spike, so only the first one through
        // the lock gets to do anything about it.
        if (!dedup.acquireClipLock(spike.channelId())) {
            return;
        }

        log.info("SPIKE in #{} - {} messages in {}s (baseline {})",
                spike.channelId(), spike.recentCount(), props.getRecentSeconds(), spike.baselineCount());

        Thread.startVirtualThread(() -> handle(spike));
    }

    private void handle(SpikeSignal spike) {
        String channel = spike.channelId();
        String clipUrl = null;

        try {
            clipUrl = clip(channel);
        } catch (Exception e) {
            log.warn("clip failed for #{}: {}", channel, e.getMessage());
        }

        // An alert with no clip still beats no alert. Whatever went wrong on
        // the Twitch side, someone should still hear that chat went off.
        if (clipUrl != null) {
            discord.sendSpikeWithClip(channel, spike.recentCount(), props.getRecentSeconds(), clipUrl);
        } else {
            discord.sendSpike(channel, spike.recentCount(), props.getRecentSeconds());
        }

        audit.record(spike, clipUrl);
    }

    private String clip(String channel) throws Exception {
        if (!twitch.isConfigured()) {
            return null;
        }
        // Creating a clip on an offline channel just 404s, so don't bother.
        if (!twitch.isLive(channel)) {
            log.info("#{} isn't live, alert only", channel);
            return null;
        }

        String broadcasterId = twitch.getBroadcasterId(channel);
        String clipId = twitch.createClip(broadcasterId);
        return twitch.waitForClipUrl(clipId);
    }
}
