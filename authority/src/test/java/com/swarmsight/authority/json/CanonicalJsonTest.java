package com.swarmsight.authority.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Locks the canonical serialisation behaviour the ledger will depend on in
 * Sprint 1. If any of these break, the hash recipe is no longer deterministic.
 */
class CanonicalJsonTest {

    private final CanonicalJson canonical = new CanonicalJson();

    @Test
    void keysAreSortedAndWhitespaceStripped() {
        Map<String, Object> unsorted = new LinkedHashMap<>();
        unsorted.put("zebra", 1);
        unsorted.put("apple", 2);
        unsorted.put("mango", 3);

        String json = canonical.toCanonicalString(unsorted);

        assertThat(json).isEqualTo("{\"apple\":2,\"mango\":3,\"zebra\":1}");
    }

    @Test
    void sameInputProducesSameBytes() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("b", 2);
        a.put("a", 1);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("a", 1);
        b.put("b", 2);

        assertThat(canonical.toCanonicalBytes(a)).isEqualTo(canonical.toCanonicalBytes(b));
    }

    @Test
    void timestampsAreRfc3339StringsNotEpochNumbers() {
        Map<String, Object> withTime = Map.of("ts", Instant.parse("2026-06-29T12:00:00Z"));

        String json = canonical.toCanonicalString(withTime);

        assertThat(json).isEqualTo("{\"ts\":\"2026-06-29T12:00:00Z\"}");
    }
}
