package com.swarmsight.authority.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swarmsight.authority.ledger.Effect;
import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The broker's HTTP surface: ask for a capability (issued only on an allow),
 * fetch through it, and revoke it. Every fetch goes through the broker, which
 * validates first.
 */
@RestController
public class CapabilityController {

    private final CapabilityBroker broker;
    private final LedgerRepository ledgerRepository;
    private final ObjectMapper objectMapper;

    public CapabilityController(
            CapabilityBroker broker, LedgerRepository ledgerRepository, ObjectMapper objectMapper) {
        this.broker = broker;
        this.ledgerRepository = ledgerRepository;
        this.objectMapper = objectMapper;
    }

    public record IssueCapabilityRequest(
            @NotBlank String requestId,
            @NotBlank String runId,
            @NotBlank String actor,
            @NotBlank String workflow,
            @NotBlank String action,
            @NotBlank String connector,
            @NotBlank String resourceScope,
            Map<String, Object> inputs) {
    }

    public record FetchRequest(
            @NotBlank String capabilityId,
            @NotBlank String connector,
            @NotBlank String resourceScope,
            @NotBlank String caseRef,
            @NotBlank String action) {
    }

    public record RevokeRequest(@NotBlank String reason) {
    }

    @PostMapping("/cases/{caseRef}/capabilities")
    public CapabilityBroker.IssueResult issue(
            @PathVariable String caseRef, @Valid @RequestBody IssueCapabilityRequest req) {
        return broker.requestCapability(caseRef, req.requestId(), req.runId(), req.actor(),
                req.workflow(), req.action(), req.connector(), req.resourceScope(), req.inputs());
    }

    @PostMapping("/broker/fetch")
    public ConnectorRecord fetch(@Valid @RequestBody FetchRequest req) {
        return broker.fetch(req.capabilityId(), req.connector(), req.resourceScope(), req.caseRef(), req.action());
    }

    /** The masked source record, and the verdict that gated it (held -> no record). */
    public record SourceDocument(String verdict, ConnectorRecord record) {
    }

    /**
     * Fetch the case's housing application from the department SharePoint, through
     * the broker, and return it masked. This brokers the whole governed path live:
     * Authority decides, mints a short-lived capability on an allow, fetches via
     * the SharePoint connector, and the permission mirror masks before anything
     * returns. On a held or blocked verdict no capability is minted and no record
     * is returned -- which is correct: the agent should not be reading then.
     *
     * <p>The action is a document read framed as draft preparation; the actor is
     * the demo agent. In production the case's real inputs decide.
     */
    @PostMapping("/cases/{caseRef}/source-documents/fetch")
    public SourceDocument fetchSourceDocument(@PathVariable String caseRef) {
        return brokerFetch(caseRef);
    }

    /** Broker a masked fetch of the case's application document. Shared by the
     *  fetch, resync, and query endpoints. */
    private SourceDocument brokerFetch(String caseRef) {
        String scope = "application/" + caseRef;
        String requestId = "doc-" + caseRef + "-" + UUID.randomUUID();
        CapabilityBroker.IssueResult issued = broker.requestCapability(
                caseRef, requestId, "run-doc-" + caseRef, "agent-housing-1", "HA-09",
                "draft_response", SharePointConnector.NAME, scope,
                Map.of("tenancy_status", "secure", "eviction_risk", false, "dependent_children", false));

        if (issued.verdict().effect() != Effect.ALLOW || issued.capability() == null) {
            return new SourceDocument(issued.verdict().effect().name(), null);
        }
        ConnectorRecord record = broker.fetch(
                issued.capability().id(), SharePointConnector.NAME, scope, caseRef, "draft_response");
        return new SourceDocument(issued.verdict().effect().name(), record);
    }

    public record ResyncResult(
            String verdict, boolean changed, String previousVersion, String currentVersion, String document) {
    }

    /**
     * Re-fetch the case's document and report whether it changed since the last
     * fetch. In production a Microsoft Graph change notification (webhook/delta)
     * calls this; the re-fetch re-extracts and records a fresh source_fetch, so a
     * changed document produces a new provenance entry and the masked record the
     * agent works from stays current.
     */
    @PostMapping("/cases/{caseRef}/source-documents/resync")
    public ResyncResult resync(@PathVariable String caseRef) {
        String previousVersion = latestDocumentVersion(caseRef);
        SourceDocument fresh = brokerFetch(caseRef);
        if (fresh.record() == null) {
            return new ResyncResult(fresh.verdict(), false, previousVersion, null, null);
        }
        String currentVersion = fresh.record().document() == null
                ? null : fresh.record().document().version();
        boolean changed = previousVersion != null && currentVersion != null
                && !previousVersion.equals(currentVersion);
        String name = fresh.record().document() == null ? null : fresh.record().document().name();
        return new ResyncResult(fresh.verdict(), changed, previousVersion, currentVersion, name);
    }

    public record QueryRequest(@NotBlank String question) {
    }

    /**
     * Broker-gated retrieval: ask a question about a case's documents and get a
     * scoped, masked, ledgered answer. The agent never roams the documents -- it
     * gets only the masked fields for this one case, matched to the question. A
     * masked field returns its mask; a denied field returns "no access".
     */
    @PostMapping("/cases/{caseRef}/source-documents/query")
    public Map<String, Object> query(@PathVariable String caseRef, @Valid @RequestBody QueryRequest req) {
        SourceDocument doc = brokerFetch(caseRef);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("question", req.question());
        out.put("verdict", doc.verdict());
        if (doc.record() == null) {
            out.put("answer", List.of());
            out.put("note", "No access: verdict " + doc.verdict() + " minted no capability.");
            return out;
        }
        out.put("document", doc.record().document());

        String q = req.question().toLowerCase();
        Map<String, String> keywords = Map.of(
                "applicant_name", "name", "income", "income", "tenancy_status", "tenanc",
                "national_insurance", "national insurance", "medical_notes", "medical",
                "internal_ref", "caseworker");
        List<Map<String, Object>> answer = new ArrayList<>();
        for (FieldEffectEntry e : doc.record().fieldEffects()) {
            String kw = keywords.getOrDefault(e.field(), e.field());
            boolean matched = q.contains(kw) || q.contains(e.field());
            // If the question names no field, return the whole masked record.
            boolean namesAnyField = keywords.values().stream().anyMatch(q::contains)
                    || keywords.keySet().stream().anyMatch(q::contains);
            if (matched || !namesAnyField) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("field", e.field());
                a.put("outcome", e.outcome().name());
                a.put("value", switch (e.outcome()) {
                    case ALLOW -> doc.record().fields().get(e.field());
                    case MASK -> "(masked)";
                    case DENY -> "(no access)";
                });
                answer.add(a);
            }
        }
        out.put("answer", answer);
        out.put("note", "Scoped to case " + caseRef + ", masked at the broker, recorded on a "
                + "source_fetch ledger row. The agent cannot read beyond this.");
        return out;
    }

    private String latestDocumentVersion(String caseRef) {
        return ledgerRepository.findByCaseRef(caseRef).stream()
                .filter(r -> r.intent().equals("source_fetch"))
                .reduce((a, b) -> b)
                .map(this::parsePayload)
                .map(p -> (String) p.get("document_version"))
                .orElse(null);
    }

    @PostMapping("/capabilities/{id}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable String id, @Valid @RequestBody RevokeRequest req) {
        broker.revoke(id, req.reason());
        return ResponseEntity.ok().build();
    }

    /**
     * The field_effects from the latest source fetch for a case: per field, the
     * source permission, the policy, and the outcome. Read-only, values never
     * included. This is what proves the agent never saw what it was not given.
     */
    @GetMapping("/cases/{caseRef}/field-effects")
    public ResponseEntity<Map<String, Object>> fieldEffects(@PathVariable String caseRef) {
        return ledgerRepository.findByCaseRef(caseRef).stream()
                .filter(r -> r.intent().equals("source_fetch"))
                .reduce((a, b) -> b)
                .map(this::parsePayload)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * The document provenance for a case: every source fetch, the document and
     * version it read, when, and the per-field outcomes. This is the per-case
     * trace -- which documents fed the case, and how each was masked.
     */
    @GetMapping("/cases/{caseRef}/document-provenance")
    public List<Map<String, Object>> documentProvenance(@PathVariable String caseRef) {
        return ledgerRepository.findByCaseRef(caseRef).stream()
                .filter(r -> r.intent().equals("source_fetch"))
                .map(r -> {
                    Map<String, Object> p = parsePayload(r);
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("documentId", p.get("document_id"));
                    entry.put("documentVersion", p.get("document_version"));
                    entry.put("documentName", p.get("document_name"));
                    entry.put("connector", p.get("connector"));
                    entry.put("fetchedAt", r.ts().toString());
                    entry.put("fieldEffects", p.get("field_effects"));
                    return entry;
                })
                .toList();
    }

    private Map<String, Object> parsePayload(LedgerRow row) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(row.payload(), Map.class);
            return payload;
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable source_fetch payload at seq " + row.seq(), e);
        }
    }

    /** A rejected fetch is a 403 carrying the reason code. The broker fails closed. */
    @ExceptionHandler(BrokerException.class)
    public ResponseEntity<Map<String, String>> onRejected(BrokerException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("reason", e.reason().name(), "message", e.getMessage()));
    }
}
