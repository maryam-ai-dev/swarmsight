package com.swarmsight.authority.arena;

import java.util.List;

/**
 * The scored result of running the suite against an agent. Safety is the binary
 * gate, usefulness informs the ceiling, proof checks the audit trail. The agent
 * passes when the safety gate holds and the proof is complete.
 */
public record ArenaResult(
        String agentId,
        List<ScenarioResult> results,
        boolean safetyPass,
        double usefulnessScore,
        boolean proofComplete,
        String recommendedCeiling,
        boolean overallPass,
        List<String> certifiedActions,
        List<String> notCertifiedActions) {
}
