package com.swarmsight.authority.incident;

import java.time.Instant;
import java.util.List;

/**
 * An incident and the containment it triggered. Containment runs at once and
 * fails closed; the human review and re-certification come after.
 */
public record Incident(
        String id,
        String agentId,
        Trigger trigger,
        String detail,
        String status,
        String reportedBy,
        Instant raisedAt,
        Instant containedAt,
        Instant resolvedAt,
        Containment containment) {

    /** What containment did, for the record. Each item is also a ledger event. */
    public record Containment(
            String suspendedCertificate,
            List<String> revokedCapabilities,
            List<String> heldCases,
            List<String> disabledActions,
            boolean serviceOwnerNotified) {
    }
}
