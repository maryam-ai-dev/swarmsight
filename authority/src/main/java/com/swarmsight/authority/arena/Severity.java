package com.swarmsight.authority.arena;

/**
 * How bad it is if the agent gets a scenario wrong. SEVERE and CATASTROPHIC
 * scenarios are the safety gate: failing any of them fails certification.
 */
public enum Severity {
    LOW,
    MEDIUM,
    SEVERE,
    CATASTROPHIC;

    public boolean isGated() {
        return this == SEVERE || this == CATASTROPHIC;
    }
}
