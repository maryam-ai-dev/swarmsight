package com.swarmsight.authority.decision;

import com.swarmsight.authority.decision.CertificateAuthority.CertificateSnapshot;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Reads an agent's live certificate at decision time and decides which regime
 * applies. The certified regime is the default and fails closed: a present
 * certificate must be ACTIVE; a governed decision with no certificate is MISSING
 * and blocks; an unreadable store blocks. The policy-only EXEMPT path is reached
 * only on purpose, by the Arena bootstrap context or a configured shadow actor,
 * never by mere absence. The read is never cached past the decision.
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final CertificateAuthority certificateAuthority;
    private final Set<String> shadowActors;

    public CertificateService(
            CertificateAuthority certificateAuthority,
            @Value("${swarmsight.governance.shadow-actors:}") String shadowActors) {
        this.certificateAuthority = certificateAuthority;
        this.shadowActors = Arrays.stream(shadowActors.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public CertificateCheck check(String actor, GovernanceContext context) {
        boolean exempt = context == GovernanceContext.BOOTSTRAP || isRegisteredShadowActor(actor);
        try {
            Optional<CertificateSnapshot> found = actor == null || actor.isBlank()
                    ? Optional.empty() : certificateAuthority.find(actor);
            if (found.isEmpty()) {
                // No certificate. Policy-only only if explicitly exempt; otherwise
                // this is a governed decision with nothing to govern it: block.
                return exempt ? CertificateCheck.exempt() : CertificateCheck.missing();
            }
            CertificateSnapshot snapshot = found.get();
            String status = snapshot.status();
            // Expiry is computed live, so a lapsed certificate is caught even if
            // its stored status was never updated.
            if (snapshot.expiresAt() != null && Instant.now().isAfter(snapshot.expiresAt())) {
                status = "EXPIRED";
            }
            return CertificateCheck.present(status, snapshot.ceiling(), Set.copyOf(snapshot.certifiedActions()));
        } catch (Exception e) {
            log.error("Certificate store unreadable for actor {}; failing closed", actor, e);
            return CertificateCheck.unreadable();
        }
    }

    private boolean isRegisteredShadowActor(String actor) {
        return actor != null && shadowActors.contains(actor);
    }
}
