package com.investguide.bonds;

import com.investguide.bonds.dto.BondPriceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADMIN-only bond price read endpoint (feature 009 {@code GET /api/v1/bond-prices}).
 *
 * <p>Gated to the ADMIN role in {@link com.investguide.common.security.SecurityConfig}, the same way
 * as {@code /api/v1/providers} (non-admin 403, anonymous 401). Read-only and served entirely from
 * stored data - it never triggers a live scrape. An empty collection returns {@code []}, not an error.
 */
@RestController
@RequestMapping("/api/v1")
public class BondPriceController {

    private final BondPriceRepository repository;

    public BondPriceController(BondPriceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/bond-prices")
    public List<BondPriceResponse> bondPrices() {
        return repository.findAll().stream()
                .map(BondPriceResponse::from)
                .toList();
    }
}
