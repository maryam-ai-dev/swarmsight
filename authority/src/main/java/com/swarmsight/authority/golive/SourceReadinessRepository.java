package com.swarmsight.authority.golive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SourceReadinessRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<SourceReadinessSnapshot> mapper;

    public SourceReadinessRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = (rs, n) -> new SourceReadinessSnapshot(
                rs.getString("source_id"),
                rs.getString("connector"),
                rs.getInt("score"),
                rs.getInt("threshold"),
                read(rs.getString("flags")),
                rs.getObject("snapshot_at", OffsetDateTime.class).toInstant());
    }

    public List<SourceReadinessSnapshot> findAll() {
        return jdbc.query("SELECT * FROM source_readiness ORDER BY source_id", mapper);
    }

    /** Upsert a snapshot. Used to seed test sources and to refresh readings. */
    public void upsert(SourceReadinessSnapshot s) {
        jdbc.update(
                "INSERT INTO source_readiness (source_id, connector, score, threshold, flags, snapshot_at) "
                        + "VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?) ON CONFLICT (source_id) DO UPDATE SET "
                        + "connector = EXCLUDED.connector, score = EXCLUDED.score, threshold = EXCLUDED.threshold, "
                        + "flags = EXCLUDED.flags, snapshot_at = EXCLUDED.snapshot_at",
                s.sourceId(), s.connector(), s.score(), s.threshold(), write(s.flags()),
                OffsetDateTime.ofInstant(s.snapshotAt(), ZoneOffset.UTC));
    }

    public void delete(String sourceId) {
        jdbc.update("DELETE FROM source_readiness WHERE source_id = ?", sourceId);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise flags", e);
        }
    }

    private List<String> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not read flags", e);
        }
    }
}
