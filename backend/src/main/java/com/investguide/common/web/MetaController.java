package com.investguide.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal public liveness/version endpoint so the SPA (and Docker Compose health checks) can
 * confirm reachability of {@code /api/v1} without authentication. Real feature endpoints arrive
 * with the BE-* tickets.
 */
@RestController
@RequestMapping("/api/v1")
public class MetaController {

    @Value("${spring.application.name:investguide-backend}")
    private String appName;

    @Value("${app.version:1.0.0}")
    private String version;

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok", "service", appName, "version", version);
    }
}
