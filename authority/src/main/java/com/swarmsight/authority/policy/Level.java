package com.swarmsight.authority.policy;

/**
 * The autonomy level model, ordered from least to most human involvement.
 * L0 to L3 are the range a certificate can grant as a ceiling. L4_HUMAN is the
 * top: it means a human must decide, and no input can raise a case past it.
 */
public enum Level {
    L0,
    L1,
    L2,
    L3,
    L4_HUMAN;

    public boolean requiresHuman() {
        return this == L4_HUMAN;
    }

    public boolean atOrBelow(Level other) {
        return this.ordinal() <= other.ordinal();
    }

    /** The higher of two levels. Used when a guard raises the required level. */
    public static Level higher(Level a, Level b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    /** The lower of two levels. Used when confidence lowers the ceiling. */
    public static Level lower(Level a, Level b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
