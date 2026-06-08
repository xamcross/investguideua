package com.investguide.metals;

import com.investguide.common.security.JwtService;
import com.investguide.common.security.RestAuthEntryPoints;
import com.investguide.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 011 US1: {@code GET /api/v1/metal-prices} is ADMIN-only and read-from-store. Exercises the
 * REAL {@link SecurityConfig} chain over a {@link MetalPriceController} slice (mirrors the bond read).
 *
 * <ul>
 *   <li>ADMIN -> 200 with rows (rates as integer minor units per gram).</li>
 *   <li>authenticated non-admin -> 403, handler never runs.</li>
 *   <li>anonymous -> 401, handler never runs.</li>
 *   <li>empty collection -> 200 {@code []}.</li>
 * </ul>
 */
@WebMvcTest(controllers = MetalPriceController.class, properties = {
        "llm.api-key=test-key",
        "security.jwt-secret=test-secret-test-secret-test-secret-123456"
})
@Import({SecurityConfig.class, RestAuthEntryPoints.class, JwtService.class})
class MetalPriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MetalPriceRepository repository;

    private static MetalPrice sampleGold() {
        return new MetalPrice("GOLD", "one", "1", 1.0, "UAH",
                678000L, 888000L, LocalDate.of(2026, 6, 8), Instant.parse("2026-06-08T07:01:13Z"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminGetsMetalPrices() throws Exception {
        when(repository.findAll()).thenReturn(List.of(sampleGold()));

        mockMvc.perform(get("/api/v1/metal-prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("GOLD:one:1"))
                .andExpect(jsonPath("$[0].metal").value("GOLD"))
                .andExpect(jsonPath("$[0].rateGroup").value("one"))
                .andExpect(jsonPath("$[0].weightKey").value("1"))
                .andExpect(jsonPath("$[0].currency").value("UAH"))
                .andExpect(jsonPath("$[0].purchaseRateMinor").value(678000))
                .andExpect(jsonPath("$[0].saleRateMinor").value(888000));

        verify(repository).findAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyStoreReturnsEmptyArray() throws Exception {
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metal-prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/metal-prices"))
                .andExpect(status().isForbidden());
        verify(repository, never()).findAll();
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/metal-prices"))
                .andExpect(status().isUnauthorized());
        verify(repository, never()).findAll();
    }

    @Test
    void realAdminTokenGetsMetalPrices() throws Exception {
        when(repository.findAll()).thenReturn(List.of(sampleGold()));
        String token = jwtService.generateAccessToken("admin-1", List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/metal-prices").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metal").value("GOLD"));

        verify(repository).findAll();
    }

    @Test
    void realUserTokenIsForbidden() throws Exception {
        String token = jwtService.generateAccessToken("user-1", List.of("USER"));

        mockMvc.perform(get("/api/v1/metal-prices").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        verify(repository, never()).findAll();
    }
}
