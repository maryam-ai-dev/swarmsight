package com.swarmsight.authority.decision;

import com.swarmsight.authority.policy.Level;
import org.springframework.stereotype.Service;

/**
 * The certificate check. Stubbed for Sprint 2: a valid certificate grants a
 * ceiling of L2, a missing one grants L0. The call is wired into the verdict
 * path so when Certificates become real in Sprint 6 nothing in the path moves.
 * A blank actor has no certificate.
 */
@Service
public class CertificateService {

    public CertificateCheck check(String actor) {
        if (actor == null || actor.isBlank()) {
            return new CertificateCheck(CertificateStatus.MISSING, Level.L0);
        }
        return new CertificateCheck(CertificateStatus.VALID, Level.L2);
    }
}
