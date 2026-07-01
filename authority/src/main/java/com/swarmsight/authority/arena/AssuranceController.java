package com.swarmsight.authority.arena;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The live assurance surface that builds the Arena from intent and policy:
 * derive the capability checkboxes from a description, generate a policy-derived
 * scenario suite scoped to the ticked actions, run it against the agent's own
 * endpoint, and certify on a pass. The hand-written suite still exists on the
 * Check agent screen; this is the generated path.
 */
@RestController
public class AssuranceController {

    private static final Set<String> DEFAULT_ALLOWED =
            new LinkedHashSet<>(List.of("draft_response", "request_evidence", "escalate"));
    private static final Set<String> DEFAULT_FORBIDDEN =
            new LinkedHashSet<>(List.of("send_decision", "close_case", "release_records"));

    private final CapabilityDeriver capabilityDeriver;
    private final ScenarioGenerator scenarioGenerator;
    private final ArenaRunner arenaRunner;
    private final CertificationService certificationService;
    private final AgentRepository agentRepository;
    private final HttpAgentFactory httpAgentFactory;

    public AssuranceController(
            CapabilityDeriver capabilityDeriver,
            ScenarioGenerator scenarioGenerator,
            ArenaRunner arenaRunner,
            CertificationService certificationService,
            AgentRepository agentRepository,
            HttpAgentFactory httpAgentFactory) {
        this.capabilityDeriver = capabilityDeriver;
        this.scenarioGenerator = scenarioGenerator;
        this.arenaRunner = arenaRunner;
        this.certificationService = certificationService;
        this.agentRepository = agentRepository;
        this.httpAgentFactory = httpAgentFactory;
    }

    public record DeriveRequest(String description, String policyId) {
    }

    public record AssuranceRequest(
            String policyId, List<String> allowedActions, List<String> forbiddenActions) {
    }

    public record CertifyRequest(
            @NotBlank String builder, @NotBlank String approver,
            String policyId, List<String> allowedActions, List<String> forbiddenActions) {
    }

    /** Description -> capability checkboxes (Claude proposes, citizen-facing default off). */
    @PostMapping("/agents/capabilities/derive")
    public List<CapabilityDeriver.CapabilityProposal> derive(@RequestBody DeriveRequest req) {
        return capabilityDeriver.derive(req.description());
    }

    /** Generate a policy-derived suite for the ticked actions and run it against the agent. */
    @PostMapping("/agents/{agentId}/assurance/run")
    public ResponseEntity<?> run(@PathVariable String agentId, @RequestBody AssuranceRequest req) {
        try {
            List<Scenario> suite = generate(req);
            return ResponseEntity.ok(arenaRunner.run(resolve(agentId), agentId, suite));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /** Generate, run, and certify on a pass (builder must differ from approver). */
    @PostMapping("/agents/{agentId}/assurance/certify")
    public ResponseEntity<?> certify(@PathVariable String agentId, @Valid @RequestBody CertifyRequest req) {
        try {
            List<Scenario> suite = generate(
                    new AssuranceRequest(req.policyId(), req.allowedActions(), req.forbiddenActions()));
            return ResponseEntity.ok(certificationService.certify(
                    resolve(agentId), agentId, req.builder(), req.approver(), suite));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    private List<Scenario> generate(AssuranceRequest req) {
        String policyId = req.policyId() == null || req.policyId().isBlank() ? "HA-09" : req.policyId();
        Set<String> allowed = req.allowedActions() == null || req.allowedActions().isEmpty()
                ? DEFAULT_ALLOWED : new LinkedHashSet<>(req.allowedActions());
        Set<String> forbidden = req.forbiddenActions() == null || req.forbiddenActions().isEmpty()
                ? DEFAULT_FORBIDDEN : new LinkedHashSet<>(req.forbiddenActions());
        return scenarioGenerator.generate(policyId, allowed, forbidden);
    }

    private Agent resolve(String agentId) {
        RegisteredAgent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent " + agentId + " is not registered."));
        return httpAgentFactory.forAgent(agent);
    }
}
