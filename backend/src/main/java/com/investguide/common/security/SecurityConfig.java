package com.investguide.common.security;

import com.investguide.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless Spring Security baseline (SPECIFICATION §2, §4.1, §10).
 *
 * <ul>
 *   <li>No server sessions — every request authenticates via a Bearer access token.</li>
 *   <li>Passwords hashed with BCrypt.</li>
 *   <li>CORS locked to the single configured app origin; credentials allowed (cookie refresh).</li>
 *   <li>Public routes: register, login, refresh, monobank callback, health. Everything else 401s
 *       without a valid token. The provider catalog ({@code GET /api/v1/providers}) additionally
 *       requires the ADMIN role (403 for an authenticated non-admin).</li>
 *   <li>CSRF disabled — safe for a token-authenticated, stateless JSON API with no cookie-based
 *       session auth (the refresh cookie is only read by the dedicated refresh endpoint).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    /** Public endpoints (SPECIFICATION §5.1 auth column + §4.3 callback + §11 health). */
    private static final String[] PUBLIC_GET = {
            "/api/v1/ping",
            "/actuator/health"
    };
    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/register",
            "/api/v1/auth/verify",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/payments/mono/callback",
            // Bond price ingest (009) is machine-to-machine: it carries a shared-secret header, not a
            // user JWT, so it must bypass the JWT chain here. It self-guards via BondIngestAuth (a
            // blank/missing/incorrect secret -> 401, fail-closed). A user JWT would 401 a no-Bearer
            // request before the secret check ever ran.
            "/api/v1/admin/bond-prices",
            // Metal price ingest (011) is the same machine-to-machine pattern with its own distinct
            // shared secret; self-guards via MetalIngestAuth (fail-closed).
            "/api/v1/admin/metal-prices"
    };

    private final JwtService jwtService;
    private final RestAuthEntryPoints authEntryPoints;
    private final AppProperties appProperties;

    public SecurityConfig(JwtService jwtService,
                          RestAuthEntryPoints authEntryPoints,
                          AppProperties appProperties) {
        this.jwtService = jwtService;
        this.authEntryPoints = authEntryPoints;
        this.appProperties = appProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        // Provider catalog is ADMIN-only (008-providers-admin-only). hasRole("ADMIN")
                        // matches the ROLE_ADMIN authority JwtAuthenticationFilter derives from the
                        // token roles claim. Non-admins get 403 (accessDeniedHandler), anonymous 401.
                        .requestMatchers(HttpMethod.GET, "/api/v1/providers").hasRole("ADMIN")
                        // Bond price read (009) reuses the same ADMIN gating as the provider catalog:
                        // authenticated non-admin -> 403, anonymous -> 401.
                        .requestMatchers(HttpMethod.GET, "/api/v1/bond-prices").hasRole("ADMIN")
                        // Metal price read (011) reuses the same ADMIN gating.
                        .requestMatchers(HttpMethod.GET, "/api/v1/metal-prices").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoints.unauthorized())
                        .accessDeniedHandler(authEntryPoints.accessDenied()))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(appProperties.cors().allowedOrigin()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true); // refresh token delivered via HttpOnly cookie (BE-A4)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
