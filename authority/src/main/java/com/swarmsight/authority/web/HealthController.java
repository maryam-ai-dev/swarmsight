package com.swarmsight.authority.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness for the Authority service. Returns ok when the app is up. This is a
 * plain endpoint, not a deep readiness probe; deeper checks come in later
 * sprints.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "authority");
    }
}
