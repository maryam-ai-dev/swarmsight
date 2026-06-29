package com.swarmsight.authority.broker;

/**
 * The permission mirror's decision for one field: what the source permitted,
 * what the sensitivity policy permitted, and the resulting outcome. This is what
 * the ledger records, never the value, so an auditor can prove what was exposed,
 * masked, and denied without the ledger holding the data.
 */
public record FieldEffectEntry(
        String field,
        FieldEffect sourcePermission,
        FieldEffect policy,
        FieldEffect outcome) {
}
