package com.swarmsight.authority.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * A user account. The password hash never leaves the repository layer; the API
 * returns {@link View} instead, which omits it.
 */
public record User(
        UUID id,
        String email,
        String passwordHash,
        Role role,
        String displayName,
        boolean active,
        Instant createdAt) {

    /** The safe projection returned to clients: no password hash. */
    public record View(UUID id, String email, Role role, String displayName, boolean active) {
    }

    public View view() {
        return new View(id, email, role, displayName, active);
    }
}
