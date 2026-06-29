package com.swarmsight.authority.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.swarmsight.authority.arena.CertificateRepository;
import com.swarmsight.authority.arena.CertificationService;
import com.swarmsight.authority.arena.CompliantAgent;
import com.swarmsight.authority.broker.CapabilityBroker;
import com.swarmsight.authority.broker.CapabilityBroker.IssueResult;
import com.swarmsight.authority.incident.IncidentService;
import com.swarmsight.authority.incident.Trigger;
import com.swarmsight.authority.ledger.Effect;
import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerRow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Closes the suspended-certificate issuance gap: the verdict path reads the real
 * certificate status, so a suspended, expired, or revoked certificate is refused
 * at issuance, not only at the broker and the gate. An active certificate still
 * mints as before.
 */
@SpringBootTest(properties = "swarmsight.demo-seed=false")
@Testcontainers
class CertificateEnforcementIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired private CertificationService certificationService;
    @Autowired private CapabilityBroker broker;
    @Autowired private IncidentService incidentService;
    @Autowired private CertificateRepository certificateRepository;
    @Autowired private LedgerRepository ledgerRepository;
    @Autowired private JdbcTemplate jdbc;

    private static final String CONNECTOR = "mock-case-system";

    private void certify(String agentId) {
        certificationService.certify(new CompliantAgent(), agentId, "swarmsight-arena", "Head of Service");
    }

    private IssueResult request(String agentId, String caseRef, String requestId) {
        return broker.requestCapability(caseRef, requestId, "run-" + requestId, agentId,
                "HA-09", "draft_response", CONNECTOR, "tenancy_record",
                Map.of("tenancy_status", "secure", "eviction_risk", false, "dependent_children", false));
    }

    @Test
    void anActiveCertificateMintsACapabilityAsBefore() {
        certify("ce-active");
        IssueResult issue = request("ce-active", "CE-ACTIVE", "ce-active-1");
        assertThat(issue.verdict().effect()).isEqualTo(Effect.ALLOW);
        assertThat(issue.capability()).isNotNull();
    }

    @Test
    void aSuspendedCertificateBlocksFreshIssuance() {
        certify("ce-susp");
        assertThat(request("ce-susp", "CE-SUSP", "ce-susp-1").capability()).isNotNull();

        certificateRepository.markSuspended("cert-ce-susp");

        IssueResult after = request("ce-susp", "CE-SUSP", "ce-susp-2");
        assertThat(after.verdict().effect()).isEqualTo(Effect.BLOCK);
        assertThat(after.verdict().reasonCode()).isEqualTo(ReasonCode.CERTIFICATE_NOT_ACTIVE);
        assertThat(after.capability()).isNull();
    }

    @Test
    void anExpiredCertificateBlocksFreshIssuance() {
        certify("ce-exp");
        jdbc.update("UPDATE certificates SET expires_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(Instant.now().minusSeconds(3600), ZoneOffset.UTC), "cert-ce-exp");

        IssueResult after = request("ce-exp", "CE-EXP", "ce-exp-1");
        assertThat(after.verdict().effect()).isEqualTo(Effect.BLOCK);
        assertThat(after.capability()).isNull();
    }

    @Test
    void aRevokedCertificateBlocksFreshIssuance() {
        certify("ce-rev");
        jdbc.update("UPDATE certificates SET status = 'REVOKED' WHERE id = ?", "cert-ce-rev");

        IssueResult after = request("ce-rev", "CE-REV", "ce-rev-1");
        assertThat(after.verdict().effect()).isEqualTo(Effect.BLOCK);
        assertThat(after.capability()).isNull();
    }

    @Test
    void anIncidentClosesTheLoopRefusingFreshIssuanceNotOnlyExistingCapabilities() {
        certify("ce-e2e");
        assertThat(request("ce-e2e", "CE-E2E", "ce-e2e-1").capability()).isNotNull();

        incidentService.raise("ce-e2e", Trigger.GUARD_BREACH, "Adverse send attempted", "officer");

        IssueResult after = request("ce-e2e", "CE-E2E", "ce-e2e-2");
        assertThat(after.verdict().effect()).isEqualTo(Effect.BLOCK);
        assertThat(after.verdict().reasonCode()).isEqualTo(ReasonCode.CERTIFICATE_NOT_ACTIVE);
        assertThat(after.capability()).isNull();
    }

    @Test
    void theDecisionRecordsTheCertificateStatusThatApplied() {
        certify("ce-ledger");
        request("ce-ledger", "CE-LEDGER", "ce-ledger-1");

        LedgerRow decision = ledgerRepository.findByCaseRef("CE-LEDGER").stream()
                .filter(r -> r.intent().equals("decision")).findFirst().orElseThrow();
        assertThat(decision.payload()).contains("\"certificate_status\":\"ACTIVE\"");
    }
}
