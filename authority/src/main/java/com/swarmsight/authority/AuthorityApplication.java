package com.swarmsight.authority;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SwarmSight Authority. Authority decides, holds the ledger, and runs the
 * broker. Sprint 0 is a scaffold: it boots, connects to Postgres, runs Flyway,
 * and answers a health check. Nothing real yet.
 */
@SpringBootApplication
public class AuthorityApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthorityApplication.class, args);
    }
}
