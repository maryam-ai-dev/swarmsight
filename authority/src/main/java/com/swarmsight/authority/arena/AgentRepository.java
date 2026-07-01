package com.swarmsight.authority.arena;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AgentRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<RegisteredAgent> mapper;

    public AgentRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = (rs, n) -> new RegisteredAgent(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("version"),
                rs.getString("endpoint_url"),
                rs.getString("environment"),
                readList(rs.getString("requested_actions")),
                rs.getString("call_secret"),
                rs.getString("owner_email"),
                rs.getBoolean("active"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getString("workflow"));
    }

    public List<RegisteredAgent> findAll() {
        return jdbc.query("SELECT * FROM agents ORDER BY created_at", mapper);
    }

    public Optional<RegisteredAgent> findById(String id) {
        try {
            return Optional.of(jdbc.queryForObject("SELECT * FROM agents WHERE id = ?", mapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsById(String id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM agents WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    public RegisteredAgent insert(RegisteredAgent a) {
        jdbc.update(
                "INSERT INTO agents (id, name, version, endpoint_url, environment, requested_actions, "
                        + "call_secret, owner_email, active, created_at, workflow) "
                        + "VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?)",
                a.id(), a.name(), a.version(), a.endpointUrl(), a.environment(),
                writeList(a.requestedActions()), a.callSecret(), a.ownerEmail(), a.active(),
                OffsetDateTime.ofInstant(a.createdAt(), ZoneOffset.UTC), a.workflow());
        return a;
    }

    private String writeList(List<String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise requested_actions", e);
        }
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not read requested_actions", e);
        }
    }
}
