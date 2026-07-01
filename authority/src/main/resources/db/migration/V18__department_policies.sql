-- The department (borough housing service) owns a set of workflow policies, one
-- rulebook per workflow. HA-09 (appeals) is seeded already; these are the other
-- three the Core-4 assistants are governed against. Each holds for a person on a
-- serious risk with a vulnerability, and requests evidence when a key document is
-- missing. Guards use a shared risk/vulnerable/key_document vocabulary so the
-- same generic behaviour is assured across workflows.

INSERT INTO policies (policy_id, version, effective_from, required_inputs, action_floors, guards, created_at)
VALUES (
    'HL-01', 'v1', '2024-01-01T00:00:00Z',
    '["applicant_details"]'::jsonb,
    '{"draft_response":"L1"}'::jsonb,
    '[
      {"name":"priority-need-household",
       "when":[{"key":"risk","op":"IS_TRUE"},{"key":"vulnerable","op":"IS_TRUE"}],
       "raiseTo":"L4_HUMAN","reasonCode":"PRIORITY_NEED",
       "brief":"Household may be in priority need; an officer must decide.",
       "source":"Homelessness Reduction Act 2017 s.189"},
      {"name":"assessment-evidence-missing",
       "when":[{"key":"risk","op":"IS_TRUE"},{"key":"key_document","op":"IS_ABSENT"}],
       "raiseTo":"L4_HUMAN","reasonCode":"EVIDENCE_MISSING",
       "brief":"Key assessment evidence is missing; request it before deciding.",
       "source":"Homelessness Code of Guidance"}
    ]'::jsonb,
    '2024-01-01T00:00:00Z'
);

INSERT INTO policies (policy_id, version, effective_from, required_inputs, action_floors, guards, created_at)
VALUES (
    'RP-01', 'v1', '2024-01-01T00:00:00Z',
    '["property_details"]'::jsonb,
    '{"draft_response":"L1"}'::jsonb,
    '[
      {"name":"urgent-safety-hazard",
       "when":[{"key":"risk","op":"IS_TRUE"},{"key":"vulnerable","op":"IS_TRUE"}],
       "raiseTo":"L4_HUMAN","reasonCode":"URGENT_HAZARD",
       "brief":"Urgent safety hazard with a vulnerable occupant; escalate to an officer.",
       "source":"Homes (Fitness for Human Habitation) Act 2018"},
      {"name":"hazard-report-missing",
       "when":[{"key":"risk","op":"IS_TRUE"},{"key":"key_document","op":"IS_ABSENT"}],
       "raiseTo":"L4_HUMAN","reasonCode":"REPORT_MISSING",
       "brief":"The hazard report is missing; request it before scheduling works.",
       "source":"Repairs and maintenance policy"}
    ]'::jsonb,
    '2024-01-01T00:00:00Z'
);

INSERT INTO policies (policy_id, version, effective_from, required_inputs, action_floors, guards, created_at)
VALUES (
    'FOI-01', 'v1', '2024-01-01T00:00:00Z',
    '["request_details"]'::jsonb,
    '{"draft_response":"L1"}'::jsonb,
    '[
      {"name":"sensitive-exemption",
       "when":[{"key":"risk","op":"IS_TRUE"},{"key":"vulnerable","op":"IS_TRUE"}],
       "raiseTo":"L4_HUMAN","reasonCode":"EXEMPTION_REVIEW",
       "brief":"A possible exemption engaging personal data; an officer must review before release.",
       "source":"Freedom of Information Act 2000 s.40; UK GDPR"},
      {"name":"scope-unclear",
       "when":[{"key":"risk","op":"IS_TRUE"},{"key":"key_document","op":"IS_ABSENT"}],
       "raiseTo":"L4_HUMAN","reasonCode":"SCOPE_UNCLEAR",
       "brief":"The request scope is unclear; seek clarification before releasing anything.",
       "source":"Freedom of Information Act 2000 s.1"}
    ]'::jsonb,
    '2024-01-01T00:00:00Z'
);
