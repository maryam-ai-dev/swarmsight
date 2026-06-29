package com.swarmsight.authority.ledger;

import java.time.Instant;

/**
 * One immutable, hash-chained entry in the ledger. Written once, never updated
 * or deleted. See DECISIONS.md Sprint 1 for the hash recipe that binds these
 * fields together.
 *
 * @param payload the canonical JSON of the decision payload, stored verbatim
 *                and bound by payloadHash
 */
public record LedgerRow(
        long seq,
        String runId,
        String caseRef,
        String intent,
        String actor,
        String action,
        String payload,
        String payloadHash,
        String policyVersion,
        Instant ts,
        String requestId,
        String prevHash,
        String rowHash) {
}
