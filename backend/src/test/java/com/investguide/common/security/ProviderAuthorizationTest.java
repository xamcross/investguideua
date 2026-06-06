package com.investguide.common.security;

import com.investguide.catalog.ProviderController;
import com.investguide.catalog.ProviderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 008-providers-admin-only US1/US2: {@code GET /api/v1/providers} is ADMIN-only. This exercises the
 * REAL {@link SecurityConfig} filter chain (with the real {@link RestAuthEntryPoints} so the 401/403
 * envelopes are produced) over a slice of just {@link ProviderController}. {@code JwtService} is
 * mocked because {@code @WithMockUser} pre-populates the security context (the JWT filter is skipped
 * when an authentication already exists), and anonymous requests carry no Bearer header.
 *
 * <ul>
 *   <li>ADMIN  -> 200 and the handler runs (FR-001, SC-003).</li>
 *   <li>USER   -> 403, handler never runs (FR-002, FR-009, SC-002).</li>
 *   <li>anon   -> 401, handler never runs (FR-003).</li>
 * </ul>
 */
@WebMvcTest(controllers = ProviderController.class, properties = {
        // The two config values that have no default in application.yml (env-only secrets). Supplying
        // them lets all @EnableConfigurationProperties beans bind so the security slice context starts.
        // App/CORS/security props otherwise bind from the real application.yml.
        "llm.api-key=test-key",
        "security.jwt-secret=test-secret-test-secret-test-secret-123456"
})
@Import({SecurityConfig.class, RestAuthEntryPoints.class, JwtService.class})
class ProviderAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    /** Real (not mocked) so the JWT roles-claim -> ROLE_* authority -> hasRole chain is exercised. */
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ProviderRepository providerRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminGetsCatalog() throws Exception {
        when(providerRepository.findByActiveTrue()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isOk());

        // The handler actually ran (authorization passed), not short-circuited by the filter chain.
        verify(providerRepository).findByActiveTrue();
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isForbidden());

        // Access denied before the handler -> no catalog data is read or returned.
        verify(providerRepository, never()).findByActiveTrue();
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isUnauthorized());

        verify(providerRepository, never()).findByActiveTrue();
    }

    // ---- End-to-end with a REAL Bearer access token (exercises JwtAuthenticationFilter's
    //      "ROLE_" + role mapping against hasRole("ADMIN"), not just @WithMockUser). ----

    @Test
    void realAdminTokenGetsCatalog() throws Exception {
        when(providerRepository.findByActiveTrue()).thenReturn(List.of());
        String token = jwtService.generateAccessToken("admin-1", List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/providers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(providerRepository).findByActiveTrue();
    }

    @Test
    void realUserTokenIsForbidden() throws Exception {
        String token = jwtService.generateAccessToken("user-1", List.of("USER"));

        mockMvc.perform(get("/api/v1/providers").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        verify(providerRepository, never()).findByActiveTrue();
    }
}
