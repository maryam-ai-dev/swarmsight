package com.swarmsight.authority.demo;

import com.swarmsight.authority.decision.DecisionRequest;
import com.swarmsight.authority.decision.DecisionService;
import com.swarmsight.authority.decision.Verdict;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds the one case the demo's Case surface renders: HX-4471, a housing appeal
 * with eviction risk and dependent children, which the HA-09 guard holds for an
 * officer. The decision is written through the normal verdict path with a fixed
 * request_id, so it is idempotent and a restart does not add a second row.
 *
 * Gated on swarmsight.demo-seed so tests can switch it off.
 */
@Component
@ConditionalOnProperty(name = "swarmsight.demo-seed", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final DecisionService decisionService;

    public DemoSeeder(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        DecisionRequest seed = new DecisionRequest(
                "seed-hx-4471",
                "run-hx-4471",
                "HX-4471",
                "agent-housing-1",
                "HA-09",
                "draft_response",
                Map.of("tenancy_status", "confirmed", "eviction_risk", true, "dependent_children", true));

        Verdict verdict = decisionService.decide(seed);
        log.info("Demo seed: case {} resolved to {} ({}) at seq {}",
                seed.caseRef(), verdict.effect(), verdict.reasonCode(), verdict.seq());
    }
}
