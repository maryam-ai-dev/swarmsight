package com.swarmsight.authority.proof;

import com.swarmsight.authority.ledger.VerificationResult;
import java.time.Instant;
import java.util.List;

/**
 * The assembled proof pack for a case: seven sections built from real ledger
 * rows, the live result of verifying the whole chain, and a stable export hash.
 * Nothing here is stored; it is all derived from the rows on each request.
 */
public record ProofPack(
        String caseRef,
        VerificationResult chainVerification,
        String exportHash,
        int ledgerEntries,
        WhatHappened whatHappened,
        WhyReview whyReview,
        Responsibility responsibility,
        List<TraceEvent> decisionTrace,
        HumanJudgement humanJudgement,
        Sources sources,
        GoverningRules governingRules) {

    /** 1. What happened: a plain narrative. */
    public record WhatHappened(String narrative, String effect, String reasonCode) {
    }

    /** 2. Why review was required: the reason and the guards that fired. */
    public record WhyReview(String reasonCode, String brief, List<GuardRef> guardsFired) {
    }

    public record GuardRef(String name, String raiseTo, String source) {
    }

    /** 3. Decision responsibility: who did what. */
    public record Responsibility(String authoredBy, String finalWordingBy, String approvedBy, String policy) {
    }

    /** 4. Decision trace: one entry per captured event. */
    public record TraceEvent(String intent, String actor, Instant at, String rowHash, String prevHash, String label) {
    }

    /** 5. Where human judgement entered: the derived diff and the redress trail. */
    public record HumanJudgement(
            String authorDraft,
            String finalDraft,
            List<TextDiff.Segment> diff,
            String note,
            String appealRoute,
            String signposting) {
    }

    /** 6. Sources and access: what the decision saw and what was missing. */
    public record Sources(List<String> provided, List<String> missing) {
    }

    /** 7. Governing rules and technical verification. */
    public record GoverningRules(
            String policy,
            String officer,
            Instant decidedAt,
            Technical technical) {
    }

    public record Technical(
            int ledgerEntries,
            String draftHash,
            String editHash,
            String approveHash,
            String exportHash) {
    }
}
