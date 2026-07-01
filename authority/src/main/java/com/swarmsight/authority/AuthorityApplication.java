package com.swarmsight.authority;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SwarmSight Authority. Authority decides, holds the ledger, runs the broker,
 * and now owns identity: it authenticates logins and signs its own session
 * tokens. It boots, connects to Postgres, runs Flyway, and answers a health
 * check.
 *
 * <p>The default in-memory user auto-configuration is excluded: identity is the
 * {@code users} table and the JWT filter, so Spring Boot must not seed a default
 * account with a generated password.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class AuthorityApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthorityApplication.class, args);
    }
}
