package com.swarmsight.authority.arena;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The live drafting surface: have a registered agent draft for a case from the
 * masked documents the broker fetches. Any signed-in user may trigger it; the
 * agent only proposes, and the broker still gates and masks.
 */
@RestController
public class CaseDraftController {

    private final CaseDraftService caseDraftService;

    public CaseDraftController(CaseDraftService caseDraftService) {
        this.caseDraftService = caseDraftService;
    }

    @PostMapping("/cases/{caseRef}/agent/draft")
    public CaseDraftService.CaseDraft draft(
            @PathVariable String caseRef,
            @RequestParam(defaultValue = "housing-appeals-agent-v3") String agentId) {
        return caseDraftService.draftForCase(caseRef, agentId);
    }

    @ExceptionHandler(AgentService.RegistrationException.class)
    public ResponseEntity<Map<String, String>> onMissingAgent(AgentService.RegistrationException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
