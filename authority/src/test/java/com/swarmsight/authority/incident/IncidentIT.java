package com.swarmsight.authority.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.swarmsight.authority.arena.CertificateRepository;
import com.swarmsight.authority.arena.CertificationService;
import com.swarmsight.authority.arena.CompliantAgent;
import com.swarmsight.authority.broker.BrokerException;
import com.swarmsight.authority.broker.CapabilityBroker;
import com.swarmsight.authority.broker.CapabilityBroker.IssueResult;
import com.swarmsight.authority.golive.GoLiveGate;
import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerRow;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The Sprint 9 guarantees against a live Postgres: raising an incident suspends
 * the certificate and the broker stops honouring the agent's capabilities at
 * once; in-flight cases are held; and the agent cannot return to live without
 * re-certification.
 */
@SpringBootTest(properties = "swarmsight.demo-seed=false")
@Testcontainers
class IncidentIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired private CertificationService certificationService;
    @Autowired private CapabilityBroker broker;
    @Autowired private IncidentService incidentService;
    @Autowired private CertificateRepository certificateRepository;
    @Autowired private GoLiveGate goLiveGate;
    @Autowired private LedgerRepository ledgerRepository;

    private static final String CONNECTOR = "mock-case-system";

    private void certify(String agentId) {
        certificationService.certify(new CompliantAgent(), agentId, "swarmsight-arena", "Head of Service");
    }

    private IssueResult capabilityFor(String agentId, String caseRef) {
        return broker.requestCapability(caseRef, "cap-" + agentId, "run-" + agentId, agentId,
                "HA-09", "draft_response", CONNECTOR, "tenancy_record",
                Map.of("tenancy_status", "secure", "eviction_risk", false, "dependent_children", false));
    }

    @Test
    void incidentSuspendsCertificateAndBrokerStopsHonouringCapabilities() {
        certify("inc-a");
        IssueResult issue = capabilityFor("inc-a", "CASE-INC-A");
        String capId = issue.capability().id();
        // The capability works before the incident.
        assertThat(broker.fetch(capId, CONNECTOR, "tenancy_record", "CASE-INC-A", "draft_response")).isNotNull();

        Incident incident = incidentService.raise("inc-a", Trigger.GUARD_BREACH, "A guard was breached", "J. Okafor");

        assertThat(certificateRepository.findLatestByAgent("inc-a").orElseThrow()
                .certificate().status()).isEqualTo("SUSPENDED");
        assertThat(incident.containment().revokedCapabilities()).contains(capId);

        BrokerException ex = catchThrowableOfType(
                () -> broker.fetch(capId, CONNECTOR, "tenancy_record", "CASE-INC-A", "draft_response"),
                BrokerException.class);
        assertThat(ex.reason()).isEqualTo(BrokerException.Reason.REVOKED);
    }

    @Test
    void inFlightCasesAreHeldNotReDecided() {
        certify("inc-b");
        capabilityFor("inc-b", "CASE-INC-B");

        incidentService.raise("inc-b", Trigger.HUMAN_REPORT, "Officer reported a bad draft", "officer");

        List<String> intents = ledgerRepository.findByCaseRef("CASE-INC-B").stream()
                .map(LedgerRow::intent).toList();
        assertThat(intents).contains("case_held");
        // The case was held, not decided again: no new decision row was written.
        assertThat(intents.stream().filter(i -> i.equals("decision")).count()).isEqualTo(1);
    }

    @Test
    void theAgentCannotReturnToLiveWithoutReCertification() {
        certify("inc-c");
        assertThat(goLiveGate.evaluate("inc-c", "HA-09", true, "L2").promotable()).isTrue();

        incidentService.raise("inc-c", Trigger.CONFIDENCE_COLLAPSE, "Confidence collapsed", "officer");

        var blocked = goLiveGate.evaluate("inc-c", "HA-09", true, "L2");
        assertThat(blocked.promotable()).isFalse();
        assertThat(blocked.blockers()).anyMatch(b -> b.contains("not active"));

        // Re-certification reactivates the certificate and clears the block.
        certify("inc-c");
        assertThat(goLiveGate.evaluate("inc-c", "HA-09", true, "L2").promotable()).isTrue();
    }

    @Test
    void containmentActionsAreEachLedgered() {
        certify("inc-d");
        Incident incident = incidentService.raise("inc-d", Trigger.SOURCE_STALE, "Source went stale", "officer");

        List<String> intents = ledgerRepository.findByRunIdDesc("incident:" + incident.id()).stream()
                .map(LedgerRow::intent).toList();
        assertThat(intents).contains(
                "certificate_suspended", "action_class_disabled", "service_owner_notified", "incident_raised");
    }

    @Test
    void restrictingAnActionIsALedgerEvent() {
        incidentService.restrict("inc-e", "send_decision", "Manual restriction", "officer");

        List<String> intents = ledgerRepository.findByCaseRef("inc-e").stream()
                .map(LedgerRow::intent).toList();
        assertThat(intents).contains("action_class_disabled");
    }
}
