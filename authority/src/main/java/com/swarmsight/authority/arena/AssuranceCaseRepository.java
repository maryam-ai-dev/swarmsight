package com.swarmsight.authority.arena;

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
public class AssuranceCaseRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<AssuranceCase> mapper;

    public AssuranceCaseRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = (rs, n) -> new AssuranceCase(
                rs.getString("id"),
                rs.getString("agent_id"),
                read(rs.getString("claims"), new TypeReference<List<AssuranceCase.Claim>>() {}),
                rs.getString("built_by"),
                rs.getObject("built_at", OffsetDateTime.class).toInstant());
    }

    public void insert(AssuranceCase ac) {
        jdbc.update(
                "INSERT INTO assurance_cases (id, agent_id, claims, built_by, built_at) "
                        + "VALUES (?, ?, CAST(? AS jsonb), ?, ?) ON CONFLICT (id) DO NOTHING",
                ac.id(), ac.agentId(), write(ac.claims()), ac.builtBy(),
                OffsetDateTime.ofInstant(ac.builtAt(), ZoneOffset.UTC));
    }

    public Optional<AssuranceCase> findById(String id) {
        return jdbc.query("SELECT * FROM assurance_cases WHERE id = ?", mapper, id).stream().findFirst();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise assurance case", e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read assurance case", e);
        }
    }
}
