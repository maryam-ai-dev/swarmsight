package com.swarmsight.authority.ledger;

import com.swarmsight.authority.json.CanonicalJson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The ledger's hash recipe, in one place and locked in DECISIONS.md Sprint 1.
 *
 * row_hash = sha256_hex(prev_hash + "|" + canonical_json(hash_input))
 *
 * Determinism comes from the single canonical JSON serialisation and a fixed
 * timestamp format. Change nothing here without changing DECISIONS.md and
 * accepting that old rows no longer recompute.
 */
@Component
public final class Hashing {

    /** The chain anchor: the first row's prev_hash. 64 zeros. */
    public static final String GENESIS_PREV_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    /** RFC3339 UTC with fixed millisecond precision. */
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final CanonicalJson canonical;

    public Hashing(CanonicalJson canonical) {
        this.canonical = canonical;
    }

    /** Truncate an instant to millisecond precision, as stored and as hashed. */
    public Instant normaliseTs(Instant ts) {
        return ts.truncatedTo(ChronoUnit.MILLIS);
    }

    /** Format an instant to the fixed ledger timestamp string. */
    public String formatTs(Instant ts) {
        return TS_FORMAT.format(ts);
    }

    /** sha256_hex of the canonical JSON of the decision payload. */
    public String payloadHash(Object payload) {
        return sha256Hex(canonical.toCanonicalBytes(payload));
    }

    /**
     * Compute a row_hash from the previous hash and the nine bound fields. The
     * canonical serialisation sorts keys, so the order they are put in here does
     * not affect the bytes.
     */
    public String rowHash(
            String prevHash,
            long seq,
            String runId,
            String caseRef,
            String intent,
            String actor,
            String action,
            String payloadHash,
            String policyVersion,
            Instant ts) {

        Map<String, Object> hashInput = new LinkedHashMap<>();
        hashInput.put("action", action);
        hashInput.put("actor", actor);
        hashInput.put("case_ref", caseRef);
        hashInput.put("intent", intent);
        hashInput.put("payload_hash", payloadHash);
        hashInput.put("policy_version", policyVersion);
        hashInput.put("run_id", runId);
        hashInput.put("seq", seq);
        hashInput.put("ts", formatTs(ts));

        String preimage = prevHash + "|" + canonical.toCanonicalString(hashInput);
        return sha256Hex(preimage.getBytes(StandardCharsets.UTF_8));
    }

    /** Lowercase hex SHA-256 of the given bytes. */
    public String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
