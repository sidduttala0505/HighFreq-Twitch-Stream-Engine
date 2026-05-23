package com.sidduttala.hypeclipper.detect;

/**
 * What the detector saw when it decided chat popped off.
 *
 * @param channelId     channel the spike happened in
 * @param recentCount   messages in the recent slice (the "5 seconds" number)
 * @param baselineCount messages in the quieter period before it
 * @param detectedAtMs  when we made the call
 */
public record SpikeSignal(String channelId, long recentCount, long baselineCount, long detectedAtMs) {
}
