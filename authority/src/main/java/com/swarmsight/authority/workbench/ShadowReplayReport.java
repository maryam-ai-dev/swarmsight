package com.swarmsight.authority.workbench;

import java.util.List;
import java.util.Map;

/**
 * The "what would change" report: replaying a candidate against a set of cases
 * before its effective date and comparing each verdict to the version in force.
 */
public record ShadowReplayReport(
        int totalCount,
        int changedCount,
        List<ReplayResult> results) {

    /** One case run under both versions, in shadow. Nothing is sent. */
    public record ReplayCase(String caseRef, String action, Map<String, Object> inputs) {
    }

    public record ReplayResult(
            String caseRef,
            String oldEffect,
            String oldReason,
            String newEffect,
            String newReason,
            boolean changed) {
    }
}
