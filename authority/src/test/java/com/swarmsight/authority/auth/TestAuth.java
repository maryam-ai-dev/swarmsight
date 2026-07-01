package com.swarmsight.authority.auth;

import java.util.Map;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * Test support for the HTTP integration tests now that every endpoint is behind
 * a login. Authenticates the {@link TestRestTemplate} as the bootstrap admin and
 * installs an interceptor that carries the bearer token on every later request,
 * so the existing get/post calls in those tests need no other change.
 *
 * <p>The tests set {@code swarmsight.auth.admin-password} to {@link #ADMIN_PASSWORD}
 * so the login is deterministic.
 */
public final class TestAuth {

    public static final String ADMIN_EMAIL = "admin@swarmsight.local";
    public static final String ADMIN_PASSWORD = "test-admin-pass";

    private TestAuth() {
    }

    @SuppressWarnings("unchecked")
    public static void authenticateAsAdmin(TestRestTemplate rest) {
        Map<String, Object> result = rest.postForObject(
                "/auth/login",
                Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD),
                Map.class);
        String token = (String) result.get("token");
        rest.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            request.getHeaders().setBearerAuth(token);
            return execution.execute(request, body);
        });
    }
}
