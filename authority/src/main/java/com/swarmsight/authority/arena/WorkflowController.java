package com.swarmsight.authority.arena;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The department's workflow registry and the task matcher. The catalog lists the
 * workflows the department runs; the matcher assigns a described agent task to
 * the best-fitting one, so an agent is governed by the department's own policy.
 */
@RestController
public class WorkflowController {

    private final WorkflowCatalog catalog;
    private final WorkflowMatcher matcher;

    public WorkflowController(WorkflowCatalog catalog, WorkflowMatcher matcher) {
        this.catalog = catalog;
        this.matcher = matcher;
    }

    @GetMapping("/workflows")
    public List<WorkflowCatalog.Workflow> workflows() {
        return catalog.all();
    }

    public record MatchRequest(String description) {
    }

    @PostMapping("/workflows/match")
    public WorkflowMatcher.Match match(@RequestBody MatchRequest req) {
        return matcher.match(req.description());
    }
}
