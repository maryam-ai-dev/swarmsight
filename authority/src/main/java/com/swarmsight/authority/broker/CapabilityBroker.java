package com.swarmsight.authority.broker;

import com.swarmsight.authority.decision.DecisionRequest;
import com.swarmsight.authority.decision.DecisionService;
import com.swarmsight.authority.decision.Verdict;
import com.swarmsight.authority.ledger.Effect;
import com.swarmsight.authority.ledger.LedgerService;
import com.swarmsight.authority.policy.PolicyRepository;
import com.swarmsight.authority.policy.PolicyVersion;
import com.swarmsight.authority.run.RunContext;
import com.swarmsight.authority.run.RunContextRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues, revokes, and honours capabilities. The only public entry to a
 * connector. Every fetch validates a capability before a connector is touched,
 * and a connector cannot be reached any other way (see CapabilityGrant and
 * Connector, both package-private). Issuance and revocation are ledger events.
 */
@Service
public class CapabilityBroker {

    private static final String ISSUED = "capability_issued";
    private static final String REVOKED = "capability_revoked";
    private static final String FETCH = "source_fetch";

    private final CapabilityRepository capabilityRepository;
    private final LedgerService ledgerService;
    private final PolicyRepository policyRepository;
    private final RunContextRepository runContextRepository;
    private final DecisionService decisionService;
    private final PermissionMirror permissionMirror;
    private final Map<String, Connector> connectors;
    private final long ttlSeconds;

    public CapabilityBroker(
            CapabilityRepository capabilityRepository,
            LedgerService ledgerService,
            PolicyRepository policyRepository,
            RunContextRepository runContextRepository,
            DecisionService decisionService,
            PermissionMirror permissionMirror,
            List<Connector> connectorList,
            @Value("${swarmsight.capability.ttl-seconds:300}") long ttlSeconds) {
        this.capabilityRepository = capabilityRepository;
        this.ledgerService = ledgerService;
        this.policyRepository = policyRepository;
        this.runContextRepository = runContextRepository;
        this.decisionService = decisionService;
        this.permissionMirror = permissionMirror;
        this.connectors = connectorList.stream().collect(Collectors.toMap(Connector::name, Function.identity()));
        this.ttlSeconds = ttlSeconds;
    }

    /** The result of asking for a capability: the verdict, and a capability iff allowed. */
    public record IssueResult(Verdict verdict, Capability capability) {
    }

    /**
     * Decide the action, and only on an allow Verdict mint a capability bound to
     * that verdict. A hold or block returns the verdict and no capability.
     */
    public IssueResult requestCapability(
            String caseRef, String requestId, String runId, String actor, String workflow, String action,
            String connector, String resourceScope, Map<String, Object> inputs) {

        Verdict verdict = decisionService.decide(new DecisionRequest(
                requestId, runId, caseRef, actor, workflow, action, inputs));

        if (verdict.effect() != Effect.ALLOW) {
            return new IssueResult(verdict, null);
        }

        Capability capability = issue(
                requestId, runId, caseRef, actor, workflow, action, connector, resourceScope, verdict.rowHash());
        return new IssueResult(verdict, capability);
    }

    private Capability issue(
            String requestId, String runId, String caseRef, String actor, String workflow, String action,
            String connector, String resourceScope, String issuedByVerdict) {

        String id = "cap-" + requestId;
        Optional<Capability> existing = capabilityRepository.findById(id);
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant now = Instant.now();
        Capability capability = new Capability(
                id, runId, caseRef, action, actor, connector, resourceScope, issuedByVerdict,
                now, now.plusSeconds(ttlSeconds), true, null, null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("capability_id", id);
        payload.put("connector", connector);
        payload.put("resource_scope", resourceScope);
        payload.put("expires_at", capability.expiresAt().toString());
        payload.put("issued_by_verdict", issuedByVerdict);
        payload.put("revocable", true);
        ledgerService.append(ISSUED, actor, runId, caseRef, action, versionFor(workflow), payload,
                requestId + ":cap-issue", now);

        capabilityRepository.insert(capability);
        return capability;
    }

    /** Revoke a capability. Idempotent and a ledger event. */
    public void revoke(String capabilityId, String reason) {
        Capability cap = capabilityRepository.findById(capabilityId)
                .orElseThrow(() -> new BrokerException(BrokerException.Reason.NO_CAPABILITY,
                        "No capability " + capabilityId));

        Instant now = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("capability_id", capabilityId);
        payload.put("reason", reason);
        String workflow = runContextRepository.find(cap.runId()).map(RunContext::workflow).orElse("unknown");
        ledgerService.append(REVOKED, "authority", cap.runId(), cap.caseRef(), cap.action(),
                versionFor(workflow), payload, capabilityId + ":revoke", now);

        capabilityRepository.markRevoked(capabilityId, reason, now);
    }

    /**
     * Fetch from a connector, only with a valid, unexpired, unrevoked capability
     * that does not exceed the connector, case, action, and resource scope it was
     * issued for. Fails closed on anything else.
     */
    public ConnectorRecord fetch(
            String capabilityId, String connector, String resourceScope, String caseRef, String action) {

        Capability cap = capabilityRepository.findById(capabilityId)
                .orElseThrow(() -> new BrokerException(BrokerException.Reason.NO_CAPABILITY,
                        "No capability presented."));

        if (cap.isRevoked()) {
            throw new BrokerException(BrokerException.Reason.REVOKED, "Capability has been revoked.");
        }
        if (cap.isExpiredAt(Instant.now())) {
            throw new BrokerException(BrokerException.Reason.EXPIRED, "Capability has expired.");
        }
        if (!cap.connector().equals(connector) || !cap.caseRef().equals(caseRef)
                || !cap.action().equals(action) || !cap.resourceScope().equals(resourceScope)) {
            throw new BrokerException(BrokerException.Reason.EXCEEDS_VERDICT,
                    "Fetch exceeds the capability's connector, case, action, or scope.");
        }

        Connector target = connectors.get(connector);
        if (target == null) {
            throw new BrokerException(BrokerException.Reason.UNKNOWN_CONNECTOR, "No connector " + connector);
        }

        // The grant exists only here, after validation. This is the single path
        // to a connector.
        CapabilityGrant grant = new CapabilityGrant(capabilityId, connector, resourceScope, caseRef, action);
        RawRecord raw = target.fetch(grant);

        // The mirror runs before anything leaves the boundary. Raw values never
        // escape un-mirrored.
        PermissionMirror.Mirrored mirrored = permissionMirror.apply(raw);
        recordFieldEffects(cap, connector, resourceScope, mirrored.fieldEffects());
        return new ConnectorRecord(connector, resourceScope, mirrored.maskedFields(), mirrored.fieldEffects());
    }

    /** Ledger the field_effects (never the values), proving what was exposed. */
    private void recordFieldEffects(
            Capability cap, String connector, String resourceScope, List<FieldEffectEntry> effects) {
        List<Map<String, Object>> effectsPayload = new ArrayList<>();
        for (FieldEffectEntry e : effects) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("field", e.field());
            m.put("source_permission", e.sourcePermission().name());
            m.put("policy", e.policy().name());
            m.put("outcome", e.outcome().name());
            effectsPayload.add(m);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("capability_id", cap.id());
        payload.put("connector", connector);
        payload.put("resource_scope", resourceScope);
        payload.put("field_effects", effectsPayload);

        String workflow = runContextRepository.find(cap.runId()).map(RunContext::workflow).orElse("unknown");
        ledgerService.append(FETCH, cap.actor(), cap.runId(), cap.caseRef(), cap.action(),
                versionFor(workflow), payload, cap.id() + ":fetch:" + resourceScope, Instant.now());
    }

    private String versionFor(String workflow) {
        return policyRepository.resolve(workflow, Instant.now())
                .map(PolicyVersion::version).orElse("n/a");
    }
}
