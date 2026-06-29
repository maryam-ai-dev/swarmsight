package com.swarmsight.authority.decision;

import com.swarmsight.authority.ledger.Effect;

/**
 * Authority's answer to a DecisionRequest. The effect and review brief are what
 * the Case surface renders. The seq and rowHash tie the verdict to the exact
 * ledger row that proves it; they are null only on an internal-error hold that
 * could not be written.
 */
public record Verdict(
        Effect effect,
        String reasonCode,
        String reviewBrief,
        String policyVersion,
        String runId,
        String caseRef,
        String action,
        Long seq,
        String rowHash) {

    public static Verdict of(
            Effect effect, String reasonCode, String reviewBrief, String policyVersion,
            String runId, String caseRef, String action) {
        return new Verdict(effect, reasonCode, reviewBrief, policyVersion,
                runId, caseRef, action, null, null);
    }

    public Verdict boundTo(long seq, String rowHash) {
        return new Verdict(effect, reasonCode, reviewBrief, policyVersion,
                runId, caseRef, action, seq, rowHash);
    }
}
