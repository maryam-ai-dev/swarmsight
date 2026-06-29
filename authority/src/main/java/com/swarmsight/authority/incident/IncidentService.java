package com.swarmsight.authority.incident;

import com.swarmsight.authority.arena.CertificateRepository;
import com.swarmsight.authority.broker.Capability;
import com.swarmsight.authority.broker.CapabilityBroker;
import com.swarmsight.authority.broker.CapabilityRepository;
import com.swarmsight.authority.ledger.LedgerService;
import com.swarmsight.authority.run.RunContextRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Raises incidents and runs containment. Containment is automatic and fails
 * closed: it suspends the certificate, revokes the agent's live capabilities
 * through the broker, holds the in-flight cases, and disables the action class,
 * each a ledger event, then notifies the service owner. The agent cannot return
 * to live without re-certification.
 */
@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final CertificateRepository certificateRepository;
    private final CapabilityRepository capabilityRepository;
    private final CapabilityBroker broker;
    private final LedgerService ledgerService;
    private final RunContextRepository runContextRepository;

    public IncidentService(
            IncidentRepository incidentRepository,
            CertificateRepository certificateRepository,
            CapabilityRepository capabilityRepository,
            CapabilityBroker broker,
            LedgerService ledgerService,
            RunContextRepository runContextRepository) {
        this.incidentRepository = incidentRepository;
        this.certificateRepository = certificateRepository;
        this.capabilityRepository = capabilityRepository;
        this.broker = broker;
        this.ledgerService = ledgerService;
        this.runContextRepository = runContextRepository;
    }

    public record Restriction(String agentId, String action, String reason, Instant at) {
    }

    public Incident raise(String agentId, Trigger trigger, String detail, String reportedBy) {
        Instant now = Instant.now();
        String incidentId = "incident-" + UUID.randomUUID();
        String runId = "incident:" + incidentId;
        runContextRepository.ensureExists(runId, agentId, "incident", now);

        // Suspend the certificate and disable the action class.
        String suspendedCertificate = null;
        List<String> disabledActions = List.of();
        var stored = certificateRepository.findLatestByAgent(agentId);
        if (stored.isPresent()) {
            var cert = stored.get().certificate();
            certificateRepository.markSuspended(cert.id());
            suspendedCertificate = cert.id();
            disabledActions = cert.certifiedActions();
            ledger("certificate_suspended", reportedBy, runId, agentId, now,
                    map("certificate_id", cert.id(), "agent_id", agentId, "trigger", trigger.name(),
                            "detail", detail),
                    incidentId + ":suspend");
            ledger("action_class_disabled", reportedBy, runId, agentId, now,
                    map("agent_id", agentId, "actions", disabledActions), incidentId + ":disable");
        }

        // Set the agent-level containment flag first, so any capability that
        // escapes the revocation snapshot below is still refused at fetch.
        broker.suspendAgent(agentId, "incident containment: " + trigger);

        // Revoke the agent's live capabilities through the broker.
        List<String> revokedCapabilities = new ArrayList<>();
        Set<String> heldCases = new LinkedHashSet<>();
        for (Capability cap : capabilityRepository.findActiveByActor(agentId)) {
            broker.revoke(cap.id(), "incident containment: " + trigger);
            revokedCapabilities.add(cap.id());
            heldCases.add(cap.caseRef());
        }

        // Hold the in-flight cases, never silently re-deciding them.
        for (String caseRef : heldCases) {
            ledger("case_held", reportedBy, runId, caseRef, now,
                    map("agent_id", agentId, "case_ref", caseRef, "reason", "incident containment"),
                    incidentId + ":hold:" + caseRef);
        }

        ledger("service_owner_notified", reportedBy, runId, agentId, now,
                map("agent_id", agentId, "incident_id", incidentId), incidentId + ":notify");

        Incident.Containment containment = new Incident.Containment(
                suspendedCertificate, revokedCapabilities, new ArrayList<>(heldCases), disabledActions, true);

        Map<String, Object> raised = new LinkedHashMap<>();
        raised.put("incident_id", incidentId);
        raised.put("agent_id", agentId);
        raised.put("trigger", trigger.name());
        raised.put("detail", detail);
        raised.put("reported_by", reportedBy);
        raised.put("suspended_certificate", suspendedCertificate);
        raised.put("revoked_capabilities", revokedCapabilities);
        raised.put("held_cases", new ArrayList<>(heldCases));
        raised.put("disabled_actions", disabledActions);
        ledger("incident_raised", reportedBy, runId, agentId, now, raised, incidentId + ":raise");

        Incident incident = new Incident(incidentId, agentId, trigger, detail, "CONTAINED", reportedBy,
                now, now, null, containment);
        incidentRepository.insert(incident);
        return incident;
    }

    /** A lighter control: restrict one action class for an agent. A ledger event. */
    public Restriction restrict(String agentId, String action, String reason, String reportedBy) {
        Instant now = Instant.now();
        String runId = "restrict:" + agentId;
        runContextRepository.ensureExists(runId, agentId, "incident", now);
        ledger("action_class_disabled", reportedBy, runId, agentId, now,
                map("agent_id", agentId, "action", action, "reason", reason),
                "restrict:" + agentId + ":" + action);
        return new Restriction(agentId, action, reason, now);
    }

    private void ledger(String intent, String actor, String runId, String caseRef, Instant ts,
            Map<String, Object> payload, String requestId) {
        ledgerService.append(intent, actor, runId, caseRef, "contain", "n/a", payload, requestId, ts);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
