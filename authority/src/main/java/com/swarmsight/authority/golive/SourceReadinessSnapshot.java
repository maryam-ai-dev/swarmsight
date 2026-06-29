package com.swarmsight.authority.golive;

import java.time.Instant;
import java.util.List;

/**
 * A point-in-time readiness reading for one source: its score, the threshold it
 * must meet, any flags, and when it was taken. Below threshold, the source is
 * blocked for citizen-facing work. The deep scanner is deferred; the gate reads
 * these snapshots.
 */
public record SourceReadinessSnapshot(
        String sourceId,
        String connector,
        int score,
        int threshold,
        List<String> flags,
        Instant snapshotAt) {

    public boolean ready() {
        return score >= threshold;
    }

    public List<String> blockedFor() {
        return ready() ? List.of() : List.of("citizen-facing");
    }
}
