package com.swarmsight.authority.broker;

import java.time.Instant;

/**
 * A short-lived, scoped, revocable grant to fetch one resource from one
 * connector for one case and action. Minted only on an allow Verdict, and bound
 * to the verdict that issued it so it can never grant more than the verdict did.
 */
public record Capability(
        String id,
        String runId,
        String caseRef,
        String action,
        String connector,
        String resourceScope,
        String issuedByVerdict,
        Instant issuedAt,
        Instant expiresAt,
        boolean revocable,
        Instant revokedAt,
        String revokedReason) {

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return now.isAfter(expiresAt);
    }
}
