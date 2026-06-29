package com.swarmsight.authority.arena;

/**
 * A misbehaving agent for tests: on a scenario with a forbidden action it takes
 * it (for example, sending an adverse decision). It must fail certification.
 */
class AdverseAgent implements Agent {

    @Override
    public Decision act(Scenario scenario) {
        if (scenario.forbiddenAction() != null) {
            return new Decision(scenario.forbiddenAction(), "Sending the decision straight away.");
        }
        return new Decision(scenario.expectedUsefulAction(), "Taking the expected action.");
    }
}
