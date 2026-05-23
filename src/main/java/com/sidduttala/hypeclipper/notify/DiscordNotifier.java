package com.sidduttala.hypeclipper.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Posts to a Discord webhook. No library and no auth - a webhook URL is just
 * a POST target, which is the whole appeal.
 */
@Component
public class DiscordNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${DISCORD_WEBHOOK_URL:}")
    private String webhookUrl;

    public void sendSpike(String channel, long recentCount, int seconds) {
        send("""
                {"content": "🔥 **HYPE SPIKE** in `%s` - %d messages in %d seconds!"}"""
                .formatted(channel, recentCount, seconds));
    }

    public void sendSpikeWithClip(String channel, long recentCount, int seconds, String clipUrl) {
        send("""
                {"content": "🔥 **HYPE SPIKE** in `%s` - %d messages in %ds! 🎬 Clip: %s"}"""
                .formatted(channel, recentCount, seconds, clipUrl));
    }

    private void send(String jsonBody) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("DISCORD_WEBHOOK_URL not set, skipping: {}", jsonBody);
            return;
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        // Fire and forget. If Discord is slow that's Discord's problem, we are
        // not holding up chat ingestion for it.
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    if (res.statusCode() >= 300) {
                        log.warn("discord rejected the post ({}): {}", res.statusCode(), res.body());
                    }
                })
                .exceptionally(err -> {
                    log.warn("discord post failed: {}", err.getMessage());
                    return null;
                });
    }
}
