package com.swarmsight.authority.golive;

import java.time.Instant;
import java.util.List;

/**
 * A service owner's sign-off to deploy an agent. A valid certificate alone is not
 * a deployment; this is. It records who approved, the scope and ceiling granted,
 * the trial period, the review checkpoint, and any conditions.
 */
public record DeploymentApproval(
        String id,
        String agentId,
        String approver,
        String scope,
        String trialPeriod,
        String reviewCheckpoint,
        List<String> conditions,
        String grantedCeiling,
        Instant approvedAt) {
}
