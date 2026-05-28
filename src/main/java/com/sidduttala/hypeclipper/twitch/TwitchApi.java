package com.sidduttala.hypeclipper.twitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin wrapper over the bits of the Twitch Helix API this project needs.
 *
 * Both calls here need a user access token (the same one clip creation needs),
 * not an app token.
 */
@Component
public class TwitchApi {

    private static final Logger log = LoggerFactory.getLogger(TwitchApi.class);

    private static final String HELIX = "https://api.twitch.tv/helix";

    // ~16s of patience. Clips usually land in 4-6s; much past this and the
    // moment is stale anyway.
    private static final int POLL_ATTEMPTS = 8;
    private static final long POLL_INTERVAL_MS = 2000;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;

    /** A login's numeric id never changes, so there's no reason to ask twice. */
    private final Map<String, String> broadcasterIds = new ConcurrentHashMap<>();

    @Value("${TWITCH_CLIENT_ID:}")
    private String clientId;

    @Value("${TWITCH_USER_TOKEN:}")
    private String userToken;

    public TwitchApi(ObjectMapper json) {
        this.json = json;
    }

    @PostConstruct
    void reportConfig() {
        if (isConfigured()) {
            log.info("twitch credentials found, clip creation is on");
        } else {
            log.warn("TWITCH_CLIENT_ID / TWITCH_USER_TOKEN not set - spikes will alert but not clip");
        }
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !userToken.isBlank();
    }

    /** Numeric broadcaster id for a login, or null if Twitch doesn't know it. */
    public String getBroadcasterId(String login) throws Exception {
        String cached = broadcasterIds.get(login);
        if (cached != null) {
            return cached;
        }

        HttpResponse<String> res = send(authed(HELIX + "/users?login=" + login).GET().build());
        JsonNode data = json.readTree(res.body()).path("data");
        if (data.isEmpty()) {
            log.warn("twitch doesn't know a channel called '{}' ({}): {}", login, res.statusCode(), res.body());
            return null;
        }

        String id = data.get(0).path("id").asText();
        broadcasterIds.put(login, id);
        return id;
    }

    /**
     * Clip creation only works on a live stream, so check before burning a
     * call on it. An offline channel returns an empty data array.
     */
    public boolean isLive(String login) throws Exception {
        HttpResponse<String> res = send(authed(HELIX + "/streams?user_login=" + login).GET().build());
        return !json.readTree(res.body()).path("data").isEmpty();
    }

    /**
     * Asks Twitch to cut a clip of roughly the last 30 seconds of the given
     * live channel. Returns the clip id - the clip is not ready yet.
     */
    public String createClip(String broadcasterId) throws Exception {
        HttpResponse<String> res = send(authed(HELIX + "/clips?broadcaster_id=" + broadcasterId)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());

        // 202 Accepted, not 200 - Twitch has taken the job, not finished it.
        if (res.statusCode() != 202) {
            throw new IllegalStateException("clip create failed (" + res.statusCode() + "): " + res.body());
        }

        JsonNode data = json.readTree(res.body()).path("data");
        if (data.isEmpty()) {
            throw new IllegalStateException("clip create returned no id: " + res.body());
        }
        return data.get(0).path("id").asText();
    }

    /**
     * Waits for Twitch to finish rendering the clip and hands back the URL.
     *
     * The clip exists as soon as create returns, but its `url` is blank until
     * processing finishes - reading it straight away just gets you an empty
     * string, which is what I was posting to Discord for an embarrassing
     * number of attempts.
     */
    public String waitForClipUrl(String clipId) throws Exception {
        for (int attempt = 0; attempt < POLL_ATTEMPTS; attempt++) {
            Thread.sleep(POLL_INTERVAL_MS);

            HttpResponse<String> res = send(authed(HELIX + "/clips?id=" + clipId).GET().build());
            JsonNode data = json.readTree(res.body()).path("data");
            if (!data.isEmpty()) {
                String url = data.get(0).path("url").asText("");
                if (!url.isBlank()) {
                    log.info("clip {} ready after {} polls", clipId, attempt + 1);
                    return url;
                }
            }
        }
        throw new IllegalStateException("clip " + clipId + " never finished processing");
    }

    private HttpRequest.Builder authed(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + userToken)
                .header("Client-Id", clientId);
    }

    private HttpResponse<String> send(HttpRequest req) throws Exception {
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 401) {
            // User tokens only last a few hours - this one bit me a lot.
            throw new IllegalStateException("Twitch says 401. TWITCH_USER_TOKEN has probably expired, "
                    + "re-run: twitch token -u -s 'clips:edit'");
        }
        return res;
    }
}
