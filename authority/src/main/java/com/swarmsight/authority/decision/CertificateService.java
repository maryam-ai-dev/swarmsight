package com.swarmsight.authority.decision;

import org.springframework.stereotype.Service;

/**
 * The certificate check. Stubbed for Sprint 1 to always report VALID, but the
 * call is wired into the decide path so the day Certificates become real
 * (Sprint 6) nothing in the path has to move. A blank actor has no certificate.
 */
@Service
public class CertificateService {

    public CertificateStatus statusFor(String actor) {
        if (actor == null || actor.isBlank()) {
            return CertificateStatus.MISSING;
        }
        return CertificateStatus.VALID;
    }
}
