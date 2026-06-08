# Feature Specification: Precious Metals Price Refresh

**Feature Branch**: `011-precious-metals-prices`  
**Created**: 2026-06-08  
**Status**: Draft  
**Input**: User description: "Prepare a scheduled request to gather precious metals (gold, silver) prices each day at 10:00am Kyiv time. Use this endpoint GET https://privatbank.ua/pb/ajax/bank-metall-courses."

## Clarifications

### Session 2026-06-08

- Q: How should the stored prices be exposed to end users (given they feed investment guidance)? → A: Internal server-side only - the investment-guidance capability reads the stored prices server-side and returns derived guidance to users; there is no user-facing raw-price endpoint, and direct/management reads of raw prices remain administrator-only.
- Q: Does feature 011 build new guidance logic, or feed existing logic? → A: It feeds the existing "5 investment options" generator. When a presented option is a precious metal (category PRECIOUS_METALS), the option MUST use the exact current stored metal price (not a model-estimated range or assumption), backfilled server-side - analogous to how provider identity fields are already backfilled from the catalog.
- Q: Which exact stored value is the price on a metals option? → A: The bank sale rate (the user's acquisition price), expressed in kopiykas per gram, taken from the primary retail rate group at the smallest available weight tier for that metal (the headline single-unit price).
- Q: What happens when a metals option would be shown but no current price is stored? → A: Drop the option server-side (do not present it), consistent with the existing rule that rejects options which cannot be grounded server-side.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administrator views current precious metals prices (Priority: P1)

An administrator of InvestGuideUA opens the application and consults an up-to-date set of PrivatBank
gold and silver quotes (purchase and sale rates per weight, per rate group), so they can see current
metals pricing without manually visiting PrivatBank's site.

**Why this priority**: This is the core value of the feature - making fresh metals pricing visible
inside the product. Without a way to read the prices, the collection and storage pipeline delivers no
observable value. It is the smallest slice that demonstrates end-to-end worth once data exists.

**Independent Test**: With at least one metals price record present in storage, an authenticated
administrator requests the metals price list and receives the current set of quotes for each metal,
rate group, and weight, including purchase rate, sale rate, the source quotation date, and the time
the data was fetched. A non-administrator is denied access.

**Acceptance Scenarios**:

1. **Given** metals price records exist and the user is authenticated as an administrator, **When** the
   user requests the metals price list, **Then** the system returns all stored quotes with metal
   (gold/silver), rate group, weight, purchase rate, sale rate, source quotation date, and fetch time.
2. **Given** the user is authenticated but is not an administrator, **When** the user requests the
   metals price list, **Then** the system denies access with an authorization error.
3. **Given** no metals price records exist yet, **When** an administrator requests the metals price
   list, **Then** the system returns an empty list (not an error).

---

### User Story 2 - Automated daily refresh keeps prices current (Priority: P1)

The system automatically collects the latest gold and silver quotes from PrivatBank's public metals
endpoint once every day at approximately 10:00 Kyiv time, so administrators always see recent pricing
without any manual action.

**Why this priority**: Freshness is the whole point - a one-time manual load would quickly go stale.
Automated daily refresh is what makes the displayed prices trustworthy and is essential to the
feature's purpose. Paired with Story 1 it forms the minimum viable product.

**Independent Test**: Trigger the scheduled collection (on its schedule or manually) and confirm that
afterward the stored metals records reflect the values currently published by the source, with an
updated fetch time, and that exactly one record exists per (metal, rate group, weight) combination
(no duplicates after repeated runs).

**Acceptance Scenarios**:

1. **Given** it is the scheduled time, **When** the automated collection runs, **Then** the latest
   published gold and silver quotes are retrieved from the metals endpoint, converted to the system's
   stored form, and saved.
2. **Given** a quote for a (metal, rate group, weight) combination already exists from a previous run,
   **When** the collection runs again with new values, **Then** the existing record is updated in place
   rather than duplicated (one record per combination).
3. **Given** an operator wants an off-schedule refresh, **When** they trigger the collection manually,
   **Then** it runs the same way as the scheduled run and updates stored prices.
4. **Given** the schedule is not due, **When** no trigger occurs, **Then** the collection does not run.

---

### User Story 3 - Secure machine-to-machine ingest of collected prices (Priority: P2)

The automated collector submits the parsed metals prices to the application through a dedicated,
secured channel intended for system-to-system use, so that pricing can be stored without exposing
database credentials to the collector and without using an end-user login.

**Why this priority**: This is the integration boundary that makes the daily refresh possible while
keeping credentials and trust properly scoped. It is essential to the architecture but sits behind the
user-visible Stories 1 and 2, so it is P2.

**Independent Test**: Submit a batch of parsed metals quotes to the ingest channel with a valid shared
secret and confirm they are stored; submit with a missing or wrong secret and confirm the request is
rejected and nothing is stored.

**Acceptance Scenarios**:

1. **Given** a valid shared secret is presented, **When** a batch of parsed metals quotes is submitted,
   **Then** the system validates and stores them and reports how many were accepted.
2. **Given** the shared secret is missing or incorrect, **When** a submission is attempted, **Then** the
   system rejects it and stores nothing.
3. **Given** a submitted batch contains a record that fails validation (e.g., missing metal, negative
   rate, unparseable price), **When** the batch is processed, **Then** the system rejects the invalid
   record and does not store malformed data.

---

### User Story 4 - Metals investment options show the exact current price (Priority: P1)

A user runs the existing investment search that returns five investment options. When one of those
options is a precious metal (gold or silver), the option carries the exact current PrivatBank price
for that metal rather than a model-estimated number or range, so the user sees a real, verifiable
acquisition price.

**Why this priority**: This is the user-facing payoff of collecting and persisting the prices. The
existing generator can otherwise only guess a metals price; grounding it in the stored exact value is
the reason the prices are collected and is essential to the feature's value.

**Independent Test**: With a current metals price stored, run a search whose results include a
precious-metals option and confirm the option's price equals the stored exact value (sale rate, per
gram, primary group, smallest weight tier) and is not a model-produced estimate. With no stored price
for that metal, confirm the metals option is not presented.

**Acceptance Scenarios**:

1. **Given** a current sale rate for gold is stored and the existing generator would present a
   precious-metals (gold) option, **When** the search completes, **Then** that option's price equals
   the stored exact gold value (per gram, primary group, smallest weight tier) and no model-estimated
   price is used.
2. **Given** no current stored price exists for a metal, **When** the generator would present an option
   for that metal, **Then** the option is dropped server-side and not shown to the user.
3. **Given** an option is not a precious metal, **When** the search completes, **Then** the metals
   price backfill does not alter that option.

---

### Edge Cases

- **Source unavailable**: When the collector cannot obtain quotes (source down, network error,
  non-success response), the run fails loudly (visible failure for the operator) and the previously
  stored prices remain unchanged rather than being wiped.
- **Source reports failure status**: When the endpoint returns a payload whose status flag is not
  success, the collector treats the run as a failure and does not overwrite existing good data.
- **Empty or partial source response**: If the source returns zero quotes or a clearly truncated set,
  the collector does not overwrite existing good data with nothing; the run is treated as a failure.
  Because a record-count floor alone would not catch "one metal entirely absent" (e.g., silver present
  in normal volume but gold missing), the partial-failure guard MUST require that both metals are
  present before the batch is accepted.
- **Divergent per-metal dates**: Each metal carries its own source quotation date, and the two dates
  may differ. Each record stores the quotation date of its own metal; a difference between gold's and
  silver's dates is normal and is not by itself a failure.
- **Per-weight gaps within a present metal**: If an individual weight entry for an otherwise-present
  metal is missing or has an unparseable rate, that single record is dropped and counted as rejected
  (per FR-010); it does not fail the whole run, provided both metals are otherwise present.
- **Backend asleep at run time**: In production the backend may be scaled to zero; the first ingest
  request must wake it, tolerating the initial cold-start delay before submission succeeds.
- **Price formatting**: Source prices arrive as strings that may contain a space as a thousands
  separator and a decimal point (e.g., "6 780.00", "100.75"). These must be parsed to integer minor
  units exactly (e.g., "6 780.00" -> 678000, "100.75" -> 10075) with no floating-point drift.
- **Varying weights per group**: Different rate groups expose different sets of weights (e.g., gold
  group "one" starts at 1 gram while silver starts at 10 grams; some groups add 250/500/1000). The
  collector stores whatever weights the source provides for each group without assuming a fixed set.
- **Date format**: The source quotation date arrives as a day-first string (e.g., "08.06.2026") and
  must be interpreted as day.month.year.
- **Stale fetch time**: Each stored record carries the time it was fetched so administrators can judge
  freshness even if a daily run was missed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST automatically collect PrivatBank gold and silver quotes from the public
  metals endpoint (`GET https://privatbank.ua/pb/ajax/bank-metall-courses`) once per day at
  approximately 10:00 Kyiv time.
- **FR-002**: The automated collection MUST also be triggerable manually (off-schedule) and behave
  identically to a scheduled run.
- **FR-003**: The collection MUST obtain quotes via a single public GET request to the metals endpoint;
  no anti-bot handshake, authentication, or session is required for the source.
- **FR-004**: For each quote the system MUST capture: metal (gold or silver), rate group, weight,
  purchase rate, sale rate, and the source quotation date. The metal MUST be one of exactly two
  recognized values (gold, silver); any other metal value is invalid. The purchase/sale rate semantics
  MUST be preserved verbatim from the source (the source's purchaseRate and saleRate are stored as
  purchase rate and sale rate respectively, with no reinterpretation of buy/sell direction).
- **FR-004a**: The weight MUST be captured from the source verbatim as a stable identifier token
  (preserving fractional weights such as 2.5 exactly) and used as-is in the record's unique key, so the
  same source weight always resolves to the same key across runs (e.g., the gram size "2.5" never
  fragments into distinct records).
- **FR-005**: The system MUST store monetary rates as integer minor units (kopiykas), parsing source
  price strings exactly with no floating-point drift (e.g., "6 780.00" -> 678000, "100.75" -> 10075).
  Parsing MUST tolerate any whitespace used as a thousands separator within the price string -
  including non-ASCII whitespace such as a non-breaking or thin space - and MUST treat a price string
  that cannot be parsed to a non-negative two-decimal value as invalid (see FR-010).
- **FR-006**: The system MUST record, for each stored quote, the time at which its data was fetched.
- **FR-007**: The system MUST persist metals prices in durable storage and MUST keep exactly one record
  per (metal, rate group, weight) combination, updating the existing record when newer data arrives.
  The combination MUST be realized as a deterministic composite key so that repeated runs update in
  place rather than create duplicates.
- **FR-008**: The collector MUST submit parsed quotes to the application through a dedicated
  machine-to-machine ingest channel secured by a shared secret, distinct from end-user login. This
  channel and its shared secret MUST be distinct from those used by any other feed (e.g., the military
  bond ingest channel and secret are not reused); the metals feed has its own ingest endpoint and its
  own secret.
- **FR-009**: The ingest channel MUST reject submissions that present a missing or incorrect shared
  secret, storing nothing in that case.
- **FR-010**: The ingest channel MUST validate each submitted record individually and MUST reject
  malformed records (e.g., missing or unrecognized metal, missing weight, missing rate group, negative
  or unparseable rate) without storing them, while accepting and storing the valid records in the same
  batch and reporting the count accepted and the count rejected (per-record drop-and-count, not
  whole-batch rejection).
- **FR-011**: Database credentials MUST remain solely within the application backend and MUST NOT be
  exposed to the collector.
- **FR-012**: The system MUST expose the stored metals prices for reading to administrators only, using
  the same access restriction as the existing providers administration capability.
- **FR-013**: Non-administrator and unauthenticated requests for metals prices MUST be denied.
- **FR-014**: The read capability MUST serve from stored data and MUST NOT trigger live collection from
  the source.
- **FR-014a**: The stored metals prices MUST be available for internal, server-side consumption by the
  existing investment-options generator, reading from the same durable storage. End users receive only
  options derived from the prices; the raw stored prices MUST NOT be exposed through a user-facing
  (non-administrator) endpoint. The administrator-only direct/management read (FR-012, FR-013) is the
  only externally exposed read of raw prices.
- **FR-018**: When the existing investment-options generator would present an option of category
  PRECIOUS_METALS, the system MUST set that option's price to the exact current stored value for the
  corresponding metal, backfilled server-side, and MUST NOT use a model-estimated price, range, or
  assumption for it. The exact value MUST be the bank sale rate (the user's acquisition price),
  expressed in integer minor units (kopiykas) per gram, taken from the primary retail rate group at the
  smallest available weight tier for that metal.
- **FR-019**: When the generator would present a PRECIOUS_METALS option but no current stored price
  exists for that metal, the system MUST drop the option server-side (it is not shown to the user),
  consistent with the existing rule that rejects options which cannot be grounded server-side; it MUST
  NOT substitute an estimated price and MUST NOT fail the whole search.
- **FR-020**: The metals-price backfill MUST apply only to options of category PRECIOUS_METALS and MUST
  NOT alter any other option.
- **FR-015**: When collection fails, reports a non-success status, or returns no usable data (including
  a response missing either metal entirely), the system MUST treat the run as a failure, MUST preserve
  previously stored prices unchanged, and MUST surface the failure to operators.
- **FR-016**: The collection MUST run on dedicated general-purpose compute and MUST NOT run on the
  resource-constrained application backend nor as an in-process scheduled job within it.
- **FR-017**: The same collection process MUST be runnable against a local development environment and
  against the production environment, including waking a backend that may be scaled to zero.

### Key Entities *(include if feature involves data)*

- **Metal Price Record**: Represents the latest known quote for one (metal, rate group, weight)
  combination. Key attributes: metal (exactly gold or silver), rate group (the source's product/rate
  category, e.g., the "one"/"two"/"three" groupings, treated as an open opaque set), weight (the
  source's gram size captured verbatim as a stable token, e.g., 1, 2.5, 10, 1000), purchase rate
  (integer minor units), sale rate (integer minor units), source quotation date (that metal's own
  date), fetched-at time. Uniquely identified by the deterministic (metal, rate group, weight)
  composite key; one record per combination.
- **Ingest Batch**: A set of parsed metal price records submitted together by the collector in a single
  machine-to-machine request, authenticated by a shared secret. Defines the unit of validation and
  reporting (count accepted/rejected); not necessarily persisted as its own entity.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a normal day, the metals prices an administrator sees reflect quotes published by the
  source that same day (fetch time is the current day).
- **SC-002**: After any number of repeated collection runs, there is exactly one stored record per
  (metal, rate group, weight) combination (zero duplicates).
- **SC-003**: 100% of stored rates match the source's published values converted to minor units with no
  rounding error (verified on a sample of records, including a thousands-separator value such as
  "6 780.00" and a small value such as "100.75").
- **SC-004**: 100% of metals-price read requests from non-administrators are denied; 100% from
  administrators succeed.
- **SC-005**: 100% of ingest attempts without a valid shared secret are rejected and result in no
  stored data change.
- **SC-006**: A failed or empty collection run never reduces or blanks the previously stored set of
  prices (count of stored quotes does not drop to zero due to a failed run).
- **SC-007**: An operator can complete a manual refresh and observe updated prices end-to-end without
  manual database access.
- **SC-008**: The collection workload imposes no memory or runtime load on the application backend (the
  backend performs only lightweight ingest and read handling).
- **SC-009**: 100% of presented precious-metals options carry the exact stored price (sale rate, per
  gram, primary group, smallest weight tier) for their metal; 0% carry a model-estimated metals price.
- **SC-010**: When no current stored price exists for a metal, 100% of would-be options for that metal
  are dropped (none shown with an estimated or blank price), and the rest of the search still returns.

## Assumptions

- **Cadence and DST**: "Once per day at ~10:00 Kyiv" is implemented on a fixed schedule in a single
  reference time zone, so the local Kyiv time drifts by one hour across daylight-saving transitions;
  this drift is acceptable and not treated as a defect. Unlike the bond refresh, the run is daily
  (every day), not business-day only, since metals quotes are published daily.
- **Rate group semantics**: The source groups rates under opaque keys ("one", "two", "three") that
  correspond to PrivatBank's own product/rate categories. The group set is treated as open and opaque
  (not assumed to be exactly those three); whatever groups the source provides are stored. Their
  business meaning is preserved verbatim as a rate-group identifier; interpreting or relabeling them is
  a presentation concern for the reading client and out of scope for collection and storage.
- **Quotation-date freshness**: Freshness is judged by the fetched-at time (SC-001), which reflects
  when the data was collected. The source's quotation date is stored as-is and is not independently
  validated for staleness (e.g., on a holiday the source may publish the prior day's quote); a stale
  quotation date with a current fetch time is acceptable and not a defect in v1.
- **No headless browser needed**: Because the metals endpoint is a plain public GET with no access
  handshake (FR-003), collection needs only a simple HTTP request; the heavier browser-automation
  approach used for the bond feed is not required here.
- **Currency**: All metals rates are quoted by the source in Ukrainian hryvnia (UAH) per gram and are
  stored as UAH minor units (kopiykas). No multi-currency handling is required for this feed.
- **Stored scope**: All metals (gold and silver), all rate groups, and all weights present in the
  source response are stored; no subset filtering happens at collection time.
- **Access model reuse**: Administrator-only direct/management read access reuses the existing
  role-based gating already applied to the providers administration capability; no new role model is
  introduced. End users never read raw prices directly; they only see precious-metals investment
  options whose price was grounded server-side.
- **Guidance consumer is existing**: The consumer is the already-existing investment-options generator
  (the LLM-backed search that returns five options with server-side catalog grounding); feature 011
  does not build new recommendation logic, only grounds the price of PRECIOUS_METALS options in the
  stored exact value, mirroring the existing server-side backfill of provider identity fields.
- **Canonical option price**: The single exact value injected into a metals option is the bank sale
  rate (acquisition price) in kopiykas per gram, from the primary retail rate group ("one") at the
  smallest available weight tier for that metal (gold's 1-gram tier, silver's smallest offered tier) -
  the headline single-unit retail price. The full set of rate groups and weights is still stored
  (Stored scope above) even though only this one value feeds an option in v1.
- **Shared secret management**: A single shared secret is provisioned out of band for the collector and
  the backend; secret rotation tooling is out of scope for v1. The same machine-to-machine ingest
  pattern established by the military bond refresh feature is reused.
- **Retention**: Only the latest quote per combination is retained (no historical price time series in
  v1); each run overwrites the prior value for a combination.
- **Single source**: PrivatBank's public metals endpoint is the sole price source for v1; no
  cross-source reconciliation.

## Out of Scope (Non-Goals)

- Trading or order execution of any kind.
- Real-time or intraday streaming price updates.
- Historical price time-series storage or charting.
- Relabeling or interpreting the source's rate-group keys into human-friendly product names.
- Multi-currency conversion of metals rates.
- Secret rotation automation and multi-secret management.
- Changing how the existing generator selects options or decides whether to include a metal; feature
  011 only grounds the price of metals options that the existing logic already chooses to present.
- Surfacing more than the single canonical price per metal in an option (e.g., per-weight tiers,
  purchase/sell-back spread) - out of scope for v1.

## Dependencies

- Availability of PrivatBank's public metals endpoint (`GET https://privatbank.ua/pb/ajax/bank-metall-courses`).
- Existing administrator role and authorization mechanism (as used by the providers capability).
- The existing investment-options generator (the LLM-backed search with server-side catalog grounding)
  and its PRECIOUS_METALS option category, into which the exact metals price is backfilled.
- Durable application storage for the metals price records.
- A dedicated general-purpose compute environment for running the collection, separate from the
  application backend.
- The machine-to-machine ingest channel and shared-secret pattern established by the military bond
  refresh feature.
- Network reachability from the collector to the production backend, including the ability to wake a
  backend that is scaled to zero.
