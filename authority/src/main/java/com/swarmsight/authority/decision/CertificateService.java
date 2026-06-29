package com.swarmsight.authority.decision;

import com.swarmsight.authority.decision.CertificateAuthority.CertificateSnapshot;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads an agent's live certificate at decision time. No longer a stub: it reads
 * the real store through the CertificateAuthority port, never caching past the
 * decision, so a suspend takes effect on the next decision. A read error fails
 * closed. A blank actor or an agent with no certificate is the un-governed
 * policy-only path.
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final CertificateAuthority certificateAuthority;

    public CertificateService(CertificateAuthority certificateAuthority) {
        this.certificateAuthority = certificateAuthority;
    }

    public CertificateCheck check(String actor) {
        if (actor == null || actor.isBlank()) {
            return CertificateCheck.none();
        }
        try {
            Optional<CertificateSnapshot> found = certificateAuthority.find(actor);
            if (found.isEmpty()) {
                return CertificateCheck.none();
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
}
