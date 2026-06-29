package com.swarmsight.authority.ledger;

import com.swarmsight.authority.json.CanonicalJson;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single serialised writer to the ledger. Every append takes the advisory
 * lock, so seq is gapless and prev_hash always points at the row immediately
 * before it. The append is idempotent on request_id.
 */
@Service
public class LedgerService {

    private final LedgerRepository repository;
    private final Hashing hashing;
    private final CanonicalJson canonical;

    public LedgerService(LedgerRepository repository, Hashing hashing, CanonicalJson canonical) {
        this.repository = repository;
        this.hashing = hashing;
        this.canonical = canonical;
    }

    /**
     * Append one row to the ledger and return it. If a row already exists for
     * this request_id, that row is returned unchanged and nothing is written.
     */
    @Transactional
    public LedgerRow append(
            String intent,
            String actor,
            String runId,
            String caseRef,
            String action,
            String policyVersion,
            Object payload,
            String requestId,
            Instant ts) {

        repository.acquireAppendLock();

        var existing = repository.findByRequestId(requestId);
        if (existing.isPresent()) {
            return existing.get();
        }

        var last = repository.findLast();
        long seq = last.map(r -> r.seq() + 1).orElse(1L);
        String prevHash = last.map(LedgerRow::rowHash).orElse(Hashing.GENESIS_PREV_HASH);

        Instant normalisedTs = hashing.normaliseTs(ts);
        String payloadJson = canonical.toCanonicalString(payload);
        String payloadHash = hashing.sha256Hex(payloadJson.getBytes(StandardCharsets.UTF_8));
        String rowHash = hashing.rowHash(
                prevHash, seq, runId, caseRef, intent, actor, action, payloadHash, policyVersion, normalisedTs);

        LedgerRow row = new LedgerRow(
                seq, runId, caseRef, intent, actor, action,
                payloadJson, payloadHash, policyVersion, normalisedTs, requestId, prevHash, rowHash);
        repository.insert(row);
        return row;
    }
}
