package com.swarmsight.authority.incident;

/**
 * What raised an incident. Any of these starts the same fail-closed containment.
 */
public enum Trigger {
    MISSED_ESCALATION,
    GUARD_BREACH,
    SOURCE_STALE,
    CONFIDENCE_COLLAPSE,
    HUMAN_REPORT
}
