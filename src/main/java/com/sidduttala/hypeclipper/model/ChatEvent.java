package com.sidduttala.hypeclipper.model;

/**
 * One chat message off the wire. Kept deliberately small - this thing gets
 * created thousands of times a minute on a busy channel.
 */
public record ChatEvent(String channelId, String userId, String message, long timestampMs) {
}
