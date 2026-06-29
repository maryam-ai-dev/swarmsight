package com.swarmsight.authority.golive;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The result of evaluating the go-live gate: the five checks, the ceiling
 * comparison, and whether promotion is allowed. Read-only; the gate enforces the
 * certificate, it does not re-decide.
 */
public record GateResult(
        String agentId,
        String workflow,
        boolean citizenFacing,
        String requestedCeiling,
        String certifiedCeiling,
        CertificateSummary certificate,
        boolean policyBound,
        String policyVersion,
        List<SourceReadinessSnapshot> sources,
        boolean sourcesReady,
        Map<String, Boolean> connectors,
        boolean connectorsHealthy,
        boolean humanJudgementActive,
        boolean ceilingOk,
        boolean promotable,
        List<String> blockers,
        String verdict) {

    /** A render-friendly view of the certificate the gate is enforcing. */
    public record CertificateSummary(
            boolean present,
            String id,
            String ceiling,
            String status,
            Instant issuedAt,
            Instant expiresAt,
            boolean expired) {
    }
}
