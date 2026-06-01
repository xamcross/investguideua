package com.investguide.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates {@code Authorization: Bearer <accessToken>} requests (SPECIFICATION §4.1, §10).
 *
 * <p>On a valid access token the security context is populated with the user id (as principal)
 * and granted authorities derived from the token roles. Invalid/expired tokens are simply not
 * authenticated — the security entry point then returns {@code 401 UNAUTHORIZED} for protected
 * routes. Tokens are never logged.
 *
 * <p>Not a {@code @Component}: instantiated by {@link SecurityConfig} and added to the security
 * filter chain only, avoiding double-registration as a global servlet filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /** Request attribute carrying the authenticated userId for the access-log filter (X6). */
    public static final String USER_ID_ATTRIBUTE = "authUserId";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                Claims claims = jwtService.parseAccessToken(token);
                String userId = claims.getSubject();
                List<SimpleGrantedAuthority> authorities = jwtService.roles(claims).stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
                var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                MDC.put("userId", userId);
                // Expose the userId as a request attribute too: the MDC value is cleared in this
                // filter's finally block (which runs before the outer access-log filter's post-step),
                // so the access log reads the authenticated user from here instead (X6).
                request.setAttribute(USER_ID_ATTRIBUTE, userId);
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid token: leave context unauthenticated; do NOT log the token value.
                SecurityContextHolder.clearContext();
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }
}
