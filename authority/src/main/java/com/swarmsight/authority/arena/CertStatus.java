package com.swarmsight.authority.arena;

/**
 * The explicit status of a certificate. The verdict path treats anything other
 * than ACTIVE as fail-closed. Stored as the enum name in the certificates table.
 */
public enum CertStatus {
    ACTIVE,
    SUSPENDED,
    EXPIRED,
    REVOKED,
    REVIEW_REQUIRED,
    PENDING_RECERTIFICATION
}
