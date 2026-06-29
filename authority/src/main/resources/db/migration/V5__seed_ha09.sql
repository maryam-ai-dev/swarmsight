-- Seed the housing example as two real, versioned policies so the temporal
-- resolver and the guards have data to work on. Sprint 8 builds the Policy
-- Workbench that creates versions through human approval; this seed stands in
-- until then.
--
-- v6 is the earlier version in force from 2024. It holds only when evidence is
-- missing. v7, in force from April 2025, adds the eviction-risk-with-dependent-
-- children guard, so the same case is judged differently before and after.

INSERT INTO policies (policy_id, version, effective_from, required_inputs, action_floors, guards, created_at)
VALUES (
    'HA-09', 'v6', '2024-01-01T00:00:00Z',
    '["tenancy_status"]'::jsonb,
    '{"draft_response":"L1"}'::jsonb,
    '[
      {"name":"evidence-missing",
       "when":[{"key":"eviction_risk","op":"IS_TRUE"},{"key":"eviction_notice","op":"IS_ABSENT"}],
       "raiseTo":"L4_HUMAN","reasonCode":"EVIDENCE_MISSING",
       "brief":"Key evidence (the latest eviction notice) is missing. Held for human review under HA-09 v6.",
       "source":"HA-09 s.4 evidence standard"}
    ]'::jsonb,
    '2024-01-01T00:00:00Z'
);

INSERT INTO policies (policy_id, version, effective_from, required_inputs, action_floors, guards, created_at)
VALUES (
    'HA-09', 'v7', '2025-04-01T00:00:00Z',
    '["tenancy_status"]'::jsonb,
    '{"draft_response":"L1"}'::jsonb,
    '[
      {"name":"eviction-risk-with-dependents",
       "when":[{"key":"eviction_risk","op":"IS_TRUE"},{"key":"dependent_children","op":"IS_TRUE"}],
       "raiseTo":"L4_HUMAN","reasonCode":"EVICTION_RISK_DEPENDENTS",
       "brief":"Eviction risk with dependent children present. Held for human review under HA-09 v7.",
       "source":"HA-09 s.4; Housing Act 1988"},
      {"name":"evidence-missing",
       "when":[{"key":"eviction_risk","op":"IS_TRUE"},{"key":"eviction_notice","op":"IS_ABSENT"}],
       "raiseTo":"L4_HUMAN","reasonCode":"EVIDENCE_MISSING",
       "brief":"Key evidence (the latest eviction notice) is missing. Held for human review under HA-09 v7.",
       "source":"HA-09 s.4 evidence standard"}
    ]'::jsonb,
    '2025-04-01T00:00:00Z'
);
