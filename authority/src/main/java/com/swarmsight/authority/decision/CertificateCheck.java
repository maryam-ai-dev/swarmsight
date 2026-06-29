package com.swarmsight.authority.decision;

import com.swarmsight.authority.policy.Level;
import java.util.Set;

/**
 * What the verdict path knows about an agent's certificate at decision time:
 * whether one is present, its status and ceiling and certified actions if so, or
 * that the store could not be read. Anything other than a present, ACTIVE
 * certificate is fail-closed; a missing one is the un-governed policy-only path.
 */
public record CertificateCheck(
        Presence presence,
        String status,
        Level ceiling,
        Set<String> certifiedActions) {

    public enum Presence {
        /** No certificate for this agent; decided on policy alone. */
        NONE,
        /** A certificate is present; its status governs the decision. */
        PRESENT,
        /** The store could not be read; fail closed. */
        UNREADABLE
    }

    public boolean present() {
        return presence == Presence.PRESENT;
    }

    public boolean active() {
        return present() && "ACTIVE".equals(status);
    }

    public static CertificateCheck none() {
        return new CertificateCheck(Presence.NONE, "NONE", null, Set.of());
    }

    public static CertificateCheck unreadable() {
        return new CertificateCheck(Presence.UNREADABLE, "UNREADABLE", null, Set.of());
    }

    public static CertificateCheck present(String status, Level ceiling, Set<String> certifiedActions) {
        return new CertificateCheck(Presence.PRESENT, status, ceiling, certifiedActions);
    }
}
