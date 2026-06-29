package com.swarmsight.authority.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swarmsight.authority.ledger.Effect;
import com.swarmsight.authority.ledger.LedgerRow;
import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerService;
import com.swarmsight.authority.policy.PolicyRepository;
import com.swarmsight.authority.policy.PolicyVersion;
import com.swarmsight.authority.run.RunContextRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one decision: replay if seen before, otherwise resolve the policy
 * version in force at the decision time, check the certificate, evaluate the
 * level model, and append exactly one ledger row. Any unexpected failure in this
 * path resolves to a hold, never an allow.
 */
@Service
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);
    private static final String INTENT = "decision";

    private final PolicyRepository policyRepository;
    private final VerdictEngine verdictEngine;
    private final CertificateService certificateService;
    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;
    private final RunContextRepository runContextRepository;
    private final ObjectMapper objectMapper;

    public DecisionService(
            PolicyRepository policyRepository,
            VerdictEngine verdictEngine,
            CertificateService certificateService,
            LedgerService ledgerService,
            LedgerRepository ledgerRepository,
            RunContextRepository runContextRepository,
            ObjectMapper objectMapper) {
        this.policyRepository = policyRepository;
        this.verdictEngine = verdictEngine;
        this.certificateService = certificateService;
        this.ledgerService = ledgerService;
        this.ledgerRepository = ledgerRepository;
        this.runContextRepository = runContextRepository;
        this.objectMapper = objectMapper;
    }

    public Verdict decide(DecisionRequest req) {
        // Idempotent replay: a request_id seen before returns its stored verdict
        // and writes nothing.
        Optional<LedgerRow> seen = ledgerRepository.findByRequestId(req.requestId());
        if (seen.isPresent()) {
            return verdictFromRow(seen.get());
        }

        try {
            Instant now = Instant.now();
            CertificateCheck certificate = certificateService.check(req.actor());
            Optional<PolicyVersion> policy = policyRepository.resolve(req.workflow(), now);
            EngineResult result = verdictEngine.evaluate(req, policy, certificate);
            Verdict verdict = result.verdict();

            runContextRepository.ensureExists(req.runId(), req.caseRef(), req.workflow(), now);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("effect", verdict.effect().name());
            payload.put("reason_code", verdict.reasonCode());
            payload.put("review_brief", verdict.reviewBrief());
            payload.put("required_level", result.requiredLevel() == null ? null : result.requiredLevel().name());
            payload.put("effective_ceiling", result.effectiveCeiling() == null ? null : result.effectiveCeiling().name());
            payload.put("triggered_guards", result.triggeredGuards());
            payload.put("inputs", req.safeInputs());

            // append is idempotent on request_id: under a concurrent duplicate it
            // returns the already-written row.
            LedgerRow row = ledgerService.append(
                    INTENT, req.actor(), req.runId(), req.caseRef(), req.action(),
                    verdict.policyVersion(), payload, req.requestId(), now);

            return verdict.boundTo(row.seq(), row.rowHash());
        } catch (Exception e) {
            // Fail closed: any internal error holds the case. There may be no row.
            log.error("Decide path failed for request {}; holding", req.requestId(), e);
            return Verdict.of(Effect.HOLD, ReasonCode.INTERNAL_ERROR,
                    "An internal error occurred while deciding. The case is held for human review.",
                    "unknown", req.runId(), req.caseRef(), req.action());
        }
    }

    /** Reconstruct a Verdict from the row that proves it. */
    public Verdict verdictFromRow(LedgerRow row) {
        try {
            Map<?, ?> payload = objectMapper.readValue(row.payload(), Map.class);
            Effect effect = Effect.valueOf((String) payload.get("effect"));
            return new Verdict(
                    effect,
                    (String) payload.get("reason_code"),
                    (String) payload.get("review_brief"),
                    row.policyVersion(),
                    row.runId(),
                    row.caseRef(),
                    row.action(),
                    row.seq(),
                    row.rowHash());
        } catch (Exception e) {
            throw new IllegalStateException("Stored payload is unreadable for seq " + row.seq(), e);
        }
    }
}
