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
    private final PolicyStandingService policyStandingService;
    private final LedgerRepository ledgerRepository;

    public OversightController(OversightService oversightService,
            PolicyStandingService policyStandingService, LedgerRepository ledgerRepository) {
        this.oversightService = oversightService;
        this.policyStandingService = policyStandingService;
        this.ledgerRepository = ledgerRepository;
    }

    @GetMapping("/oversight/metrics")
    public Map<String, Object> metrics() {
        return oversightService.metrics();
    }

    /** Which agents are operating against an out-of-date policy, and why. */
    @GetMapping("/oversight/policy-standing")
    public List<PolicyStandingService.AgentPolicyStanding> policyStanding() {
        return policyStandingService.standings();
    }

    @GetMapping("/agents/{agentId}/log")
    public List<LedgerRow> log(@PathVariable String agentId) {
        return ledgerRepository.findByAgent(agentId);
    }
}
