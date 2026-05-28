package com.sidduttala.hypeclipper.twitch;

/**
 * We couldn't clip, but nothing is broken - channel went offline, clips are
 * turned off, or Twitch is rate limiting. Expected often enough that the
 * dispatcher just falls back to an alert without a stack trace.
 */
public class ClipUnavailableException extends RuntimeException {

    public ClipUnavailableException(String message) {
        super(message);
    }
}
