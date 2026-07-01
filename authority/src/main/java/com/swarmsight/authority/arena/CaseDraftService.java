package com.swarmsight.authority.arena;

import com.swarmsight.authority.broker.CapabilityBroker;
import com.swarmsight.authority.broker.ConnectorRecord;
import com.swarmsight.authority.broker.FieldEffectEntry;
import com.swarmsight.authority.broker.SourceDocumentRef;
import com.swarmsight.authority.ledger.Effect;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Closes the loop: the live agent drafts from masked, brokered documents. For a
 * case, Authority brokers the case's application from the department SharePoint
 * (the broker decides, mints a short-lived capability on an allow, fetches, and
 * the permission mirror masks), then hands that masked record to the registered
 * agent as its only case context and returns the agent's proposal and draft.
 *
 * <p>The agent never reaches a connector and never sees an unmasked field: it is
 * given the masked record and nothing more, so a draft cannot leak what the
 * agent never received. On a held or blocked verdict the broker mints no
 * capability and no draft is produced, which is correct -- the agent should not
 * be reading then.
 */
@Service
public class CaseDraftService {

    // The department's SharePoint document library connector.
    private static final String CONNECTOR = "sharepoint-housing";

    private final CapabilityBroker broker;
    private final AgentRepository agents;
    private final HttpAgentFactory httpAgentFactory;

    public CaseDraftService(CapabilityBroker broker, AgentRepository agents,
            HttpAgentFactory httpAgentFactory) {
        this.broker = broker;
        this.agents = agents;
        this.httpAgentFactory = httpAgentFactory;
    }

    public record CaseDraft(
            String verdict,
            String agentId,
            String proposedAction,
            String rationale,
            String draft,
            String connector,
            SourceDocumentRef document,
            List<FieldEffectEntry> fieldEffects,
            Map<String, Object> agentInputs,
            String rejectionReason) {
    }

    public CaseDraft draftForCase(String caseRef, String agentId) {
        RegisteredAgent agent = agents.findById(agentId).orElseThrow(
                () -> new AgentService.RegistrationException("Agent " + agentId + " is not registered."));

        String scope = "application/" + caseRef;
        String requestId = "draft-" + caseRef + "-" + UUID.randomUUID();
        CapabilityBroker.IssueResult issued = broker.requestCapability(
                caseRef, requestId, "run-draft-" + caseRef, "agent-housing-1", "HA-09",
                "draft_response", CONNECTOR, scope,
                Map.of("tenancy_status", "secure", "eviction_risk", false, "dependent_children", false));

        if (issued.verdict().effect() != Effect.ALLOW || issued.capability() == null) {
            return new CaseDraft(issued.verdict().effect().name(), agentId, null, null, null,
                    CONNECTOR, null, List.of(), Map.of(),
                    "The broker minted no capability for this case; the agent may not read it.");
        }

        // The masked record is the agent's entire view of the case.
        ConnectorRecord record = broker.fetch(
                issued.capability().id(), CONNECTOR, scope, caseRef, "draft_response");

        Scenario scenario = new Scenario(scope, "Draft from masked application", "Casework",
                Severity.LOW, "HA-09", "draft_response", record.fields(),
                "send_decision", "draft_response");
        Agent.Decision decision = httpAgentFactory.forAgent(agent).act(scenario);

        return new CaseDraft(issued.verdict().effect().name(), agentId,
                decision.proposedAction(), decision.rationale(), decision.draft(),
                CONNECTOR, record.document(), record.fieldEffects(), record.fields(), null);
    }
}
