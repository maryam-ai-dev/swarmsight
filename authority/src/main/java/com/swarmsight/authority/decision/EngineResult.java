package com.swarmsight.authority.decision;

import com.swarmsight.authority.policy.Level;
import java.util.List;

/**
 * What the VerdictEngine returns: the Verdict plus the levels it was computed
 * from, so DecisionService can record them on the ledger for the audit trail.
 * The levels are null on a structural block where they have no meaning.
 */
public record EngineResult(
        Verdict verdict,
        Level requiredLevel,
        Level effectiveCeiling,
        List<String> triggeredGuards) {
}
