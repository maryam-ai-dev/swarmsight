package com.swarmsight.authority.arena;

import java.time.Instant;
import java.util.List;

/**
 * A registered agent: a team's own agent service, at its own HTTP endpoint, that
 * Authority governs. The {@code endpointUrl} is the full URL Authority POSTs to
 * (the agent's {@code /agent/act}). The {@code callSecret} is the bearer token
 * Authority presents so the agent can trust the caller; it is never returned in
 * a listing, only once at registration.
 */
public record RegisteredAgent(
        String id,
        String name,
        String version,
        String endpointUrl,
        String environment,
        List<String> requestedActions,
        String callSecret,
        String ownerEmail,
        boolean active,
        Instant createdAt,
        String workflow) {

    /** Safe projection: no call secret. */
    public record View(
            String id,
            String name,
            String version,
            String endpointUrl,
            String environment,
            List<String> requestedActions,
            String ownerEmail,
            boolean active,
            Instant createdAt,
            String workflow) {
    }

    public View view() {
        return new View(id, name, version, endpointUrl, environment, requestedActions,
                ownerEmail, active, createdAt, workflow);
    }
}
