package com.swarmsight.authority.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the session token. HS256, signed with a secret Authority
 * holds. The token carries the user id (subject), role, email, and name, so the
 * security filter can authenticate a request without a database hit, and expires
 * after a configured TTL.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(
            @Value("${swarmsight.auth.jwt-secret}") String secret,
            @Value("${swarmsight.auth.jwt-ttl-minutes:480}") long ttlMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "swarmsight.auth.jwt-secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.id().toString())
                .claim("email", user.email())
                .claim("role", user.role().name())
                .claim("name", user.displayName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Parse and verify a token. Returns the authenticated principal, or throws. */
    public Principal verify(String token) {
        Claims c = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new Principal(
                UUID.fromString(c.getSubject()),
                c.get("email", String.class),
                Role.valueOf(c.get("role", String.class)),
                c.get("name", String.class));
    }

    /** The identity carried by a verified token. */
    public record Principal(UUID id, String email, Role role, String displayName) {
    }
}
