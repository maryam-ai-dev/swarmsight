package com.swarmsight.authority.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swarmsight.authority.ledger.Effect;
import com.swarmsight.authority.ledger.LedgerRow;
import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerService;
import com.swarmsight.authority.policy.Level;
import com.swarmsight.authority.policy.PolicyRepository;
import com.swarmsight.authority.run.RunContextRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the orchestration that do not need a database: the
 * internal-error fail-closed branch and idempotent replay.
 */
@ExtendWith(MockitoExtension.class)
class DecisionServiceTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private VerdictEngine verdictEngine;
    @Mock private CertificateService certificateService;
    @Mock private LedgerService ledgerService;
    @Mock private LedgerRepository ledgerRepository;
    @Mock private RunContextRepository runContextRepository;

    private DecisionService service() {
        return new DecisionService(policyRepository, verdictEngine, certificateService, ledgerService,
                ledgerRepository, runContextRepository, new ObjectMapper());
    }

    private DecisionRequest request() {
        return new DecisionRequest("req-1", "run-1", "case-1", "agent-1", "HA-09", "draft_response", Map.of());
    }

    @Test
    void internalErrorHoldsAndWritesNothing() {
        when(ledgerRepository.findByRequestId("req-1")).thenReturn(Optional.empty());
        when(certificateService.check(eq("agent-1"), any())).thenReturn(CertificateCheck.exempt());
        when(policyRepository.resolve(eq("HA-09"), any())).thenReturn(Optional.empty());
        when(verdictEngine.evaluate(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        Verdict v = service().decide(request());

        assertThat(v.effect()).isEqualTo(Effect.HOLD);
        assertThat(v.reasonCode()).isEqualTo(ReasonCode.INTERNAL_ERROR);
        assertThat(v.seq()).isNull();
        verify(ledgerService, never()).append(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString(), any());
    }

    @Test
    void repeatedRequestReplaysStoredVerdictWithoutDeciding() {
        LedgerRow stored = new LedgerRow(
                7L, "run-1", "case-1", "decision", "agent-1", "draft_response",
                "{\"effect\":\"ALLOW\",\"reason_code\":\"CLEAR\",\"review_brief\":\"ok\",\"inputs\":{}}",
                "ph", "v7", Instant.parse("2026-06-29T12:00:00.000Z"), "req-1", "prev", "rowhash");
        when(ledgerRepository.findByRequestId("req-1")).thenReturn(Optional.of(stored));

        Verdict v = service().decide(request());

        assertThat(v.effect()).isEqualTo(Effect.ALLOW);
        assertThat(v.seq()).isEqualTo(7L);
        assertThat(v.rowHash()).isEqualTo("rowhash");
        verify(verdictEngine, never()).evaluate(any(), any(), any());
        verify(ledgerService, never()).append(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString(), any());
    }
}
