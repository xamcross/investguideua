# Contract: Metals grounding in investment options

How the stored prices reach the user via the existing "5 investment options" search (FR-018/019/020).
This changes the advisor prompt schema and the output parser; the search endpoint shape gains two
nullable option fields.

## Advisor output schema addition (prompt, BE-S4)

For a precious-metals recommendation, the model includes a metal discriminator on that option:

```json
{ "providerId": "privatbank-metals", "instrument": "Gold bullion 1g", "metal": "GOLD", "...": "..." }
```

`metal` is `GOLD` or `SILVER`. It is the only new model-supplied field; the price is never supplied by
the model.

## Server enforcement (AdvisorOutputParser) — TWO STAGES

The grounding is applied as a **two-stage** transform so that a metals drop never re-triggers the
hallucination failure path. This is critical for FR-019/SC-010.

**Stage 1 — catalog validity (UNCHANGED hallucination gate).** Exactly as today: an option whose
`providerId` is not an active-catalog slug is dropped; if the model returned a non-empty list and
**zero** in-catalog providers survive, `parse()` throws `AdvisorOutputException` (orchestrator retries
once, then fails). This gate is measured on **catalog membership only** — the metal price is NOT part
of it.

**Stage 2 — metal grounding (NON-THROWING post-filter).** Applied to the already-catalog-valid
candidates:

1. If `provider.category != PRECIOUS_METALS`: leave `metal` and `metalPricePerGramMinor` null
   (FR-020); option unaffected.
2. If `provider.category == PRECIOUS_METALS`:
   - Read `metal` from model output; validate it is `GOLD`/`SILVER`. Invalid/missing => **drop the
     option** (FR-019).
   - Resolve `currentSalePricePerGramMinor(metal)`. Empty (no stored price) => **drop the option**
     (FR-019).
   - Otherwise set `metal` and `metalPricePerGramMinor` (the resolved sale rate, kopiykas/gram).

**Why two stages**: a PRECIOUS_METALS option references a *real* in-catalog provider, so it is NOT a
hallucination — it is merely ungroundable for price. Therefore a Stage-2 drop MUST NOT throw, even if
it empties the final list. If the only surviving candidates were ungroundable metals, the result is a
legitimate **empty** option set (the existing "model returned nothing that fits" path, §8.4 rule 2) —
NOT a `502 ADVISOR_UNAVAILABLE`, and the user is NOT charged a corrective retry. This is the opposite
of the out-of-catalog case, which still throws in Stage 1.

**Implementation note**: because `toOption` currently returns a non-null option unconditionally and the
caller does `valid.add(toOption(...))`, Stage 2 requires a real control-flow change — either
`toOption` returns `null` for a dropped metals option and the caller skips nulls, or Stage 2 is a
separate `.stream().filter(...)` pass over the Stage-1 list. Either is acceptable; the drop must be a
filter, not an exception.

## Search response shape (SearchResponse.options[])

Each `InvestmentOption` gains two nullable fields:

| Field | Type | When set |
|-------|------|----------|
| `metal` | string \| null | `GOLD`/`SILVER` for a grounded metals option; null otherwise |
| `metalPricePerGramMinor` | number \| null | sale rate kopiykas/gram; null otherwise |

Old persisted searches (history) read these back as null - backward compatible.

## Acceptance mapping

- SC-009: presented metals options carry the exact stored sale-rate-per-gram; 0% model-estimated.
- SC-010: when no stored price exists, would-be options for that metal are dropped; the rest of the
  search still returns.
