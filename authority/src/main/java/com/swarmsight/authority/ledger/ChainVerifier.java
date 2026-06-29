package com.swarmsight.authority.ledger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Recomputes a hash chain from stored fields and confirms it is intact. For each
 * row in seq order it checks three things: the payload_hash recomputes from the
 * stored payload, the row_hash recomputes from the stored fields, and the
 * prev_hash links to the row before it. The first failure is reported loudly.
 *
 * This is the same recipe the appender uses, run in reverse as a check, so a
 * tampered payload, a forged hash, or a broken link cannot pass.
 */
@Component
public class ChainVerifier {

    private final Hashing hashing;

    public ChainVerifier(Hashing hashing) {
        this.hashing = hashing;
    }

    /** Verify a full chain that should start from the genesis constant. */
    public VerificationResult verify(List<LedgerRow> rowsBySeq) {
        return verify(rowsBySeq, Hashing.GENESIS_PREV_HASH);
    }

    /** Verify a chain, expecting the first row to link to expectedFirstPrev. */
    public VerificationResult verify(List<LedgerRow> rowsBySeq, String expectedFirstPrev) {
        String expectedPrev = expectedFirstPrev;
        int checked = 0;
        for (LedgerRow row : rowsBySeq) {
            if (!row.prevHash().equals(expectedPrev)) {
                return VerificationResult.broken(checked, row.seq(),
                        "prev_hash at seq " + row.seq() + " does not link to the previous row.");
            }

            String recomputedPayloadHash = hashing.sha256Hex(row.payload().getBytes(StandardCharsets.UTF_8));
            if (!recomputedPayloadHash.equals(row.payloadHash())) {
                return VerificationResult.broken(checked, row.seq(),
                        "payload_hash at seq " + row.seq() + " does not match the stored payload.");
            }

            String recomputedRowHash = hashing.rowHash(
                    row.prevHash(), row.seq(), row.runId(), row.caseRef(), row.intent(),
                    row.actor(), row.action(), row.payloadHash(), row.policyVersion(), row.ts());
            if (!recomputedRowHash.equals(row.rowHash())) {
                return VerificationResult.broken(checked, row.seq(),
                        "row_hash at seq " + row.seq() + " does not recompute.");
            }

            expectedPrev = row.rowHash();
            checked++;
        }
        return VerificationResult.intact(checked);
    }
}
