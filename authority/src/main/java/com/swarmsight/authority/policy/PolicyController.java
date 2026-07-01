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
        List<PolicyVersion> versions = policyRepository.findVersions(workflow);
        // findVersions is newest effective_from first. The in-force version is the
        // first whose effective_from is at or before now; later-dated ones are
        // SCHEDULED, earlier-dated ones below the in-force one are SUPERSEDED. This
        // is derived from the immutable rows, never a stored mutable flag.
        Instant now = Instant.now();
        int activeIdx = -1;
        for (int i = 0; i < versions.size(); i++) {
            if (!versions.get(i).effectiveFrom().isAfter(now)) {
                activeIdx = i;
                break;
            }
        }
        List<PolicyVersionView> out = new java.util.ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            String status = i == activeIdx ? "ACTIVE"
                    : versions.get(i).effectiveFrom().isAfter(now) ? "SCHEDULED" : "SUPERSEDED";
            out.add(PolicyVersionView.of(versions.get(i), status));
        }
        return out;
    }

    /** A compact, render-friendly summary of one policy version. */
    public record PolicyVersionView(
            String policyId,
            String version,
            Instant effectiveFrom,
            String status,
            List<String> actions,
            List<String> requiredInputs,
            List<GuardView> guards) {

        static PolicyVersionView of(PolicyVersion v, String status) {
            List<GuardView> guards = v.guards().stream()
                    .map(g -> new GuardView(g.name(), g.raiseTo().name(), g.source()))
                    .toList();
            return new PolicyVersionView(
                    v.policyId(), v.version(), v.effectiveFrom(), status,
                    v.actionFloors().keySet().stream().sorted().toList(),
                    v.requiredInputs(), guards);
        }
    }

    public record GuardView(String name, String raiseTo, String source) {
    }
}
