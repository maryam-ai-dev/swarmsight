package com.swarmsight.authority.decision;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * A live counter of internal errors in the decide path. An internal error fails
 * closed to a hold and writes no ledger row, so it cannot be counted from the
 * ledger; this counter makes it visible (and alertable) on the oversight metrics.
 * A rising count means decisions are silently holding on bugs, not policy.
 */
@Component
public class DecisionMetrics {

    private final AtomicLong internalErrors = new AtomicLong();

    public void recordInternalError() {
        internalErrors.incrementAndGet();
    }

    public long internalErrors() {
        return internalErrors.get();
    }
}
