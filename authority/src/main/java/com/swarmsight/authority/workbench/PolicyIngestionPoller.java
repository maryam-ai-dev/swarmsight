package com.swarmsight.authority.workbench;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Watches a configured set of policy sources and, on a schedule, ingests any that
 * have changed since the last poll. It stages a proposed change exactly as the
 * manual trigger does -- it never activates -- so the only thing automation adds
 * is noticing a source moved. Off by default; enable it and list sources to watch.
 *
 * <p>Each watch entry is {@code url|policyId} (the policy id defaults to HA-09 if
 * omitted), comma-separated. One entry per council source is how per-borough
 * ingestion is driven: each council's allocation scheme into its own policy id.
 */
@Component
public class PolicyIngestionPoller {

    private static final Logger log = LoggerFactory.getLogger(PolicyIngestionPoller.class);

    private final PolicyIngestionService ingestion;
    private final boolean enabled;
    private final List<Watch> watches;

    private record Watch(String url, String policyId) {
    }

    public PolicyIngestionPoller(
            PolicyIngestionService ingestion,
            @Value("${swarmsight.ingestion.poll.enabled:false}") boolean enabled,
            @Value("${swarmsight.ingestion.watch:}") String watch) {
        this.ingestion = ingestion;
        this.enabled = enabled;
        this.watches = parse(watch);
        if (enabled) {
            log.info("Policy ingestion poller enabled, watching {} source(s).", watches.size());
        }
    }

    private static List<Watch> parse(String watch) {
        List<Watch> out = new ArrayList<>();
        for (String entry : watch.split(",")) {
            String e = entry.trim();
            if (e.isBlank()) {
                continue;
            }
            int bar = e.indexOf('|');
            String url = bar < 0 ? e : e.substring(0, bar).trim();
            String policyId = bar < 0 ? "HA-09" : e.substring(bar + 1).trim();
            out.add(new Watch(url, policyId.isBlank() ? "HA-09" : policyId));
        }
        return out;
    }

    /** The list of watched sources, for the manual "poll now" endpoint and diagnostics. */
    public List<String> watchedUrls() {
        return watches.stream().map(Watch::url).toList();
    }

    /**
     * Poll every watched source once and stage a change for any that moved. Used by
     * the scheduler and by the manual "poll now" endpoint. Resilient: a failing
     * source is logged and skipped, never aborting the rest.
     */
    public List<PollResult> pollOnce() {
        List<PollResult> results = new ArrayList<>();
        for (Watch w : watches) {
            try {
                PolicyIngestionService.PollOutcome o = ingestion.ingestIfChanged(w.url(), w.policyId());
                results.add(new PollResult(
                        w.url(), w.policyId(), o.changed(), false,
                        o.result() == null ? null : o.result().proposedVersion(), null));
                if (o.changed()) {
                    log.info("Poll: {} changed -> staged {}", w.url(), o.result().proposedVersion());
                }
            } catch (Exception ex) {
                log.warn("Poll of {} failed: {}", w.url(), ex.toString());
                results.add(new PollResult(w.url(), w.policyId(), false, true, null, ex.getMessage()));
            }
        }
        return results;
    }

    /** The outcome of polling one watched source. */
    public record PollResult(
            String url, String policyId, boolean changed, boolean failed,
            String stagedVersion, String error) {
    }

    @Scheduled(
            fixedRateString = "${swarmsight.ingestion.poll.interval-ms:3600000}",
            initialDelayString = "${swarmsight.ingestion.poll.initial-delay-ms:60000}")
    public void scheduledPoll() {
        if (!enabled || watches.isEmpty()) {
            return;
        }
        log.info("Polling {} watched policy source(s) for changes…", watches.size());
        pollOnce();
    }
}
