package com.investguide.bonds;

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
 * Feature 009 US1: {@code GET /api/v1/bond-prices} is ADMIN-only and read-from-store. Exercises the
 * REAL {@link SecurityConfig} chain over a {@link BondPriceController} slice (mirrors
 * {@code ProviderAuthorizationTest}).
 *
 * <ul>
 *   <li>ADMIN -> 200 with rows (prices as integer minor units).</li>
 *   <li>authenticated non-admin -> 403, handler never runs.</li>
 *   <li>anonymous -> 401, handler never runs.</li>
 *   <li>empty collection -> 200 {@code []}.</li>
 * </ul>
 */
@WebMvcTest(controllers = BondPriceController.class, properties = {
        "llm.api-key=test-key",
        "security.jwt-secret=test-secret-test-secret-test-secret-123456"
})
@Import({SecurityConfig.class, RestAuthEntryPoints.class, JwtService.class})
class BondPriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BondPriceRepository repository;

    private static BondPrice sampleBond() {
        return new BondPrice("UA4000227545", true, "UAH",
                LocalDate.of(2026, 11, 18), LocalDate.of(2026, 6, 5),
                107658L, 106900L, 15.25, 15.80, Instant.parse("2026-06-06T07:00:12Z"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminGetsBondPrices() throws Exception {
        when(repository.findAll()).thenReturn(List.of(sampleBond()));

        mockMvc.perform(get("/api/v1/bond-prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isin").value("UA4000227545"))
                .andExpect(jsonPath("$[0].military").value(true))
                .andExpect(jsonPath("$[0].currency").value("UAH"))
                .andExpect(jsonPath("$[0].sellPriceMinor").value(107658))
                .andExpect(jsonPath("$[0].buyPriceMinor").value(106900))
                .andExpect(jsonPath("$[0].sellYield").value(15.25));

        verify(repository).findAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyStoreReturnsEmptyArray() throws Exception {
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bond-prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/bond-prices"))
                .andExpect(status().isForbidden());
        verify(repository, never()).findAll();
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/bond-prices"))
                .andExpect(status().isUnauthorized());
        verify(repository, never()).findAll();
    }

    @Test
    void realAdminTokenGetsBondPrices() throws Exception {
        when(repository.findAll()).thenReturn(List.of(sampleBond()));
        String token = jwtService.generateAccessToken("admin-1", List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/bond-prices").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isin").value("UA4000227545"));

        verify(repository).findAll();
    }

    @Test
    void realUserTokenIsForbidden() throws Exception {
        String token = jwtService.generateAccessToken("user-1", List.of("USER"));

        mockMvc.perform(get("/api/v1/bond-prices").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        verify(repository, never()).findAll();
    }
}
