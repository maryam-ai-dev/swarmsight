package com.swarmsight.authority.workbench;

import com.swarmsight.authority.policy.Guard;
import java.util.List;

/**
 * A request to propose a policy change: the policy and base version it changes,
 * the version it proposes, and the sources. Each source carries the guards it
 * proposes to add (with their provenance) and any it removes. Extraction is
 * structured here, not free-text parsing.
 */
public record ProposeRequest(
        String policyId,
        String baseVersion,
        String proposedVersion,
        List<SourceInput> sources) {

    public record SourceInput(
            SourceDocument document,
            List<Guard> addGuards,
            List<String> removeGuards) {

        public List<Guard> safeAdds() {
            return addGuards == null ? List.of() : addGuards;
        }

        public List<String> safeRemoves() {
            return removeGuards == null ? List.of() : removeGuards;
        }
    }
}
