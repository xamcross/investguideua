package com.investguide.metals;

import com.investguide.metals.dto.MetalPriceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADMIN-only metal price read endpoint (feature 011 {@code GET /api/v1/metal-prices}).
 *
 * <p>Gated to the ADMIN role in {@link com.investguide.common.security.SecurityConfig}, the same way as
 * {@code /api/v1/providers} and {@code /api/v1/bond-prices} (non-admin 403, anonymous 401). Read-only
 * and served entirely from stored data - it never triggers a live fetch (FR-014). An empty collection
 * returns {@code []}, not an error. This is the only externally exposed read of raw metal prices; end
 * users only see grounded precious-metals investment options (FR-014a).
 */
@RestController
@RequestMapping("/api/v1")
public class MetalPriceController {

    private final MetalPriceRepository repository;

    public MetalPriceController(MetalPriceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/metal-prices")
    public List<MetalPriceResponse> metalPrices() {
        return repository.findAll().stream()
                .map(MetalPriceResponse::from)
                .toList();
    }
}
