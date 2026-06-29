package com.swarmsight.authority.incident;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class IncidentRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<Incident> mapper;

    public IncidentRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = (rs, n) -> new Incident(
                rs.getString("id"),
                rs.getString("agent_id"),
                Trigger.valueOf(rs.getString("trigger_type")),
                rs.getString("detail"),
                rs.getString("status"),
                rs.getString("reported_by"),
                rs.getObject("raised_at", OffsetDateTime.class).toInstant(),
                instant(rs.getObject("contained_at", OffsetDateTime.class)),
                instant(rs.getObject("resolved_at", OffsetDateTime.class)),
                read(rs.getString("containment")));
    }

    public void insert(Incident i) {
        jdbc.update(
                "INSERT INTO incidents (id, agent_id, trigger_type, detail, status, reported_by, raised_at, "
                        + "contained_at, resolved_at, containment) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb)) ON CONFLICT (id) DO NOTHING",
                i.id(), i.agentId(), i.trigger().name(), i.detail(), i.status(), i.reportedBy(),
                OffsetDateTime.ofInstant(i.raisedAt(), ZoneOffset.UTC),
                i.containedAt() == null ? null : OffsetDateTime.ofInstant(i.containedAt(), ZoneOffset.UTC),
                i.resolvedAt() == null ? null : OffsetDateTime.ofInstant(i.resolvedAt(), ZoneOffset.UTC),
                write(i.containment()));
    }

    public Optional<Incident> findById(String id) {
        return jdbc.query("SELECT * FROM incidents WHERE id = ?", mapper, id).stream().findFirst();
    }

    public List<Incident> findByAgent(String agentId) {
        return jdbc.query("SELECT * FROM incidents WHERE agent_id = ? ORDER BY raised_at DESC", mapper, agentId);
    }

    public List<Incident> findAll() {
        return jdbc.query("SELECT * FROM incidents ORDER BY raised_at DESC", mapper);
    }

    private static java.time.Instant instant(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise incident", e);
        }
    }

    private Incident.Containment read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Incident.Containment>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not read incident", e);
        }
    }
}
