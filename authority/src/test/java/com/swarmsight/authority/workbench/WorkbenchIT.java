package com.swarmsight.authority.workbench;

import static org.assertj.core.api.Assertions.assertThat;

import com.swarmsight.authority.arena.CertificateRepository;
import com.swarmsight.authority.arena.CertificationService;
import com.swarmsight.authority.arena.CompliantAgent;
import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerRow;
import com.swarmsight.authority.policy.Guard;
import com.swarmsight.authority.policy.Guard.Clause;
import com.swarmsight.authority.policy.Guard.Clause.Op;
import com.swarmsight.authority.policy.Level;
import com.swarmsight.authority.policy.PolicyRepository;
import com.swarmsight.authority.policy.PolicyVersion;
import com.swarmsight.authority.workbench.ProposeRequest.SourceInput;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The Sprint 8 guarantees against a live Postgres: a staged change is previewed
 * by shadow replay; activation is future-dated, flags impacted certificates, and
 * is ledgered with a transition rule; a decision before the change still audits
 * under the old version; and a conflict between sources holds.
 */
@SpringBootTest(properties = "swarmsight.demo-seed=false")
@Testcontainers
class WorkbenchIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired private PolicyWorkbench workbench;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private PolicyChangeRepository changeRepository;
    @Autowired private CertificationService certificationService;
    @Autowired private CertificateRepository certificateRepository;
    @Autowired private LedgerRepository ledgerRepository;

    private SourceDocument source() {
        return new SourceDocument("https://www.legislation.gov.uk/renters-reform-2025/section/21",
                "2025-enacted", "sha256:9f1c2d7e4a", "Renters Reform Act 2025, section 21 abolition");
    }

    private Guard section21(Level raiseTo) {
        return new Guard("section-21-ground-removed",
                List.of(new Clause("section_21_ground", Op.IS_TRUE)),
                raiseTo, "SECTION_21_ABOLISHED", "Section 21 grounds abolished. Held for officer review.",
                "Renters Reform Act 2025 s.21");
    }

    private ProposeRequest propose(String proposedVersion, List<SourceInput> sources) {
        return new ProposeRequest("HA-09", "v7", proposedVersion, sources);
    }

    @Test
    void aStagedChangeIsPreviewedByShadowReplay() {
        PolicyChange change = workbench.propose(propose("v8r",
                List.of(new SourceInput(source(), List.of(section21(Level.L4_HUMAN)), List.of()))));
        assertThat(change.status()).isEqualTo(PolicyChange.Status.PROPOSED);
        assertThat(change.candidate().guards()).anyMatch(g -> g.name().equals("section-21-ground-removed"));

        ShadowReplayReport report = workbench.replay(change);
        assertThat(report.changedCount()).isEqualTo(1);
        assertThat(report.results())
                .anyMatch(r -> r.caseRef().equals("syn-section21") && r.changed()
                        && r.oldEffect().equals("ALLOW") && r.newEffect().equals("HOLD"));
    }

    @Test
    void aConflictBetweenTwoSourcesHolds() {
        PolicyChange change = workbench.propose(propose("v8c", List.of(
                new SourceInput(source(), List.of(section21(Level.L4_HUMAN)), List.of()),
                new SourceInput(source(), List.of(section21(Level.L1)), List.of()))));

        assertThat(change.status()).isEqualTo(PolicyChange.Status.HELD);
        assertThat(change.candidate()).isNull();
        assertThat(change.conflictReason()).contains("disagree");
    }

    @Test
    void activationFlagsImpactedCertificatesAndIsLedgered() {
        certificationService.certify(new CompliantAgent(), "wb-agent-a", "swarmsight-arena", "Head of Service");
        PolicyChange change = workbench.propose(propose("v8a",
                List.of(new SourceInput(source(), List.of(section21(Level.L4_HUMAN)), List.of()))));

        PolicyWorkbench.ActivateOutcome outcome = workbench.activate(
                change.id(), "Policy Owner", Instant.parse("2031-01-01T00:00:00Z"));

        assertThat(outcome.rejectionReason()).isNull();
        assertThat(outcome.result().impactedCertificates()).contains("cert-wb-agent-a");
        assertThat(outcome.result().transitionRule()).contains("remain under v7");
        assertThat(certificateRepository.findLatestByAgent("wb-agent-a").orElseThrow()
                .certificate().status()).isEqualTo("REVIEW_REQUIRED");

        List<String> intents = ledgerRepository.findByCaseRef("HA-09").stream()
                .map(LedgerRow::intent).toList();
        assertThat(intents).contains("policy_changed");
    }

    @Test
    void aDecisionBeforeTheChangeAuditsUnderTheOldVersionForever() {
        PolicyChange change = workbench.propose(propose("v8t",
                List.of(new SourceInput(source(), List.of(section21(Level.L4_HUMAN)), List.of()))));
        Instant effective = Instant.parse("2040-01-01T00:00:00Z");
        workbench.activate(change.id(), "Policy Owner", effective);

        // Now still resolves to v7; only on or after the effective date is it v8t.
        assertThat(policyRepository.resolve("HA-09", Instant.parse("2026-06-29T00:00:00Z")))
                .map(PolicyVersion::version).contains("v7");
        assertThat(policyRepository.resolve("HA-09", effective.plusSeconds(1)))
                .map(PolicyVersion::version).contains("v8t");
    }

    @Test
    void activationBeforeAFutureDateIsRejected() {
        PolicyChange change = workbench.propose(propose("v8p",
                List.of(new SourceInput(source(), List.of(section21(Level.L4_HUMAN)), List.of()))));

        PolicyWorkbench.ActivateOutcome outcome = workbench.activate(
                change.id(), "Policy Owner", Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(outcome.result()).isNull();
        assertThat(outcome.rejectionReason()).contains("future");
    }
}
