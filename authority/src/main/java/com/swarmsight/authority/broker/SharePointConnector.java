package com.swarmsight.authority.broker;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * A department's SharePoint document library, reached over the Microsoft Graph
 * API. When Graph credentials are configured (tenant, client id and secret, and
 * a site + list) it reads live list items; otherwise it falls back to an
 * in-process mock so the demo, tests, and offline runs work with no tenant.
 *
 * <p>Either way it returns raw values plus the source's own per-field permission
 * and never masks: the broker's permission mirror does that. The
 * SharePoint-column to policy-field mapping is the same on both paths, so a test
 * list with the columns named below works against real SharePoint unchanged.
 * Per department you register one connector, one site/list, and one credential
 * set; column-level source permissions can be wired from SharePoint later (today
 * the source side is ALLOW and the department policy does the restricting).
 */
@Component
class SharePointConnector implements Connector {

    static final String NAME = "sharepoint-housing";
    private static final Logger log = LoggerFactory.getLogger(SharePointConnector.class);
    private static final String GRAPH = "https://graph.microsoft.com/v1.0";

    // A realistic free-text housing application, the offline stand-in for a
    // document fetched from SharePoint. Claude extracts the fields from prose like
    // this; the deterministic fallback returns the demo fields.
    private static final String MOCK_APPLICATION_TEXT = """
            HOUSING APPLICATION FORM HX-5821

            I am writing to apply for housing assistance. My name is Ms A. Adeyemi.
            I currently rent and my tenancy has been confirmed by my landlord. My
            annual income is approximately 18,400 pounds. My National Insurance
            number is QQ 12 34 56 C. I have a disability affecting my mobility and
            would appreciate any accessibility considerations. Internal caseworker
            reference: HW-INT-2025-5821.
            """;

    private final RestClient http;
    private final DocumentExtractor extractor;
    private final String mode;      // "document" (extract from a file) or "list"
    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String siteRef;   // pre-resolved site id, or "host:/sites/Name" to resolve
    private final String listRef;   // list id (preferred) or display name
    private final String caseRefColumn; // optional SharePoint column matched against the case ref
    private final boolean live;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    SharePointConnector(
            RestClient.Builder builder,
            DocumentExtractor extractor,
            @Value("${swarmsight.sharepoint.mode:document}") String mode,
            @Value("${swarmsight.sharepoint.tenant-id:}") String tenantId,
            @Value("${swarmsight.sharepoint.client-id:}") String clientId,
            @Value("${swarmsight.sharepoint.client-secret:}") String clientSecret,
            @Value("${swarmsight.sharepoint.site:}") String siteRef,
            @Value("${swarmsight.sharepoint.list:}") String listRef,
            @Value("${swarmsight.sharepoint.case-ref-column:}") String caseRefColumn) {
        this.http = builder.build();
        this.extractor = extractor;
        this.mode = mode;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.siteRef = siteRef;
        this.listRef = listRef;
        this.caseRefColumn = caseRefColumn;
        boolean document = "document".equalsIgnoreCase(mode);
        this.live = !tenantId.isBlank() && !clientId.isBlank() && !clientSecret.isBlank()
                && !siteRef.isBlank() && (document || !listRef.isBlank());
        log.info("SharePoint connector: mode={}, graph={}, extraction={}",
                document ? "document" : "list", live ? "live" : "mock",
                extractor.live() ? "Claude" : "fallback");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RawRecord fetch(CapabilityGrant grant) {
        // grant.resourceScope() identifies the document, e.g. "application/HX-5821".
        String caseRef = grant.resourceScope().contains("/")
                ? grant.resourceScope().substring(grant.resourceScope().indexOf('/') + 1)
                : grant.resourceScope();

        // document mode: read an application document and extract the fields from
        // it (the trusted intake step). list mode: read pre-filled list columns.
        Map<String, Object> graph;
        SourceDocumentRef document;
        if ("document".equalsIgnoreCase(mode)) {
            DocContent doc = live ? graphDocument(caseRef) : mockDocument();
            graph = extractor.extract(doc.bytes(), doc.mediaType(), doc.name());
            document = doc.ref();
        } else {
            Cols cols = live ? graphListItem(caseRef) : mockListItem();
            graph = cols.fields();
            document = cols.ref();
        }

        // Map SharePoint columns to the field names the sensitivity policy knows,
        // and record SharePoint's own per-field permission (the source side of the
        // mirror). The policy does the masking: NI -> MASK, medical -> DENY, and an
        // unmapped column -> DENY (fail closed).
        Map<String, Object> fields = new LinkedHashMap<>();
        Map<String, FieldEffect> sourcePermissions = new LinkedHashMap<>();
        put(fields, sourcePermissions, "applicant_name", graph.get("ApplicantName"), FieldEffect.ALLOW);
        put(fields, sourcePermissions, "income", graph.get("AnnualIncome"), FieldEffect.ALLOW);
        put(fields, sourcePermissions, "tenancy_status", graph.get("TenancyStatus"), FieldEffect.ALLOW);
        put(fields, sourcePermissions, "national_insurance", graph.get("NINumber"), FieldEffect.ALLOW);
        put(fields, sourcePermissions, "medical_notes", graph.get("MedicalNotes"), FieldEffect.ALLOW);
        put(fields, sourcePermissions, "internal_ref", graph.get("CaseworkerRef"), FieldEffect.ALLOW);

        return new RawRecord(grant.connector(), grant.resourceScope(), fields, sourcePermissions, document);
    }

    /** A document's raw bytes and content type, plus a reference to it (id, version, name). */
    private record DocContent(byte[] bytes, String mediaType, String name, SourceDocumentRef ref) {
    }

    /** A list item's column values plus a reference to it. */
    private record Cols(Map<String, Object> fields, SourceDocumentRef ref) {
    }

    /** A live housing application read from the department's SharePoint list. */
    private Cols graphListItem(String caseRef) {
        String token = token();
        String siteId = resolveSiteId(token);
        String listId = resolveListId(token, siteId);

        JsonNode items = http.get()
                .uri(URI.create(GRAPH + "/sites/" + siteId + "/lists/" + listId
                        + "/items?$expand=fields&$top=50"))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);

        JsonNode chosen = null;
        for (JsonNode item : items.path("value")) {
            JsonNode f = item.path("fields");
            if (caseRefColumn.isBlank()
                    || caseRef.equalsIgnoreCase(f.path(caseRefColumn).asText(""))) {
                chosen = item;
                break;
            }
        }
        if (chosen == null) {
            throw new IllegalStateException(
                    "No SharePoint list item found for case " + caseRef + " in list " + listRef);
        }
        JsonNode f = chosen.path("fields");

        Map<String, Object> item = new LinkedHashMap<>();
        for (String col : new String[] {
                "ApplicantName", "AnnualIncome", "TenancyStatus", "NINumber", "MedicalNotes", "CaseworkerRef"}) {
            if (f.has(col) && !f.path(col).isNull()) {
                item.put(col, f.path(col).isNumber() ? f.path(col).numberValue() : f.path(col).asText());
            }
        }
        SourceDocumentRef ref = new SourceDocumentRef(
                listRef + "/items/" + chosen.path("id").asText(),
                etag(chosen), listRef + " (list item)");
        return new Cols(item, ref);
    }

    /** A summary of an application document in the library, for the live caseload. */
    public record Application(String caseRef, String name, String version) {
    }

    private static final Pattern CASE_REF = Pattern.compile("([A-Za-z]{2}-?\\d{3,})");

    /**
     * List the application documents in the department's SharePoint library, one
     * per case, with a case ref parsed from the file name. This is the live
     * caseload: each file the borough holds is a case. Falls back to the mock.
     */
    public List<Application> listApplications() {
        if (!live) {
            return List.of(new Application("HX-5821", "Housing application (mock document).txt", "v1"));
        }
        String token = token();
        String siteId = resolveSiteId(token);
        List<Application> apps = new ArrayList<>();
        for (JsonNode c : driveChildren(siteId, token).path("value")) {
            if (!c.has("file")) {
                continue;
            }
            String name = c.path("name").asText("application");
            Matcher m = CASE_REF.matcher(name);
            // Only files whose name carries a case ref are cases. This keeps policy
            // documents and other library files out of the officer's caseload.
            if (!m.find()) {
                continue;
            }
            apps.add(new Application(m.group(1).toUpperCase(), name, etag(c)));
        }
        return apps;
    }

    /** A policy document held in the library, offered as a source for inference. */
    public record PolicyDoc(String name, String version, byte[] bytes, String mediaType) {
    }

    // A file is treated as a policy source (not a case) when its name says "policy".
    private static boolean looksLikePolicy(String name) {
        return name.toLowerCase().contains("policy");
    }

    // A realistic offline policy document, the stand-in when no tenant is set, so
    // policy inference from a document works in the demo without SharePoint.
    private static final String MOCK_POLICY_TEXT = """
            HOMELESSNESS TRIAGE POLICY (HL-01)

            An assistant may read an approach, summarise it, request missing
            evidence, and draft an internal triage note for an officer. It must
            never decide or send a decision to an applicant.

            Where a household is homeless or threatened with homelessness and a
            vulnerable person is present (a dependent child, a pregnant person, or
            a person with a disability or serious health condition), the case is
            priority need and must be escalated to an officer. Where a key document
            proving the loss of a settled home is missing, request it first.

            This policy takes effect on 1 April 2026.
            """;

    /**
     * List the policy documents in the library (files whose name mentions
     * "policy"), so a service owner can pick one to infer guards from. Falls back
     * to a single mock policy document offline.
     */
    public List<PolicyDoc> listPolicyDocuments() {
        if (!live) {
            return List.of(new PolicyDoc(
                    "Policy-Homelessness-Triage-HL-01.txt", "v1", null, "text/plain"));
        }
        String token = token();
        String siteId = resolveSiteId(token);
        List<PolicyDoc> docs = new ArrayList<>();
        for (JsonNode c : driveChildren(siteId, token).path("value")) {
            if (!c.has("file")) {
                continue;
            }
            String name = c.path("name").asText("");
            if (looksLikePolicy(name)) {
                docs.add(new PolicyDoc(name, etag(c), null, c.path("file").path("mimeType").asText("")));
            }
        }
        return docs;
    }

    /**
     * Fetch a policy document by a name fragment (e.g. "HL-01" or "Homelessness"),
     * returning its raw bytes for the extractor. Used by policy inference: the
     * department's own policy document becomes the source a proposed change is
     * derived from. Falls back to the mock policy text offline.
     */
    public PolicyDoc fetchPolicyDocument(String nameContains) {
        if (!live) {
            return new PolicyDoc("Policy-Homelessness-Triage-HL-01.txt", "v1",
                    MOCK_POLICY_TEXT.getBytes(StandardCharsets.UTF_8), "text/plain");
        }
        String token = token();
        String siteId = resolveSiteId(token);
        String needle = nameContains == null ? "" : nameContains.toLowerCase();
        JsonNode chosen = null;
        for (JsonNode c : driveChildren(siteId, token).path("value")) {
            if (!c.has("file") || !looksLikePolicy(c.path("name").asText(""))) {
                continue;
            }
            String name = c.path("name").asText("").toLowerCase();
            if (chosen == null || name.contains(needle)) {
                chosen = c;
                if (name.contains(needle)) {
                    break;
                }
            }
        }
        if (chosen == null) {
            throw new IllegalStateException(
                    "No policy document matching '" + nameContains + "' in the library.");
        }
        byte[] content = download(chosen, siteId, token);
        return new PolicyDoc(chosen.path("name").asText("policy"), etag(chosen), content,
                chosen.path("file").path("mimeType").asText(""));
    }

    /** List the files at the drive root. Shared by the caseload and policy listers. */
    private JsonNode driveChildren(String siteId, String token) {
        return http.get()
                .uri(URI.create(GRAPH + "/sites/" + siteId + "/drive/root/children?$top=100"))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);
    }

    /** Download a drive child's bytes, preferring its pre-authenticated URL. */
    private byte[] download(JsonNode child, String siteId, String token) {
        String downloadUrl = child.path("@microsoft.graph.downloadUrl").asText("");
        byte[] content;
        if (!downloadUrl.isBlank()) {
            content = http.get().uri(URI.create(downloadUrl)).retrieve().body(byte[].class);
        } else {
            content = http.get()
                    .uri(URI.create(GRAPH + "/sites/" + siteId + "/drive/items/"
                            + child.path("id").asText() + "/content"))
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(byte[].class);
        }
        return content == null ? new byte[0] : content;
    }

    /**
     * Read an application document from the site's default document library.
     * Picks the file whose name contains the case ref, else the first. Returns
     * the raw bytes and Graph's reported content type; the extractor decides how
     * to read it (PDF natively, office formats via Tika, else as text), so any
     * format the department stores works.
     */
    private DocContent graphDocument(String caseRef) {
        String token = token();
        String siteId = resolveSiteId(token);

        JsonNode chosen = null;
        for (JsonNode child : driveChildren(siteId, token).path("value")) {
            String childName = child.path("name").asText("");
            // Only case files are candidates; a policy document is never a case.
            if (!child.has("file") || looksLikePolicy(childName)) {
                continue;
            }
            if (chosen == null || childName.toLowerCase().contains(caseRef.toLowerCase())) {
                chosen = child;
            }
        }
        if (chosen == null) {
            throw new IllegalStateException("No document found for case " + caseRef + " in the library");
        }
        String name = chosen.path("name").asText("application");
        String mediaType = chosen.path("file").path("mimeType").asText("");
        byte[] content = download(chosen, siteId, token);
        SourceDocumentRef ref = new SourceDocumentRef(chosen.path("id").asText(), etag(chosen), name);
        return new DocContent(content, mediaType, name, ref);
    }

    /**
     * A first-run health check: report the connector mode and, when live, test
     * each Graph step (token, site, then drive or list) so a misconfiguration
     * names the failing step. Returns status only, never secrets.
     */
    Map<String, Object> diagnose() {
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        boolean document = "document".equalsIgnoreCase(mode);
        r.put("mode", document ? "document" : "list");
        r.put("graph", live ? "live" : "mock");
        r.put("extraction", extractor.live() ? "claude" : "fallback");
        if (!live) {
            r.put("ok", true);
            r.put("message", "Using the in-process mock. Set SHAREPOINT_* to read live SharePoint.");
            return r;
        }
        try {
            String token = token();
            r.put("token", "ok");
            String siteId = resolveSiteId(token);
            r.put("siteId", siteId);
            if (document) {
                JsonNode children = http.get()
                        .uri(URI.create(GRAPH + "/sites/" + siteId + "/drive/root/children?$top=50"))
                        .header("Authorization", "Bearer " + token).retrieve().body(JsonNode.class);
                long files = 0;
                for (JsonNode c : children.path("value")) {
                    if (c.has("file")) {
                        files++;
                    }
                }
                r.put("documentLibraryFiles", files);
            } else {
                String listId = resolveListId(token, siteId);
                r.put("listId", listId);
            }
            r.put("ok", true);
        } catch (Exception e) {
            r.put("ok", false);
            r.put("error", e.getMessage());
        }
        return r;
    }

    /** Offline application document, with a content-hash version so a change shows. */
    private DocContent mockDocument() {
        SourceDocumentRef ref = new SourceDocumentRef(
                "mock-doc:housing-application",
                Integer.toHexString(MOCK_APPLICATION_TEXT.hashCode()),
                "Housing application (mock document)");
        return new DocContent(MOCK_APPLICATION_TEXT.getBytes(StandardCharsets.UTF_8),
                "text/plain", "Housing application (mock document).txt", ref);
    }

    /** Stand-in for a Graph list-item response, keyed by SharePoint column names. */
    private Cols mockListItem() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ApplicantName", "Ms A. Adeyemi");
        item.put("AnnualIncome", 18400);
        item.put("TenancyStatus", "confirmed");
        item.put("NINumber", "QQ123456C");
        item.put("MedicalNotes", "Disability, mobility needs");
        item.put("CaseworkerRef", "HW-INT-2025-5821");
        return new Cols(item, new SourceDocumentRef(
                "mock-list:housing-applications", "v1", "Housing applications list (mock)"));
    }

    private static String etag(JsonNode node) {
        String tag = node.path("eTag").asText(node.path("@odata.etag").asText(""));
        return tag.isBlank() ? "unknown" : tag;
    }

    /** Client-credentials (app-only) token for Graph. Cached until shortly before expiry. */
    private String token() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedToken;
        }
        String form = "client_id=" + enc(clientId)
                + "&scope=" + enc("https://graph.microsoft.com/.default")
                + "&client_secret=" + enc(clientSecret)
                + "&grant_type=client_credentials";
        JsonNode res = http.post()
                .uri(URI.create("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        cachedToken = res.path("access_token").asText();
        tokenExpiry = Instant.now().plusSeconds(res.path("expires_in").asLong(3600));
        return cachedToken;
    }

    /** Use a pre-resolved site id if given, else resolve "host:/sites/Name" to its id. */
    private String resolveSiteId(String token) {
        if (!siteRef.contains(":") && !siteRef.contains("/")) {
            return siteRef;
        }
        JsonNode site = http.get()
                .uri(URI.create(GRAPH + "/sites/" + siteRef))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);
        return site.path("id").asText();
    }

    /** Use the list id if it looks like a GUID, else resolve by display name. */
    private String resolveListId(String token, String siteId) {
        if (listRef.matches("[0-9a-fA-F-]{36}")) {
            return listRef;
        }
        JsonNode lists = http.get()
                .uri(URI.create(GRAPH + "/sites/" + siteId + "/lists?$filter=displayName eq '"
                        + listRef.replace("'", "''") + "'"))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);
        JsonNode first = lists.path("value").path(0);
        if (first.isMissingNode()) {
            throw new IllegalStateException("No SharePoint list named " + listRef);
        }
        return first.path("id").asText();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private void put(Map<String, Object> fields, Map<String, FieldEffect> perms,
            String field, Object value, FieldEffect sourcePermission) {
        fields.put(field, value);
        perms.put(field, sourcePermission);
    }
}
