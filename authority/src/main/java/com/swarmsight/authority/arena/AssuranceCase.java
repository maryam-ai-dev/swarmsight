package com.swarmsight.authority.arena;

import java.time.Instant;
import java.util.List;

/**
 * The reasoned argument that an agent is safe to certify: a set of claims, each
 * linked to the scenario evidence that supports it. A certificate references one
 * of these. Built by the Arena; signed off by a different human.
 */
public record AssuranceCase(
        String id,
        String agentId,
        List<Claim> claims,
        String builtBy,
        Instant builtAt) {

    /** A claim and the scenario evidence behind it. */
    public record Claim(String claim, List<Evidence> evidence) {
    }

    /** One piece of evidence: a scenario, its outcome, and the ledger row proving it. */
    public record Evidence(String scenarioId, String outcome, String verdictEffect, Long verdictSeq) {
    }
}
