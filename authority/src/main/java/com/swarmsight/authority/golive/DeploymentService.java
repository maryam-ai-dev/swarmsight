package com.swarmsight.authority.golive;

import com.swarmsight.authority.ledger.LedgerService;
import com.swarmsight.authority.run.RunContextRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Records a service owner's sign-off to deploy an agent. It re-evaluates the gate
 * and refuses if promotion is blocked, so a certificate alone never equals a
 * deployment. The approval is a ledger event and is granted only up to the
 * certified ceiling.
 */
@Service
public class DeploymentService {

    private final GoLiveGate goLiveGate;
    private final DeploymentApprovalRepository approvalRepository;
    private final LedgerService ledgerService;
    private final RunContextRepository runContextRepository;

    public DeploymentService(
            GoLiveGate goLiveGate,
            DeploymentApprovalRepository approvalRepository,
            LedgerService ledgerService,
            RunContextRepository runContextRepository) {
        this.goLiveGate = goLiveGate;
        this.approvalRepository = approvalRepository;
        this.ledgerService = ledgerService;
        this.runContextRepository = runContextRepository;
    }

    /** A sign-off request and what the gate said. */
    public record Request(
            String approver, String scope, String trialPeriod, String reviewCheckpoint,
            List<String> conditions, String requestedCeiling, String workflow, boolean citizenFacing) {
    }

    public record Outcome(GateResult gate, DeploymentApproval approval, String rejectionReason) {
    }

    public Outcome approve(String agentId, Request req) {
        GateResult gate = goLiveGate.evaluate(agentId, req.workflow(), req.citizenFacing(), req.requestedCeiling());
        if (!gate.promotable()) {
            return new Outcome(gate, null, "Promotion blocked: " + String.join(" ", gate.blockers()));
        }

        Instant now = Instant.now();
        DeploymentApproval approval = new DeploymentApproval(
                "dep-" + agentId, agentId, req.approver(), req.scope(), req.trialPeriod(),
                req.reviewCheckpoint(), conditions(req.conditions()), gate.requestedCeiling(), now);
        approvalRepository.insert(approval);
        ledgerApproval(approval, gate.policyVersion(), now);

        return new Outcome(gate, approval, null);
    }

    private void ledgerApproval(DeploymentApproval a, String policyVersion, Instant now) {
        runContextRepository.ensureExists("deploy-run:" + a.agentId(), a.agentId(), "deployment", now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approval_id", a.id());
        payload.put("agent_id", a.agentId());
        payload.put("approver", a.approver());
        payload.put("scope", a.scope());
        payload.put("granted_ceiling", a.grantedCeiling());
        payload.put("trial_period", a.trialPeriod());
        payload.put("review_checkpoint", a.reviewCheckpoint());
        payload.put("conditions", a.conditions());
        ledgerService.append("deployment_approved", a.approver(), "deploy-run:" + a.agentId(), a.agentId(),
                "deploy", policyVersion == null ? "n/a" : policyVersion, payload, "deploy:" + a.agentId(), now);
    }

    private List<String> conditions(List<String> conditions) {
        return conditions == null ? List.of() : conditions;
    }
}
