package com.swarmsight.authority.broker;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The live caseload: the application documents the department holds in its
 * SharePoint library, one per case. Any authenticated user can list them; the
 * masked record and verdict for each case are still brokered per case.
 */
@RestController
public class CasesController {

    private final SharePointConnector connector;

    public CasesController(SharePointConnector connector) {
        this.connector = connector;
    }

    @GetMapping("/cases")
    public List<SharePointConnector.Application> cases() {
        return connector.listApplications();
    }
}
