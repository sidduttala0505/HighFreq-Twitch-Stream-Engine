package com.sidduttala.hypeclipper.detect;

import com.sidduttala.hypeclipper.model.ChatEvent;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /** When we first saw traffic on a channel, so we know when the window is full. */
    private final Map<String, Long> watchingSince = new ConcurrentHashMap<>();

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
            conn.zSetCommands().zCount(k, windowStart, recentStart);    // 4: the 25s BEFORE that
            return null;
        });

        long recent5s = asLong(results.get(3));
        long baseline25s = asLong(results.get(4));

        // First run against a busy channel fired on basically every message:
        // there's no baseline yet, so the first burst of chat looks infinitely
        // louder than the nothing that came before it. Sit out one window.
        long since = watchingSince.computeIfAbsent(event.channelId(), c -> now);
        if (now - since < WINDOW_MS) {
            return false;
        }

        // Baseline is the 25s *before* the recent slice, not the whole window.
        // Counting the full 30s meant the spike was inside its own baseline:
        // chat goes wild -> baseline goes up too -> ratio never clears 3x and
        // the loudest moments were the ones it missed.
        double expected5s = (baseline25s / (double) baselineSeconds()) * RECENT_MS / 1000.0;

        return recent5s >= MIN_MESSAGES && recent5s >= SPIKE_MULTIPLIER * Math.max(expected5s, 1.0);
    }

    /** Length of the baseline period: the window minus the recent slice. */
    private static long baselineSeconds() {
        return (WINDOW_MS - RECENT_MS) / 1000;
    }

    private static long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }
}
