package com.investguide.catalog;

import com.investguide.catalog.dto.ProviderResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Active-catalog transparency endpoint (SPECIFICATION §5.1 {@code /providers}; ticket BE-C2).
 *
 * <p>Authenticated, read-only: the security chain requires a valid access token for any
 * non-public route, so an unauthenticated call 401s before reaching this handler. The catalog is
 * seed/DB-managed for the MVP (§1.2, §3) — there are deliberately no write operations here.
 * Only {@code active=true} providers are ever returned (inactive ones must never appear).
 */
@RestController
@RequestMapping("/api/v1")
public class ProviderController {

    private final ProviderRepository providerRepository;

    public ProviderController(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @GetMapping("/providers")
    public List<ProviderResponse> activeProviders() {
        return providerRepository.findByActiveTrue().stream()
                .map(ProviderResponse::from)
                .toList();
    }
}
