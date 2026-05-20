package com.sidduttala.hypeclipper.detect;

import com.sidduttala.hypeclipper.model.ChatEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Sliding window of chat message timestamps in a Redis sorted set, scored by
 * epoch millis. A spike is "chat just got a lot louder than it normally is".
 */
@Service
public class VelocityTrackingService {

    private static final long WINDOW_MS = 30_000;
    private static final long RECENT_MS = 5_000;
    private static final long MIN_MESSAGES = 10;
    private static final double SPIKE_MULTIPLIER = 3.0;

    private final StringRedisTemplate redis;

    public VelocityTrackingService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isSpike(ChatEvent event) {
        String key = "chat:window:" + event.channelId();
        long now = event.timestampMs();
        long windowStart = now - WINDOW_MS;
        long recentStart = now - RECENT_MS;

        // The member has to be unique or two people saying "W" in the same
        // millisecond collapse into one entry.
        String member = event.userId() + ":" + UUID.randomUUID();
        redis.opsForZSet().add(key, member, now);
        redis.opsForZSet().removeRangeByScore(key, 0, windowStart);
        redis.expire(key, Duration.ofMinutes(5));

        long recent5s = count(key, recentStart, now);
        long window30s = count(key, windowStart, now);

        // How many messages a normal 5s slice of this window holds.
        double expected5s = (window30s / 30.0) * 5.0;

        return recent5s >= MIN_MESSAGES && recent5s >= SPIKE_MULTIPLIER * Math.max(expected5s, 1.0);
    }

    private long count(String key, long min, long max) {
        Long n = redis.opsForZSet().count(key, min, max);
        return n == null ? 0 : n;
    }
}
