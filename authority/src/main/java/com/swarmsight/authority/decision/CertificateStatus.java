package com.swarmsight.authority.decision;

/**
 * Whether an agent's certificate may be relied on for this decision. Only VALID
 * lets the path proceed; anything else fails closed to a hold.
 */
public enum CertificateStatus {
    VALID,
    MISSING,
    EXPIRED
}
