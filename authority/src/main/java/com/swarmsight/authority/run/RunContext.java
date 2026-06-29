package com.swarmsight.authority.run;

import java.time.Instant;

/**
 * A RunContext ties a unit of work together. Every LedgerRow carries this run's
 * run_id, so any row can be traced back to the run it belongs to.
 */
public record RunContext(
        String runId,
        String caseRef,
        String workflow,
        Instant createdAt) {
}
