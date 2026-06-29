package com.swarmsight.authority.decision;

import com.swarmsight.authority.policy.Level;

/**
 * The outcome of checking an agent's certificate: whether it can be relied on,
 * and the autonomy ceiling it grants. Only a VALID certificate grants a ceiling
 * above L0.
 */
public record CertificateCheck(CertificateStatus status, Level ceiling) {

    public boolean isValid() {
        return status == CertificateStatus.VALID;
    }
}
