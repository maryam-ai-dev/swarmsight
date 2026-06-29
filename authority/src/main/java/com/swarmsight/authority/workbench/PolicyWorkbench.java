package com.swarmsight.authority.workbench;

import com.swarmsight.authority.arena.CertificateRepository;
import com.swarmsight.authority.ledger.LedgerService;
import com.swarmsight.authority.policy.Guard;
import com.swarmsight.authority.policy.PolicyRepository;
import com.swarmsight.authority.policy.PolicyVersion;
import com.swarmsight.authority.run.RunContextRepository;
import com.swarmsight.authority.workbench.ShadowReplayReport.ReplayCase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * The Policy Workbench: propose a change from sources, show the diff, preview by
 * shadow replay, and activate on a future effective date. No rule goes live
 * unreviewed, conflicts hold, and activation is a ledger event that flags the
 * certificates it impacts.
 */
@Service
public class PolicyWorkbench {

    private final PolicyRepository policyRepository;
    private final PolicyChangeRepository changeRepository;
    private final CandidateExtractor extractor;
    private final ShadowReplayer replayer;
    private final CertificateRepository certificateRepository;
    private final LedgerService ledgerService;
    private final RunContextRepository runContextRepository;

    public PolicyWorkbench(
            PolicyRepository policyRepository,
            PolicyChangeRepository changeRepository,
            CandidateExtractor extractor,
            ShadowReplayer replayer,
            CertificateRepository certificateRepository,
            LedgerService ledgerService,
            RunContextRepository runContextRepository) {
        this.policyRepository = policyRepository;
        this.changeRepository = changeRepository;
        this.extractor = extractor;
        this.replayer = replayer;
        this.certificateRepository = certificateRepository;
        this.ledgerService = ledgerService;
        this.runContextRepository = runContextRepository;
    }

    public record ActivationResult(
            String newVersion, Instant effectiveFrom, List<String> impactedCertificates, String transitionRule) {
    }

    public record ActivateOutcome(PolicyChange change, ActivationResult result, String rejectionReason) {
    }

    /** Extract a candidate from the sources. Conflicts are held, not resolved. */
    public PolicyChange propose(ProposeRequest req) {
        PolicyVersion base = policyRepository.findVersion(req.policyId(), req.baseVersion())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No base version " + req.policyId() + " " + req.baseVersion()));

        CandidateExtractor.ExtractionResult extraction = extractor.extract(base, req.sources());
        PolicyChange.Status status = extraction.conflictReason() == null
                ? PolicyChange.Status.PROPOSED : PolicyChange.Status.HELD;

        PolicyChange change = new PolicyChange(
                "change-" + req.policyId() + "-" + req.proposedVersion(),
                req.policyId(), req.baseVersion(), req.proposedVersion(),
                req.sources().stream().map(ProposeRequest.SourceInput::document).toList(),
                extraction.candidate(), status, extraction.conflictReason(), null, Instant.now(), null);
        changeRepository.insert(change);
        return change;
    }

    /** The diff a human reviews: the guards the change adds and removes. */
    public PolicyDiff diff(PolicyChange change) {
        PolicyVersion base = policyRepository.findVersion(change.policyId(), change.baseVersion()).orElseThrow();
        Set<String> baseNames = new TreeSet<>(base.guards().stream().map(Guard::name).toList());

        List<PolicyDiff.GuardSummary> added = new ArrayList<>();
        Set<String> candidateNames = new TreeSet<>();
        if (change.candidate() != null) {
            for (Guard g : change.candidate().guards()) {
                candidateNames.add(g.name());
                if (!baseNames.contains(g.name())) {
                    added.add(new PolicyDiff.GuardSummary(g.name(), g.raiseTo().name(), g.source()));
                }
            }
        }
        List<String> removed = base.guards().stream().map(Guard::name)
                .filter(n -> !candidateNames.contains(n)).toList();

        List<String> notes = new ArrayList<>();
        if (change.status() == PolicyChange.Status.HELD) {
            notes.add(change.conflictReason());
        }
        return new PolicyDiff(change.baseVersion(), change.proposedVersion(), added, removed, notes);
    }

    /** Preview the change by replaying it against synthetic cases. Stored on the change. */
    public ShadowReplayReport replay(PolicyChange change) {
        PolicyVersion base = policyRepository.findVersion(change.policyId(), change.baseVersion()).orElseThrow();
        PolicyVersion candidate = new PolicyVersion(
                change.policyId(), change.proposedVersion(), Instant.now(),
                change.candidate().requiredInputs(), change.candidate().actionFloors(),
                change.candidate().guards());
        ShadowReplayReport report = replayer.replay(base, candidate, syntheticCases());
        changeRepository.saveShadowReport(change.id(), report);
        return report;
    }

    /** Activate on a future effective date, flag impacted certificates, ledger it. */
    public ActivateOutcome activate(String changeId, String approver, Instant effectiveFrom) {
        PolicyChange change = changeRepository.findById(changeId)
                .orElseThrow(() -> new IllegalArgumentException("No change " + changeId));

        if (change.status() == PolicyChange.Status.HELD) {
            return new ActivateOutcome(change, null, "The change is held due to a source conflict.");
        }
        if (change.status() == PolicyChange.Status.ACTIVATED) {
            return new ActivateOutcome(change, null, "The change is already activated.");
        }
        Instant now = Instant.now();
        if (!effectiveFrom.isAfter(now)) {
            return new ActivateOutcome(change, null, "The effective date must be in the future.");
        }

        PolicyVersion newVersion = new PolicyVersion(
                change.policyId(), change.proposedVersion(), effectiveFrom,
                change.candidate().requiredInputs(), change.candidate().actionFloors(),
                change.candidate().guards());
        policyRepository.insert(newVersion);

        List<String> impacted = certificateRepository.findActiveIds();
        impacted.forEach(certificateRepository::markReviewRequired);

        String transitionRule = "Decisions before " + effectiveFrom + " remain under " + change.baseVersion()
                + "; on or after, under " + change.proposedVersion() + ".";

        ledgerChange(change, effectiveFrom, approver, impacted, transitionRule, now);
        changeRepository.markActivated(change.id(), effectiveFrom, now);

        return new ActivateOutcome(change,
                new ActivationResult(change.proposedVersion(), effectiveFrom, impacted, transitionRule), null);
    }

    private void ledgerChange(PolicyChange change, Instant effectiveFrom, String approver,
            List<String> impacted, String transitionRule, Instant now) {
        String runId = "policy-change:" + change.id();
        runContextRepository.ensureExists(runId, change.policyId(), change.policyId(), now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("change_id", change.id());
        payload.put("policy_id", change.policyId());
        payload.put("base_version", change.baseVersion());
        payload.put("new_version", change.proposedVersion());
        payload.put("effective_from", effectiveFrom.toString());
        payload.put("sources", change.sources().stream().map(SourceDocument::uri).toList());
        payload.put("impacted_certificates", impacted);
        payload.put("transition_rule", transitionRule);
        ledgerService.append("policy_changed", approver, runId, change.policyId(), "policy_change",
                change.proposedVersion(), payload, "policy-change-activate:" + change.id(), now);
    }

    /** A small synthetic suite, including a section 21 case the change would flip. */
    private List<ReplayCase> syntheticCases() {
        return List.of(
                new ReplayCase("syn-clear", "draft_response", Map.of(
                        "tenancy_status", "secure", "eviction_risk", false,
                        "dependent_children", false, "eviction_notice", true)),
                new ReplayCase("syn-eviction-dependents", "draft_response", Map.of(
                        "tenancy_status", "at_risk", "eviction_risk", true,
                        "dependent_children", true, "eviction_notice", true)),
                new ReplayCase("syn-section21", "draft_response", Map.of(
                        "tenancy_status", "at_risk", "eviction_risk", true, "dependent_children", false,
                        "eviction_notice", true, "section_21_ground", true)),
                new ReplayCase("syn-evidence-missing", "draft_response", Map.of(
                        "tenancy_status", "at_risk", "eviction_risk", true, "dependent_children", false)));
    }
}
