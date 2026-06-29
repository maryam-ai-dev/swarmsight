package com.swarmsight.authority.arena;

import static org.assertj.core.api.Assertions.assertThat;

import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The Sprint 6 guarantees against a live Postgres: a compliant agent earns a
 * certificate and an assurance case with claims linked to scenario evidence; an
 * agent that sends an adverse decision fails certification; the certificate
 * lists certified and not-certified actions; and the builder cannot be the
 * approver.
 */
@SpringBootTest(properties = "swarmsight.demo-seed=false")
@Testcontainers
class CertificationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired private CertificationService certificationService;
    @Autowired private LedgerRepository ledgerRepository;

    @Test
    void aCompliantAgentEarnsACertificateAndAssuranceCase() {
        CertificationService.Outcome outcome = certificationService.certify(
                new CompliantAgent(), "agent-pass", "swarmsight-arena", "Head of Service");

        assertThat(outcome.rejectionReason()).isNull();
        assertThat(outcome.arenaResult().overallPass()).isTrue();
        assertThat(outcome.certificate()).isNotNull();
        assertThat(outcome.certificate().ceiling()).isEqualTo("L2");
        assertThat(outcome.certificate().assuranceCaseRef()).isEqualTo(outcome.assuranceCase().id());

        // Claims are linked to scenario evidence.
        assertThat(outcome.assuranceCase().claims()).isNotEmpty();
        assertThat(outcome.assuranceCase().claims())
                .allSatisfy(c -> assertThat(c.evidence()).isNotEmpty());
        assertThat(outcome.assuranceCase().claims().get(0).evidence().get(0).verdictSeq()).isNotNull();
    }

    @Test
    void theCertificateListsCertifiedAndNotCertifiedActions() {
        CertificationService.Outcome outcome = certificationService.certify(
                new CompliantAgent(), "agent-actions", "swarmsight-arena", "Head of Service");

        assertThat(outcome.certificate().certifiedActions())
                .contains("draft_response", "request_evidence", "escalate");
        assertThat(outcome.certificate().notCertifiedActions()).contains("send_decision");
    }

    @Test
    void issuanceIsALedgerEvent() {
        certificationService.certify(new CompliantAgent(), "agent-ledger", "swarmsight-arena", "Head of Service");

        List<String> intents = ledgerRepository.findByCaseRef("agent-ledger").stream()
                .map(LedgerRow::intent).toList();
        assertThat(intents).contains("certificate_issued");
    }

    @Test
    void anAgentThatSendsAnAdverseDecisionFailsCertification() {
        CertificationService.Outcome outcome = certificationService.certify(
                new AdverseAgent(), "agent-adverse", "swarmsight-arena", "Head of Service");

        assertThat(outcome.certificate()).isNull();
        assertThat(outcome.arenaResult().safetyPass()).isFalse();
        assertThat(outcome.rejectionReason()).contains("safety gate");
    }

    @Test
    void theBuilderCannotBeTheApprover() {
        CertificationService.Outcome outcome = certificationService.certify(
                new CompliantAgent(), "agent-sep", "same-person", "same-person");

        assertThat(outcome.certificate()).isNull();
        assertThat(outcome.rejectionReason()).contains("builder cannot be the approver");
    }
}
