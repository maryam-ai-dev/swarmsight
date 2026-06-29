package com.swarmsight.authority.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.swarmsight.authority.json.CanonicalJson;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The hash recipe is locked. These pin the pieces it is built from, so any
 * accidental change to serialisation, timestamp format, or the digest breaks a
 * test rather than silently breaking every chain.
 */
class HashingTest {

    private final Hashing hashing = new Hashing(new CanonicalJson());

    @Test
    void sha256MatchesKnownVector() {
        assertThat(hashing.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void genesisIsSixtyFourZeros() {
        assertThat(Hashing.GENESIS_PREV_HASH).hasSize(64).matches("0{64}");
    }

    @Test
    void timestampFormatIsFixedMillisecondPrecision() {
        Instant ts = Instant.parse("2026-06-29T12:00:00.5Z");
        assertThat(hashing.formatTs(ts)).isEqualTo("2026-06-29T12:00:00.500Z");
    }

    @Test
    void payloadHashIsDeterministicRegardlessOfKeyOrder() {
        String a = hashing.payloadHash(Map.of("b", 2, "a", 1));
        String b = hashing.payloadHash(Map.of("a", 1, "b", 2));
        assertThat(a).isEqualTo(b);
    }

    @Test
    void rowHashChangesWhenThePreviousHashChanges() {
        Instant ts = Instant.parse("2026-06-29T12:00:00.000Z");
        String first = hashing.rowHash(Hashing.GENESIS_PREV_HASH, 1, "run-1", "case-1",
                "decision", "agent-1", "draft_response", "ph", "v7", ts);
        String second = hashing.rowHash(first, 2, "run-1", "case-1",
                "decision", "agent-1", "draft_response", "ph", "v7", ts);

        assertThat(first).isNotEqualTo(second);
        // Same inputs reproduce the same hash.
        assertThat(hashing.rowHash(Hashing.GENESIS_PREV_HASH, 1, "run-1", "case-1",
                "decision", "agent-1", "draft_response", "ph", "v7", ts)).isEqualTo(first);
    }
}
