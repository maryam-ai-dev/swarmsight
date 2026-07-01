package com.swarmsight.authority.auth;

/**
 * The roles the live product recognises. They mirror the personas already in
 * the UI: an officer works cases, a head of department oversees and contains,
 * a service owner signs off deployments. ADMIN manages accounts.
 *
 * <p>Spring Security authorities are these names prefixed with {@code ROLE_}.
 */
public enum Role {
    ADMIN,
    SERVICE_OWNER,
    HEAD_OF_DEPARTMENT,
    OFFICER;

    public String authority() {
        return "ROLE_" + name();
    }
}
