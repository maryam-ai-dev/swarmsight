package com.swarmsight.authority.web;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS, configurable so a pilot can lock it down to its real frontend origin
 * rather than the demo's permissive default. Set swarmsight.cors.allowed-origins
 * (comma-separated origin patterns) for production; the default stays open.
 *
 * <p>This is a {@link CorsConfigurationSource} bean so Spring Security applies it
 * (the security filter chain enables {@code cors()}). In the live product the
 * browser reaches Authority through the frontend's same-origin proxy, so CORS is
 * rarely exercised, but credentials are allowed for any direct, allow-listed
 * origin. See DECISIONS.md.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOriginPatterns;

    public CorsConfig(@Value("${swarmsight.cors.allowed-origins:*}") String allowedOrigins) {
        this.allowedOriginPatterns = Arrays.asList(allowedOrigins.split(","));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(allowedOriginPatterns);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
