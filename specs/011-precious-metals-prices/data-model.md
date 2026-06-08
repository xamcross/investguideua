# Phase 1 Data Model: Precious Metals Price Refresh

**Feature**: 011-precious-metals-prices | **Date**: 2026-06-08

## Entity: MetalPrice (MongoDB collection `metalPrices`)

The latest known quote for one (metal, rate group, weight) combination. Mirrors `BondPrice`
(collection `bondPrices`) in storage discipline: natural-key `_id`, integer minor units, server-stamped
`fetchedAt`, no delete path.

| Field | Type | Notes |
|-------|------|-------|
| `id` (`@Id`) | String | Deterministic composite key `"<METAL>:<rateGroup>:<weightKey>"`, e.g. `GOLD:one:1`, `SILVER:one:10`. Upsert-by-`_id` gives one record per combination (FR-007, SC-002). |
| `metal` | String | `GOLD` or `SILVER` (uppercase enum value). |
| `rateGroup` | String | Source group key, opaque (`one`/`two`/`three`); stored verbatim. |
| `weightKey` | String | Source weight token, verbatim (`1`, `2.5`, `1000`) - part of the key (FR-004a). |
| `weightGrams` | double | Numeric weight for ordering / smallest-tier selection (a dimension label, not money). |
| `currency` | String | `UAH` (single currency for this feed; stored for parity/future-proofing). |
| `purchaseRateMinor` | long | Bank purchase rate (user sell-back), integer minor units (kopiykas) per gram. |
| `saleRateMinor` | long | Bank sale rate (user acquisition), integer minor units (kopiykas) per gram. |
| `quotationDate` | LocalDate | That metal's own source date (FR-004; gold and silver may differ). |
| `fetchedAt` | Instant | Server-stamped at ingest, one instant per batch (FR-006). |

**Validation (backend ingest, per record, drop-and-count)**:
- `metal` matches `GOLD|SILVER`; else reject the record.
- `rateGroup`, `weightKey` non-blank; `weightGrams` present and `> 0`.
- `currency` matches `UAH`.
- `purchaseRateMinor`, `saleRateMinor` present and `>= 0` (boxed `Long`, so omitted = null = reject).
- `quotationDate` parses as ISO `yyyy-MM-dd` (scraper converts source `dd.MM.yyyy`).

**Uniqueness / lifecycle**: One document per composite key; repeated runs upsert (no duplicates, no
deletes). A failed/partial run leaves existing documents untouched (FR-015).

## Derived query: canonical price-per-gram

`MetalPriceService.currentSalePricePerGramMinor(metal)` returns the saleRateMinor of the smallest
weight tier in the primary rate group, or empty if none:

```
findFirstByMetalAndRateGroupOrderByWeightGramsAsc(metal, primaryRateGroup)  // primaryRateGroup default "one"
  -> present ? Optional.of(doc.saleRateMinor) : Optional.empty()
```

Empty result => the option is dropped (FR-019).

## DTOs

### IngestMetalRequest (mirrors IngestBondRequest)
```
record IngestMetalRequest(
    @NotBlank @Pattern(regexp="GOLD|SILVER") String metal,
    @NotBlank String rateGroup,
    @NotBlank String weightKey,
    @NotNull @Positive Double weightGrams,
    @NotBlank @Pattern(regexp="UAH") String currency,
    @NotBlank String quotationDate,                 // ISO yyyy-MM-dd
    @NotNull @Min(0) Long purchaseRateMinor,
    @NotNull @Min(0) Long saleRateMinor
)
```

### MetalPriceResponse (admin read view, mirrors BondPriceResponse)
Mirrors the entity fields (id, metal, rateGroup, weightKey, weightGrams, currency, purchaseRateMinor,
saleRateMinor, quotationDate, fetchedAt).

### IngestResult
Reuse the existing `record IngestResult(int accepted, int rejected)` shape (own DTO in metals package
or shared) - identical to 009.

## Changed entity: InvestmentOption (existing record + persisted + wire shape)

Add two nullable trailing fields (backward compatible; old persisted searches read back null):

| New field | Type | Notes |
|-----------|------|-------|
| `metal` | String (nullable) | `GOLD`/`SILVER` for a grounded metals option; null otherwise. |
| `metalPricePerGramMinor` | Long (nullable) | Sale rate kopiykas/gram for the metal; null otherwise. |

Both are boxed (`Long`/`String`, never `long`) so old persisted searches deserialize them as null.
Set during a **non-throwing Stage-2 post-filter** in `AdvisorOutputParser` (after the unchanged
Stage-1 catalog-grounding hallucination gate): when `provider.getCategory() == PRECIOUS_METALS` and a
price is resolvable, set both; otherwise the option is dropped via a filter (NOT an exception), so an
all-metals-ungroundable result yields an empty option set rather than failing the search (FR-019,
SC-010, see contracts/investment-option-metal-grounding.md). Non-metals options keep both null
(FR-020).

## Config

| Key | Default | Purpose |
|-----|---------|---------|
| `metals.ingest.secret` | `${METAL_INGEST_SECRET:}` (blank) | Shared secret; blank => ingest fails closed. |
| `metals.primaryRateGroup` | `one` | Which rate group is the canonical retail group for pricing. |

## Frontend model (investment.models.ts)

Add optional `metal?: string` and `metalPricePerGramMinor?: number` to the `InvestmentOption`
interface; render a price-per-gram fact row in `results.component.ts` only when both are present.
