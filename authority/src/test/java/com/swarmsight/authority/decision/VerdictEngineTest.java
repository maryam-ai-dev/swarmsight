package com.swarmsight.authority.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.swarmsight.authority.ledger.Effect;
import com.swarmsight.authority.policy.Guard;
import com.swarmsight.authority.policy.Guard.Clause;
import com.swarmsight.authority.policy.Guard.Clause.Op;
import com.swarmsight.authority.policy.Level;
import com.swarmsight.authority.policy.PolicyVersion;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The level model, tested in isolation. Floors, guards that raise, confidence
 * that only lowers, and the comparison to the certificate ceiling. Every
 * fail-closed branch resolves to HOLD or BLOCK, never ALLOW.
 */
class VerdictEngineTest {

    private final VerdictEngine engine = new VerdictEngine();

    private final Guard evictionGuard = new Guard(
            "eviction-risk-with-dependents",
            List.of(new Clause("eviction_risk", Op.IS_TRUE), new Clause("dependent_children", Op.IS_TRUE)),
            Level.L4_HUMAN, ReasonCode.EVICTION_RISK_DEPENDENTS,
            "Eviction risk with dependent children present. Held for human review under HA-09 v7.",
            "HA-09 s.4");

    private PolicyVersion policy(Map<String, Level> floors, List<Guard> guards) {
        return new PolicyVersion("HA-09", "v7", Instant.parse("2025-04-01T00:00:00Z"),
                List.of("tenancy_status"), floors, guards);
    }

    private DecisionRequest request(Map<String, Object> inputs) {
        return new DecisionRequest("req-1", "run-1", "case-1", "agent-1", "HA-09", "draft_response", inputs);
    }

    private CertificateCheck validCert(Level ceiling) {
        return CertificateCheck.present("ACTIVE", ceiling, java.util.Set.of("draft_response"));
    }

    private Map<String, Object> clearInputs() {
        Map<String, Object> m = new HashMap<>();
        m.put("tenancy_status", "secure");
        m.put("eviction_risk", false);
        m.put("dependent_children", false);
        return m;
    }

    @Test
    void clearCaseAllowsWithinCeiling() {
        EngineResult r = engine.evaluate(
                request(clearInputs()),
                Optional.of(policy(Map.of("draft_response", Level.L1), List.of(evictionGuard))),
                validCert(Level.L2));

        assertThat(r.verdict().effect()).isEqualTo(Effect.ALLOW);
        assertThat(r.requiredLevel()).isEqualTo(Level.L1);
        assertThat(r.effectiveCeiling()).isEqualTo(Level.L2);
    }

    @Test
    void guardRaisesRequiredLevelToHumanAndHolds() {
        Map<String, Object> inputs = clearInputs();
        inputs.put("eviction_risk", true);
        inputs.put("dependent_children", true);

        EngineResult r = engine.evaluate(
                request(inputs),
                Optional.of(policy(Map.of("draft_response", Level.L1), List.of(evictionGuard))),
                validCert(Level.L2));

        assertThat(r.verdict().effect()).isEqualTo(Effect.HOLD);
        assertThat(r.verdict().reasonCode()).isEqualTo(ReasonCode.EVICTION_RISK_DEPENDENTS);
        // A guard raises to L4_HUMAN and no higher: that is the top of the model.
        assertThat(r.requiredLevel()).isEqualTo(Level.L4_HUMAN);
        assertThat(r.triggeredGuards()).contains("eviction-risk-with-dependents");
    }

    @Test
    void confidenceCanOnlyLowerTheCeilingNeverRaiseIt() {
        // High confidence must not lift the ceiling above the certificate's L2.
        Map<String, Object> highConfidence = clearInputs();
        highConfidence.put("confidence", Map.of("source", 1.0, "evidence", 1.0, "interpretation", 1.0));

        EngineResult high = engine.evaluate(
                request(highConfidence),
                Optional.of(policy(Map.of("draft_response", Level.L1), List.of())),
                validCert(Level.L2));

        assertThat(high.effectiveCeiling()).isEqualTo(Level.L2);
        assertThat(high.verdict().effect()).isEqualTo(Effect.ALLOW);
    }

    @Test
    void lowConfidenceLowersCeilingAndEscalates() {
        Map<String, Object> lowConfidence = clearInputs();
        lowConfidence.put("confidence", Map.of("evidence", 0.4));

        EngineResult r = engine.evaluate(
                request(lowConfidence),
                Optional.of(policy(Map.of("draft_response", Level.L2), List.of())),
                validCert(Level.L2));

        // Floor L2, ceiling lowered to L1 by confidence, so the case escalates.
        assertThat(r.effectiveCeiling()).isEqualTo(Level.L1);
        assertThat(r.verdict().effect()).isEqualTo(Effect.HOLD);
        assertThat(r.verdict().reasonCode()).isEqualTo(ReasonCode.AUTONOMY_CEILING_EXCEEDED);
    }

    @Test
    void requiredAboveCeilingButNotHumanHoldsAsCeilingExceeded() {
        Guard raiseToL3 = new Guard("risky", List.of(new Clause("risky", Op.IS_TRUE)),
                Level.L3, "RISKY", "Risky case.", "test");
        Map<String, Object> inputs = clearInputs();
        inputs.put("risky", true);

        EngineResult r = engine.evaluate(
                request(inputs),
                Optional.of(policy(Map.of("draft_response", Level.L1), List.of(raiseToL3))),
                validCert(Level.L2));

        assertThat(r.requiredLevel()).isEqualTo(Level.L3);
        assertThat(r.verdict().effect()).isEqualTo(Effect.HOLD);
        assertThat(r.verdict().reasonCode()).isEqualTo(ReasonCode.AUTONOMY_CEILING_EXCEEDED);
    }

    @Test
    void unresolvablePolicyBlocks() {
        EngineResult r = engine.evaluate(request(clearInputs()), Optional.empty(), validCert(Level.L2));
        assertThat(r.verdict().effect()).isEqualTo(Effect.BLOCK);
        assertThat(r.verdict().reasonCode()).isEqualTo(ReasonCode.POLICY_UNRESOLVABLE);
    }

    @Test
    void unknownActionBlocks() {
        DecisionRequest req = new DecisionRequest("r", "run", "case", "agent", "HA-09", "delete_record", clearInputs());
        EngineResult r = engine.evaluate(req,
                Optional.of(policy(Map.of("draft_response", Level.L1), List.of())), validCert(Level.L2));
        assertThat(r.verdict().effect()).isEqualTo(Effect.BLOCK);
        assertThat(r.verdict().reasonCode()).isEqualTo(ReasonCode.UNKNOWN_ACTION);
    }

    @Test
    void aSuspendedCertificateBlocks() {
        EngineResult r = engine.evaluate(request(clearInputs()),
                Optional.of(policy(Map.of("draft_response", Level.L1), List.of())),
                CertificateCheck.present("SUSPENDED", Level.L2, java.util.Set.of("draft_response")));
        assertThat(r.verdict().effect()).isEqualTo(Effect.BLOCK);
        assertThat(r.verdict().reasonCode()).isEqualTo(ReasonCode.CERTIFICATE_NOT_ACTIVE);
    }

    @Test
    void anUnreadableCertificateStoreBlocks() {
        EngineResult r = engine.evaluate(request(clearInputs()),
                Optional.of(policy(Map.of("draft_response", Level.L1), List.of())),
                CertificateCheck.unreadable());
        assertThat(r.verdict().effect()).isEqualTo(Effect.BLOCK);
        assertThat(r.verdict().reasonCode()).isEqualTo(ReasonCode.CERTIFICATE_UNREADABLE);
    }

    @Test
    void anActionOutsideTheCertifiedSetBlocks() {
        EngineResult r = engine.evaluate(request(clearInputs()),
                Optional.of(policy(Map.of("draft_response", Level.L1), List.of())),
                CertificateCheck.present("ACTIVE", Level.L2, java.util.Set.of("request_evidence")));
        assertThat(r.verdict().effect()).isEqualTo(Effect.BLOCK);
        assertThat(r.verdict().reasonCode()).isEqualTo(ReasonCode.ACTION_NOT_CERTIFIED);
    }

    @Test
    void theExplicitExemptPathIsDecidedOnPolicyAlone() {
        EngineResult r = engine.evaluate(request(clearInputs()),
                Optional.of(policy(Map.of("draft_response", Level.L1), List.of())),
                CertificateCheck.exempt());
        assertThat(r.verdict().effect()).isEqualTo(Effect.ALLOW);
    }

    @Test
    void aGovernedDecisionWithNoCertificateBlocks() {
        EngineResult r = engine.evaluate(request(clearInputs()),
                Optional.of(policy(Map.of("draft_response", Level.L1), List.of())),
                CertificateCheck.missing());
        assertThat(r.verdict().effect()).isEqualTo(Effect.BLOCK);
        assertThat(r.verdict().reasonCode()).isEqualTo(ReasonCode.CERTIFICATE_MISSING);
    }

    @Test
    void requiredInputAbsentHolds() {
        Map<String, Object> inputs = clearInputs();
        inputs.remove("tenancy_status");
        EngineResult r = engine.evaluate(request(inputs),
                Optional.of(policy(Map.of("draft_response", Level.L1), List.of())), validCert(Level.L2));
        assertThat(r.verdict().effect()).isEqualTo(Effect.HOLD);
        assertThat(r.verdict().reasonCode()).isEqualTo(ReasonCode.REQUIRED_INPUT_ABSENT);
    }
}
