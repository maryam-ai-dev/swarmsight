-- An agent is spun up for a department workflow and governed against the
-- department's policy for that workflow. It does not own a policy; it references
-- one. Existing agents default to the housing appeals workflow.
ALTER TABLE agents ADD COLUMN workflow TEXT NOT NULL DEFAULT 'HA-09';
