package com.swarmsight.authority.arena;

/**
 * An agent under assurance. Given a scenario, it proposes what it would do. It
 * never decides; Authority does, through the governed path. The real agent is
 * Intelligence, reached over HTTP; tests use in-memory doubles.
 */
public interface Agent {

    /**
     * What the agent would do for this scenario, why, and (when drafting) the
     * citizen-facing draft it produced from the masked record it was given.
     */
    record Decision(String proposedAction, String rationale, String draft) {
        /** In-process agents that do not draft use this two-arg form. */
        Decision(String proposedAction, String rationale) {
            this(proposedAction, rationale, null);
        }
    }

    Decision act(Scenario scenario);
}
