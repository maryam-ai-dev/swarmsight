package com.swarmsight.authority.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swarmsight.authority.policy.Guard;
import com.swarmsight.authority.policy.Guard.Clause;
import com.swarmsight.authority.policy.Level;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The trusted intake step for policy: reads a piece of legislation or policy text
 * and proposes the governance guards it implies, plus the date it comes into
 * force. It reasons with Claude when an extraction key is configured, and falls
 * back to a worked example (the section 21 abolition) offline.
 *
 * <p>Like the document extractor, this only ever <em>proposes</em>. The output is
 * a candidate that a human reviews on the Policy Workbench (diff + shadow replay)
 * and a service owner activates. Nothing it returns governs a real decision until
 * that human step. So a misread clause is caught at review, never enacted.
 */
@Component
public class PolicyChangeExtractor {

    private static final Logger log = LoggerFactory.getLogger(PolicyChangeExtractor.class);

    // The input keys the housing policy reasons over; guards may only test these.
    private static final String KEYS =
            "tenancy_status, eviction_risk, dependent_children, eviction_notice, section_21_ground";

    private static final String SYSTEM = "You convert a piece of UK housing legislation or policy "
            + "into governance guards for an automated casework system. A guard raises the required "
            + "human-oversight level when its condition holds.\n"
            + "Return ONLY a JSON object with these keys:\n"
            + "  guardsToAdd: array of { name (snake_case), when: array of { key, op }, raiseTo, "
            + "reasonCode, brief, source },\n"
            + "  guardsToRemove: array of guard names (strings) the text repeals,\n"
            + "  commencementDate: the date it comes into force as \"YYYY-MM-DD\", or null if unstated,\n"
            + "  summary: one sentence describing the change.\n"
            + "Rules: 'key' must be one of [" + KEYS + "]. 'op' must be IS_TRUE or IS_ABSENT. "
            + "'raiseTo' must be one of L0, L1, L2, L3, L4_HUMAN (L4_HUMAN means a human must decide). "
            + "'source' should cite the legislation. If the text abolishes a no-fault eviction ground, "
            + "raise cases with section_21_ground to L4_HUMAN. Output the JSON object and nothing else.";

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Tika tika = new Tika();

    public PolicyChangeExtractor(
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

    public boolean live() {
        return !apiKey.isBlank();
    }

    /** The proposed guard changes, the commencement date, and a one-line summary. */
    public record PolicyExtraction(
            List<Guard> addGuards, List<String> removeGuards, Instant commencementDate, String summary) {
    }

    private enum Kind { PDF, OFFICE, TEXT }

    /** Extract from already-text content (legislation HTML, the offline fallback). */
    public PolicyExtraction extract(String policyText) {
        return extract(policyText.getBytes(StandardCharsets.UTF_8), "text/plain", "policy.txt");
    }

    /**
     * Extract from a source's raw bytes, read by content type: a PDF (e.g. a
     * council allocation scheme) goes to Claude natively, an office file via Tika,
     * else it is read as text. Falls back to the worked example offline or on error.
     */
    public PolicyExtraction extract(byte[] content, String mediaType, String fileName) {
        if (apiKey.isBlank()) {
            return fallback();
        }
        try {
            Object userContent = switch (classify(mediaType, fileName)) {
                case PDF -> pdfContent(content);
                case OFFICE -> "Policy / legislation text:\n\n"
                        + tika.parseToString(new ByteArrayInputStream(content));
                case TEXT -> "Policy / legislation text:\n\n" + new String(content, StandardCharsets.UTF_8);
            };
            return callClaude(userContent);
        } catch (Exception e) {
            log.warn("Policy extraction failed, using the worked-example fallback: {}", e.toString());
            return fallback();
        }
    }

    private Kind classify(String mediaType, String fileName) {
        String t = mediaType == null ? "" : mediaType.toLowerCase();
        String n = fileName == null ? "" : fileName.toLowerCase();
        if (t.contains("pdf") || n.endsWith(".pdf")) {
            return Kind.PDF;
        }
        if (t.contains("officedocument") || t.contains("msword") || t.contains("ms-excel")
                || t.contains("ms-powerpoint") || t.contains("opendocument")
                || n.endsWith(".docx") || n.endsWith(".doc") || n.endsWith(".xlsx")
                || n.endsWith(".xls") || n.endsWith(".pptx") || n.endsWith(".ppt")
                || n.endsWith(".odt") || n.endsWith(".rtf")) {
            return Kind.OFFICE;
        }
        return Kind.TEXT;
    }

    /** A native PDF content block: Claude reads the PDF (incl. scanned) directly. */
    private Object pdfContent(byte[] pdf) {
        String b64 = Base64.getEncoder().encodeToString(pdf);
        return List.of(
                Map.of("type", "document",
                        "source", Map.of(
                                "type", "base64",
                                "media_type", "application/pdf",
                                "data", b64)),
                Map.of("type", "text",
                        "text", "Extract the governance guards this policy implies, as instructed."));
    }

    /** One Messages API call; {@code userContent} is a string or a content-block list. */
    private PolicyExtraction callClaude(Object userContent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 2048);
        body.put("system", SYSTEM);
        body.put("messages", List.of(Map.of("role", "user", "content", userContent)));

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
        return parse(text.toString());
    }

    private PolicyExtraction parse(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0) {
            throw new IllegalStateException("No JSON object in policy-extraction response");
        }
        JsonNode json;
        try {
            json = objectMapper.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable policy-extraction JSON", e);
        }

        List<Guard> add = new ArrayList<>();
        for (JsonNode g : json.path("guardsToAdd")) {
            try {
                add.add(objectMapper.treeToValue(g, Guard.class));
            } catch (Exception e) {
                log.warn("Skipping an unreadable extracted guard: {}", e.toString());
            }
        }
        List<String> remove = new ArrayList<>();
        for (JsonNode n : json.path("guardsToRemove")) {
            if (n.isTextual()) {
                remove.add(n.asText());
            }
        }
        Instant commencement = parseDate(json.path("commencementDate").asText(null));
        String summary = json.path("summary").asText("");
        return new PolicyExtraction(add, remove, commencement, summary);
    }

    private Instant parseDate(String date) {
        if (date == null || date.isBlank() || "null".equalsIgnoreCase(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim()).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            log.warn("Unparseable commencement date '{}', leaving it unset", date);
            return null;
        }
    }

    /**
     * Offline worked example: the Renters' Rights abolition of the section 21
     * no-fault ground, which a human must now decide. Commencement is left unset
     * so the service proposes a sensible future date.
     */
    private PolicyExtraction fallback() {
        Guard g = new Guard(
                "section_21_abolition",
                List.of(new Clause("section_21_ground", Clause.Op.IS_TRUE)),
                Level.L4_HUMAN,
                "S21_ABOLISHED",
                "The section 21 'no-fault' eviction ground is abolished; a human must decide this case.",
                "Renters' Rights Act 2025 (worked example, offline)");
        return new PolicyExtraction(
                List.of(g), List.of(), null,
                "Abolishes the section 21 no-fault eviction ground; such cases require human decision.");
    }
}
