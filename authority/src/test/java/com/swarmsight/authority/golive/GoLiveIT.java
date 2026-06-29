package com.swarmsight.authority.golive;

import static org.assertj.core.api.Assertions.assertThat;

import com.swarmsight.authority.arena.CertificationService;
import com.swarmsight.authority.arena.CompliantAgent;
import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerRow;
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
 * The Sprint 7 guarantees against a live Postgres: promotion is allowed only up
 * to the certified ceiling and only with a recorded sign-off; sources below
 * threshold block citizen-facing promotion; the deployment approval is a ledger
 * event; and a certificate alone is not a deployment.
 */
@SpringBootTest(properties = "swarmsight.demo-seed=false")
@Testcontainers
class GoLiveIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired private CertificationService certificationService;
    @Autowired private GoLiveGate goLiveGate;
    @Autowired private DeploymentService deploymentService;
    @Autowired private DeploymentApprovalRepository approvalRepository;
    @Autowired private SourceReadinessRepository sourceReadinessRepository;
    @Autowired private LedgerRepository ledgerRepository;

    private void certify(String agentId) {
        certificationService.certify(new CompliantAgent(), agentId, "swarmsight-arena", "Head of Service");
    }

    private DeploymentService.Request request(String requestedCeiling, boolean citizenFacing) {
        return new DeploymentService.Request("Head of Housing Service", "Housing appeals, live trial",
                "8 weeks", "review at week 4", List.of("Officer signs every citizen decision"),
                requestedCeiling, "HA-09", citizenFacing);
    }

    @Test
    void aCertifiedAgentPassesTheGateAndCanBeSignedOff() {
        certify("agent-promote");

        GateResult gate = goLiveGate.evaluate("agent-promote", "HA-09", true, "L2");
        assertThat(gate.promotable()).isTrue();
        assertThat(gate.certificate().present()).isTrue();
        assertThat(gate.policyBound()).isTrue();
        assertThat(gate.humanJudgementActive()).isTrue();
        assertThat(gate.connectorsHealthy()).isTrue();
        assertThat(gate.ceilingOk()).isTrue();

        DeploymentService.Outcome outcome = deploymentService.approve("agent-promote", request("L2", true));
        assertThat(outcome.rejectionReason()).isNull();
        assertThat(outcome.approval().grantedCeiling()).isEqualTo("L2");
        assertThat(approvalRepository.findLatestByAgent("agent-promote")).isPresent();
    }

    @Test
    void promotionAboveTheCertifiedCeilingIsBlocked() {
        certify("agent-ceiling");

        GateResult gate = goLiveGate.evaluate("agent-ceiling", "HA-09", true, "L3");
        assertThat(gate.ceilingOk()).isFalse();
        assertThat(gate.promotable()).isFalse();

        DeploymentService.Outcome outcome = deploymentService.approve("agent-ceiling", request("L3", true));
        assertThat(outcome.approval()).isNull();
        assertThat(outcome.rejectionReason()).contains("exceeds the certified ceiling");
        assertThat(approvalRepository.findLatestByAgent("agent-ceiling")).isEmpty();
    }

    @Test
    void sourcesBelowThresholdBlockCitizenFacingPromotion() {
        certify("agent-readiness");
        sourceReadinessRepository.upsert(new SourceReadinessSnapshot(
                "stale-source", "mock-case-system", 50, 85, List.of("stale index"), Instant.now()));
        try {
            GateResult citizenFacing = goLiveGate.evaluate("agent-readiness", "HA-09", true, "L2");
            assertThat(citizenFacing.sourcesReady()).isFalse();
            assertThat(citizenFacing.promotable()).isFalse();
            assertThat(citizenFacing.blockers())
                    .anyMatch(b -> b.contains("below its readiness threshold"));

            // The same agent, not citizen-facing, is not blocked by readiness.
            GateResult internal = goLiveGate.evaluate("agent-readiness", "HA-09", false, "L2");
            assertThat(internal.sourcesReady()).isTrue();
            assertThat(internal.promotable()).isTrue();
        } finally {
            sourceReadinessRepository.delete("stale-source");
        }
    }

    @Test
    void theDeploymentApprovalIsALedgerEvent() {
        certify("agent-ledger");
        deploymentService.approve("agent-ledger", request("L2", true));

        List<String> intents = ledgerRepository.findByCaseRef("agent-ledger").stream()
                .map(LedgerRow::intent).toList();
        assertThat(intents).contains("deployment_approved");
    }

    @Test
    void aCertificateAloneIsNotADeployment() {
        certify("agent-cert-alone");
        // Certified and the gate passes, but until the sign-off there is no
        // deployment approval recorded.
        assertThat(goLiveGate.evaluate("agent-cert-alone", "HA-09", true, "L2").promotable()).isTrue();
        assertThat(approvalRepository.findLatestByAgent("agent-cert-alone")).isEmpty();
    }

    @Test
    void withoutACertificateTheGateBlocks() {
        GateResult gate = goLiveGate.evaluate("agent-uncertified", "HA-09", true, "L2");
        assertThat(gate.certificate().present()).isFalse();
        assertThat(gate.promotable()).isFalse();
        assertThat(gate.blockers()).anyMatch(b -> b.contains("No certificate"));
    }
}
