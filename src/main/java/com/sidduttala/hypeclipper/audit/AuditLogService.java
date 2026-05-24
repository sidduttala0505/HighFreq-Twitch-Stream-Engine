package com.sidduttala.hypeclipper.audit;

import com.sidduttala.hypeclipper.detect.SpikeSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

/**
 * Writes one row per spike we acted on, so there's a history to query later
 * ("which channel popped off most this week") rather than just log lines that
 * scroll away.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final String INSERT = """
            INSERT INTO hype_spikes (channel, recent_count, baseline_count, clip_url, detected_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public AuditLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(SpikeSignal spike, String clipUrl) {
        try {
            jdbc.update(INSERT,
                    spike.channelId(),
                    spike.recentCount(),
                    spike.baselineCount(),
                    clipUrl,
                    new Timestamp(spike.detectedAtMs()));
        } catch (Exception e) {
            // Losing an audit row is annoying, but it is not a reason to stop
            // detecting spikes.
            log.warn("could not write audit row for #{}: {}", spike.channelId(), e.getMessage());
        }
    }
}
