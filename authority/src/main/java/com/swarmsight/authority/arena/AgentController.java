package com.swarmsight.authority.arena;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.swarmsight.authority.auth.JwtService;

/**
 * The agent registry surface. Registration (a real write that opens an outbound
 * call path) is restricted to service owners and admins in the security chain;
 * listing and reading are available to any signed-in user. The call secret is
 * returned exactly once, here, at registration.
 */
@RestController
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    public record RegisterRequest(
            @NotBlank String name,
            @NotBlank String version,
            @NotBlank String endpointUrl,
            @NotBlank String environment,
            @NotEmpty List<String> requestedActions) {
    }

    @PostMapping("/agents")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentService.Registered register(
            @Valid @RequestBody RegisterRequest req,
            @AuthenticationPrincipal JwtService.Principal principal) {
        return agentService.register(req.name(), req.version(), req.endpointUrl(),
                req.environment(), req.requestedActions(),
                principal == null ? null : principal.email());
    }

    @GetMapping("/agents")
    public List<RegisteredAgent.View> list() {
        return agentService.list();
    }

    @GetMapping("/agents/{agentId}")
    public RegisteredAgent.View get(@PathVariable String agentId) {
        return agentService.get(agentId);
    }

    @ExceptionHandler(AgentService.RegistrationException.class)
    public ResponseEntity<Map<String, String>> onRegistrationError(AgentService.RegistrationException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AgentEndpointValidator.InvalidEndpointException.class)
    public ResponseEntity<Map<String, String>> onInvalidEndpoint(
            AgentEndpointValidator.InvalidEndpointException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
