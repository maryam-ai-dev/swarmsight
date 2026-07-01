package com.swarmsight.authority.auth;

import com.swarmsight.authority.auth.AuthRequests.ChangePasswordRequest;
import com.swarmsight.authority.auth.AuthRequests.CreateUserRequest;
import com.swarmsight.authority.auth.AuthRequests.LoginRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * The identity surface. Login is the only open write; everything else runs under
 * the security chain, which pins account management to ADMIN. The token is
 * returned in the body so the frontend can set it as an httpOnly cookie; it is
 * never logged.
 */
@RestController
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/login")
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.email(), req.password());
    }

    public record DemoLoginRequest(String email) {
    }

    /** Demo-only persona switch (see AuthService.demoLogin). Requires a session. */
    @PostMapping("/auth/demo-login")
    public AuthService.LoginResult demoLogin(@RequestBody DemoLoginRequest req) {
        return auth.demoLogin(req.email());
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me(@AuthenticationPrincipal JwtService.Principal principal) {
        return Map.of(
                "id", principal.id(),
                "email", principal.email(),
                "role", principal.role(),
                "displayName", principal.displayName());
    }

    @GetMapping("/auth/users")
    public List<User.View> listUsers() {
        return auth.listUsers();
    }

    /**
     * A read-only staff directory (no secrets) for the department dashboard: who
     * holds which role. Any authenticated user can see it; only ADMIN can manage
     * accounts via /auth/users.
     */
    @GetMapping("/auth/directory")
    public List<User.View> directory() {
        return auth.listUsers();
    }

    @PostMapping("/auth/users")
    @ResponseStatus(HttpStatus.CREATED)
    public User.View createUser(@Valid @RequestBody CreateUserRequest req) {
        return auth.createUser(req.email(), req.displayName(), req.role(), req.password());
    }

    @PostMapping("/auth/users/{id}/deactivate")
    public void deactivate(@PathVariable UUID id) {
        auth.setActive(id, false);
    }

    @PostMapping("/auth/users/{id}/activate")
    public void activate(@PathVariable UUID id) {
        auth.setActive(id, true);
    }

    @PostMapping("/auth/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal JwtService.Principal principal,
            @Valid @RequestBody ChangePasswordRequest req) {
        auth.changePassword(principal.id(), req.currentPassword(), req.newPassword());
    }

    /** A bad login or invalid account operation answers 400, never a stack trace. */
    @ExceptionHandler(AuthService.AuthException.class)
    public ResponseEntity<Map<String, String>> onAuthError(AuthService.AuthException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
