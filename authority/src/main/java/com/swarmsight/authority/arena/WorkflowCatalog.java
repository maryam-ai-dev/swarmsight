package com.swarmsight.authority.arena;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The department's workflow registry: each workflow it runs, with a plain
 * description and the policy id that governs it. This is what an agent's task is
 * matched against, so a task is assigned the department's own policy rather than
 * having one forced on it. (A single borough's registry today; would be per
 * borough in production.)
 */
@Component
public class WorkflowCatalog {

    public record Workflow(String id, String name, String summary) {
    }

    private static final List<Workflow> WORKFLOWS = List.of(
            new Workflow("HA-09", "Housing appeals",
                    "Appeals against housing decisions: reading the file, drafting a response for "
                            + "officer review, requesting missing evidence, escalating eviction risk."),
            new Workflow("HL-01", "Homelessness",
                    "Homelessness applications and priority-need assessments: triage, requesting "
                            + "assessment evidence, escalating vulnerable households."),
            new Workflow("RP-01", "Repairs and maintenance",
                    "Repair requests: categorising the work, drafting acknowledgements, escalating "
                            + "urgent safety hazards for a vulnerable occupant."),
            new Workflow("FOI-01", "FOI and records",
                    "Freedom of Information requests: classifying the request, redacting sensitive "
                            + "fields, escalating borderline exemptions before any release."));

    public List<Workflow> all() {
        return WORKFLOWS;
    }

    public Optional<Workflow> byId(String id) {
        return WORKFLOWS.stream().filter(w -> w.id().equals(id)).findFirst();
    }
}
