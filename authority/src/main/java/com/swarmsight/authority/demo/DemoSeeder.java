package com.swarmsight.authority.demo;

import com.swarmsight.authority.capture.CaptureRequests.ApproveRequest;
import com.swarmsight.authority.capture.CaptureRequests.AuthorRequest;
import com.swarmsight.authority.capture.CaptureRequests.EditRequest;
import com.swarmsight.authority.capture.CaptureService;
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
 * Seeds the one case the demo renders: HX-4471, a housing appeal with eviction
 * risk and dependent children, which the HA-09 guard holds for an officer. The
 * full flow is seeded (decision, author, edit, approve) so the Case surface and
 * the Proof pack both render real data. Fixed request_ids keep it idempotent, so
 * a restart adds no extra rows.
 *
 * Gated on swarmsight.demo-seed so tests can switch it off.
 */
@Component
@ConditionalOnProperty(name = "swarmsight.demo-seed", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private static final String CASE = "HX-4471";
    private static final String RUN = "run-hx-4471";

    private final DecisionService decisionService;
    private final CaptureService captureService;

    public DemoSeeder(DecisionService decisionService, CaptureService captureService) {
        this.decisionService = decisionService;
        this.captureService = captureService;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        Verdict verdict = decisionService.decide(new DecisionRequest(
                "seed-hx-4471", RUN, CASE, "agent-housing-1", "HA-09", "draft_response",
                Map.of("tenancy_status", "confirmed", "eviction_risk", true, "dependent_children", true)));

        captureService.author(CASE, new AuthorRequest(
                "seed-hx-4471-author", RUN, "housing-appeals-agent-v3", "HA-09", "draft_response",
                "Dear Ms Adeyemi, thank you for your appeal regarding your housing application. "
                        + "Having reviewed your income and tenancy documents, I am writing to inform you that "
                        + "your application has been refused."));

        captureService.edit(CASE, new EditRequest(
                "seed-hx-4471-edit", RUN, "J. Okafor", "HA-09", "draft_response",
                "Dear Ms Adeyemi, thank you for your appeal regarding your housing application. "
                        + "Having reviewed your income and tenancy documents, I am writing to confirm the next "
                        + "steps in your case, and to set out how you can appeal this decision."));

        captureService.approve(CASE, new ApproveRequest(
                "seed-hx-4471-approve", RUN, "J. Okafor", "HA-09", "draft_response", "OFFICER_APPROVED",
                "Softened the refusal wording and added appeals signposting before approval.",
                "First-tier Tribunal (Property Chamber)",
                "Appeals paragraph added to the closing of the letter."));

        log.info("Demo seed: case {} resolved to {} ({}) at seq {}, with author, edit, approve captured",
                CASE, verdict.effect(), verdict.reasonCode(), verdict.seq());
    }
}
