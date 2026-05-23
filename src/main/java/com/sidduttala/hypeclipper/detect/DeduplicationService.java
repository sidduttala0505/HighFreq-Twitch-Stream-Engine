package com.sidduttala.hypeclipper.detect;

import com.sidduttala.hypeclipper.config.HypeProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * A spike isn't one message, it's a wall of them - the detector will happily
 * fire on every message for the whole burst. This gives the first one through
 * and locks the channel out until the moment has passed.
 *
 * Doubles as rate-limit protection: Twitch's clip endpoint has a global cap
 * shared across every developer, so hammering it is a good way to get 429s.
 */
@Service
public class DeduplicationService {

    private final StringRedisTemplate redis;
    private final HypeProperties props;

    public DeduplicationService(StringRedisTemplate redis, HypeProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** True if this caller won the race and should act on the spike. */
    public boolean acquireClipLock(String channel) {
        String key = "clip:lock:" + channel;
        Boolean won = redis.opsForValue().setIfAbsent(
                key, Instant.now().toString(), Duration.ofSeconds(props.getDedupLockSeconds()));
        return Boolean.TRUE.equals(won);
    }
}
