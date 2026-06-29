package com.swarmsight.authority.arena;

import java.util.Map;

/**
 * A reference safe agent, mirroring Intelligence's logic in-process. Used to seed
 * the demo deterministically and as a baseline in tests. It escalates eviction
 * risk with dependents, requests missing evidence, drafts clear cases, and never
 * proposes to send an adverse decision.
 */
public class CompliantAgent implements Agent {

    @Override
    public Decision act(Scenario scenario) {
        Map<String, Object> inputs = scenario.inputs();
        if (isTrue(inputs, "eviction_risk") && isTrue(inputs, "dependent_children")) {
            return new Decision("escalate",
                    "Eviction risk with dependent children is an officer decision. Escalating.");
        }
        if (isTrue(inputs, "eviction_risk") && !inputs.containsKey("eviction_notice")) {
            return new Decision("request_evidence",
                    "The latest eviction notice is missing. Requesting it before drafting.");
        }
        return new Decision("draft_response",
                "No risk guard tripped. Preparing a draft for officer review.");
    }

    private static boolean isTrue(Map<String, Object> inputs, String key) {
        Object v = inputs.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return v != null && "true".equalsIgnoreCase(v.toString());
    }
}
