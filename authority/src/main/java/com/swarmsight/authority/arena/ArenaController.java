package com.swarmsight.authority.arena;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The assurance surface: run the Arena against the live agent, certify on a pass,
 * and read back the certificate and its assurance case. These drive the Check
 * agent, Test results, and Certificate screens.
 */
@RestController
public class ArenaController {

    private final ArenaRunner arenaRunner;
    private final CertificationService certificationService;
    private final AgentRepository agentRepository;
    private final HttpAgentFactory httpAgentFactory;
    private final CertificateRepository certificateRepository;
    private final AssuranceCaseRepository assuranceCaseRepository;

    public ArenaController(
            ArenaRunner arenaRunner,
            CertificationService certificationService,
            AgentRepository agentRepository,
            HttpAgentFactory httpAgentFactory,
            CertificateRepository certificateRepository,
            AssuranceCaseRepository assuranceCaseRepository) {
        this.arenaRunner = arenaRunner;
        this.certificationService = certificationService;
        this.agentRepository = agentRepository;
        this.httpAgentFactory = httpAgentFactory;
        this.certificateRepository = certificateRepository;
        this.assuranceCaseRepository = assuranceCaseRepository;
    }

    public record CertifyRequest(@NotBlank String builder, @NotBlank String approver) {
    }

    public record CertificateReport(Certificate certificate, AssuranceCase assuranceCase, JsonNode arenaSummary) {
    }

    /** The agent reached at its own registered endpoint. */
    private Agent resolve(String agentId) {
        RegisteredAgent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new AgentService.RegistrationException(
                        "Agent " + agentId + " is not registered."));
        return httpAgentFactory.forAgent(agent);
    }

    /** Run the suite against the agent's own endpoint, in shadow. No certificate. */
    @PostMapping("/agents/{agentId}/arena/run")
    public ArenaResult run(@PathVariable String agentId) {
        return arenaRunner.run(resolve(agentId), agentId);
    }

    /** Run and, on a pass, issue the certificate. Builder must differ from approver. */
    @PostMapping("/agents/{agentId}/certify")
    public CertificationService.Outcome certify(
            @PathVariable String agentId, @Valid @RequestBody CertifyRequest req) {
        return certificationService.certify(resolve(agentId), agentId, req.builder(), req.approver());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(AgentService.RegistrationException.class)
    public ResponseEntity<java.util.Map<String, String>> onMissingAgent(
            AgentService.RegistrationException e) {
        return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
    }

    /** The stored certificate, its assurance case, and the arena summary behind it. */
    @GetMapping("/agents/{agentId}/certificate")
    public ResponseEntity<CertificateReport> certificate(@PathVariable String agentId) {
        return certificateRepository.findLatestByAgent(agentId)
                .map(stored -> {
                    AssuranceCase ac = assuranceCaseRepository
                            .findById(stored.certificate().assuranceCaseRef()).orElse(null);
                    return ResponseEntity.ok(
                            new CertificateReport(stored.certificate(), ac, stored.arenaSummary()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
