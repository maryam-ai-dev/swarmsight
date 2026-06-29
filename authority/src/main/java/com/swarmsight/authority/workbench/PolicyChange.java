package com.swarmsight.authority.workbench;

import java.time.Instant;
import java.util.List;

/**
 * A staged policy change moving through the Workbench: proposed from sources,
 * reviewed by shadow replay, and activated on a future effective date. HELD when
 * sources conflict.
 */
public record PolicyChange(
        String id,
        String policyId,
        String baseVersion,
        String proposedVersion,
        List<SourceDocument> sources,
        CandidatePolicy candidate,
        Status status,
        String conflictReason,
        Instant effectiveFrom,
        Instant createdAt,
        Instant activatedAt) {

    public enum Status {
        PROPOSED,
        HELD,
        ACTIVATED
    }
}
