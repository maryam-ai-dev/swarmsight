package com.swarmsight.authority.decision;

import com.swarmsight.authority.policy.Level;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The port the verdict path reads certificates through. The decision package
 * owns this interface; an adapter in the arena package implements it over the
 * certificate store, so the decision package does not depend on the arena
 * package. An implementation may throw if the store cannot be read; the verdict
 * path treats that as fail-closed.
 */
public interface CertificateAuthority {

    /** A point-in-time view of an agent's latest certificate. */
    record CertificateSnapshot(
            String status,
            Level ceiling,
            List<String> certifiedActions,
            Instant expiresAt) {
    }

    Optional<CertificateSnapshot> find(String agentId);
}
