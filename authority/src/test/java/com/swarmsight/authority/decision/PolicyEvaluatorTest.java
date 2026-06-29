package com.swarmsight.authority.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.swarmsight.authority.ledger.Effect;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves the hardcoded policy and, crucially, that every fail-closed case
 * resolves to HOLD or BLOCK and never ALLOW.
 */
class PolicyEvaluatorTest {

    private final PolicyEvaluator evaluator = new PolicyEvaluator();

    private DecisionRequest request(String workflow, String action, Map<String, Object> inputs) {
        return new DecisionRequest("req-1", "run-1", "case-1", "agent-1", workflow, action, inputs);
    }

    private Map<String, Object> clearInputs() {
        Map<String, Object> m = new HashMap<>();
        m.put("tenancy_status", "secure");
        m.put("eviction_risk", false);
        m.put("dependent_children", false);
        return m;
    }

    @Test
    void clearCaseAllows() {
        Verdict v = evaluator.evaluate(request("HA-09", "draft_response", clearInputs()), CertificateStatus.VALID);
        assertThat(v.effect()).isEqualTo(Effect.ALLOW);
        assertThat(v.reasonCode()).isEqualTo(ReasonCode.CLEAR);
        assertThat(v.policyVersion()).isEqualTo("v7");
    }

    @Test
    void evictionRiskWithDependentsHolds() {
        Map<String, Object> inputs = clearInputs();
        inputs.put("eviction_risk", true);
        inputs.put("dependent_children", true);

        Verdict v = evaluator.evaluate(request("HA-09", "draft_response", inputs), CertificateStatus.VALID);

        assertThat(v.effect()).isEqualTo(Effect.HOLD);
        assertThat(v.reasonCode()).isEqualTo(ReasonCode.EVICTION_RISK_DEPENDENTS);
        assertThat(v.reviewBrief()).contains("dependent children");
    }

    @Test
    void unknownWorkflowBlocksAndNeverAllows() {
        Verdict v = evaluator.evaluate(request("XX-00", "draft_response", clearInputs()), CertificateStatus.VALID);
        assertThat(v.effect()).isEqualTo(Effect.BLOCK);
        assertThat(v.reasonCode()).isEqualTo(ReasonCode.POLICY_UNRESOLVABLE);
    }

    @Test
    void unknownActionBlocksAndNeverAllows() {
        Verdict v = evaluator.evaluate(request("HA-09", "delete_record", clearInputs()), CertificateStatus.VALID);
        assertThat(v.effect()).isEqualTo(Effect.BLOCK);
        assertThat(v.reasonCode()).isEqualTo(ReasonCode.UNKNOWN_ACTION);
    }

    @Test
    void missingCertificateHoldsAndNeverAllows() {
        Verdict missing = evaluator.evaluate(request("HA-09", "draft_response", clearInputs()), CertificateStatus.MISSING);
        Verdict expired = evaluator.evaluate(request("HA-09", "draft_response", clearInputs()), CertificateStatus.EXPIRED);

        assertThat(missing.effect()).isEqualTo(Effect.HOLD);
        assertThat(missing.reasonCode()).isEqualTo(ReasonCode.CERTIFICATE_INVALID);
        assertThat(expired.effect()).isEqualTo(Effect.HOLD);
    }

    @Test
    void requiredInputAbsentHoldsAndNeverAllows() {
        Map<String, Object> inputs = clearInputs();
        inputs.remove("tenancy_status");

        Verdict v = evaluator.evaluate(request("HA-09", "draft_response", inputs), CertificateStatus.VALID);

        assertThat(v.effect()).isEqualTo(Effect.HOLD);
        assertThat(v.reasonCode()).isEqualTo(ReasonCode.REQUIRED_INPUT_ABSENT);
    }
}
