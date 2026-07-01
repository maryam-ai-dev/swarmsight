package com.swarmsight.authority.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstraps identity on startup so the live product is never left with no way
 * in. Creates the admin account if it is missing: with the configured password,
 * or a freshly generated one logged once at WARN so the operator can capture it.
 * Idempotent, so a restart adds nothing.
 *
 * <p>When the demo seed is on, it also creates the three persona accounts (an
 * officer, a head of department, a service owner) so each role can be exercised
 * immediately. Switch these off for production by setting swarmsight.demo-seed
 * to false.
 */
@Component
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean demoSeed;
    private final String demoPassword;

    public UserSeeder(UserRepository users, PasswordEncoder encoder,
            @Value("${swarmsight.auth.admin-email:admin@swarmsight.local}") String adminEmail,
            @Value("${swarmsight.auth.admin-password:}") String adminPassword,
            @Value("${swarmsight.demo-seed:false}") boolean demoSeed,
            @Value("${swarmsight.auth.demo-password:swarmsight-demo}") String demoPassword) {
        this.users = users;
        this.encoder = encoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.demoSeed = demoSeed;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.existsByEmail(adminEmail)) {
            String password = adminPassword;
            boolean generated = password == null || password.isBlank();
            if (generated) {
                byte[] bytes = new byte[18];
                RANDOM.nextBytes(bytes);
                password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            }
            create(adminEmail, "Administrator", Role.ADMIN, password);
            if (generated) {
                log.warn("Bootstrap admin created: {} with generated password: {}  "
                        + "(set swarmsight.auth.admin-password / AUTH_ADMIN_PASSWORD to control it; "
                        + "change it after first login)", adminEmail, password);
            } else {
                log.info("Bootstrap admin created: {} with the configured password.", adminEmail);
            }
        }

        if (demoSeed) {
            seedPersona("officer@swarmsight.local", "J. Okafor", Role.OFFICER);
            seedPersona("head@swarmsight.local", "Head of Housing Service", Role.HEAD_OF_DEPARTMENT);
            seedPersona("owner@swarmsight.local", "Service Owner, Housing", Role.SERVICE_OWNER);
            log.info("Demo persona accounts ensured (officer/head/owner @swarmsight.local), "
                    + "password from swarmsight.auth.demo-password.");
        }
    }

    private void seedPersona(String email, String displayName, Role role) {
        if (!users.existsByEmail(email)) {
            create(email, displayName, role, demoPassword);
        }
    }

    private void create(String email, String displayName, Role role, String password) {
        users.insert(new User(UUID.randomUUID(), email, encoder.encode(password),
                role, displayName, true, Instant.now()));
    }
}
