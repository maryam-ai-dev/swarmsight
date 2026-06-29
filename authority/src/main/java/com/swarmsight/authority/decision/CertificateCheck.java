package com.swarmsight.authority.decision;

import com.swarmsight.authority.policy.Level;
import java.util.Set;

/**
 * What the verdict path knows about an agent's certificate at decision time. The
 * certified regime is the default and fails closed: a present certificate must be
 * ACTIVE, and a governed decision with no certificate is MISSING and blocks. The
 * policy-only EXEMPT path is reached only by an explicit exemption (the Arena
 * bootstrap or a registered shadow actor), never by mere absence.
 */
public record CertificateCheck(
        Presence presence,
        String status,
        Level ceiling,
        Set<String> certifiedActions) {

    public enum Presence {
        /** Explicitly outside the certified regime; decided on policy alone. */
        EXEMPT,
        /** A certificate is present; its status governs the decision. */
        PRESENT,
        /** Governed, but no certificate was found. The failure case: block. */
        MISSING,
        /** The store could not be read; fail closed. */
        UNREADABLE
    }

    public boolean present() {
        return presence == Presence.PRESENT;
    }

    public boolean active() {
        return present() && "ACTIVE".equals(status);
    }

    /** The explicit un-governed exemption: decided on policy alone. */
    public static CertificateCheck exempt() {
        return new CertificateCheck(Presence.EXEMPT, "EXEMPT", null, Set.of());
    }

    /** Governed, but no certificate. Fails closed. */
    public static CertificateCheck missing() {
        return new CertificateCheck(Presence.MISSING, "MISSING", null, Set.of());
    }

    public static CertificateCheck unreadable() {
        return new CertificateCheck(Presence.UNREADABLE, "UNREADABLE", null, Set.of());
    }

    public static CertificateCheck present(String status, Level ceiling, Set<String> certifiedActions) {
        return new CertificateCheck(Presence.PRESENT, status, ceiling, certifiedActions);
    }
}
