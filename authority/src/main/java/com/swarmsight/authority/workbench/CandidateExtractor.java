package com.swarmsight.authority.workbench;

import com.swarmsight.authority.policy.Guard;
import com.swarmsight.authority.policy.PolicyVersion;
import com.swarmsight.authority.workbench.ProposeRequest.SourceInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Extracts a candidate policy from a base version and the sources' directives.
 * If two sources disagree, the same guard proposed with different effects or one
 * adding what another removes, the extraction conflicts and the change is held
 * rather than silently resolved.
 */
@Component
public class CandidateExtractor {

    /** Either a candidate, or a conflict reason that holds the change. */
    public record ExtractionResult(CandidatePolicy candidate, String conflictReason) {
    }

    public ExtractionResult extract(PolicyVersion base, List<SourceInput> sources) {
        Map<String, Guard> additions = new LinkedHashMap<>();
        Set<String> removals = new TreeSet<>();

        for (SourceInput source : sources) {
            for (Guard guard : source.safeAdds()) {
                Guard existing = additions.get(guard.name());
                if (existing != null && !existing.equals(guard)) {
                    return new ExtractionResult(null,
                            "Sources disagree on guard " + guard.name()
                                    + ": proposed with different effects. Held for the policy owner.");
                }
                additions.put(guard.name(), guard);
            }
            removals.addAll(source.safeRemoves());
        }

        for (String removed : removals) {
            if (additions.containsKey(removed)) {
                return new ExtractionResult(null,
                        "Sources disagree on guard " + removed
                                + ": one adds it, another removes it. Held for the policy owner.");
            }
        }

        // Base guards, minus removals, plus additions.
        List<Guard> guards = new ArrayList<>();
        for (Guard guard : base.guards()) {
            if (!removals.contains(guard.name()) && !additions.containsKey(guard.name())) {
                guards.add(guard);
            }
        }
        guards.addAll(additions.values());

        CandidatePolicy candidate = new CandidatePolicy(base.requiredInputs(), base.actionFloors(), guards);
        return new ExtractionResult(candidate, null);
    }
}
