package com.swarmsight.authority.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * The identity operations behind the auth endpoints: authenticate a login,
 * create accounts (admin), change a password. Passwords are only ever compared
 * and stored as BCrypt hashes.
 */
@Service
public class AuthService {

    /** Thrown for a bad login. The message is deliberately vague. */
    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final boolean demoSeed;

    public AuthService(
            UserRepository users, PasswordEncoder encoder, JwtService jwt,
            @Value("${swarmsight.demo-seed:false}") boolean demoSeed) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.demoSeed = demoSeed;
    }

    /** Validate credentials and mint a session token. */
    public LoginResult login(String email, String password) {
        User user = users.findByEmail(email)
                .filter(User::active)
                .filter(u -> encoder.matches(password, u.passwordHash()))
                .orElseThrow(() -> new AuthException("Invalid email or password"));
        return new LoginResult(jwt.issue(user), user.view());
    }

    /**
     * Demo-only: mint a token for one of the seeded demo persona accounts without
     * a password, so a presenter can view the app as each role. Enabled only when
     * demo-seed is on (off in production) and restricted to the seeded demo emails.
     * The caller must already be authenticated (enforced by the security chain).
     */
    public LoginResult demoLogin(String email) {
        if (!demoSeed) {
            throw new AuthException("Demo login is not available.");
        }
        String e = email == null ? "" : email.trim().toLowerCase();
        boolean isDemoPersona = e.equals("officer@swarmsight.local")
                || e.equals("head@swarmsight.local")
                || e.equals("owner@swarmsight.local")
                || e.equals("admin@swarmsight.local");
        if (!isDemoPersona) {
            throw new AuthException("Not a demo persona.");
        }
        User user = users.findByEmail(e)
                .filter(User::active)
                .orElseThrow(() -> new AuthException("Demo persona not found."));
        return new LoginResult(jwt.issue(user), user.view());
    }

    /** Admin: create a new account with an initial password. */
    public User.View createUser(String email, String displayName, Role role, String password) {
        if (email == null || email.isBlank()) {
            throw new AuthException("Email is required");
        }
        if (password == null || password.length() < 8) {
            throw new AuthException("Password must be at least 8 characters");
        }
        if (users.existsByEmail(email)) {
            throw new AuthException("An account with that email already exists");
        }
        User user = new User(UUID.randomUUID(), email.trim(), encoder.encode(password),
                role, displayName == null ? email.trim() : displayName.trim(), true, Instant.now());
        return users.insert(user).view();
    }

    public List<User.View> listUsers() {
        return users.findAll().stream().map(User::view).toList();
    }

    public void setActive(UUID id, boolean active) {
        setActiveOrThrow(id, active);
    }

    private void setActiveOrThrow(UUID id, boolean active) {
        users.findById(id).orElseThrow(() -> new AuthException("No such user"));
        users.setActive(id, active);
    }

    /** Self-service: change your own password, current one required. */
    public void changePassword(UUID id, String currentPassword, String newPassword) {
        User user = users.findById(id).orElseThrow(() -> new AuthException("No such user"));
        if (!encoder.matches(currentPassword, user.passwordHash())) {
            throw new AuthException("Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new AuthException("New password must be at least 8 characters");
        }
        users.updatePasswordHash(id, encoder.encode(newPassword));
    }

    public record LoginResult(String token, User.View user) {
    }
}
