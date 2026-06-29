package com.swarmsight.authority.incident;

import com.swarmsight.authority.ledger.LedgerRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incident response surface: raise an incident (running containment), read its
 * audit pack, and restrict an action class. These drive the suspend and
 * restrict-action controls on the oversight screen.
 */
@RestController
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentRepository incidentRepository;
    private final LedgerRepository ledgerRepository;

    public IncidentController(
            IncidentService incidentService,
            IncidentRepository incidentRepository,
            LedgerRepository ledgerRepository) {
        this.incidentService = incidentService;
        this.incidentRepository = incidentRepository;
        this.ledgerRepository = ledgerRepository;
    }

    public record RaiseRequest(
            @NotBlank String agentId,
            @NotNull Trigger trigger,
            String detail,
            @NotBlank String reportedBy) {
    }

    public record RestrictRequest(
            @NotBlank String action,
            @NotBlank String reason,
            @NotBlank String reportedBy) {
    }

    @PostMapping("/incidents")
    public Incident raise(@Valid @RequestBody RaiseRequest req) {
        return incidentService.raise(req.agentId(), req.trigger(), req.detail(), req.reportedBy());
    }

    @GetMapping("/incidents")
    public List<Incident> list(@RequestParam(required = false) String agentId) {
        return agentId == null ? incidentRepository.findAll() : incidentRepository.findByAgent(agentId);
    }

    @GetMapping("/incidents/{id}")
    public ResponseEntity<IncidentAuditPack> auditPack(@PathVariable String id) {
        return incidentRepository.findById(id)
                .map(incident -> ResponseEntity.ok(new IncidentAuditPack(
                        incident, ledgerRepository.findByRunIdDesc("incident:" + id))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/agents/{agentId}/restrict")
    public IncidentService.Restriction restrict(
            @PathVariable String agentId, @Valid @RequestBody RestrictRequest req) {
        return incidentService.restrict(agentId, req.action(), req.reason(), req.reportedBy());
    }
}
