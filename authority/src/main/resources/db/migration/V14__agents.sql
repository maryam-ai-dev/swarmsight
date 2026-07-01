-- The agent registry. SwarmSight becomes a real assurance platform: a team
-- registers its own agent at its own HTTP endpoint, and Authority governs it.
-- The agent only ever proposes; Authority decides, the arena tests in shadow,
-- the certificate constrains, and the broker mediates data. So the endpoint is
-- untrusted by design.
--
-- call_secret is the per-agent bearer token Authority presents when it calls the
-- endpoint, so the agent can prove the caller is SwarmSight. It is shown to the
-- owner once at registration. (Stored as-is because Authority must replay it;
-- encrypt at rest in production.)
CREATE TABLE agents (
    id                TEXT PRIMARY KEY,
    name              TEXT NOT NULL,
    version           TEXT NOT NULL,
    endpoint_url      TEXT NOT NULL,
    environment       TEXT NOT NULL,
    requested_actions JSONB NOT NULL,
    call_secret       TEXT,
    owner_email       TEXT,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
