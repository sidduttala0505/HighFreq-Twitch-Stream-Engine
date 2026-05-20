package com.sidduttala.hypeclipper.detect;

import com.sidduttala.hypeclipper.model.ChatEvent;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
    private static final long KEY_TTL_SECONDS = 300;

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

        // One round trip instead of five. On a big channel this runs a few
        // hundred times a second, and every extra hop is detection latency -
        // which matters here because the clip window is only ~30s wide.
        List<Object> results = redis.executePipelined((RedisCallback<Object>) conn -> {
            byte[] k = key.getBytes(StandardCharsets.UTF_8);
            byte[] v = member.getBytes(StandardCharsets.UTF_8);
            conn.zSetCommands().zAdd(k, now, v);                        // 0: record this message
            conn.zSetCommands().zRemRangeByScore(k, 0, windowStart);    // 1: drop anything older than the window
            conn.keyCommands().expire(k, KEY_TTL_SECONDS);              // 2: don't leak keys for dead channels
            conn.zSetCommands().zCount(k, recentStart, now);            // 3: last 5s
            conn.zSetCommands().zCount(k, windowStart, now);            // 4: the whole 30s
            return null;
        });

        long recent5s = asLong(results.get(3));
        long window30s = asLong(results.get(4));

        // How many messages a normal 5s slice of this window holds.
        double expected5s = (window30s / 30.0) * 5.0;

        return recent5s >= MIN_MESSAGES && recent5s >= SPIKE_MULTIPLIER * Math.max(expected5s, 1.0);
    }

    private static long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }
}
