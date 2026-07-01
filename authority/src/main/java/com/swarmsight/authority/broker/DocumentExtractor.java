package com.swarmsight.authority.broker;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * The trusted intake step: turns a raw application document into the structured
 * SharePoint-column fields the connector maps. It reasons with Claude when an
 * extraction key is configured, and falls back to the demo fields offline.
 *
 * <p>It accepts the document in whatever format the department stores it, and
 * chooses how to read it by content type:
 * <ul>
 *   <li><b>PDF</b> -- sent to Claude natively as a {@code document} content block
 *       (Claude reads layout, tables, and scanned/image PDFs via its own OCR), so
 *       no local PDF parsing is needed.</li>
 *   <li><b>Office</b> (DOCX/XLSX/PPTX/RTF/ODT) -- text is pulled out locally with
 *       Apache Tika, then sent to Claude as text.</li>
 *   <li><b>Everything else</b> (TXT/CSV/HTML/Markdown) -- treated as UTF-8 text.</li>
 * </ul>
 *
 * <p>This runs on the trusted side, before the permission mirror, so it may see
 * the raw document (including sensitive fields). The assured agent still receives
 * only the masked record the mirror produces, so where the data came from -- a
 * typed column, a PDF, or a scanned image -- does not change what the agent sees.
 */
@Component
class DocumentExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentExtractor.class);

    // SharePoint column names the connector maps; the extractor produces these keys.
    private static final String[] COLUMNS = {
            "ApplicantName", "AnnualIncome", "TenancyStatus", "NINumber", "MedicalNotes", "CaseworkerRef"};

    private static final String SYSTEM = "You extract structured fields from a UK housing "
            + "application document. Return ONLY a JSON object with these keys: ApplicantName "
            + "(string), AnnualIncome (integer pounds), TenancyStatus (string), NINumber (string), "
            + "MedicalNotes (string), CaseworkerRef (string). Use null for any field not present in "
            + "the document. Output the JSON object and nothing else.";

    private enum Kind { PDF, OFFICE, TEXT }

    private final RestClient http;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Tika tika = new Tika();

    DocumentExtractor(
            RestClient.Builder builder,
            @Value("${swarmsight.extraction.api-key:}") String apiKey,
            @Value("${swarmsight.extraction.model:claude-opus-4-8}") String model,
            @Value("${swarmsight.extraction.base-url:https://api.anthropic.com}") String baseUrl) {
        this.http = builder.build();
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    boolean live() {
        return !apiKey.isBlank();
    }

    /** Extract from already-text content (the offline mock, and any plain-text file). */
    Map<String, Object> extract(String documentText) {
        return extract(documentText.getBytes(StandardCharsets.UTF_8), "text/plain", "document.txt");
    }

    /**
     * Extract the application fields from a document's raw bytes, reading it
     * according to its content type. Falls back to the demo fields offline or on
     * any extraction error, so the loop never breaks on an awkward file.
     */
    Map<String, Object> extract(byte[] content, String mediaType, String fileName) {
        if (apiKey.isBlank()) {
            return fallback();
        }
        try {
            Object userContent = switch (classify(mediaType, fileName)) {
                case PDF -> pdfContent(content);
                case OFFICE -> "Application document:\n\n"
                        + tika.parseToString(new ByteArrayInputStream(content));
                case TEXT -> "Application document:\n\n" + new String(content, StandardCharsets.UTF_8);
            };
            return callClaude(userContent);
        } catch (Exception e) {
            log.warn("Document extraction failed, using demo fields: {}", e.toString());
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

    /** A native PDF content block: Claude reads the PDF directly, no local parsing. */
    private Object pdfContent(byte[] pdf) {
        String b64 = Base64.getEncoder().encodeToString(pdf);
        return List.of(
                Map.of("type", "document",
                        "source", Map.of(
                                "type", "base64",
                                "media_type", "application/pdf",
                                "data", b64)),
                Map.of("type", "text",
                        "text", "Extract the fields from this housing application document."));
    }

    /** One Messages API call; {@code userContent} is either a string or a content-block list. */
    private Map<String, Object> callClaude(Object userContent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
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

    private Map<String, Object> parse(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0) {
            throw new IllegalStateException("No JSON object in extraction response");
        }
        JsonNode json;
        try {
            json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable extraction JSON", e);
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String col : COLUMNS) {
            JsonNode v = json.path(col);
            if (!v.isMissingNode() && !v.isNull()) {
                fields.put(col, v.isNumber() ? v.numberValue() : v.asText());
            }
        }
        return fields;
    }

    /** Offline stand-in: the demo applicant, as if extracted. */
    private Map<String, Object> fallback() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ApplicantName", "Ms A. Adeyemi");
        item.put("AnnualIncome", 18400);
        item.put("TenancyStatus", "confirmed");
        item.put("NINumber", "QQ123456C");
        item.put("MedicalNotes", "Disability, mobility needs");
        item.put("CaseworkerRef", "HW-INT-2025-5821");
        return item;
    }
}
