-- Identity for the live product. Authority owns users the way it owns the
-- ledger and certificates: one place, one source of truth. Accounts are
-- created by an admin (no open sign-up); each carries one role that the UI
-- and the API both enforce. Passwords are stored only as BCrypt hashes.
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL,
    display_name  TEXT NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_role_check
        CHECK (role IN ('ADMIN', 'SERVICE_OWNER', 'HEAD_OF_DEPARTMENT', 'OFFICER'))
);

-- Email is the login identifier, case-insensitive unique.
CREATE UNIQUE INDEX users_email_unique ON users (lower(email));
