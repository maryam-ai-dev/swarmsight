package com.swarmsight.authority.arena;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Matches a described agent task to the department's stored workflows, so the
 * agent is assigned the department's own policy rather than having one forced on
 * it. Claude classifies the task against the catalog when a key is set; a
 * keyword fallback keeps it working offline. It only proposes a match; the
 * assurance run and certification still apply against the matched policy.
 */
@Component
public class WorkflowMatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkflowMatcher.class);

    public record Match(String workflowId, String workflowName, String rationale, boolean live) {
    }

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final WorkflowCatalog catalog;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public WorkflowMatcher(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            WorkflowCatalog catalog,
            @Value("${swarmsight.extraction.api-key:}") String apiKey,
            @Value("${swarmsight.extraction.model:claude-opus-4-8}") String model,
            @Value("${swarmsight.extraction.base-url:https://api.anthropic.com}") String baseUrl) {
        this.http = builder.build();
        this.objectMapper = objectMapper;
        this.catalog = catalog;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    public Match match(String description) {
        if (apiKey.isBlank() || description == null || description.isBlank()) {
            return fallback(description);
        }
        try {
            return matchWithClaude(description);
        } catch (Exception e) {
            log.warn("Workflow match failed, using keyword fallback: {}", e.toString());
            return fallback(description);
        }
    }

    private Match matchWithClaude(String description) throws Exception {
        StringBuilder cat = new StringBuilder();
        for (WorkflowCatalog.Workflow w : catalog.all()) {
            cat.append(w.id()).append(": ").append(w.name()).append(" — ").append(w.summary()).append("\n");
        }
        String system = "You assign an assistant to one of a department's workflows. Here are the "
                + "workflows (id: name -- what it covers):\n" + cat
                + "\nGiven a description of what an assistant should do, return ONLY a JSON object "
                + "{\"workflowId\": \"<one id from the list>\", \"rationale\": \"<one short sentence>\"}. "
                + "Pick the single best-matching workflow.";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 400);
        body.put("system", system);
        body.put("messages", List.of(Map.of("role", "user", "content", "Assistant task:\n\n" + description)));

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
        JsonNode json = objectMapper.readTree(s.substring(start, end + 1));
        String id = json.path("workflowId").asText("");
        WorkflowCatalog.Workflow w = catalog.byId(id).orElseGet(() -> catalog.all().get(0));
        String rationale = json.path("rationale").asText("Best match for the described task.");
        return new Match(w.id(), w.name(), rationale, true);
    }

    /** Keyword scoring across the catalog; defaults to the first workflow. */
    private Match fallback(String description) {
        String d = description == null ? "" : description.toLowerCase(Locale.ROOT);
        String id = "HA-09";
        if (d.contains("homeless") || d.contains("priority need") || d.contains("rough sleep")) {
            id = "HL-01";
        } else if (d.contains("repair") || d.contains("hazard") || d.contains("maintenance") || d.contains("damp")) {
            id = "RP-01";
        } else if (d.contains("foi") || d.contains("freedom of information") || d.contains("redact")
                || d.contains("records request")) {
            id = "FOI-01";
        } else if (d.contains("appeal") || d.contains("eviction")) {
            id = "HA-09";
        }
        WorkflowCatalog.Workflow w = catalog.byId(id).orElseGet(() -> catalog.all().get(0));
        return new Match(w.id(), w.name(), "Matched by keywords in the task description.", false);
    }
}
