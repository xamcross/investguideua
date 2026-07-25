package com.investguide.bonds;

import com.investguide.bonds.dto.BondPriceResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
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
        return snapshot();
    }

    /**
     * Public latest-per-ISIN snapshot (feature 015, {@code GET /api/v1/bond-prices/latest}).
     *
     * <p>The collection stores exactly one document per ISIN (ingest upserts), so {@code findAll()}
     * IS the latest snapshot - there is no history to reduce over. Whitelisted for anonymous reads
     * in {@link com.investguide.common.security.SecurityConfig}: it feeds the public SEO data pages
     * (/ovdp/rates and the calculators) both at build time and via browser hydration. Cacheable for
     * an hour - ingest runs on a slow external cadence, and quote data is not user-specific.
     */
    @GetMapping("/bond-prices/latest")
    public ResponseEntity<List<BondPriceResponse>> latestBondPrices() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(snapshot());
    }

    private List<BondPriceResponse> snapshot() {
        return repository.findAll().stream()
                .map(BondPriceResponse::from)
                .toList();
    }
}
