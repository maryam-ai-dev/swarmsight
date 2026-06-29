package com.swarmsight.authority.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Policy Workbench surface: propose a change, review its diff and shadow
 * replay, and activate it. These drive the staged change and Activate action on
 * the Policy versions screen.
 */
@RestController
public class WorkbenchController {

    private final PolicyWorkbench workbench;
    private final PolicyChangeRepository changeRepository;

    public WorkbenchController(PolicyWorkbench workbench, PolicyChangeRepository changeRepository) {
        this.workbench = workbench;
        this.changeRepository = changeRepository;
    }

    public record ActivateRequest(@NotBlank String approver, @NotBlank String effectiveFrom) {
    }

    public record ChangeView(PolicyChange change, PolicyDiff diff, JsonNode shadowReport) {
    }

    @PostMapping("/policy-changes")
    public PolicyChange propose(@RequestBody ProposeRequest req) {
        return workbench.propose(req);
    }

    @GetMapping("/policy-changes")
    public List<PolicyChange> list(@RequestParam(defaultValue = "HA-09") String policyId) {
        return changeRepository.findByPolicyId(policyId);
    }

    @GetMapping("/policy-changes/{id}")
    public ResponseEntity<ChangeView> get(@PathVariable String id) {
        return changeRepository.findById(id)
                .map(change -> ResponseEntity.ok(new ChangeView(
                        change, workbench.diff(change), changeRepository.findShadowReport(id).orElse(null))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/policy-changes/{id}/replay")
    public ResponseEntity<ShadowReplayReport> replay(@PathVariable String id) {
        return changeRepository.findById(id)
                .map(change -> ResponseEntity.ok(workbench.replay(change)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/policy-changes/{id}/activate")
    public PolicyWorkbench.ActivateOutcome activate(
            @PathVariable String id, @Valid @RequestBody ActivateRequest req) {
        return workbench.activate(id, req.approver(), Instant.parse(req.effectiveFrom()));
    }
}
