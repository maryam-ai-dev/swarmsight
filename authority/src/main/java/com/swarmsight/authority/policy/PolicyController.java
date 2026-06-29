package com.swarmsight.authority.policy;

import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of versioned policy. The Policy versions screen renders this.
 */
@RestController
public class PolicyController {

    private final PolicyRepository policyRepository;

    public PolicyController(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @GetMapping("/policies/{workflow}/versions")
    public List<PolicyVersionView> versions(@PathVariable String workflow) {
        return policyRepository.findVersions(workflow).stream()
                .map(PolicyVersionView::of)
                .toList();
    }

    /** A compact, render-friendly summary of one policy version. */
    public record PolicyVersionView(
            String policyId,
            String version,
            Instant effectiveFrom,
            List<String> actions,
            List<String> requiredInputs,
            List<GuardView> guards) {

        static PolicyVersionView of(PolicyVersion v) {
            List<GuardView> guards = v.guards().stream()
                    .map(g -> new GuardView(g.name(), g.raiseTo().name(), g.source()))
                    .toList();
            return new PolicyVersionView(
                    v.policyId(), v.version(), v.effectiveFrom(),
                    v.actionFloors().keySet().stream().sorted().toList(),
                    v.requiredInputs(), guards);
        }
    }

    public record GuardView(String name, String raiseTo, String source) {
    }
}
