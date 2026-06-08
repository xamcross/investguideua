# Quickstart: Bond Price Grounding

How to verify feature 012 locally. Grounding-only: it reuses the feature 009 bond collection/storage
and the merged feature 011 grounding pattern, so there is no new scraper, ingest endpoint, or workflow.

## Prerequisites

- Backend running with MongoDB; the `bondPrices` collection populated (run the feature 009 bond scraper,
  or seed a few records directly).
- An ADMIN account (to inspect raw bond prices) and a verified user with tokens (to run a search).

## Verify the data is present (feature 009, unchanged)

```powershell
curl -H "Authorization: Bearer <admin token>" http://localhost:8080/api/v1/bond-prices
# expect 200 with bond records (isin, sellPriceMinor, sellYield, ...); non-admin -> 403
```

## Verify grounding in a search

1. Run an investment search (`POST /api/v1/investments/search`) whose results include a government/
   military bond option (e.g. an amount/currency where a bond provider fits).
2. For any option with `category` = `MILITARY_BOND`/`GOV_BOND` that the model grounded, confirm:
   - `bondIsin` is one of the stored ISINs,
   - `bondSellPriceMinor` equals that bond's stored sell price (minor units per 1000 face),
   - `expectedReturnPct.min == expectedReturnPct.max` and equals the stored sell yield (clamped),
   - no model-estimated return range remains.
3. Confirm a bond option whose ISIN is not stored is **not** shown, and the rest of the search still
   returns (drop, not failure).
4. Confirm non-bond options are unchanged (no `bondIsin`/`bondSellPriceMinor`).

## Verify the prompt lists bonds (unit-level)

`PromptBuilderTest` asserts the built prompt contains an `ALLOWED_BONDS` block with stored ISINs
(currency-filtered) and instructs the `isin` field; the whole prompt stays within `llm.maxInputTokens`.

## Verification gates (Constitution V/VI) before "done"

- Non-ASCII scan of any touched `.ps1`/`.cmd`/`.bat` (expected none) returns clean.
- `mvn -q test` (backend) passes (full suite, given cross-cutting `InvestmentOption`/parser changes);
  declare static-only if a runtime is unavailable.
- Two role sub-agents review, including the scan/parse step.
