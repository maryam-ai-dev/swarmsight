package com.swarmsight.authority.workbench;

import java.util.List;

/**
 * The human-readable diff between the version in force and a candidate: the
 * guards a change adds or removes, and any floor changes. No rule goes live
 * unreviewed, and this is what the reviewer sees.
 */
public record PolicyDiff(
        String baseVersion,
        String proposedVersion,
        List<GuardSummary> addedGuards,
        List<String> removedGuards,
        List<String> notes) {

    public record GuardSummary(String name, String raiseTo, String source) {
    }
}
