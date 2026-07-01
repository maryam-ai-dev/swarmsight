package com.swarmsight.authority.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swarmsight.authority.auth.TestAuth;
import com.swarmsight.authority.decision.CertificateCheck;
import com.swarmsight.authority.decision.DecisionRequest;
import com.swarmsight.authority.decision.DecisionService;
import com.swarmsight.authority.decision.EngineResult;
import com.swarmsight.authority.decision.ReasonCode;
import com.swarmsight.authority.decision.VerdictEngine;
import com.swarmsight.authority.ledger.Effect;
import com.swarmsight.authority.decision.Verdict;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The Sprint 2 guarantees, against a real Postgres with the seeded HA-09 v6 and
 * v7: temporal resolution, a decision judged under the earlier version before a
 * change, the eviction guard holding under v7, policy immutability, and the
 * versions endpoint.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"swarmsight.demo-seed=false",
                "swarmsight.auth.admin-password=test-admin-pass"})
@Testcontainers
class PolicyVersioningIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired private PolicyRepository policyRepository;
    @Autowired private VerdictEngine verdictEngine;
    @Autowired private DecisionService decisionService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestRestTemplate rest;

    @BeforeEach
    void authenticate() {
        TestAuth.authenticateAsAdmin(rest);
    }

    // Policy versioning is about policy effect, not certificates: policy-only path.
    private final CertificateCheck validCert = CertificateCheck.exempt();

    private Map<String, Object> evictionInputs(boolean evidencePresent) {
        return Map.of(
                "tenancy_status", "secure",
                "eviction_risk", true,
                "dependent_children", true,
                "eviction_notice", evidencePresent);
    }

    @Test
    void resolverReturnsTheVersionInForceAtATimestamp() {
        assertThat(policyRepository.resolve("HA-09", Instant.parse("2024-06-01T00:00:00Z")))
                .map(PolicyVersion::version).contains("v6");
        assertThat(policyRepository.resolve("HA-09", Instant.parse("2025-06-01T00:00:00Z")))
                .map(PolicyVersion::version).contains("v7");
        // Before any version takes effect, nothing resolves.
        assertThat(policyRepository.resolve("HA-09", Instant.parse("2023-01-01T00:00:00Z"))).isEmpty();
    }

    @Test
    void aDecisionBeforeV7IsJudgedUnderV6() {
        DecisionRequest req = new DecisionRequest("t-1", "run", "case", "agent-1", "HA-09",
                "draft_response", evictionInputs(true));

        EngineResult underV6 = verdictEngine.evaluate(
                req, policyRepository.resolve("HA-09", Instant.parse("2024-06-01T00:00:00Z")), validCert);
        EngineResult underV7 = verdictEngine.evaluate(
                req, policyRepository.resolve("HA-09", Instant.parse("2025-06-01T00:00:00Z")), validCert);

        // v6 had no eviction-with-dependents guard, so the same case clears.
        assertThat(underV6.verdict().effect()).isEqualTo(Effect.ALLOW);
        // v7 added the guard, so it holds.
        assertThat(underV7.verdict().effect()).isEqualTo(Effect.HOLD);
        assertThat(underV7.verdict().reasonCode()).isEqualTo(ReasonCode.EVICTION_RISK_DEPENDENTS);
    }

    @Test
    void evictionCaseHoldsUnderV7ThroughTheDecidePath() {
        DecisionRequest req = new DecisionRequest("hold-v7-1", "run-hold", "CASE-HOLD", "agent-1", "HA-09",
                "draft_response", evictionInputs(false));

        Verdict v = decisionService.decide(req);

        assertThat(v.effect()).isEqualTo(Effect.HOLD);
        assertThat(v.reasonCode()).isEqualTo(ReasonCode.EVICTION_RISK_DEPENDENTS);
        assertThat(v.policyVersion()).isEqualTo("v7");
        assertThat(v.seq()).isNotNull();
    }

    @Test
    void policyVersionsAreNeverUpdatedOrDeleted() {
        assertThatThrownBy(() -> jdbc.update("UPDATE policies SET version = 'v8' WHERE version = 'v7'"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("never mutated");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM policies WHERE version = 'v6'"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("never mutated");
    }

    @Test
    void versionsEndpointListsRealVersionsWithEffectiveDates() {
        String body = rest.getForObject("/policies/HA-09/versions", String.class);
        assertThat(body).contains("\"version\":\"v7\"").contains("\"version\":\"v6\"");
        assertThat(body).contains("2025-04-01").contains("2024-01-01");
        assertThat(body).contains("eviction-risk-with-dependents");
    }
}
