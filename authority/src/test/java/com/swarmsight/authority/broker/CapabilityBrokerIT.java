package com.swarmsight.authority.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.swarmsight.authority.broker.CapabilityBroker.IssueResult;
import com.swarmsight.authority.ledger.Effect;
import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerRow;
import java.time.Instant;
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
 * The Sprint 4 broker guarantees against a live Postgres: a capability is minted
 * only on an allow, a fetch succeeds with a valid one and is rejected for every
 * failure mode, and issuance and revocation are ledger events.
 */
@SpringBootTest(properties = "swarmsight.demo-seed=false")
@Testcontainers
class CapabilityBrokerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired private CapabilityBroker broker;
    @Autowired private CapabilityRepository capabilityRepository;
    @Autowired private LedgerRepository ledgerRepository;

    private static final String CONNECTOR = "mock-case-system";

    private Map<String, Object> clearInputs() {
        return Map.of("tenancy_status", "secure", "eviction_risk", false, "dependent_children", false);
    }

    private Map<String, Object> evictionInputs() {
        return Map.of("tenancy_status", "at_risk", "eviction_risk", true, "dependent_children", true);
    }

    private IssueResult requestFor(String suffix, String caseRef, Map<String, Object> inputs, String scope) {
        return broker.requestCapability(caseRef, "cap-req-" + suffix, "run-" + suffix, "agent-1",
                "HA-09", "draft_response", CONNECTOR, scope, inputs);
    }

    @Test
    void allowMintsACapabilityAndFetchSucceeds() {
        IssueResult result = requestFor("ok", "CAP-OK", clearInputs(), "tenancy_record");

        assertThat(result.verdict().effect()).isEqualTo(Effect.ALLOW);
        assertThat(result.capability()).isNotNull();
        assertThat(result.capability().issuedByVerdict()).isEqualTo(result.verdict().rowHash());

        ConnectorRecord record = broker.fetch(result.capability().id(), CONNECTOR, "tenancy_record",
                "CAP-OK", "draft_response");
        assertThat(record.fields()).containsEntry("tenancy_status", "confirmed");
    }

    @Test
    void holdMintsNoCapability() {
        IssueResult result = requestFor("hold", "CAP-HOLD", evictionInputs(), "tenancy_record");
        assertThat(result.verdict().effect()).isEqualTo(Effect.HOLD);
        assertThat(result.capability()).isNull();
    }

    @Test
    void fetchWithoutACapabilityIsRejected() {
        BrokerException ex = catchThrowableOfType(
                () -> broker.fetch("cap-nope", CONNECTOR, "tenancy_record", "CAP-NONE", "draft_response"),
                BrokerException.class);
        assertThat(ex.reason()).isEqualTo(BrokerException.Reason.NO_CAPABILITY);
    }

    @Test
    void fetchExceedingTheVerdictIsRejected() {
        IssueResult result = requestFor("exceed", "CAP-EXCEED", clearInputs(), "tenancy_record");
        // Same capability, but asking for a scope it was not issued for.
        BrokerException ex = catchThrowableOfType(
                () -> broker.fetch(result.capability().id(), CONNECTOR, "medical_notes",
                        "CAP-EXCEED", "draft_response"),
                BrokerException.class);
        assertThat(ex.reason()).isEqualTo(BrokerException.Reason.EXCEEDS_VERDICT);
    }

    @Test
    void fetchWithAnExpiredCapabilityIsRejected() {
        Instant past = Instant.now().minusSeconds(120);
        capabilityRepository.insert(new Capability(
                "cap-expired", "run-exp", "CAP-EXP", "draft_response", CONNECTOR, "tenancy_record",
                "verdict-x", past.minusSeconds(60), past, true, null, null));

        BrokerException ex = catchThrowableOfType(
                () -> broker.fetch("cap-expired", CONNECTOR, "tenancy_record", "CAP-EXP", "draft_response"),
                BrokerException.class);
        assertThat(ex.reason()).isEqualTo(BrokerException.Reason.EXPIRED);
    }

    @Test
    void revokedCapabilityIsRejectedAndBothEventsAreLedgered() {
        IssueResult result = requestFor("rev", "CAP-REV", clearInputs(), "tenancy_record");
        String capId = result.capability().id();

        broker.revoke(capId, "agent suspended");

        BrokerException ex = catchThrowableOfType(
                () -> broker.fetch(capId, CONNECTOR, "tenancy_record", "CAP-REV", "draft_response"),
                BrokerException.class);
        assertThat(ex.reason()).isEqualTo(BrokerException.Reason.REVOKED);
        assertThat(capabilityRepository.findById(capId).orElseThrow().isRevoked()).isTrue();

        List<String> intents = ledgerRepository.findByCaseRef("CAP-REV").stream()
                .map(LedgerRow::intent).toList();
        assertThat(intents).contains("capability_issued", "capability_revoked");
    }
}
