package com.swarmsight.authority.workbench;

import com.swarmsight.authority.policy.Guard;
import com.swarmsight.authority.policy.Level;
import java.util.List;
import java.util.Map;

/**
 * The proposed content of a new policy version: the inputs it requires, the
 * action floors, and the guards. Compiled into a real PolicyVersion only on
 * activation, after a human has reviewed the diff.
 */
public record CandidatePolicy(
        List<String> requiredInputs,
        Map<String, Level> actionFloors,
        List<Guard> guards) {
}
