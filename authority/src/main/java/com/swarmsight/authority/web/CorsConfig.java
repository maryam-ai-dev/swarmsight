package com.swarmsight.authority.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS, configurable so a pilot can lock it down to its real frontend origin
 * rather than the demo's permissive default. Set swarmsight.cors.allowed-origins
 * (comma-separated origin patterns) for production; the default stays open so the
 * static demo can be opened from the filesystem. Only GET and POST, no
 * credentials. See DECISIONS.md.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOriginPatterns;

    public CorsConfig(@Value("${swarmsight.cors.allowed-origins:*}") String allowedOrigins) {
        this.allowedOriginPatterns = allowedOrigins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST");
    }
}
