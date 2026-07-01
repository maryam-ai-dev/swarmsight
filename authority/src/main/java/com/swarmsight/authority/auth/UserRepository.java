package com.swarmsight.authority.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<User> mapper = (rs, n) -> new User(
            UUID.fromString(rs.getString("id")),
            rs.getString("email"),
            rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")),
            rs.getString("display_name"),
            rs.getBoolean("active"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Look up by login email, case-insensitive. */
    public Optional<User> findByEmail(String email) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT * FROM users WHERE lower(email) = lower(?)", mapper, email));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findById(UUID id) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT * FROM users WHERE id = ?", mapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<User> findAll() {
        return jdbc.query("SELECT * FROM users ORDER BY created_at", mapper);
    }

    public boolean existsByEmail(String email) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE lower(email) = lower(?)", Integer.class, email);
        return n != null && n > 0;
    }

    public User insert(User u) {
        jdbc.update(
                "INSERT INTO users (id, email, password_hash, role, display_name, active, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                u.id(), u.email(), u.passwordHash(), u.role().name(), u.displayName(), u.active(),
                OffsetDateTime.ofInstant(u.createdAt(), java.time.ZoneOffset.UTC));
        return u;
    }

    public void updatePasswordHash(UUID id, String passwordHash) {
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, id);
    }

    public void setActive(UUID id, boolean active) {
        jdbc.update("UPDATE users SET active = ? WHERE id = ?", active, id);
    }
}
