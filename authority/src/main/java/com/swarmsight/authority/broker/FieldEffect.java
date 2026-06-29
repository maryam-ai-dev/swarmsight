package com.swarmsight.authority.broker;

/**
 * What happens to a field at the boundary. Ordered from most to least
 * restrictive, so the permission mirror can take the more restrictive of two
 * judgements as their intersection.
 */
public enum FieldEffect {
    DENY,
    MASK,
    ALLOW;

    /** The intersection of two effects: the more restrictive one. */
    public static FieldEffect intersect(FieldEffect a, FieldEffect b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
