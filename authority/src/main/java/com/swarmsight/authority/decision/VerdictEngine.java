package com.swarmsight.authority.decision;

import com.swarmsight.authority.ledger.Effect;
import com.swarmsight.authority.policy.Guard;
import com.swarmsight.authority.policy.Level;
import com.swarmsight.authority.policy.PolicyVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Computes a Verdict from a resolved policy version, the request, and the
 * certificate check. The level model, locked in DECISIONS.md Sprint 2:
 *
 *   required_level  = action floor, raised by any guard that triggers,
 *                     clamped at L4_HUMAN.
 *   effective_ceiling = certificate ceiling, lowered by confidence sub-scores,
 *                     never raised above the certificate ceiling.
 *
 * Effect: L4_HUMAN holds for a human; required at or below the ceiling allows;
 * required above the ceiling holds and escalates. Structural failures block.
 */
@Component
public class VerdictEngine {

    public EngineResult evaluate(DecisionRequest req, Optional<PolicyVersion> policyOpt, CertificateCheck cert) {
        if (policyOpt.isEmpty()) {
            return structural(Effect.BLOCK, ReasonCode.POLICY_UNRESOLVABLE,
                    "No policy is in force for workflow " + req.workflow() + " at the decision time.",
                    "unresolved", req);
        }
        PolicyVersion policy = policyOpt.get();
        String label = policy.policyId() + " " + policy.version();

        if (!policy.knowsAction(req.action())) {
            return structural(Effect.BLOCK, ReasonCode.UNKNOWN_ACTION,
                    "Action " + req.action() + " is not permitted under " + label + ".",
                    policy.version(), req);
        }
        // The certificate, read live. Fail closed on an unreadable store or a
        // certificate that is present but not ACTIVE or not certified for the
        // action. A missing certificate is the un-governed policy-only path.
        if (cert.presence() == CertificateCheck.Presence.UNREADABLE) {
            return structural(Effect.BLOCK, ReasonCode.CERTIFICATE_UNREADABLE,
                    "The certificate store could not be read; failing closed.", policy.version(), req);
        }
        if (cert.present()) {
            if (!cert.active()) {
                return structural(Effect.BLOCK, ReasonCode.CERTIFICATE_NOT_ACTIVE,
                        "The agent's certificate is " + cert.status() + "; it is not cleared to act.",
                        policy.version(), req);
            }
            if (!cert.certifiedActions().contains(req.action())) {
                return structural(Effect.BLOCK, ReasonCode.ACTION_NOT_CERTIFIED,
                        "The agent is not certified for action " + req.action() + ".",
                        policy.version(), req);
            }
        }
        Map<String, Object> inputs = req.safeInputs();
        for (String required : policy.requiredInputs()) {
            if (isAbsent(inputs.get(required))) {
                return structural(Effect.HOLD, ReasonCode.REQUIRED_INPUT_ABSENT,
                        "Required input " + required + " is missing, so the case cannot be decided safely.",
                        policy.version(), req);
            }
        }

        // Start at the action floor; guards raise the required level.
        Level requiredLevel = policy.actionFloor(req.action());
        Guard primaryGuard = null;
        List<String> triggered = new ArrayList<>();
        for (Guard guard : policy.guards()) {
            if (guard.triggers(inputs)) {
                triggered.add(guard.name());
                requiredLevel = Level.higher(requiredLevel, guard.raiseTo());
                if (primaryGuard == null || guard.raiseTo().ordinal() > primaryGuard.raiseTo().ordinal()) {
                    primaryGuard = guard;
                }
            }
        }

        // The certificate ceiling, lowered by confidence. Never raised. A
        // policy-only decision (no certificate) uses the default ceiling.
        Level baseCeiling = cert.present() ? cert.ceiling() : Level.L2;
        Level effectiveCeiling = applyConfidence(baseCeiling, inputs);

        Effect effect;
        String reasonCode;
        String brief;
        if (requiredLevel.requiresHuman()) {
            effect = Effect.HOLD;
            if (primaryGuard != null) {
                reasonCode = primaryGuard.reasonCode();
                brief = primaryGuard.brief();
            } else {
                reasonCode = ReasonCode.AUTONOMY_CEILING_EXCEEDED;
                brief = "This action requires a human decision under " + label + ".";
            }
        } else if (requiredLevel.atOrBelow(effectiveCeiling)) {
            effect = Effect.ALLOW;
            reasonCode = ReasonCode.CLEAR;
            brief = "No guard tripped. Cleared to proceed under " + label + " at level "
                    + requiredLevel + ", within the certified ceiling " + effectiveCeiling + ".";
        } else {
            effect = Effect.HOLD;
            reasonCode = ReasonCode.AUTONOMY_CEILING_EXCEEDED;
            brief = "This case needs level " + requiredLevel + " but the agent is certified and confident only to "
                    + effectiveCeiling + ". Held for human review under " + label + ".";
        }

        Verdict verdict = Verdict.of(effect, reasonCode, brief, policy.version(),
                req.runId(), req.caseRef(), req.action());
        return new EngineResult(verdict, requiredLevel, effectiveCeiling, triggered);
    }

    private EngineResult structural(Effect effect, String reasonCode, String brief, String version, DecisionRequest req) {
        Verdict verdict = Verdict.of(effect, reasonCode, brief, version, req.runId(), req.caseRef(), req.action());
        return new EngineResult(verdict, null, null, List.of());
    }

    /** Lower the ceiling by each present confidence sub-score. Never raises it. */
    private Level applyConfidence(Level ceiling, Map<String, Object> inputs) {
        Object raw = inputs.get("confidence");
        if (!(raw instanceof Map<?, ?> confidence)) {
            return ceiling;
        }
        Level result = ceiling;
        for (String sub : List.of("source", "evidence", "interpretation")) {
            Object score = confidence.get(sub);
            if (score instanceof Number n) {
                result = Level.lower(result, scoreToLevel(n.doubleValue()));
            }
        }
        return result;
    }

    private Level scoreToLevel(double score) {
        if (score >= 0.8) {
            return Level.L3;
        }
        if (score >= 0.5) {
            return Level.L2;
        }
        if (score >= 0.3) {
            return Level.L1;
        }
        return Level.L0;
    }

    private static boolean isAbsent(Object v) {
        if (v == null) {
            return true;
        }
        return v instanceof String s && s.isBlank();
    }
}
