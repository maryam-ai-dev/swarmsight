package com.swarmsight.authority.decision;

import com.swarmsight.authority.ledger.Effect;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The hardcoded policy for Sprint 1: one workflow, one action, one required
 * input, one guard. Pure: same request and certificate status in, same Verdict
 * out, no database. Policy becomes versioned data in Sprint 2.
 *
 * Checks run in a fixed order so the first failing one wins: resolve the policy,
 * recognise the action, trust the certificate, require the input, then apply the
 * guard. Anything unexpected fails closed before it reaches ALLOW.
 */
@Component
public class PolicyEvaluator {

    private static final String WORKFLOW = "HA-09";
    private static final String VERSION = "v7";
    private static final String ACTION = "draft_response";
    private static final String REQUIRED_INPUT = "tenancy_status";

    public Verdict evaluate(DecisionRequest req, CertificateStatus certificate) {
        if (!WORKFLOW.equals(req.workflow())) {
            return Verdict.of(Effect.BLOCK, ReasonCode.POLICY_UNRESOLVABLE,
                    "No policy is in force for workflow " + req.workflow() + ".",
                    "unresolved", req.runId(), req.caseRef(), req.action());
        }
        if (!ACTION.equals(req.action())) {
            return Verdict.of(Effect.BLOCK, ReasonCode.UNKNOWN_ACTION,
                    "Action " + req.action() + " is not permitted under HA-09 v7.",
                    VERSION, req.runId(), req.caseRef(), req.action());
        }
        if (certificate != CertificateStatus.VALID) {
            return Verdict.of(Effect.HOLD, ReasonCode.CERTIFICATE_INVALID,
                    "The agent's certificate is missing or expired. Routed for human handling.",
                    VERSION, req.runId(), req.caseRef(), req.action());
        }
        Map<String, Object> inputs = req.safeInputs();
        if (!isPresent(inputs, REQUIRED_INPUT)) {
            return Verdict.of(Effect.HOLD, ReasonCode.REQUIRED_INPUT_ABSENT,
                    "Required input tenancy_status is missing, so the case cannot be decided safely.",
                    VERSION, req.runId(), req.caseRef(), req.action());
        }
        if (isTrue(inputs, "eviction_risk") && isTrue(inputs, "dependent_children")) {
            return Verdict.of(Effect.HOLD, ReasonCode.EVICTION_RISK_DEPENDENTS,
                    "Eviction risk with dependent children present. Held for human review under HA-09 v7.",
                    VERSION, req.runId(), req.caseRef(), req.action());
        }
        return Verdict.of(Effect.ALLOW, ReasonCode.CLEAR,
                "No guard tripped. Cleared to proceed under HA-09 v7.",
                VERSION, req.runId(), req.caseRef(), req.action());
    }

    private static boolean isPresent(Map<String, Object> inputs, String key) {
        Object v = inputs.get(key);
        return v != null && !(v instanceof String s && s.isBlank());
    }

    private static boolean isTrue(Map<String, Object> inputs, String key) {
        Object v = inputs.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return v != null && "true".equalsIgnoreCase(v.toString());
    }
}
