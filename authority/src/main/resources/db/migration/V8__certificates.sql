-- AssuranceCase and Certificate. An assurance case is the reasoned argument,
-- with claims linked to scenario evidence, that supports a certificate. A
-- certificate grants a ceiling and lists the actions an agent is and is not
-- certified for. See DECISIONS.md Sprint 6.

CREATE TABLE assurance_cases (
    id         TEXT        PRIMARY KEY,
    agent_id   TEXT        NOT NULL,
    claims     JSONB       NOT NULL,
    built_by   TEXT        NOT NULL,
    built_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE certificates (
    id                    TEXT        PRIMARY KEY,
    agent_id              TEXT        NOT NULL,
    assurance_case_ref    TEXT        NOT NULL REFERENCES assurance_cases(id),
    certified_actions     JSONB       NOT NULL,
    not_certified_actions JSONB       NOT NULL,
    ceiling               TEXT        NOT NULL,
    builder               TEXT        NOT NULL,
    approver              TEXT        NOT NULL,
    arena_summary         JSONB       NOT NULL,
    issued_at             TIMESTAMPTZ NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    status                TEXT        NOT NULL
);

CREATE INDEX idx_certificates_agent ON certificates (agent_id);
