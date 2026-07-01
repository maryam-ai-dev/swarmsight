package com.swarmsight.authority.demo;

import com.swarmsight.authority.arena.AgentRepository;
import com.swarmsight.authority.arena.CertificationService;
import com.swarmsight.authority.arena.CompliantAgent;
import com.swarmsight.authority.arena.RegisteredAgent;
import com.swarmsight.authority.capture.CaptureRequests.ApproveRequest;
import com.swarmsight.authority.capture.CaptureRequests.AuthorRequest;
import com.swarmsight.authority.capture.CaptureRequests.EditRequest;
import com.swarmsight.authority.capture.CaptureService;
import com.swarmsight.authority.decision.DecisionRequest;
import com.swarmsight.authority.decision.DecisionService;
import com.swarmsight.authority.decision.Verdict;
import com.swarmsight.authority.policy.Guard;
import com.swarmsight.authority.policy.Guard.Clause;
import com.swarmsight.authority.policy.Guard.Clause.Op;
import com.swarmsight.authority.policy.Level;
import com.swarmsight.authority.workbench.PolicyChange;
import com.swarmsight.authority.workbench.PolicyWorkbench;
import com.swarmsight.authority.workbench.ProposeRequest;
import com.swarmsight.authority.workbench.ProposeRequest.SourceInput;
import com.swarmsight.authority.workbench.SourceDocument;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final String AGENT = "housing-appeals-agent-v3";

    private final DecisionService decisionService;
    private final CaptureService captureService;
    private final CertificationService certificationService;
    private final PolicyWorkbench policyWorkbench;
    private final AgentRepository agentRepository;
    private final String intelligenceBaseUrl;

    public DemoSeeder(DecisionService decisionService, CaptureService captureService,
            CertificationService certificationService, PolicyWorkbench policyWorkbench,
            AgentRepository agentRepository,
            @Value("${swarmsight.intelligence.base-url:http://localhost:8000}") String intelligenceBaseUrl) {
        this.decisionService = decisionService;
        this.captureService = captureService;
        this.certificationService = certificationService;
        this.policyWorkbench = policyWorkbench;
        this.agentRepository = agentRepository;
        this.intelligenceBaseUrl = intelligenceBaseUrl;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        // Register the demo agent in the registry, pointing at the internal
        // Intelligence service, so the live assurance run reaches a real endpoint
        // and the rest of the flow (certificate, go-live, oversight) keeps its id.
        if (!agentRepository.existsById(AGENT)) {
            agentRepository.insert(new RegisteredAgent(
                    AGENT, "Housing appeals agent", "v3",
                    intelligenceBaseUrl + "/agent/act", "gov-uk-prod",
                    List.of("draft_response", "request_evidence", "escalate",
                            "send_decision", "close_case"),
                    null, "owner@swarmsight.local", true, Instant.now(), "HA-09"));
        }
        // The rest of the department's Core-4 assistants, each spun up for a
        // workflow and governed against the department's policy for that workflow.
        // They share the Intelligence endpoint (same model) and are registered but
        // not yet certified, so the arena can be run against them live in the demo.
        seedAgent("homelessness-triage-v1", "Homelessness triage agent", "HL-01");
        seedAgent("repairs-triage-v1", "Repairs triage agent", "RP-01");
        seedAgent("foi-redaction-v1", "FOI redaction agent", "FOI-01");

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

        // Certify the demo agent deterministically with an in-process compliant
        // agent (the live endpoints exercise Intelligence over HTTP). The builder
        // and approver differ, as certification requires.
        CertificationService.Outcome outcome = certificationService.certify(
                new CompliantAgent(), AGENT, "swarmsight-arena", "Head of Housing Service");

        // Stage the section 21 abolition as a proposed policy change, and preview
        // it by shadow replay, so the Policy versions screen shows a real staged
        // change awaiting human activation.
        Guard section21 = new Guard(
                "section-21-ground-removed",
                List.of(new Clause("section_21_ground", Op.IS_TRUE)),
                Level.L4_HUMAN, "SECTION_21_ABOLISHED",
                "Section 21 no-fault eviction grounds are abolished under the Renters Reform Act. "
                        + "Held for officer review.",
                "Renters Reform Act 2025 s.21");
        SourceDocument source = new SourceDocument(
                "https://www.legislation.gov.uk/renters-reform-2025/section/21",
                "2025-enacted", "sha256:9f1c2d7e4a", "Renters Reform Act 2025, section 21 abolition");
        PolicyChange change = policyWorkbench.propose(new ProposeRequest(
                "HA-09", "v7", "v8", List.of(new SourceInput(source, List.of(section21), List.of()))));
        policyWorkbench.replay(change);

        log.info("Demo seed: case {} {} ({}) seq {}; agent {} certified={} ceiling={}; staged change {} status={}",
                CASE, verdict.effect(), verdict.reasonCode(), verdict.seq(),
                AGENT, outcome.certificate() != null,
                outcome.certificate() == null ? "none" : outcome.certificate().ceiling(),
                change.id(), change.status());
    }

    /** Register another department assistant for a workflow (idempotent). */
    private void seedAgent(String id, String name, String workflow) {
        if (agentRepository.existsById(id)) {
            return;
        }
        agentRepository.insert(new RegisteredAgent(
                id, name, "v1", intelligenceBaseUrl + "/agent/act", "gov-uk-prod",
                List.of("draft_response", "request_evidence", "escalate"),
                null, "owner@swarmsight.local", true, Instant.now(), workflow));
    }
}
