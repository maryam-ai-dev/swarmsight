package com.swarmsight.authority.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The live product's access rules. Stateless, token-authenticated (no server
 * sessions), and fail-closed: every endpoint requires a login except the health
 * check and the login call itself. The sensitive writes are pinned to the role
 * that owns them, matching the personas in the UI.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Preflight and liveness are open.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/health").permitAll()
                        // The login call is the only unauthenticated write.
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        // Account management is admin-only.
                        .requestMatchers("/auth/users", "/auth/users/**").hasRole("ADMIN")
                        // Registering an agent opens an outbound call path, so it
                        // belongs to a service owner (or admin).
                        .requestMatchers(HttpMethod.POST, "/agents")
                        .hasAnyRole("SERVICE_OWNER", "ADMIN")
                        // Containment belongs to the head of department.
                        .requestMatchers(HttpMethod.POST, "/incidents")
                        .hasAnyRole("HEAD_OF_DEPARTMENT", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/agents/*/restrict")
                        .hasAnyRole("HEAD_OF_DEPARTMENT", "ADMIN")
                        // Deployment sign-off belongs to the service owner.
                        .requestMatchers(HttpMethod.POST, "/agents/*/deployment-approval")
                        .hasAnyRole("SERVICE_OWNER", "ADMIN")
                        // Activating a policy supersession belongs to the service owner.
                        .requestMatchers(HttpMethod.POST, "/policy-changes/*/activate")
                        .hasAnyRole("SERVICE_OWNER", "ADMIN")
                        // Ingesting policy from an external source opens an outbound
                        // fetch and stages a change, so it belongs to the service owner.
                        .requestMatchers(HttpMethod.POST, "/policy-ingestion/**")
                        .hasAnyRole("SERVICE_OWNER", "ADMIN")
                        // Inferring a policy from a SharePoint document stages a change,
                        // so the same service-owner gate applies (the read-only list of
                        // documents below stays open to any authenticated user).
                        .requestMatchers(HttpMethod.POST, "/policy-documents/infer")
                        .hasAnyRole("SERVICE_OWNER", "ADMIN")
                        // Everything else: any authenticated user.
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"unauthenticated\"}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"forbidden\"}");
                        }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
