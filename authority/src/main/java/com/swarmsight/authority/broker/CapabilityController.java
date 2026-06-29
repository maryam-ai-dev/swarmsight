package com.swarmsight.authority.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swarmsight.authority.ledger.LedgerRepository;
import com.swarmsight.authority.ledger.LedgerRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The broker's HTTP surface: ask for a capability (issued only on an allow),
 * fetch through it, and revoke it. Every fetch goes through the broker, which
 * validates first.
 */
@RestController
public class CapabilityController {

    private final CapabilityBroker broker;
    private final LedgerRepository ledgerRepository;
    private final ObjectMapper objectMapper;

    public CapabilityController(
            CapabilityBroker broker, LedgerRepository ledgerRepository, ObjectMapper objectMapper) {
        this.broker = broker;
        this.ledgerRepository = ledgerRepository;
        this.objectMapper = objectMapper;
    }

    public record IssueCapabilityRequest(
            @NotBlank String requestId,
            @NotBlank String runId,
            @NotBlank String actor,
            @NotBlank String workflow,
            @NotBlank String action,
            @NotBlank String connector,
            @NotBlank String resourceScope,
            Map<String, Object> inputs) {
    }

    public record FetchRequest(
            @NotBlank String capabilityId,
            @NotBlank String connector,
            @NotBlank String resourceScope,
            @NotBlank String caseRef,
            @NotBlank String action) {
    }

    public record RevokeRequest(@NotBlank String reason) {
    }

    @PostMapping("/cases/{caseRef}/capabilities")
    public CapabilityBroker.IssueResult issue(
            @PathVariable String caseRef, @Valid @RequestBody IssueCapabilityRequest req) {
        return broker.requestCapability(caseRef, req.requestId(), req.runId(), req.actor(),
                req.workflow(), req.action(), req.connector(), req.resourceScope(), req.inputs());
    }

    @PostMapping("/broker/fetch")
    public ConnectorRecord fetch(@Valid @RequestBody FetchRequest req) {
        return broker.fetch(req.capabilityId(), req.connector(), req.resourceScope(), req.caseRef(), req.action());
    }

    @PostMapping("/capabilities/{id}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable String id, @Valid @RequestBody RevokeRequest req) {
        broker.revoke(id, req.reason());
        return ResponseEntity.ok().build();
    }

    /**
     * The field_effects from the latest source fetch for a case: per field, the
     * source permission, the policy, and the outcome. Read-only, values never
     * included. This is what proves the agent never saw what it was not given.
     */
    @GetMapping("/cases/{caseRef}/field-effects")
    public ResponseEntity<Map<String, Object>> fieldEffects(@PathVariable String caseRef) {
        return ledgerRepository.findByCaseRef(caseRef).stream()
                .filter(r -> r.intent().equals("source_fetch"))
                .reduce((a, b) -> b)
                .map(this::parsePayload)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> parsePayload(LedgerRow row) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(row.payload(), Map.class);
            return payload;
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable source_fetch payload at seq " + row.seq(), e);
        }
    }

    /** A rejected fetch is a 403 carrying the reason code. The broker fails closed. */
    @ExceptionHandler(BrokerException.class)
    public ResponseEntity<Map<String, String>> onRejected(BrokerException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("reason", e.reason().name(), "message", e.getMessage()));
    }
}
