# Phase 1 Data Model: Bond Price Grounding

**Feature**: 012-bond-price-grounding | **Date**: 2026-06-08

No new persisted entity. This feature reads the existing feature 009 store and adds two fields to an
existing in-memory/persisted DTO.

## Reused entity: BondPrice (collection `bondPrices`, feature 009 — unchanged)

ISIN-keyed (`@Id`), per instrument: `sellPriceMinor`/`buyPriceMinor` (integer minor units per 1000 face
value), `sellYield`/`buyYield` (double %), `military` (bool), `currency`, `maturity`, `quotationDate`,
`fetchedAt`. This feature consumes it read-only. The values used for grounding are **`sellPriceMinor`**
and **`sellYield`**.

## Changed entity: InvestmentOption (record + persisted embed + wire shape)

Append two nullable trailing fields after the feature-011 metals fields (boxed, so legacy persisted
searches and non-bond options read back null):

| New field | Type | Notes |
|-----------|------|-------|
| `bondIsin` | String (nullable) | The grounded bond's ISIN; null unless a grounded MILITARY_BOND/GOV_BOND option. |
| `bondSellPriceMinor` | Long (nullable) | The bond's stored sell price, integer minor units per 1000 face value; null otherwise. |

For a grounded bond option, `expectedReturnPct` is **also** set server-side to a degenerate range
`min == max == clamp(sellYield, [0, llm.maxReturnPct])` (it is an existing field, not a new one).

Final field order: `providerId, providerName, instrument, category, currency, expectedReturnPct,
riskLevel, minAmount, liquidity, rationale, sourceUrl, metal, metalPricePerGramMinor, bondIsin,
bondSellPriceMinor`.

**Construction sites to update** (the record gains 2 components — grep-confirmed: exactly 3 total
`new InvestmentOption(`): the **one production** site `AdvisorOutputParser.toOption`, and the **two
test** sites in `InvestmentSearchServiceTest` (append `, null, null` for non-bond). NOTE:
`AdvisorOutputParserTest` has **no** `new InvestmentOption(` calls (it drives the real parser via JSON),
so there is nothing to fix there.

## Grounding logic (AdvisorOutputParser.toOption — bond branch)

```
// resolvedCurrency = parseCurrency(...) already computed in toOption
if (category == MILITARY_BOND || category == GOV_BOND) {
    isin = text(node."isin")
    if (isin == null) return null                       // drop (FR-004)
    BondPrice b = bondPriceService.findByIsin(isin).orElse(null)
    if (b == null) return null                           // unknown/unpriced ISIN -> drop (FR-004)
    if (!b.currency.equalsIgnoreCase(resolvedCurrency.name())) return null  // currency mismatch -> drop (FR-011)
    bondIsin = b.isin
    bondSellPriceMinor = b.sellPriceMinor
    double y = clamp(b.sellYield, maxReturnPct)
    expected = new ReturnRange(y, y)                     // REASSIGN the existing 'expected' local (FR-003)
}
```

The bond branch **reassigns the existing `expected` local** (it does not compute a second range), and
reuses the existing `clamp(v, max)` helper. A bond and a metals branch are mutually exclusive (a
provider is one category). Non-bond, non-metals options keep all four grounded fields null and their
normal `expectedReturnPct` (FR-006).

## Service methods (BondPriceService — additions)

| Method | Purpose |
|--------|---------|
| `Optional<BondPrice> findByIsin(String isin)` | ISIN lookup for grounding (delegates to `repository.findById`). |
| `List<BondPrice> listForPrompt(String currency)` | Stored bonds filtered to the request currency, for the `ALLOWED_BONDS` prompt block (bounded by the prompt cap). |

## Prompt shape (PromptBuilder)

- New schema field: `"isin":"<isin from ALLOWED_BONDS>"`.
- New `ALLOWED_BONDS` block (currency-filtered, capped, truncated to fit the input-token budget):
  `- isin=<isin> | maturity=<date> | currency=<ccy> | yield~<sellYield>`
- New rule: include `isin` only for a MILITARY_BOND/GOV_BOND option, copied verbatim from
  `ALLOWED_BONDS`; never include a bond price/yield. If `ALLOWED_BONDS` is empty, do not return bond
  options.

## Config

No new required config. A constant caps the listed bond count in `PromptBuilder` (promotable to
`bonds.prompt-max-list` later if needed). `llm.maxInputTokens` / `llm.maxReturnPct` reused as-is.

## Frontend (investment.models.ts + results.component.ts)

- Add optional `bondIsin?: string | null` and `bondSellPriceMinor?: number | null` to the model.
- Render a bond-price fact row only when both are present; the i18n label MUST explicitly carry the
  **"per 1000 face value"** qualifier in EN and UK (it is a quote, not the user's outlay) — FR-012/IV.
- Collapse the expected-return display to a **single number when `min == max`** (a grounded bond's
  degenerate range), instead of the current `min-max` rendering (FR-012). This applies wherever options
  render (shared results component), so non-bond ranges are unaffected.
