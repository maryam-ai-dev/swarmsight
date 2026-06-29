package com.swarmsight.authority.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.swarmsight.authority.decision.CertificateAuthority.CertificateSnapshot;
import com.swarmsight.authority.decision.CertificateCheck.Presence;
import com.swarmsight.authority.policy.Level;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The certificate read fails closed. An unreadable store is UNREADABLE (which the
 * verdict path blocks on), a missing certificate is the policy-only path, and a
 * lapsed expiry is caught live even if the stored status was never updated.
 */
@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock private CertificateAuthority authority;

    private CertificateService service() {
        return new CertificateService(authority);
    }

    @Test
    void anUnreadableStoreFailsClosed() {
        when(authority.find("agent")).thenThrow(new RuntimeException("db down"));
        assertThat(service().check("agent").presence()).isEqualTo(Presence.UNREADABLE);
    }

    @Test
    void aMissingCertificateIsThePolicyOnlyPath() {
        when(authority.find("agent")).thenReturn(Optional.empty());
        assertThat(service().check("agent").presence()).isEqualTo(Presence.NONE);
    }

    @Test
    void anActiveCertificateIsPresentAndActive() {
        when(authority.find("agent")).thenReturn(Optional.of(new CertificateSnapshot(
                "ACTIVE", Level.L2, List.of("draft_response"), Instant.parse("2030-01-01T00:00:00Z"))));
        CertificateCheck check = service().check("agent");
        assertThat(check.active()).isTrue();
        assertThat(check.certifiedActions()).contains("draft_response");
    }

    @Test
    void aLapsedExpiryIsCaughtLive() {
        when(authority.find("agent")).thenReturn(Optional.of(new CertificateSnapshot(
                "ACTIVE", Level.L2, List.of("draft_response"), Instant.parse("2020-01-01T00:00:00Z"))));
        CertificateCheck check = service().check("agent");
        assertThat(check.active()).isFalse();
        assertThat(check.status()).isEqualTo("EXPIRED");
    }
}
