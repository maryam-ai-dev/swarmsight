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
 * The certificate read fails closed and the un-governed path is an explicit
 * exemption. A governed decision with no certificate is MISSING (block); the
 * policy-only EXEMPT path is reached only by the bootstrap context or a
 * registered shadow actor; an unreadable store is UNREADABLE; a lapsed expiry is
 * caught live.
 */
@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock private CertificateAuthority authority;

    private CertificateService service(String shadowActors) {
        return new CertificateService(authority, shadowActors);
    }

    @Test
    void aGovernedDecisionWithNoCertificateIsMissing() {
        when(authority.find("agent")).thenReturn(Optional.empty());
        assertThat(service("").check("agent", GovernanceContext.GOVERNED).presence())
                .isEqualTo(Presence.MISSING);
    }

    @Test
    void theBootstrapContextWithNoCertificateIsExempt() {
        when(authority.find("agent")).thenReturn(Optional.empty());
        assertThat(service("").check("agent", GovernanceContext.BOOTSTRAP).presence())
                .isEqualTo(Presence.EXEMPT);
    }

    @Test
    void aRegisteredShadowActorWithNoCertificateIsExempt() {
        when(authority.find("agent-1")).thenReturn(Optional.empty());
        assertThat(service("agent-1, agent-housing-1").check("agent-1", GovernanceContext.GOVERNED).presence())
                .isEqualTo(Presence.EXEMPT);
    }

    @Test
    void anUnreadableStoreFailsClosed() {
        when(authority.find("agent")).thenThrow(new RuntimeException("db down"));
        assertThat(service("").check("agent", GovernanceContext.GOVERNED).presence())
                .isEqualTo(Presence.UNREADABLE);
    }

    @Test
    void anActiveCertificateIsPresentAndActive() {
        when(authority.find("agent")).thenReturn(Optional.of(new CertificateSnapshot(
                "ACTIVE", Level.L2, List.of("draft_response"), Instant.parse("2030-01-01T00:00:00Z"))));
        CertificateCheck check = service("").check("agent", GovernanceContext.GOVERNED);
        assertThat(check.active()).isTrue();
        assertThat(check.certifiedActions()).contains("draft_response");
    }

    @Test
    void aLapsedExpiryIsCaughtLive() {
        when(authority.find("agent")).thenReturn(Optional.of(new CertificateSnapshot(
                "ACTIVE", Level.L2, List.of("draft_response"), Instant.parse("2020-01-01T00:00:00Z"))));
        CertificateCheck check = service("").check("agent", GovernanceContext.GOVERNED);
        assertThat(check.active()).isFalse();
        assertThat(check.status()).isEqualTo("EXPIRED");
    }
}
