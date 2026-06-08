# Feature Specification: Bond Price Grounding in Investment Options

**Feature Branch**: `012-bond-price-grounding`  
**Created**: 2026-06-08  
**Status**: Draft  
**Input**: User description: "Make the bond pipeline work like the metal pipeline: fetched + persisted bond prices should be used as the real price for an investment option when an appropriate bond option is presented to the user. Option A: per-ISIN — the model emits the specific bond ISIN, the server backfills that bond's exact stored value, and drops the option if the ISIN is unknown or unpriced."

## Context

Feature 009 already **collects** Ukrainian government/military bond quotes from PrivatBank and
**persists** them (the `bondPrices` store, one record per ISIN: sell/buy price in integer minor units
per 1000 face value, sell/buy yield, maturity, currency, quotation date), exposed **admin-only**.
What 009 does **not** do is use those stored values when a bond shows up as an investment option — a
`MILITARY_BOND`/`GOV_BOND` option's figures come from the catalog and the model, not the live data.

Feature 011 closed exactly this loop for precious metals (model emits a `GOLD`/`SILVER` discriminator;
the server backfills the exact stored price and drops the option if it cannot be grounded). This
feature does the same for bonds, **per-ISIN** (Option A): the model names the specific bond, the server
grounds its real values. This is the third stage (fetch -> persist -> **use as real price**) that bonds
are currently missing.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bond investment options show the exact current price and yield (Priority: P1)

A user runs the existing investment search that returns five investment options. When one of those
options is a government or military bond, the option carries the **exact current PrivatBank values for
that specific bond** (its real sell price and sell yield), rather than a model-estimated number or a
generic catalog range — so the user sees real, verifiable, up-to-date bond economics.

**Why this priority**: This is the entire point of collecting and persisting bond prices in feature
009. Without it, the stored data is admin-only and never reaches the user; grounding it in the options
the user actually sees is what makes the collection worthwhile.

**Independent Test**: With a current bond quote stored for a known ISIN, run a search whose results
include a bond option that names that ISIN, and confirm the option's price and yield equal the stored
exact values (not a model estimate). With no stored quote for the named ISIN, confirm the bond option
is not presented.

**Acceptance Scenarios**:

1. **Given** a current quote for a specific bond ISIN is stored and the existing generator presents a
   bond option that names that ISIN, **When** the search completes, **Then** that option's grounded
   values equal the stored exact values for that ISIN (sell price in minor units, sell yield) and no
   model-estimated figure is used for them.
2. **Given** the generator presents a bond option whose named ISIN is not in the stored set (or the
   ISIN is missing/unparseable), **When** the search completes, **Then** that option is dropped
   server-side and not shown to the user; the rest of the search still returns.
3. **Given** an option is not a government/military bond, **When** the search completes, **Then** the
   bond-price grounding does not alter that option.
4. **Given** several bond options each name a different valid stored ISIN, **When** the search
   completes, **Then** each carries its own bond's exact values (no cross-contamination).

---

### User Story 2 - Administrator still reads raw bond prices unchanged (Priority: P3)

An administrator continues to read the full stored bond price set exactly as feature 009 provides it;
this feature does not change that capability, and does not expose any new raw bond-price endpoint to
end users.

**Why this priority**: Confirms the change is additive and does not weaken the existing access model;
it is a guardrail, not new user value, so it is the lowest priority.

**Independent Test**: The existing admin bond-price read returns the same data as before; no
non-administrator can read raw bond prices.

**Acceptance Scenarios**:

1. **Given** stored bond prices exist, **When** an administrator reads the bond prices, **Then** they
   receive the full set unchanged from feature 009 behavior.
2. **Given** a non-administrator, **When** they attempt to read raw bond prices, **Then** access is
   denied (no new public bond-price surface is introduced).

---

### Edge Cases

- **Unknown / hallucinated ISIN**: If the model names an ISIN that is not in the stored set, the option
  is dropped (not shown), never filled with an estimated value.
- **Known provider, missing ISIN field**: If a bond option omits the ISIN entirely, it is dropped (the
  server cannot ground it).
- **All bond options ungroundable**: If every surviving option is a bond that cannot be grounded, the
  result is a legitimate empty option set — the whole search MUST NOT fail, and the user MUST NOT be
  charged a corrective retry (the same non-throwing drop semantics feature 011 established).
- **Stale stored quote**: The grounded values reflect the latest stored quote; freshness is judged by
  the stored fetch time (as in 009). A stale-but-present quote is still used (not dropped).
- **Mixed option set**: A non-bond option in the same result is never altered by bond grounding.
- **Multiple ISINs for one issuer**: The model selects a single ISIN per bond option; the server grounds
  that one. There is no aggregation across ISINs.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When the existing investment-options generator would present an option of category
  `MILITARY_BOND` or `GOV_BOND`, the system MUST set that option's grounded bond values from the exact
  current stored quote for the specific bond the option names, backfilled server-side, and MUST NOT use
  a model-estimated value for those grounded fields.
- **FR-002**: The specific bond MUST be identified by an ISIN supplied by the model on that option (a
  per-option discriminator), validated server-side against the stored bond set.
- **FR-003**: The grounded values taken from the stored quote MUST include the bond's sell price
  (integer minor units per 1000 face value) and its sell yield. [NEEDS CLARIFICATION: should the
  grounded sell yield REPLACE the option's existing expected-return range, or be surfaced as separate
  bond-specific fields alongside it? And should buy price/buy yield also be surfaced, or sell-side
  only?]
- **FR-004**: When the generator would present a bond option but the named ISIN is missing,
  unparseable, not in the stored set, or has no stored price, the system MUST drop the option
  server-side (it is not shown to the user); it MUST NOT substitute an estimated value and MUST NOT
  fail the whole search.
- **FR-005**: The drop MUST be a non-throwing post-filter: a dropped bond option (which references a
  real in-catalog provider) MUST NOT trigger the "no valid options / hallucination" failure path, even
  if it empties the final option set (which then returns as a legitimate empty result).
- **FR-006**: The bond-price grounding MUST apply only to `MILITARY_BOND`/`GOV_BOND` options and MUST
  NOT alter any other option.
- **FR-007**: The grounding MUST read from the existing stored bond data (feature 009's `bondPrices`)
  and MUST NOT trigger a live fetch from the source.
- **FR-008**: For the model to name a valid ISIN, the system MUST make the set of currently stored bond
  identifiers available to the option generator. [NEEDS CLARIFICATION: should the available bonds
  (ISIN + maturity + currency + indicative yield) be listed to the model in the prompt so it can choose
  a real one, or is the model expected to know ISINs and the server simply drops invalid ones? The
  former is needed to avoid nearly all bond options dropping; confirm the approach and any prompt
  size/budget impact.]
- **FR-009**: Raw bond-price read access MUST remain administrator-only (unchanged from feature 009);
  end users MUST only see the grounded option, never a raw bond-price endpoint.
- **FR-010**: The model MUST contribute only the ISIN selection for a bond option; the price and yield
  values MUST always be the server-grounded stored values, never model-supplied.

### Key Entities *(include if feature involves data)*

- **Bond Price Record** (existing, feature 009): the stored latest quote for one bond, keyed by ISIN,
  carrying sell/buy price (integer minor units per 1000 face), sell/buy yield, maturity, currency,
  quotation date, fetched-at. This feature consumes it read-only; it adds no new storage.
- **Investment Option** (existing): gains grounded bond fields for a `MILITARY_BOND`/`GOV_BOND` option
  — at minimum the bond ISIN and its exact stored sell price and sell yield. Exact field shape and the
  relationship to the existing expected-return range is pending FR-003 clarification. Null/absent for
  non-bond options and for legacy persisted searches.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of presented bond options carry the exact stored values for their named ISIN; 0%
  carry a model-estimated bond price or yield in the grounded fields.
- **SC-002**: When the named ISIN has no stored quote, 100% of those would-be bond options are dropped
  (none shown with an estimated or blank grounded value), and the rest of the search still returns.
- **SC-003**: An all-bond-ungroundable result never fails the search and never charges a corrective
  retry (the search returns an empty option set instead).
- **SC-004**: Non-bond options are byte-for-byte unaffected by the grounding (no grounded bond fields
  set on them).
- **SC-005**: Raw bond-price reads remain administrator-only; 100% of non-administrator raw-read
  attempts are denied (unchanged from feature 009).
- **SC-006**: For a verified sample, each grounded option's values match the stored record for its ISIN
  exactly (no rounding drift; minor units preserved).

## Assumptions

- **Reuse of the metals pattern**: The grounding reuses the feature 011 mechanism — a model-emitted
  per-option discriminator, server-side exact-value backfill, and a two-stage non-throwing drop-on-
  missing applied after catalog grounding. No new fetch/persist/ingest is built; feature 009's
  collection and storage are reused as-is.
- **Bond identity is the model's editorial choice; values are grounded**: Choosing which bond to
  recommend (its ISIN) is the model's job, exactly like choosing the instrument text; the price and
  yield are always server-grounded, so the model can never invent bond economics.
- **Default grounded fields (pending FR-003)**: absent other direction, the option surfaces the bond's
  sell price (minor units) and sell yield as the grounded values, with the real sell yield treated as
  the authoritative return figure for that option.
- **Default discriminator delivery (pending FR-008)**: absent other direction, the currently stored
  bond identifiers (ISIN + maturity + currency + indicative yield) are listed to the model alongside
  the allowed providers so it can pick a real ISIN, keeping within the existing input-token budget.
- **Latest-quote-only**: only the latest stored quote per ISIN is used (no history), consistent with
  feature 009.
- **No model/option-selection change**: this feature does not change which options the generator
  chooses to present, only how a bond option's values are grounded.

## Out of Scope (Non-Goals)

- Any change to bond collection, ingest, or storage (feature 009 is reused unchanged).
- A user-facing raw bond-price endpoint (reads stay administrator-only).
- Aggregating across multiple ISINs for a single bond option (Option B / category-aggregate is
  explicitly not chosen).
- Historical bond price/yield series or charting.
- Changing how the generator decides whether to include a bond at all.

## Dependencies

- **Feature 011 grounding pattern**: this feature reuses the two-stage, non-throwing grounding in the
  advisor output enforcement that feature 011 introduced; feature 011 MUST be merged first (this work
  extends the same enforcement point for bonds).
- **Feature 009 bond storage**: the `bondPrices` store and its ISIN-keyed records are the data source.
- The existing investment-options generator (the LLM-backed search with server-side catalog grounding)
  and its `MILITARY_BOND`/`GOV_BOND` option categories.
- The existing administrator role/authorization for raw bond-price reads.
