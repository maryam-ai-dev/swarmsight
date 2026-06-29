package com.swarmsight.authority.golive;

import com.swarmsight.authority.arena.Certificate;
import com.swarmsight.authority.arena.CertificateRepository;
import com.swarmsight.authority.broker.ConnectorHealth;
import com.swarmsight.authority.policy.Guard;
import com.swarmsight.authority.policy.Level;
import com.swarmsight.authority.policy.PolicyRepository;
import com.swarmsight.authority.policy.PolicyVersion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Evaluates whether an agent may be promoted: it reads the certificate and the
 * surrounding conditions and enforces them. It never re-runs the Arena or
 * re-decides a verdict. Read-only; the write is the separate sign-off.
 */
@Service
public class GoLiveGate {

    private final CertificateRepository certificateRepository;
    private final PolicyRepository policyRepository;
    private final SourceReadinessRepository sourceReadinessRepository;
    private final ConnectorHealth connectorHealth;

    public GoLiveGate(
            CertificateRepository certificateRepository,
            PolicyRepository policyRepository,
            SourceReadinessRepository sourceReadinessRepository,
            ConnectorHealth connectorHealth) {
        this.certificateRepository = certificateRepository;
        this.policyRepository = policyRepository;
        this.sourceReadinessRepository = sourceReadinessRepository;
        this.connectorHealth = connectorHealth;
    }

    public GateResult evaluate(String agentId, String workflow, boolean citizenFacing, String requestedCeiling) {
        Instant now = Instant.now();

        Optional<Certificate> cert = certificateRepository.findLatestByAgent(agentId)
                .map(CertificateRepository.Stored::certificate);
        boolean present = cert.isPresent();
        boolean expired = present && now.isAfter(cert.get().expiresAt());
        String certifiedCeiling = present ? cert.get().ceiling() : null;
        String certStatus = present ? cert.get().status() : null;
        boolean active = present && "ACTIVE".equals(certStatus);
        GateResult.CertificateSummary summary = new GateResult.CertificateSummary(
                present,
                present ? cert.get().id() : null,
                certifiedCeiling,
                certStatus,
                present ? cert.get().issuedAt() : null,
                present ? cert.get().expiresAt() : null,
                expired);

        // Promote up to the certified ceiling by default.
        String requested = (requestedCeiling == null || requestedCeiling.isBlank())
                ? certifiedCeiling : requestedCeiling;

        Optional<PolicyVersion> policy = policyRepository.resolve(workflow, now);
        boolean policyBound = policy.isPresent();
        String policyVersion = policy.map(PolicyVersion::version).orElse(null);
        boolean humanJudgementActive = policy.isPresent()
                && policy.get().guards().stream().anyMatch(g -> g.raiseTo() == Level.L4_HUMAN);

        List<SourceReadinessSnapshot> sources = sourceReadinessRepository.findAll();
        boolean sourcesReady = !citizenFacing || sources.stream().allMatch(SourceReadinessSnapshot::ready);

        Map<String, Boolean> connectors = connectorHealth.statuses();
        boolean connectorsHealthy = connectorHealth.allHealthy();

        boolean withinCeiling = withinCeiling(requested, certifiedCeiling);
        boolean ceilingOk = active && !expired && withinCeiling;

        List<String> blockers = new ArrayList<>();
        if (!present) {
            blockers.add("No certificate has been issued for this agent.");
        } else if (expired) {
            blockers.add("The certificate has expired.");
        } else if (!active) {
            blockers.add("The certificate is not active (status " + certStatus + "); re-certification is required.");
        }
        if (!policyBound) {
            blockers.add("No policy version is in force for " + workflow + ".");
        }
        if (!sourcesReady) {
            blockers.add("A source is below its readiness threshold for citizen-facing work.");
        }
        if (!connectorsHealthy) {
            blockers.add("A connector is not healthy.");
        }
        if (!humanJudgementActive) {
            blockers.add("The human-judgement rule is not active in the policy.");
        }
        if (active && !expired && !withinCeiling) {
            blockers.add("The requested ceiling " + requested + " exceeds the certified ceiling "
                    + certifiedCeiling + ".");
        }

        boolean promotable = blockers.isEmpty();
        String verdict = promotable
                ? "Promotion allowed for " + requested + " actions only."
                : "Promotion blocked.";

        return new GateResult(agentId, workflow, citizenFacing, requested, certifiedCeiling, summary,
                policyBound, policyVersion, sources, sourcesReady, connectors, connectorsHealthy,
                humanJudgementActive, ceilingOk, promotable, blockers, verdict);
    }

    private boolean withinCeiling(String requested, String certified) {
        if (requested == null || certified == null) {
            return false;
        }
        try {
            return Level.valueOf(requested).ordinal() <= Level.valueOf(certified).ordinal();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
