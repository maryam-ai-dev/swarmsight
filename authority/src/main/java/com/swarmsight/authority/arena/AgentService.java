package com.swarmsight.authority.arena;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Registers agents into the registry. Registration validates the endpoint
 * (refusing internal and non-routable targets), mints a per-agent call secret,
 * and derives a stable id. A team's agent is untrusted from here on; Authority
 * governs every call it makes.
 */
@Service
public class AgentService {

    public static class RegistrationException extends RuntimeException {
        public RegistrationException(String message) {
            super(message);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AgentRepository agents;
    private final AgentEndpointValidator validator;

    public AgentService(AgentRepository agents, AgentEndpointValidator validator) {
        this.agents = agents;
        this.validator = validator;
    }

    /** The registration result: the saved view plus the call secret, shown once. */
    public record Registered(RegisteredAgent.View agent, String callSecret) {
    }

    public Registered register(String name, String version, String endpointUrl, String environment,
            List<String> requestedActions, String ownerEmail) {
        if (name == null || name.isBlank()) {
            throw new RegistrationException("Name is required.");
        }
        if (requestedActions == null || requestedActions.isEmpty()) {
            throw new RegistrationException("At least one requested action is required.");
        }
        // SSRF gate: refuse internal/non-routable endpoints before anything is stored.
        validator.validate(endpointUrl);

        String id = uniqueId(slug(name) + "-" + slug(version));
        String callSecret = randomSecret();
        RegisteredAgent agent = new RegisteredAgent(
                id, name.trim(), version == null ? "" : version.trim(), endpointUrl.trim(),
                environment == null ? "" : environment.trim(), requestedActions, callSecret,
                ownerEmail, true, Instant.now(), "HA-09");
        agents.insert(agent);
        return new Registered(agent.view(), callSecret);
    }

    public List<RegisteredAgent.View> list() {
        return agents.findAll().stream().map(RegisteredAgent::view).toList();
    }

    public RegisteredAgent.View get(String id) {
        return agents.findById(id).map(RegisteredAgent::view)
                .orElseThrow(() -> new RegistrationException("No such agent."));
    }

    private String uniqueId(String base) {
        String id = base.isBlank() ? "agent" : base;
        if (!agents.existsById(id)) {
            return id;
        }
        for (int i = 0; i < 100; i++) {
            String candidate = id + "-" + Integer.toHexString(RANDOM.nextInt(0x10000));
            if (!agents.existsById(candidate)) {
                return candidate;
            }
        }
        throw new RegistrationException("Could not allocate an agent id.");
    }

    private static String slug(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
    }

    private static String randomSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
