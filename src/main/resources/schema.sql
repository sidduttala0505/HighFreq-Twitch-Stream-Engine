-- Audit trail of every spike we acted on. Spring Boot runs this at startup.
CREATE TABLE IF NOT EXISTS hype_spikes (
    id             BIGSERIAL PRIMARY KEY,
    channel        TEXT        NOT NULL,
    recent_count   BIGINT      NOT NULL,
    baseline_count BIGINT      NOT NULL,
    clip_url       TEXT,
    detected_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every query I actually run is "what happened lately", usually per channel.
CREATE INDEX IF NOT EXISTS idx_hype_spikes_detected_at ON hype_spikes (detected_at DESC);
CREATE INDEX IF NOT EXISTS idx_hype_spikes_channel ON hype_spikes (channel, detected_at DESC);
