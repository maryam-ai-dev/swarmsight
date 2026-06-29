package com.swarmsight.authority.decision;

import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerRow;
import com.swarmsight.authority.run.RunContext;
import com.swarmsight.authority.run.RunContextRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The verdict path's HTTP surface. POST /decide is the one write; the GETs are
 * read-only views the frontend renders.
 */
@RestController
public class DecideController {

    private final DecisionService decisionService;
    private final LedgerRepository ledgerRepository;
    private final RunContextRepository runContextRepository;

    public DecideController(
            DecisionService decisionService,
            LedgerRepository ledgerRepository,
            RunContextRepository runContextRepository) {
        this.decisionService = decisionService;
        this.ledgerRepository = ledgerRepository;
        this.runContextRepository = runContextRepository;
    }

    @PostMapping("/decide")
    public Verdict decide(@Valid @RequestBody DecisionRequest request) {
        return decisionService.decide(request);
    }

    /** The latest Verdict for a case, for the read-only Case surface. */
    @GetMapping("/cases/{caseRef}/verdict")
    public ResponseEntity<Verdict> caseVerdict(@PathVariable String caseRef) {
        return ledgerRepository.findLatestByCaseRef(caseRef)
                .map(row -> ResponseEntity.ok(decisionService.verdictFromRow(row)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** A RunContext and its ledger rows, so any row traces to its run. */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<RunView> run(@PathVariable String runId) {
        return runContextRepository.find(runId)
                .map(run -> ResponseEntity.ok(new RunView(run, ledgerRepository.findByRunId(runId))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record RunView(RunContext run, List<LedgerRow> rows) {
    }
}
