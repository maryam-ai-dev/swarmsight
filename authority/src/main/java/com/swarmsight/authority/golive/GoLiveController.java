package com.swarmsight.authority.golive;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The go-live surface: read the gate, read source readiness, and record the
 * service-owner sign-off. These drive the Go-live check and Sources screens.
 */
@RestController
public class GoLiveController {

    private final GoLiveGate goLiveGate;
    private final DeploymentService deploymentService;
    private final SourceReadinessRepository sourceReadinessRepository;

    public GoLiveController(
            GoLiveGate goLiveGate,
            DeploymentService deploymentService,
            SourceReadinessRepository sourceReadinessRepository) {
        this.goLiveGate = goLiveGate;
        this.deploymentService = deploymentService;
        this.sourceReadinessRepository = sourceReadinessRepository;
    }

    public record DeployRequest(
            @NotBlank String approver,
            @NotBlank String scope,
            @NotBlank String trialPeriod,
            @NotBlank String reviewCheckpoint,
            List<String> conditions,
            String requestedCeiling,
            String workflow,
            Boolean citizenFacing) {
    }

    /** Evaluate the gate. Read-only; enforces the certificate, does not re-decide. */
    @GetMapping("/agents/{agentId}/go-live")
    public GateResult goLive(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "HA-09") String workflow,
            @RequestParam(defaultValue = "true") boolean citizenFacing,
            @RequestParam(required = false) String requestedCeiling) {
        return goLiveGate.evaluate(agentId, workflow, citizenFacing, requestedCeiling);
    }

    /** The source readiness snapshots the gate reads. */
    @GetMapping("/sources/readiness")
    public List<SourceReadinessSnapshot> readiness() {
        return sourceReadinessRepository.findAll();
    }

    /** Record the service-owner sign-off. Refused if the gate blocks promotion. */
    @PostMapping("/agents/{agentId}/deployment-approval")
    public DeploymentService.Outcome approve(
            @PathVariable String agentId, @Valid @RequestBody DeployRequest req) {
        DeploymentService.Request request = new DeploymentService.Request(
                req.approver(), req.scope(), req.trialPeriod(), req.reviewCheckpoint(),
                req.conditions(), req.requestedCeiling(),
                req.workflow() == null ? "HA-09" : req.workflow(),
                req.citizenFacing() == null || req.citizenFacing());
        return deploymentService.approve(agentId, request);
    }
}
