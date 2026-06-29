package com.swarmsight.authority.arena;

import java.time.Instant;
import java.util.List;

/**
 * A certificate: what an agent is and is not certified to do, the ceiling it may
 * operate at, the assurance case behind it, who built it, who signed it off, and
 * when it expires. The builder and the approver are always different people.
 */
public record Certificate(
        String id,
        String agentId,
        String assuranceCaseRef,
        List<String> certifiedActions,
        List<String> notCertifiedActions,
        String ceiling,
        String builder,
        String approver,
        Instant issuedAt,
        Instant expiresAt,
        String status) {
}
