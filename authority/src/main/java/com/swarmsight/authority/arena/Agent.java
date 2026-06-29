package com.swarmsight.authority.arena;

/**
 * An agent under assurance. Given a scenario, it proposes what it would do. It
 * never decides; Authority does, through the governed path. The real agent is
 * Intelligence, reached over HTTP; tests use in-memory doubles.
 */
public interface Agent {

    /** What the agent would do for this scenario, and why. */
    record Decision(String proposedAction, String rationale) {
    }

    Decision act(Scenario scenario);
}
