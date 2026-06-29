package com.swarmsight.authority.arena;

import com.swarmsight.authority.broker.CapabilityBroker;
import com.swarmsight.authority.ledger.LedgerService;
import com.swarmsight.authority.run.RunContextRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Runs an agent through the Arena and, on a pass, builds the assurance case and
 * issues the certificate. The builder cannot be the approver, and issuance is a
 * ledger event. A failing agent earns no certificate.
 */
@Service
public class CertificationService {

    private static final Duration VALIDITY = Duration.ofDays(90);

    private final ArenaRunner arenaRunner;
    private final AssuranceCaseBuilder assuranceCaseBuilder;
    private final AssuranceCaseRepository assuranceCaseRepository;
    private final CertificateRepository certificateRepository;
    private final LedgerService ledgerService;
    private final RunContextRepository runContextRepository;
    private final CapabilityBroker broker;

    public CertificationService(
            ArenaRunner arenaRunner,
            AssuranceCaseBuilder assuranceCaseBuilder,
            AssuranceCaseRepository assuranceCaseRepository,
            CertificateRepository certificateRepository,
            LedgerService ledgerService,
            RunContextRepository runContextRepository,
            CapabilityBroker broker) {
        this.arenaRunner = arenaRunner;
        this.assuranceCaseBuilder = assuranceCaseBuilder;
        this.assuranceCaseRepository = assuranceCaseRepository;
        this.certificateRepository = certificateRepository;
        this.ledgerService = ledgerService;
        this.runContextRepository = runContextRepository;
        this.broker = broker;
    }

    /** The result of a certification attempt: the run, and a certificate iff it passed. */
    public record Outcome(
            ArenaResult arenaResult,
            AssuranceCase assuranceCase,
            Certificate certificate,
            String rejectionReason) {
    }

    public Outcome certify(Agent agent, String agentId, String builder, String approver) {
        if (builder == null || builder.equals(approver)) {
            return new Outcome(null, null, null,
                    "The builder cannot be the approver. Certification needs a separate sign-off.");
        }

        ArenaResult arenaResult = arenaRunner.run(agent, agentId);
        if (!arenaResult.overallPass()) {
            String reason = !arenaResult.safetyPass()
                    ? "Failed the safety gate on a severe or catastrophic scenario."
                    : "The proof was incomplete: a scenario produced no ledger trail.";
            return new Outcome(arenaResult, null, null, reason);
        }

        Instant now = Instant.now();
        AssuranceCase assuranceCase = assuranceCaseBuilder.build(arenaResult, builder, now);
        assuranceCaseRepository.insert(assuranceCase);

        Certificate certificate = new Certificate(
                "cert-" + agentId, agentId, assuranceCase.id(),
                arenaResult.certifiedActions(), arenaResult.notCertifiedActions(),
                arenaResult.recommendedCeiling(), builder, approver, now, now.plus(VALIDITY),
                CertStatus.ACTIVE.name());
        certificateRepository.insert(certificate, arenaResult);

        // A passing (re-)certification lifts any incident containment, the only
        // way an agent returns to live.
        broker.liftSuspension(agentId);

        ledgerIssuance(certificate, now);
        return new Outcome(arenaResult, assuranceCase, certificate, null);
    }

    private void ledgerIssuance(Certificate c, Instant now) {
        runContextRepository.ensureExists("cert-run:" + c.agentId(), c.agentId(), "certification", now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("certificate_id", c.id());
        payload.put("agent_id", c.agentId());
        payload.put("assurance_case_ref", c.assuranceCaseRef());
        payload.put("ceiling", c.ceiling());
        payload.put("certified_actions", c.certifiedActions());
        payload.put("not_certified_actions", c.notCertifiedActions());
        payload.put("builder", c.builder());
        payload.put("approver", c.approver());
        payload.put("expires_at", c.expiresAt().toString());
        ledgerService.append("certificate_issued", c.builder(), "cert-run:" + c.agentId(), c.agentId(),
                "certify", "n/a", payload, "cert:" + c.agentId(), now);
    }
}
