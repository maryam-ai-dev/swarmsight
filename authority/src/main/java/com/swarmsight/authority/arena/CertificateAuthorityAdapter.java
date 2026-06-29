package com.swarmsight.authority.arena;

import com.swarmsight.authority.decision.CertificateAuthority;
import com.swarmsight.authority.policy.Level;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Implements the verdict path's CertificateAuthority port over the real
 * certificate store. This is the seam that replaces the Sprint 2 stub: the
 * verdict path now sees the live certificate, status and all.
 */
@Component
public class CertificateAuthorityAdapter implements CertificateAuthority {

    private final CertificateRepository certificateRepository;

    public CertificateAuthorityAdapter(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Override
    public Optional<CertificateSnapshot> find(String agentId) {
        return certificateRepository.findLatestByAgent(agentId)
                .map(CertificateRepository.Stored::certificate)
                .map(c -> new CertificateSnapshot(
                        c.status(), Level.valueOf(c.ceiling()), c.certifiedActions(), c.expiresAt()));
    }
}
