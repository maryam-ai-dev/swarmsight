package com.swarmsight.authority.workbench;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live policy ingestion surface. A service owner points this at a piece of
 * published legislation or policy; Authority fetches it, extracts the guards it
 * implies, and stages a proposed change on the Workbench for review. It never
 * activates: the existing diff / replay / activate gate still applies.
 */
@RestController
public class PolicyIngestionController {

    private final PolicyIngestionService ingestion;
    private final PolicyIngestionPoller poller;

    public PolicyIngestionController(PolicyIngestionService ingestion, PolicyIngestionPoller poller) {
        this.ingestion = ingestion;
        this.poller = poller;
    }

    public record IngestRequest(@NotBlank String url, String policyId) {

        public String safePolicyId() {
            return policyId == null || policyId.isBlank() ? "HA-09" : policyId;
        }
    }

    @PostMapping("/policy-ingestion/fetch")
    public ResponseEntity<?> fetch(@Valid @RequestBody IngestRequest req) {
        try {
            return ResponseEntity.ok(ingestion.ingest(req.url(), req.safePolicyId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(java.util.Map.of(
                    "error", "Could not ingest the source: " + e.getMessage()));
        }
    }

    /** The sources the poller watches (diagnostics for the policy screen). */
    @GetMapping("/policy-ingestion/watched")
    public List<String> watched() {
        return poller.watchedUrls();
    }

    /** Run the poll now instead of waiting for the schedule. Stages a change for any moved source. */
    @PostMapping("/policy-ingestion/poll")
    public List<PolicyIngestionPoller.PollResult> poll() {
        return poller.pollOnce();
    }
}
