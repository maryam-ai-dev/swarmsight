package com.swarmsight.authority.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/**
 * The one canonical JSON serialisation for Authority, locked in DECISIONS.md
 * Sprint 0. The ledger's hash determinism depends on this being identical
 * across runs and machines, so it lives in one place and nowhere else.
 *
 * Rules, per the decision:
 * - map keys sorted alphabetically,
 * - no insignificant whitespace,
 * - timestamps as RFC3339 strings in UTC, never epoch numbers,
 * - UTF-8 on the way out.
 *
 * This is deliberately separate from the ObjectMapper Spring uses for normal
 * API responses, so a change to web formatting can never alter a hash input.
 */
@Component
public final class CanonicalJson {

    private final ObjectMapper mapper;

    public CanonicalJson() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    /**
     * Serialise an object to its canonical JSON string. Same input, same bytes,
     * every time.
     */
    public String toCanonicalString(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Canonical serialisation failed", e);
        }
    }

    /**
     * Canonical JSON as UTF-8 bytes, the form a hash takes as input.
     */
    public byte[] toCanonicalBytes(Object value) {
        return toCanonicalString(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
