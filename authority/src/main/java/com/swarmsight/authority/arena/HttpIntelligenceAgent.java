package com.swarmsight.authority.arena;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The live agent: Intelligence reached over HTTP. This is where Intelligence
 * stops being a stub. The Arena calls /agent/act for each scenario. The agent
 * only ever proposes; Authority decides.
 */
@Component
public class HttpIntelligenceAgent implements Agent {

    private final RestClient restClient;

    public HttpIntelligenceAgent(
            RestClient.Builder builder,
            @Value("${swarmsight.intelligence.base-url:http://localhost:8000}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    record ActRequest(String scenario_id, Map<String, Object> inputs) {
    }

    record ActResponse(
            @JsonProperty("proposed_action") String proposedAction,
            String rationale) {
    }

    @Override
    public Decision act(Scenario scenario) {
        ActResponse response = restClient.post()
                .uri("/agent/act")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ActRequest(scenario.id(), scenario.inputs()))
                .retrieve()
                .body(ActResponse.class);
        if (response == null) {
            throw new IllegalStateException("Intelligence returned no decision for " + scenario.id());
        }
        return new Decision(response.proposedAction(), response.rationale());
    }
}
