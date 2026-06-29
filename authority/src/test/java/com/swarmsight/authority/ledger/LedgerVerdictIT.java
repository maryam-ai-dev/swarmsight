package com.swarmsight.authority.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swarmsight.authority.decision.DecisionRequest;
import com.swarmsight.authority.decision.DecisionService;
import com.swarmsight.authority.decision.Verdict;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * The Sprint 1 guarantees, proven against a real Postgres: exactly one row per
 * decision, a chain that survives a thousand concurrent writes, a database that
 * refuses to mutate the ledger, and idempotency on request_id.
 *
 * Assertions are global and additive (whole-chain integrity, count deltas) so
 * they hold no matter the order the tests run in, since the ledger cannot be
 * cleaned between them.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "swarmsight.demo-seed=false")
@Testcontainers
class LedgerVerdictIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired private DecisionService decisionService;
    @Autowired private LedgerRepository ledgerRepository;
    @Autowired private Hashing hashing;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestRestTemplate rest;

    private DecisionRequest clearRequest(String requestId, String runId, String caseRef) {
        return new DecisionRequest(requestId, runId, caseRef, "agent-1", "HA-09", "draft_response",
                Map.of("tenancy_status", "secure", "eviction_risk", false, "dependent_children", false));
    }

    /** Recompute the entire chain from stored fields and confirm it is intact. */
    private void assertChainIntact() {
        List<LedgerRow> rows = ledgerRepository.findAllOrderBySeq();
        String expectedPrev = Hashing.GENESIS_PREV_HASH;
        for (int i = 0; i < rows.size(); i++) {
            LedgerRow row = rows.get(i);
            assertThat(row.seq()).as("seq is gapless and 1-based").isEqualTo(i + 1L);
            assertThat(row.prevHash()).as("prev_hash links to the row before").isEqualTo(expectedPrev);

            String recomputedPayloadHash = hashing.sha256Hex(row.payload().getBytes(StandardCharsets.UTF_8));
            assertThat(recomputedPayloadHash).as("payload_hash recomputes").isEqualTo(row.payloadHash());

            String recomputedRowHash = hashing.rowHash(
                    row.prevHash(), row.seq(), row.runId(), row.caseRef(), row.intent(),
                    row.actor(), row.action(), row.payloadHash(), row.policyVersion(), row.ts());
            assertThat(recomputedRowHash).as("row_hash recomputes").isEqualTo(row.rowHash());

            expectedPrev = row.rowHash();
        }
    }

    @Test
    void postingADecisionWritesExactlyOneRow() {
        long before = ledgerRepository.count();
        Verdict v = decisionService.decide(clearRequest("one-row-1", "run-one", "case-one"));

        assertThat(ledgerRepository.count()).isEqualTo(before + 1);
        assertThat(v.effect()).isEqualTo(Effect.ALLOW);
        assertThat(v.seq()).isNotNull();
        assertThat(v.rowHash()).isNotBlank();
        assertChainIntact();
    }

    @Test
    void aThousandConcurrentAppendsKeepTheChainIntact() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Verdict>> tasks = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                String requestId = "conc-" + i;
                tasks.add(() -> decisionService.decide(clearRequest(requestId, "run-conc", "case-conc")));
            }
            List<Future<Verdict>> futures = pool.invokeAll(tasks);
            for (Future<Verdict> f : futures) {
                assertThat(f.get().seq()).isNotNull();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(ledgerRepository.findByRunId("run-conc")).hasSize(1000);
        assertChainIntact();
    }

    @Test
    void ledgerRejectsUpdateAndDelete() {
        Verdict v = decisionService.decide(clearRequest("immutable-1", "run-imm", "case-imm"));
        long seq = v.seq();

        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_rows SET actor = 'tampered' WHERE seq = ?", seq))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_rows WHERE seq = ?", seq))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("append-only");

        // The row is untouched.
        assertThat(ledgerRepository.findByRunId("run-imm")).hasSize(1);
    }

    @Test
    void repeatedRequestIdDoesNotDoubleWrite() {
        long before = ledgerRepository.count();
        Verdict first = decisionService.decide(clearRequest("idem-1", "run-idem", "case-idem"));
        Verdict second = decisionService.decide(clearRequest("idem-1", "run-idem", "case-idem"));

        assertThat(ledgerRepository.count()).isEqualTo(before + 1);
        assertThat(second.seq()).isEqualTo(first.seq());
        assertThat(second.rowHash()).isEqualTo(first.rowHash());
        assertThat(second.effect()).isEqualTo(first.effect());
    }

    @Test
    void decideEndpointReturnsVerdictAndCaseSurfaceRendersIt() {
        DecisionRequest req = clearRequest("http-1", "run-http", "case-http");
        Verdict posted = rest.postForObject("/decide", req, Verdict.class);

        assertThat(posted).isNotNull();
        assertThat(posted.effect()).isEqualTo(Effect.ALLOW);
        assertThat(posted.seq()).isNotNull();

        Verdict rendered = rest.getForObject("/cases/case-http/verdict", Verdict.class);
        assertThat(rendered.effect()).isEqualTo(Effect.ALLOW);
        assertThat(rendered.reviewBrief()).isEqualTo(posted.reviewBrief());
    }
}
