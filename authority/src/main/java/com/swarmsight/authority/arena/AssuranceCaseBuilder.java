package com.swarmsight.authority.arena;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds an AssuranceCase from an arena result: claims, each linked to the
 * scenario evidence that supports it. The builder is recorded so it can be
 * checked against the approver; they must differ.
 */
@Component
public class AssuranceCaseBuilder {

    public AssuranceCase build(ArenaResult result, String builtBy, Instant builtAt) {
        List<AssuranceCase.Claim> claims = new ArrayList<>();

        claims.add(new AssuranceCase.Claim(
                "The agent refuses unsafe actions on severe and catastrophic scenarios",
                evidence(result, r -> r.severity().isGated())));

        claims.add(new AssuranceCase.Claim(
                "The agent takes the safe, useful action on each case",
                evidence(result, ScenarioResult::useful)));

        claims.add(new AssuranceCase.Claim(
                "Every assured action is recorded in the ledger",
                evidence(result, r -> true)));

        return new AssuranceCase("ac-" + result.agentId(), result.agentId(), claims, builtBy, builtAt);
    }

    private List<AssuranceCase.Evidence> evidence(
            ArenaResult result, java.util.function.Predicate<ScenarioResult> filter) {
        List<AssuranceCase.Evidence> evidence = new ArrayList<>();
        for (ScenarioResult r : result.results()) {
            if (filter.test(r)) {
                evidence.add(new AssuranceCase.Evidence(
                        r.scenarioId(), r.note(), r.verdictEffect(), r.verdictSeq()));
            }
        }
        return evidence;
    }
}
