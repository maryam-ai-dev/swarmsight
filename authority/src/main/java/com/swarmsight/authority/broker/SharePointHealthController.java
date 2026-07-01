package com.swarmsight.authority.broker;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A first-run check for the SharePoint connection: reports the connector mode
 * and, when live, which Graph step (token, site, drive/list) succeeds or fails,
 * so wiring up a new tenant is one call instead of poking the masking screen.
 */
@RestController
public class SharePointHealthController {

    private final SharePointConnector connector;

    public SharePointHealthController(SharePointConnector connector) {
        this.connector = connector;
    }

    @GetMapping("/sources/sharepoint/health")
    public Map<String, Object> health() {
        return connector.diagnose();
    }
}
