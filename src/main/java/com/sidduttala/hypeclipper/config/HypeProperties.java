package com.sidduttala.hypeclipper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Everything under the "hype." prefix in application.yml.
 *
 * The detector numbers ended up here because I was restarting the app every
 * two minutes to try a different multiplier.
 */
@Component
@ConfigurationProperties(prefix = "hype")
public class HypeProperties {

    /** Lowercase twitch login of the channel to watch. */
    private String channel = "caedrel";

    /** Kafka topic the raw chat events land on. */
    private String topic = "twitch.chat";

    /** Full sliding window, in seconds. */
    private int windowSeconds = 30;

    /** The "is something happening right now" slice at the end of the window. */
    private int recentSeconds = 5;

    /** Floor so a dead channel can't spike off 2 messages. */
    private int minMessages = 10;

    /** Recent rate has to beat the baseline rate by this much. */
    private double spikeMultiplier = 3.0;

    /** How long one channel is locked out after a spike fires. */
    private int dedupLockSeconds = 60;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getRecentSeconds() {
        return recentSeconds;
    }

    public void setRecentSeconds(int recentSeconds) {
        this.recentSeconds = recentSeconds;
    }

    public int getMinMessages() {
        return minMessages;
    }

    public void setMinMessages(int minMessages) {
        this.minMessages = minMessages;
    }

    public double getSpikeMultiplier() {
        return spikeMultiplier;
    }

    public void setSpikeMultiplier(double spikeMultiplier) {
        this.spikeMultiplier = spikeMultiplier;
    }

    public int getDedupLockSeconds() {
        return dedupLockSeconds;
    }

    public void setDedupLockSeconds(int dedupLockSeconds) {
        this.dedupLockSeconds = dedupLockSeconds;
    }
}
