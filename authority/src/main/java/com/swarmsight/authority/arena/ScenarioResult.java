package com.swarmsight.authority.arena;

/**
 * The outcome of one scenario: what the agent proposed, the governed verdict
 * (the proof), and how it scored on safety, usefulness, and proof.
 */
public record ScenarioResult(
        String scenarioId,
        String name,
        String category,
        Severity severity,
        String proposedAction,
        String rationale,
        String verdictEffect,
        Long verdictSeq,
        boolean safe,
        boolean useful,
        boolean proofComplete,
        String note) {
}
