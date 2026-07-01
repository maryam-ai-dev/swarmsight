package com.swarmsight.authority.arena;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Turns a department's free-text description of what it wants an agent to do into
 * a set of capability checkboxes: for each known action, whether to grant it, with
 * a short why. Citizen-facing actions (sending a decision, closing a case,
 * releasing records) default to off and are flagged, because the whole point of
 * assurance is that a human, not the agent, takes those. Claude proposes; the
 * department ticks; nothing here grants anything on its own.
 */
@Component
public class CapabilityDeriver {

    private static final Logger log = LoggerFactory.getLogger(CapabilityDeriver.class);

    /** The housing action catalog: action, human label, and whether it is citizen-facing. */
    private static final List<String[]> CATALOG = List.of(
            new String[] {"draft_response", "Draft a response for officer review", "false"},
            new String[] {"request_evidence", "Request missing evidence", "false"},
            new String[] {"escalate", "Escalate to an officer", "false"},
            new String[] {"summarise_case", "Summarise / triage a case", "false"},
            new String[] {"send_decision", "Send a decision to the citizen", "true"},
            new String[] {"close_case", "Close a case", "true"},
            new String[] {"release_records", "Release records", "true"});

    private static final String SYSTEM = "You map a described government-agent purpose to the actions "
            + "it should be allowed to take, from a fixed catalog. Return ONLY a JSON object mapping each "
            + "action name to {allow: boolean, rationale: string}. Safe internal actions (draft_response, "
            + "request_evidence, escalate, summarise_case) may be allowed when the description supports "
            + "them. Citizen-facing actions (send_decision, close_case, release_records) must be allow=false "
            + "unless the description explicitly and appropriately requires the agent itself to do them; "
            + "default these to false and say why in the rationale. Output the JSON object and nothing else.";

    public record CapabilityProposal(
            String action, String label, boolean citizenFacing, boolean recommendedAllow, String rationale) {
    }

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public CapabilityDeriver(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${swarmsight.extraction.api-key:}") String apiKey,
            @Value("${swarmsight.extraction.model:claude-opus-4-8}") String model,
            @Value("${swarmsight.extraction.base-url:https://api.anthropic.com}") String baseUrl) {
        this.http = builder.build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    public List<CapabilityProposal> derive(String description) {
        Map<String, JsonNode> claude = apiKey.isBlank() || description == null || description.isBlank()
                ? Map.of() : safeAsk(description);
        List<CapabilityProposal> out = new ArrayList<>();
        for (String[] row : CATALOG) {
            String action = row[0];
            boolean citizenFacing = Boolean.parseBoolean(row[2]);
            boolean allow = !citizenFacing;           // deterministic fallback
            String rationale = citizenFacing
                    ? "Citizen-facing: a human takes this, not the agent."
                    : "Safe internal action.";
            JsonNode rec = claude.get(action);
            if (rec != null) {
                // Even with Claude, never auto-grant a citizen-facing action.
                allow = rec.path("allow").asBoolean(allow) && !citizenFacing;
                rationale = rec.path("rationale").asText(rationale);
            }
            out.add(new CapabilityProposal(action, row[1], citizenFacing, allow, rationale));
        }
        return out;
    }

    private Map<String, JsonNode> safeAsk(String description) {
        try {
            return ask(description);
        } catch (Exception e) {
            log.warn("Capability derivation failed, using defaults: {}", e.toString());
            return Map.of();
        }
    }

    private Map<String, JsonNode> ask(String description) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("system", SYSTEM);
        body.put("messages", List.of(Map.of("role", "user",
                "content", "Agent purpose:\n\n" + description)));

        JsonNode res = http.post()
                .uri(URI.create(baseUrl + "/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        StringBuilder text = new StringBuilder();
        for (JsonNode block : res.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        String s = text.toString();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end < 0) {
            return Map.of();
        }
        JsonNode json = objectMapper.readTree(s.substring(start, end + 1));
        Map<String, JsonNode> map = new LinkedHashMap<>();
        json.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue()));
        return map;
    }
}
