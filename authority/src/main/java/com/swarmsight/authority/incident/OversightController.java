package com.swarmsight.authority.incident;

import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerRow;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only oversight: live metrics for the head-of-department screen and the
 * run-filtered ledger rows for the per-agent log.
 */
@RestController
public class OversightController {

    private final OversightService oversightService;
    private final LedgerRepository ledgerRepository;

    public OversightController(OversightService oversightService, LedgerRepository ledgerRepository) {
        this.oversightService = oversightService;
        this.ledgerRepository = ledgerRepository;
    }

    @GetMapping("/oversight/metrics")
    public Map<String, Object> metrics() {
        return oversightService.metrics();
    }

    @GetMapping("/agents/{agentId}/log")
    public List<LedgerRow> log(@PathVariable String agentId) {
        return ledgerRepository.findByAgent(agentId);
    }
}
