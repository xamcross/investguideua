# Contract: Bond grounding in investment options

How stored bond prices reach the user via the existing investment search (FR-001..FR-010). No new HTTP
endpoint: this changes the advisor prompt schema, the output parser, and adds two nullable fields to
the search response options. Reuses feature 009 storage and the merged feature 011 two-stage grounding.

## Advisor output schema addition (prompt, BE-S4)

For a government/military-bond recommendation, the model includes an ISIN chosen from the listed bonds:

```json
{ "providerId": "privatbank", "category-implied": "MILITARY_BOND", "instrument": "OVDP 1y",
  "isin": "UA4000227545", "expectedReturnPct": {"min": 14, "max": 16}, "riskLevel": "LOW" }
```

`isin` must be copied verbatim from the `ALLOWED_BONDS` block. The model never supplies a bond price or
yield. (The model still emits `expectedReturnPct`, but for a grounded bond the server overwrites it with
the real yield — see below.)

### ALLOWED_BONDS prompt block (server-rendered)

```
ALLOWED_BONDS (for a government/military-bond option, copy one isin verbatim):
- isin=UA4000227545 | maturity=2026-11-18 | currency=UAH | yield~15.25
- isin=UA4000226893 | maturity=2027-05-12 | currency=UAH | yield~16.10
```

Filtered to the request currency, capped, and truncated to fit the input-token budget (bonds dropped
before providers). Empty block => the model is told not to return bond options.

## Server enforcement (AdvisorOutputParser — bond branch, non-throwing)

For each catalog-grounded option (Stage 2, after the unchanged Stage-1 catalog hallucination gate):

1. Category not MILITARY_BOND/GOV_BOND (and not PRECIOUS_METALS): unchanged; `bondIsin`/
   `bondSellPriceMinor` stay null (FR-006).
2. Category MILITARY_BOND or GOV_BOND:
   - Read `isin`. Missing => **drop** the option (FR-004).
   - `BondPriceService.findByIsin(isin)` empty (unknown/unpriced ISIN) => **drop** (FR-004).
   - Stored bond currency != the option's resolved currency => **drop** (FR-011) — the server, not the
     prompt list, is the currency guarantee.
   - Otherwise set `bondIsin` and `bondSellPriceMinor` (stored sell price, minor units per 1000 face),
     and **overwrite** `expectedReturnPct` with `min == max == clamp(storedSellYield, [0, maxReturnPct])`
     (FR-003) — any model-emitted return range for this option is discarded (FR-010).

A drop is a filter, never an exception: an all-bond-ungroundable result returns an empty option set,
never a 502 + charged retry (FR-005, reusing the 011 `inCatalog` gate).

## Search response shape (SearchResponse.options[])

Each `InvestmentOption` gains two nullable fields (after the 011 metals fields):

| Field | Type | When set |
|-------|------|----------|
| `bondIsin` | string \| null | grounded MILITARY_BOND/GOV_BOND option; null otherwise |
| `bondSellPriceMinor` | number \| null | stored sell price, minor units per 1000 face; null otherwise |

For a grounded bond option, `expectedReturnPct.min == expectedReturnPct.max == the real sell yield`.
Old persisted searches read both new fields back as null — backward compatible.

## Access

Raw bond-price reads stay admin-only (feature 009, unchanged). No user-facing raw bond-price endpoint
is added — end users only see the grounded option (FR-009).

## Acceptance mapping

- SC-001: presented bond options carry the exact stored sell price + sell-yield-as-return; 0% model
  estimates in the grounded fields.
- SC-002/SC-003: unknown ISIN drops the option; all-ungroundable yields empty (no search failure).
- SC-004: non-bond options unaffected. SC-006: grounded values match the stored record exactly.
