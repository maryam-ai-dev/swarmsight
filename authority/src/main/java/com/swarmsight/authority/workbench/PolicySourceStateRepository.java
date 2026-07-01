package com.swarmsight.authority.workbench;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The last content the poller saw at each watched source. Used to detect a
 * change cheaply (a new content hash) so Claude is only called when the source
 * actually moves.
 */
@Repository
public class PolicySourceStateRepository {

    private final JdbcTemplate jdbc;

    public PolicySourceStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The content hash last seen at this source, if it has been checked before. */
    public Optional<String> lastHash(String uri) {
        return jdbc.queryForList(
                "SELECT last_content_hash FROM policy_source_state WHERE uri = ?", String.class, uri)
                .stream().findFirst();
    }

    /** Record a poll that found no change: only the checked time moves. */
    public void recordChecked(String uri, String policyId, Instant at) {
        jdbc.update(
                "INSERT INTO policy_source_state (uri, policy_id, last_checked_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (uri) DO UPDATE SET last_checked_at = EXCLUDED.last_checked_at, "
                        + "policy_id = EXCLUDED.policy_id",
                uri, policyId, ts(at));
    }

    /** Record a poll (or manual ingest) that saw new content: hash and both times move. */
    public void recordChanged(String uri, String policyId, String hash, Instant at) {
        jdbc.update(
                "INSERT INTO policy_source_state (uri, policy_id, last_content_hash, last_checked_at, "
                        + "last_changed_at) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (uri) DO UPDATE SET last_content_hash = EXCLUDED.last_content_hash, "
                        + "last_checked_at = EXCLUDED.last_checked_at, "
                        + "last_changed_at = EXCLUDED.last_changed_at, policy_id = EXCLUDED.policy_id",
                uri, policyId, hash, ts(at), ts(at));
    }

    private static OffsetDateTime ts(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
