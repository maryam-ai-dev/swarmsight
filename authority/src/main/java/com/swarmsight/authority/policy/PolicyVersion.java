package com.swarmsight.authority.policy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One immutable version of a policy. A change produces a new PolicyVersion with
 * a later effectiveFrom; the old one is kept forever so past decisions can be
 * re-audited under the exact rules that applied.
 *
 * @param actionFloors the minimum required level per known action
 * @param guards       conditions that can raise the required level
 */
public record PolicyVersion(
        String policyId,
        String version,
        Instant effectiveFrom,
        List<String> requiredInputs,
        Map<String, Level> actionFloors,
        List<Guard> guards) {

    public boolean knowsAction(String action) {
        return actionFloors.containsKey(action);
    }

    public Level actionFloor(String action) {
        return actionFloors.get(action);
    }
}
