package com.swarmsight.authority.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.swarmsight.authority.json.CanonicalJson;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Chain verification passes on an intact chain and fails loudly on a tampered
 * one, whether the tampering is to a payload, a hash, or a link. Rows are built
 * in memory with the real recipe, so no database tampering is needed.
 */
class ChainVerifierTest {

    private final Hashing hashing = new Hashing(new CanonicalJson());
    private final ChainVerifier verifier = new ChainVerifier(hashing);
    private final Instant ts = Instant.parse("2026-06-29T12:00:00.000Z");

    private LedgerRow row(long seq, String prevHash, String payload) {
        String payloadHash = hashing.sha256Hex(payload.getBytes(StandardCharsets.UTF_8));
        String rowHash = hashing.rowHash(prevHash, seq, "run-1", "case-1", "decision",
                "agent-1", "draft_response", payloadHash, "v7", ts);
        return new LedgerRow(seq, "run-1", "case-1", "decision", "agent-1", "draft_response",
                payload, payloadHash, "v7", ts, "req-" + seq, prevHash, rowHash);
    }

    private List<LedgerRow> intactChain() {
        LedgerRow r1 = row(1, Hashing.GENESIS_PREV_HASH, "{\"n\":1}");
        LedgerRow r2 = row(2, r1.rowHash(), "{\"n\":2}");
        LedgerRow r3 = row(3, r2.rowHash(), "{\"n\":3}");
        return List.of(r1, r2, r3);
    }

    @Test
    void intactChainVerifies() {
        VerificationResult result = verifier.verify(intactChain());
        assertThat(result.ok()).isTrue();
        assertThat(result.checkedRows()).isEqualTo(3);
        assertThat(result.brokenAtSeq()).isNull();
    }

    @Test
    void tamperedPayloadFailsLoudly() {
        List<LedgerRow> chain = new java.util.ArrayList<>(intactChain());
        LedgerRow good = chain.get(1);
        // Change the payload but keep the old payload_hash and row_hash.
        chain.set(1, new LedgerRow(good.seq(), good.runId(), good.caseRef(), good.intent(), good.actor(),
                good.action(), "{\"n\":999}", good.payloadHash(), good.policyVersion(), good.ts(),
                good.requestId(), good.prevHash(), good.rowHash()));

        VerificationResult result = verifier.verify(chain);
        assertThat(result.ok()).isFalse();
        assertThat(result.brokenAtSeq()).isEqualTo(2);
        assertThat(result.message()).contains("payload_hash");
    }

    @Test
    void forgedRowHashFailsLoudly() {
        List<LedgerRow> chain = new java.util.ArrayList<>(intactChain());
        LedgerRow good = chain.get(2);
        chain.set(2, new LedgerRow(good.seq(), good.runId(), good.caseRef(), good.intent(), good.actor(),
                good.action(), good.payload(), good.payloadHash(), good.policyVersion(), good.ts(),
                good.requestId(), good.prevHash(), "deadbeef"));

        VerificationResult result = verifier.verify(chain);
        assertThat(result.ok()).isFalse();
        assertThat(result.brokenAtSeq()).isEqualTo(3);
        assertThat(result.message()).contains("row_hash");
    }

    @Test
    void brokenLinkFailsLoudly() {
        List<LedgerRow> chain = new java.util.ArrayList<>(intactChain());
        LedgerRow good = chain.get(1);
        // Point seq 2 at the wrong previous hash, breaking the link.
        chain.set(1, row(2, "0000000000000000000000000000000000000000000000000000000000000000", "{\"n\":2}"));

        VerificationResult result = verifier.verify(chain);
        assertThat(result.ok()).isFalse();
        assertThat(result.brokenAtSeq()).isEqualTo(2);
        assertThat(result.message()).contains("prev_hash");
    }
}
