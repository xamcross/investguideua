# Phase 0 Research: Bond Price Grounding in Investment Options

**Feature**: 012-bond-price-grounding | **Date**: 2026-06-08

Grounding-only feature: reuses feature 009 storage (`bondPrices`, ISIN-keyed) and the feature 011
two-stage grounding in `AdvisorOutputParser` (already merged on this branch). No new fetch/persist/
ingest. Decisions below resolve how the bond price/yield reaches a presented option.

## R1. Where grounding is wired (no orchestrator change)

**Decision**: Inject `BondPriceService` into **both** `PromptBuilder` (to list available bonds) and
`AdvisorOutputParser` (to look up the chosen ISIN and ground it). `InvestmentSearchService.runAdvisor`
is unchanged: it already calls `promptBuilder.build(input, allowed, corrective)` and
`parser.parse(text, bySlug, currency)`; both collaborators self-serve bonds via the injected service.

**Rationale**: Minimal blast radius and mirrors how feature 011 injected `MetalPriceService` into the
parser. The bond list and the ISIN lookup both come from the same already-stored data.

## R2. Listing bonds to the model within the token budget (FR-008)

**Decision**: In `PromptBuilder`, render an `ALLOWED_BONDS` block in the **userPrompt** (never in the
`systemPrompt`, which is estimated once and never truncated), after `ALLOWED_PROVIDERS`, listing the
stored bonds **filtered to the request currency** as `isin | maturity | currency | indicative yield`.
`userPrompt` MUST take the (possibly-truncated) bond list as a parameter. The budget loop becomes **two
explicit ordered passes**, NOT a single tail-drop that relies on textual position:
1. Cap the bond list at a constant (e.g. 25) and render full providers + capped bonds.
2. While over `llm.maxInputTokens`, **drop bonds from the tail until the bond list is empty**.
3. Only if still over budget, fall back to the existing **provider** tail-truncation.

This guarantees providers (the hard catalog grounding) survive while supplementary bonds are dropped
first, and the hard input-token bound is preserved regardless of stored-bond count.

**Rationale**: Without listing real ISINs the model can't emit valid ones and nearly every bond option
would drop (Clarifications Q1). Filtering by request currency keeps the list relevant and small (an
option's currency must match the provider/request anyway). Truncating bonds before providers preserves
the catalog-grounding guarantee (providers are the hard requirement; bonds enrich bond options). The
fit-loop keeps Constitution III's hard input-token budget intact.

**Alternatives**: list all bonds regardless of currency (more tokens, less relevant) - rejected;
promote the count cap to config now - deferred (a constant is enough for v1, can be promoted later).

## R3. Grounding a bond option (parser bond branch, reuses 011 two-stage)

**Decision**: In `AdvisorOutputParser.toOption`, add a branch parallel to the metals branch: when
`provider.getCategory()` is `MILITARY_BOND` or `GOV_BOND`, read `isin` from the model node; if missing
or `BondPriceService.findByIsin(isin)` is empty, **return null (drop)** - the existing non-throwing
Stage-2 filter (the `inCatalog` counter already incremented, so an all-bond-ungroundable result yields
an empty set, never an exception). Otherwise set the new option fields from the stored quote.

**Rationale**: Exact reuse of the merged 011 drop semantics (FR-004/FR-005/SC-002/SC-003). A dropped
bond option references a real in-catalog provider, so it must not trip the hallucination throw.

## R4. Which stored values, and the expected-return override (FR-003)

**Decision**: For a grounded bond option:
- `expectedReturnPct` is set to a degenerate range `min == max == clamp(storedSellYield, [0,
  llm.maxReturnPct])` - the real sell yield becomes the authoritative return figure, replacing the
  model/catalog range.
- A new `bondSellPriceMinor` (the stored sell price, integer minor units per 1000 face value) and
  `bondIsin` (the chosen ISIN) are added to the option.
- Sell-side only; buy price/yield are not surfaced in v1.

**Rationale**: One authoritative, verifiable return number (the live yield) rather than a model guess,
avoiding two competing return figures (Clarifications Q2). Clamping reuses the existing
`[0, maxReturnPct]` sanity bound; real OVDP yields (~15%) sit well under the 40% cap. Price stays
integer minor units (Constitution IV); yield is a percentage (double, like existing yields/returns).

**Implementation note**: `toOption` currently computes `expected` via `clampReturn(...)` then constructs
the option. The bond branch MUST **reassign** that `expected` local to the degenerate range
`new ReturnRange(clamp(sellYield, max), clamp(sellYield, max))` (reusing the existing `clamp` helper)
before construction — it does not compute two ranges.

## R4a. Currency consistency (the one real hole the review caught)

**Decision**: `BondPrice.currency` is a string (`UAH|USD|EUR`) while `SearchCurrency` is a closed enum
(`UAH|USD`). Two server-side rules:
- `listForPrompt` maps the request `SearchCurrency` to its name and lists only bonds with that exact
  currency; **EUR bonds are never listed** (no search can request EUR) — an accepted v1 limitation
  (spec FR-011, Assumptions).
- In the parser bond branch, after `findByIsin`, the server MUST verify the stored bond's currency
  equals the option's **resolved** currency (the `currency` computed by `parseCurrency`); on mismatch
  the option is **dropped** (FR-011, SC-007). The server is the guarantee, not the prompt list — a model
  that names an out-of-currency ISIN cannot surface a wrong-currency price.

**Rationale**: Without this, a model could name a EUR/USD ISIN under a UAH option and the server would
ground a price in the wrong currency. Dropping on mismatch keeps Constitution III's "server-side
enforcement is the real guarantee" intact.

## R4b. Degenerate return-range display

**Decision**: The results renderer currently prints `min&ndash;max`; for a grounded bond `min == max`,
which would read as "15.25-15.25". The frontend MUST collapse a `min == max` expected-return range to a
single number (FR-012). This is a small `results.component.ts` template change plus a rendering test.

**Rationale**: A degenerate range is confusing; one number is the correct presentation of a single live
yield.

## R5. BondPriceService additions

**Decision**: Add read methods to the existing `BondPriceService`:
- `Optional<BondPrice> findByIsin(String isin)` - delegates to `repository.findById(isin)` (ISIN is the
  `_id`), used by the parser.
- `List<BondPrice> listForPrompt(String currency)` - `findAll()` filtered to the currency (and bounded
  by the prompt cap in `PromptBuilder`), used to render `ALLOWED_BONDS`.

**Rationale**: Keeps bond data access behind the service (mirrors `MetalPriceService`); `BondPrice` is
already ISIN-keyed so `findById` is the natural lookup. No repository change strictly required, though a
`findByCurrency` derived query is an option if `findAll`+filter proves heavy (not expected for this
dataset).

## R6. Prompt schema + rule additions

**Decision**: Add `"isin":"<isin from ALLOWED_BONDS>"` to the JSON schema block; add a rule: include
`isin` ONLY for a government/military-bond option, chosen exactly from `ALLOWED_BONDS`, and never
include a bond price or yield (the server fills them). Extend the enum/verbatim rule so the ISIN must be
copied verbatim from the list. When `ALLOWED_BONDS` is empty, instruct the model not to return bond
options.

**Rationale**: Symmetric with the metals `metal` instruction; server still validates + drops, so the
prompt only steers. Constitution III preserved (server-side enforcement is the guarantee).

## R7. Frontend surfacing

**Decision**: Add nullable `bondIsin?` and `bondSellPriceMinor?` to the `InvestmentOption` TS model;
render a "bond price (per 1000 face)" fact row in `results.component.ts` only when present. The return
figure already renders from `expectedReturnPct` (now the real yield), so no extra return rendering is
needed. Add i18n labels.

**Rationale**: Backward compatible (old searches read null); mirrors the metals price-per-gram row.

## Resolved unknowns summary

| Unknown | Resolution |
|---------|-----------|
| How does the model get valid ISINs? | List currency-filtered stored bonds in the prompt; server validates + drops (R2) |
| Which values are grounded? | Sell yield -> expectedReturnPct (min==max), + bondSellPriceMinor + bondIsin; sell-side only (R4) |
| Where is grounding applied? | `AdvisorOutputParser` bond branch, reusing the merged 011 non-throwing drop (R3) |
| Data access? | `BondPriceService.findByIsin` + `listForPrompt(currency)` (R5) |
| Budget impact? | Bond list in userPrompt, two ordered truncation passes (bonds first, then providers) (R2) |
| Currency consistency? | List by request currency; drop on stored-vs-option currency mismatch; EUR never grounded (R4a) |
| Degenerate yield display? | Collapse `min == max` to a single number in the renderer (R4b) |

No `NEEDS CLARIFICATION` markers remain.
