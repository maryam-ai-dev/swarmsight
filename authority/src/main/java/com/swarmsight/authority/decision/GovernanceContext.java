package com.swarmsight.authority.decision;

/**
 * The regime a decision runs under. GOVERNED is the default and fails closed:
 * with no certificate it blocks. BOOTSTRAP is the Arena's explicit exemption, used
 * while it is still deciding whether to certify an agent; it takes the
 * policy-only path. This is a method argument, never a request field, so an
 * external caller cannot grant itself the exemption.
 */
public enum GovernanceContext {
    GOVERNED,
    BOOTSTRAP
}
