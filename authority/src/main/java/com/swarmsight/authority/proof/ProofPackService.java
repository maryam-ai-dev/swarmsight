package com.swarmsight.authority.proof;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swarmsight.authority.json.CanonicalJson;
import com.swarmsight.authority.ledger.ChainVerifier;
import com.swarmsight.authority.ledger.Hashing;
import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerRow;
import com.swarmsight.authority.ledger.VerificationResult;
import com.swarmsight.authority.policy.Guard;
import com.swarmsight.authority.policy.PolicyRepository;
import com.swarmsight.authority.policy.PolicyVersion;
import com.swarmsight.authority.run.RunContext;
import com.swarmsight.authority.run.RunContextRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Assembles a case's proof pack from real ledger rows: the seven sections, the
 * live whole-chain verification, and a stable export hash. The draft-to-final
 * diff is derived here from the author and final rows, never stored.
 */
@Service
public class ProofPackService {

    private final LedgerRepository ledgerRepository;
    private final PolicyRepository policyRepository;
    private final RunContextRepository runContextRepository;
    private final ChainVerifier chainVerifier;
    private final Hashing hashing;
    private final CanonicalJson canonical;
    private final TextDiff textDiff;
    private final ObjectMapper objectMapper;

    public ProofPackService(
            LedgerRepository ledgerRepository,
            PolicyRepository policyRepository,
            RunContextRepository runContextRepository,
            ChainVerifier chainVerifier,
            Hashing hashing,
            CanonicalJson canonical,
            TextDiff textDiff,
            ObjectMapper objectMapper) {
        this.ledgerRepository = ledgerRepository;
        this.policyRepository = policyRepository;
        this.runContextRepository = runContextRepository;
        this.chainVerifier = chainVerifier;
        this.hashing = hashing;
        this.canonical = canonical;
        this.textDiff = textDiff;
        this.objectMapper = objectMapper;
    }

    public Optional<ProofPack> assemble(String caseRef) {
        List<LedgerRow> caseRows = ledgerRepository.findByCaseRef(caseRef);
        if (caseRows.isEmpty()) {
            return Optional.empty();
        }

        // The chain-verification result at the top is the whole ledger, intact.
        List<LedgerRow> allRows = ledgerRepository.findAllOrderBySeq();
        VerificationResult chain = chainVerifier.verify(allRows);

        LedgerRow decisionRow = lastByIntent(caseRows, "decision");
        LedgerRow authorRow = firstByIntent(caseRows, "author");
        LedgerRow editRow = lastByIntent(caseRows, "edit");
        LedgerRow approveRow = lastByIntent(caseRows, "approve");

        Map<String, Object> decision = decisionRow == null ? Map.of() : parse(decisionRow);
        String workflow = workflowOf(caseRows);
        String version = decisionRow != null ? decisionRow.policyVersion() : caseRows.get(0).policyVersion();
        Optional<PolicyVersion> policy = policyRepository.findVersion(workflow, version);
        String policyLabel = workflow + " " + version;

        // Section 5 source: drafts and the derived diff.
        String authorDraft = authorRow == null ? "" : str(parse(authorRow).get("draft"));
        String finalDraft = editRow == null ? authorDraft : str(parse(editRow).get("draft"));
        List<TextDiff.Segment> diff = textDiff.diff(authorDraft, finalDraft);
        Map<String, Object> approve = approveRow == null ? Map.of() : parse(approveRow);

        ProofPack.WhyReview whyReview = new ProofPack.WhyReview(
                str(decision.get("reason_code")),
                str(decision.get("review_brief")),
                guardsFired(decision, policy));

        ProofPack.Responsibility responsibility = new ProofPack.Responsibility(
                authorRow != null ? authorRow.actor() : "Service",
                editRow != null ? editRow.actor() : (authorRow != null ? authorRow.actor() : "Service"),
                approveRow != null ? approveRow.actor() : "pending",
                policyLabel);

        List<ProofPack.TraceEvent> trace = traceEvents(authorRow, editRow, approveRow);

        ProofPack.HumanJudgement humanJudgement = new ProofPack.HumanJudgement(
                authorDraft, finalDraft, diff,
                str(approve.get("note")), str(approve.get("appeal_route")), str(approve.get("signposting")));

        ProofPack.Sources sources = sources(decision);

        String exportHash = exportHash(caseRef, caseRows, policy);
        ProofPack.Technical technical = new ProofPack.Technical(
                allRows.size(),
                shortHash(authorRow), shortHash(editRow), shortHash(approveRow), exportHash);

        ProofPack.GoverningRules governing = new ProofPack.GoverningRules(
                policyLabel,
                approveRow != null ? approveRow.actor() : "pending",
                approveRow != null ? approveRow.ts() : (decisionRow != null ? decisionRow.ts() : caseRows.get(0).ts()),
                technical);

        ProofPack.WhatHappened whatHappened = new ProofPack.WhatHappened(
                narrative(caseRef, responsibility, approveRow != null),
                str(decision.get("effect")), str(decision.get("reason_code")));

        return Optional.of(new ProofPack(
                caseRef, chain, exportHash, allRows.size(),
                whatHappened, whyReview, responsibility, trace, humanJudgement, sources, governing));
    }

    private List<ProofPack.GuardRef> guardsFired(Map<String, Object> decision, Optional<PolicyVersion> policy) {
        Object triggered = decision.get("triggered_guards");
        if (!(triggered instanceof List<?> names)) {
            return List.of();
        }
        List<ProofPack.GuardRef> out = new ArrayList<>();
        for (Object n : names) {
            String name = String.valueOf(n);
            String raiseTo = "";
            String source = "";
            if (policy.isPresent()) {
                for (Guard g : policy.get().guards()) {
                    if (g.name().equals(name)) {
                        raiseTo = g.raiseTo().name();
                        source = g.source();
                    }
                }
            }
            out.add(new ProofPack.GuardRef(name, raiseTo, source));
        }
        return out;
    }

    private List<ProofPack.TraceEvent> traceEvents(LedgerRow authorRow, LedgerRow editRow, LedgerRow approveRow) {
        List<ProofPack.TraceEvent> trace = new ArrayList<>();
        if (authorRow != null) {
            trace.add(event(authorRow, "Service drafted the response"));
        }
        if (editRow != null) {
            trace.add(event(editRow, "Officer edited the draft"));
        }
        if (approveRow != null) {
            trace.add(event(approveRow, "Officer approved the decision"));
        }
        return trace;
    }

    private ProofPack.TraceEvent event(LedgerRow row, String label) {
        return new ProofPack.TraceEvent(row.intent(), row.actor(), row.ts(), row.rowHash(), row.prevHash(), label);
    }

    private ProofPack.Sources sources(Map<String, Object> decision) {
        List<String> provided = new ArrayList<>();
        Object inputs = decision.get("inputs");
        if (inputs instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!"confidence".equals(e.getKey())) {
                    provided.add(e.getKey() + ": " + e.getValue());
                }
            }
        }
        List<String> missing = new ArrayList<>();
        Object triggered = decision.get("triggered_guards");
        if (triggered instanceof List<?> names && names.contains("evidence-missing")) {
            missing.add("latest eviction notice");
        }
        return new ProofPack.Sources(provided, missing);
    }

    private String narrative(String caseRef, ProofPack.Responsibility r, boolean approved) {
        String base = "The service prepared a draft response for " + caseRef + " and set out the evidence.";
        if (approved) {
            return base + " " + r.approvedBy() + " edited the wording and approved the final response."
                    + " The service did not send, close, or decide the case.";
        }
        return base + " The case is awaiting an officer decision. The service cannot send or decide it.";
    }

    private String exportHash(String caseRef, List<LedgerRow> caseRows, Optional<PolicyVersion> policy) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LedgerRow r : caseRows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", r.seq());
            m.put("run_id", r.runId());
            m.put("case_ref", r.caseRef());
            m.put("intent", r.intent());
            m.put("actor", r.actor());
            m.put("action", r.action());
            m.put("payload", r.payload());
            m.put("payload_hash", r.payloadHash());
            m.put("policy_version", r.policyVersion());
            m.put("ts", hashing.formatTs(r.ts()));
            m.put("request_id", r.requestId());
            m.put("prev_hash", r.prevHash());
            m.put("row_hash", r.rowHash());
            rows.add(m);
        }
        List<Map<String, Object>> policies = new ArrayList<>();
        policy.ifPresent(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("policy_id", p.policyId());
            m.put("version", p.version());
            m.put("effective_from", hashing.formatTs(p.effectiveFrom()));
            policies.add(m);
        });
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("case_ref", caseRef);
        bundle.put("rows", rows);
        bundle.put("policies", policies);
        return hashing.sha256Hex(canonical.toCanonicalBytes(bundle));
    }

    private String workflowOf(List<LedgerRow> caseRows) {
        return runContextRepository.find(caseRows.get(0).runId())
                .map(RunContext::workflow)
                .orElse("HA-09");
    }

    private LedgerRow firstByIntent(List<LedgerRow> rows, String intent) {
        return rows.stream().filter(r -> r.intent().equals(intent)).findFirst().orElse(null);
    }

    private LedgerRow lastByIntent(List<LedgerRow> rows, String intent) {
        return rows.stream().filter(r -> r.intent().equals(intent)).reduce((a, b) -> b).orElse(null);
    }

    private Map<String, Object> parse(LedgerRow row) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(row.payload(), Map.class);
            return m;
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable payload at seq " + row.seq(), e);
        }
    }

    private String shortHash(LedgerRow row) {
        return row == null ? null : row.rowHash().substring(0, 12);
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }
}
