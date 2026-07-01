package com.swarmsight.authority.arena;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * A registered agent reached over HTTP at its own endpoint. Built per agent by
 * {@link HttpAgentFactory}. The endpoint is untrusted: the URL is re-validated
 * before every call (DNS-rebinding defence), the request carries the per-agent
 * bearer secret so the agent can trust the caller, and the client is bounded by
 * connect and read timeouts. The agent only proposes; Authority decides.
 */
public class HttpAgent implements Agent {

    private final RestClient restClient;
    private final String actUrl;
    private final String callSecret;
    private final String agentId;
    private final AgentEndpointValidator validator;

    public HttpAgent(RestClient restClient, String actUrl, String callSecret, String agentId,
            AgentEndpointValidator validator) {
        this.restClient = restClient;
        this.actUrl = actUrl;
        this.callSecret = callSecret;
        this.agentId = agentId;
        this.validator = validator;
    }

    record ActRequest(String scenario_id, Map<String, Object> inputs) {
    }

    record ActResponse(
            @JsonProperty("proposed_action") String proposedAction,
            String rationale,
            String draft) {
    }

    @Override
    public Decision act(Scenario scenario) {
        // Re-validate at call time, not just at registration: a host that
        // resolved to a public IP when registered could resolve to an internal
        // one now.
        validator.validate(actUrl);

        RestClient.RequestBodySpec spec = restClient.post()
                .uri(actUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-SwarmSight-Agent", agentId);
        if (callSecret != null && !callSecret.isBlank()) {
            spec = spec.header("Authorization", "Bearer " + callSecret);
        }

        ActResponse response = spec
                .body(new ActRequest(scenario.id(), scenario.inputs()))
                .retrieve()
                .body(ActResponse.class);
        if (response == null) {
            throw new IllegalStateException("Agent returned no decision for " + scenario.id());
        }
        return new Decision(response.proposedAction(), response.rationale(), response.draft());
    }
}
