package com.swarmsight.authority.incident;

import com.swarmsight.authority.arena.AgentRepository;
import com.swarmsight.authority.arena.Certificate;
import com.swarmsight.authority.arena.CertificateRepository;
import com.swarmsight.authority.arena.RegisteredAgent;
import com.swarmsight.authority.policy.PolicyRepository;
import com.swarmsight.authority.policy.PolicyVersion;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Traces which agents are operating against an out-of-date policy. For each
 * registered agent it reads the latest certificate and compares it to the policy
 * version currently in force: a suspended or review-flagged certificate, an
 * expired one, or one issued before the current policy took effect all mean the
 * agent's assurance no longer matches the rules it would now be judged under.
 *
 * <p>Decisions always resolve under the version in force at their timestamp, so
 * the gap is never in the verdict path -- it is in the agent's certificate, which
 * is what this surfaces so a head of department can order re-certification.
 */
@Service
public class PolicyStandingService {

    // The single governed workflow today. With more workflows, an agent would
    // carry its own; here every agent is assured against HA-09.
    private static final String POLICY_ID = "HA-09";

    private final AgentRepository agents;
    private final CertificateRepository certificates;
    private final PolicyRepository policies;

    public PolicyStandingService(AgentRepository agents, CertificateRepository certificates,
            PolicyRepository policies) {
        this.agents = agents;
        this.certificates = certificates;
        this.policies = policies;
    }

    public record AgentPolicyStanding(
            String agentId,
            String name,
            String version,
            boolean certified,
            String certificateStatus,
            Instant certifiedAt,
            Instant expiresAt,
            String policyId,
            String inForcePolicyVersion,
            Instant inForceEffectiveFrom,
            boolean expired,
            boolean certifiedBeforeCurrentPolicy,
            String standing) {
    }

    public List<AgentPolicyStanding> standings() {
        Instant now = Instant.now();
        PolicyVersion inForce = policies.resolve(POLICY_ID, now).orElse(null);
        String inForceVersion = inForce == null ? null : inForce.version();
        Instant inForceFrom = inForce == null ? null : inForce.effectiveFrom();

        return agents.findAll().stream().map(agent -> {
            Certificate cert = certificates.findLatestByAgent(agent.id())
                    .map(CertificateRepository.Stored::certificate).orElse(null);
            return standingFor(agent, cert, inForceVersion, inForceFrom, now);
        }).toList();
    }

    private AgentPolicyStanding standingFor(RegisteredAgent agent, Certificate cert,
            String inForceVersion, Instant inForceFrom, Instant now) {
        if (cert == null) {
            return new AgentPolicyStanding(agent.id(), agent.name(), agent.version(), false, null,
                    null, null, POLICY_ID, inForceVersion, inForceFrom, false, false, "UNCERTIFIED");
        }

        boolean expired = cert.expiresAt().isBefore(now);
        boolean certifiedBeforeCurrentPolicy =
                inForceFrom != null && inForceFrom.isAfter(cert.issuedAt());

        String standing;
        if ("SUSPENDED".equals(cert.status())) {
            standing = "SUSPENDED";
        } else if ("REVIEW_REQUIRED".equals(cert.status())) {
            standing = "REVIEW_REQUIRED";
        } else if (expired) {
            standing = "EXPIRED";
        } else if (certifiedBeforeCurrentPolicy) {
            standing = "CERTIFIED_UNDER_EARLIER_POLICY";
        } else {
            standing = "CURRENT";
        }

        return new AgentPolicyStanding(agent.id(), agent.name(), agent.version(), true, cert.status(),
                cert.issuedAt(), cert.expiresAt(), POLICY_ID, inForceVersion, inForceFrom,
                expired, certifiedBeforeCurrentPolicy, standing);
    }
}
