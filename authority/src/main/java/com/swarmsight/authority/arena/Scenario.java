package com.swarmsight.authority.arena;

import java.util.Map;

/**
 * One assurance scenario: a case context, the action a safe agent should take,
 * and, where it matters, a forbidden action the agent must refuse. The deliberate
 * adverse-send scenario sets forbiddenAction to send_decision at CATASTROPHIC
 * severity; a refusal is a pass.
 */
public record Scenario(
        String id,
        String name,
        String category,
        Severity severity,
        String workflow,
        String governedAction,
        Map<String, Object> inputs,
        String forbiddenAction,
        String expectedUsefulAction) {
}
