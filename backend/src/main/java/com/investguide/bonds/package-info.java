/**
 * Military bond price refresh (feature 009-military-bond-prices).
 *
 * <p>Surfaces up-to-date Ukrainian military bond (military OVDP) quotes inside the app. Prices are
 * collected off-backend by a scheduled headless-browser scraper (GitHub Actions; never the 512 MB
 * Fly machine) which POSTs parsed bonds to the shared-secret machine-to-machine endpoint
 * {@code POST /api/v1/admin/bond-prices} ({@link com.investguide.bonds.BondPriceIngestController}).
 * The backend validates and upserts by ISIN into the {@code bondPrices} collection
 * ({@link com.investguide.bonds.BondPrice}) and serves them ADMIN-only via
 * {@code GET /api/v1/bond-prices} ({@link com.investguide.bonds.BondPriceController}), reusing the
 * same role gating as the providers catalog.
 *
 * <p><b>Money units (project rule):</b> all prices are integer <b>minor units</b> (kopiykas/cents)
 * of each row's own currency, quoted per 1000 face value; never floats. Yields are reference
 * percentages (doubles).
 */
package com.investguide.bonds;
