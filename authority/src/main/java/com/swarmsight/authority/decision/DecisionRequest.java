package com.swarmsight.authority.decision;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * What a caller posts to ask Authority for a decision. The envelope fields are
 * required; without them there is no run to tie the decision to, so a missing
 * one is a malformed request (400), not a decision.
 *
 * @param requestId idempotency key; a repeat must not double-write or re-decide
 * @param inputs    policy inputs (for HA-09: tenancy_status, eviction_risk,
 *                  dependent_children); may be null and is treated as empty
 */
public record DecisionRequest(
        @NotBlank String requestId,
        @NotBlank String runId,
        @NotBlank String caseRef,
        @NotBlank String actor,
        @NotBlank String workflow,
        @NotBlank String action,
        Map<String, Object> inputs) {

    public Map<String, Object> safeInputs() {
        return inputs == null ? Map.of() : inputs;
    }
}
