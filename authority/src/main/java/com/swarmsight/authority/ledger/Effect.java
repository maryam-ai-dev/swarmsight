package com.swarmsight.authority.ledger;

/**
 * The three possible effects of a Verdict. There is no fourth. When in doubt
 * the path fails closed to HOLD or BLOCK, never ALLOW.
 */
public enum Effect {
    ALLOW,
    HOLD,
    BLOCK
}
