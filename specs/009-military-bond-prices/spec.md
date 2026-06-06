# Feature Specification: Military Bond Price Refresh

**Feature Branch**: `009-military-bond-prices`  
**Created**: 2026-06-06  
**Status**: Draft  
**Input**: User description: "Ukrainian military bond price refresh for InvestGuideUA. Surface up-to-date Ukrainian military bond (military OVDP) prices inside the app, sourced from PrivatBank's public Privat24 bonds endpoint, refreshed automatically once per business day."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administrator views current military bond prices (Priority: P1)

An administrator of InvestGuideUA opens the application and consults an up-to-date list of Ukrainian
military bond quotes (sell/buy prices and yields per instrument), so they can see current market
pricing without manually visiting PrivatBank's site.

**Why this priority**: This is the core value of the feature - making fresh bond pricing visible
inside the product. Without a way to read the prices, the entire collection and storage pipeline
delivers no observable value. It is the smallest slice that demonstrates end-to-end worth once data
exists.

**Independent Test**: With at least one bond price record present in storage, an authenticated
administrator requests the bond price list and receives the current set of bonds with their prices,
yields, currency, maturity, and the time the data was fetched. A non-administrator is denied access.

**Acceptance Scenarios**:

1. **Given** bond price records exist and the user is authenticated as an administrator, **When** the
   user requests the bond price list, **Then** the system returns all stored bonds with ISIN, military
   flag, currency, maturity, quotation date, sell/buy prices, sell/buy yields, and fetch time.
2. **Given** the user is authenticated but is not an administrator, **When** the user requests the bond
   price list, **Then** the system denies access with an authorization error.
3. **Given** no bond price records exist yet, **When** an administrator requests the bond price list,
   **Then** the system returns an empty list (not an error).

---

### User Story 2 - Automated daily refresh keeps prices current (Priority: P1)

The system automatically collects the latest military bond quotes from PrivatBank's public bonds data
once every business day, so administrators always see recent pricing without any manual action.

**Why this priority**: Freshness is the whole point - a one-time manual load would quickly go stale.
Automated daily refresh is what makes the displayed prices trustworthy and is essential to the
feature's purpose. Paired with Story 1 it forms the minimum viable product.

**Independent Test**: Trigger the scheduled collection (on its schedule or manually) and confirm that
afterward the stored bond records reflect the values currently published by the source, with an
updated fetch time, and that exactly one record exists per instrument (no duplicates after repeated
runs).

**Acceptance Scenarios**:

1. **Given** it is a business day at the scheduled time, **When** the automated collection runs, **Then**
   the latest published bond quotes are retrieved, converted to the system's stored form, and saved.
2. **Given** a bond already exists in storage from a previous run, **When** the collection runs again
   with new values for that bond, **Then** the existing record is updated in place rather than
   duplicated (one record per instrument).
3. **Given** an operator wants an off-schedule refresh, **When** they trigger the collection manually,
   **Then** it runs the same way as the scheduled run and updates stored prices.
4. **Given** it is a weekend or the schedule is not due, **When** no trigger occurs, **Then** the
   collection does not run.

---

### User Story 3 - Secure machine-to-machine ingest of collected prices (Priority: P2)

The automated collector submits the parsed bond prices to the application through a dedicated,
secured channel intended for system-to-system use, so that pricing can be stored without exposing
database credentials to the collector and without using an end-user login.

**Why this priority**: This is the integration boundary that makes the daily refresh possible while
keeping credentials and trust properly scoped. It is essential to the architecture but sits behind
the user-visible Stories 1 and 2, so it is P2.

**Independent Test**: Submit a batch of parsed bonds to the ingest channel with a valid shared
secret and confirm they are stored; submit with a missing or wrong secret and confirm the request is
rejected and nothing is stored.

**Acceptance Scenarios**:

1. **Given** a valid shared secret is presented, **When** a batch of parsed bonds is submitted, **Then**
   the system validates and stores them and reports how many were accepted.
2. **Given** the shared secret is missing or incorrect, **When** a submission is attempted, **Then** the
   system rejects it and stores nothing.
3. **Given** a submitted batch contains a record that fails validation (e.g., missing ISIN or a
   negative price), **When** the batch is processed, **Then** the system rejects the invalid record and
   does not store malformed data.

---

### Edge Cases

- **Source unavailable / anti-bot handshake fails**: When the collector cannot obtain quotes (source
  down, handshake fails, no data intercepted), the run fails loudly (visible failure for the
  operator) and the previously stored prices remain unchanged rather than being wiped.
- **Empty or partial source response**: If the source returns zero bonds or a clearly truncated set,
  the collector does not overwrite existing good data with nothing; the run is treated as a failure.
- **Backend asleep at run time**: In production the backend may be scaled to zero; the first ingest
  request must wake it, tolerating the initial cold-start delay before submission succeeds.
- **Price precision**: Prices arriving as decimal major units must convert to integer minor units
  exactly (e.g., 1076.58 -> 107658) with no floating-point drift; fractional sub-minor values must be
  handled deterministically.
- **Currency variety**: Bonds may be quoted in UAH, USD, or EUR; each is stored with its own currency
  and prices are still expressed in that currency's minor units.
- **Non-military bonds in the feed**: The source feed includes both military and non-military bonds;
  each record carries a military flag and is stored with it. (Scope of what is displayed is governed
  by Assumptions.)
- **Duplicate ISIN within one batch**: If the same instrument appears twice in a single batch, the
  system resolves to a single stored record deterministically.
- **Stale fetch time**: Each stored record carries the time it was fetched so administrators can judge
  freshness even if a daily run was missed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST automatically collect Ukrainian military bond quotes from PrivatBank's
  public Privat24 bonds data once per business day (Monday-Friday) at approximately 10:00 Kyiv time.
- **FR-002**: The automated collection MUST also be triggerable manually (off-schedule) and behave
  identically to a scheduled run.
- **FR-003**: The collection MUST obtain quotes by allowing the source's own published page logic to
  complete its required access handshake and reading the resulting bond data, rather than by
  reconstructing that handshake independently.
- **FR-004**: For each bond the system MUST capture: ISIN, military flag, currency, maturity date,
  quotation date, sell price, buy price, sell yield, buy yield.
- **FR-005**: The system MUST store monetary prices as integer minor units (kopiykas), converting
  source decimal prices exactly (e.g., 1076.58 -> 107658), and MUST store yields as decimal numbers.
- **FR-006**: The system MUST record, for each stored bond, the time at which its data was fetched.
- **FR-007**: The system MUST persist bond prices in durable storage and MUST keep exactly one record
  per instrument, updating the existing record when newer data arrives (keyed by ISIN).
- **FR-008**: The collector MUST submit parsed bonds to the application through a dedicated
  machine-to-machine ingest channel secured by a shared secret, distinct from end-user login.
- **FR-009**: The ingest channel MUST reject submissions that present a missing or incorrect shared
  secret, storing nothing in that case.
- **FR-010**: The ingest channel MUST validate each submitted record and MUST reject malformed records
  (e.g., missing ISIN, negative price) without storing them.
- **FR-011**: Database credentials MUST remain solely within the application backend and MUST NOT be
  exposed to the collector.
- **FR-012**: The system MUST expose the stored bond prices for reading to administrators only, using
  the same access restriction as the existing providers administration capability.
- **FR-013**: Non-administrator and unauthenticated requests for bond prices MUST be denied.
- **FR-014**: The read capability MUST serve from stored data and MUST NOT trigger live collection from
  the source.
- **FR-015**: When collection fails or returns no usable data, the system MUST preserve previously
  stored prices unchanged and MUST surface the failure to operators.
- **FR-016**: The collection MUST run on dedicated general-purpose compute and MUST NOT run on the
  resource-constrained application backend nor as an in-process scheduled job within it.
- **FR-017**: The same collection process MUST be runnable against a local development environment and
  against the production environment, including waking a backend that may be scaled to zero.

### Key Entities *(include if feature involves data)*

- **Bond Price Record**: Represents the latest known quote for one bond instrument. Key attributes:
  ISIN (unique identifier), military flag, currency (UAH/USD/EUR), maturity date, quotation date, sell
  price (integer minor units), buy price (integer minor units), sell yield (decimal), buy yield
  (decimal), fetched-at time. Uniquely identified by ISIN; one record per instrument.
- **Ingest Batch**: A set of parsed bond price records submitted together by the collector in a single
  machine-to-machine request, authenticated by a shared secret. Defines the unit of validation and
  reporting (count accepted/rejected); not necessarily persisted as its own entity.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a normal business day, the bond prices an administrator sees reflect quotes published
  by the source that same day (fetch time is the current business day).
- **SC-002**: After any number of repeated collection runs, there is exactly one stored record per
  bond instrument (zero duplicate ISINs).
- **SC-003**: 100% of stored prices match the source's published values converted to minor units with
  no rounding error (verified on a sample of records).
- **SC-004**: 100% of bond-price read requests from non-administrators are denied; 100% from
  administrators succeed.
- **SC-005**: 100% of ingest attempts without a valid shared secret are rejected and result in no
  stored data change.
- **SC-006**: A failed or empty collection run never reduces or blanks the previously stored set of
  prices (count of stored bonds does not drop to zero due to a failed run).
- **SC-007**: An operator can complete a manual refresh and observe updated prices end-to-end without
  manual database access.
- **SC-008**: The collection workload imposes no memory or runtime load on the application backend
  (the backend performs only lightweight ingest and read handling).

## Assumptions

- **Display scope**: The primary consumer of this data is military OVDP pricing for administrators;
  the feed's non-military bonds are stored with their flag, and any filtering to military-only for
  display is a presentation concern handled by the reading client. The stored dataset may include both.
- **Cadence and DST**: "Once per business day at ~10:00 Kyiv" is implemented on a fixed schedule in a
  single reference time zone, so the local Kyiv time drifts by one hour across daylight-saving
  transitions; this drift is acceptable and not treated as a defect.
- **Source shape**: PrivatBank's public bonds data continues to expose, per record, a military flag,
  sell/buy prices quoted per 1000 face value, sell/buy yields, ISIN, maturity, currency, and quotation
  date. A material change to this shape is a maintenance event outside this feature's scope.
- **Access model reuse**: Administrator-only read access reuses the existing role-based gating already
  applied to the providers administration capability; no new role model is introduced.
- **Shared secret management**: A single shared secret is provisioned out of band for the collector
  and the backend; secret rotation tooling is out of scope for v1.
- **Coupons**: Although the source exposes coupon schedules per bond, coupons are not persisted in v1
  (only the listed attributes are stored).
- **Retention**: Only the latest quote per instrument is retained (no historical price time series in
  v1); each run overwrites the prior value for an instrument.
- **Single source**: PrivatBank's Privat24 public bonds data is the sole price source for v1; no
  cross-source reconciliation.

## Out of Scope (Non-Goals)

- Trading or order execution of any kind.
- Real-time or intraday streaming price updates.
- Reconstructing the source's access handshake independently of its published page logic.
- Historical price time-series storage or charting.
- Coupon schedule persistence and analytics.
- Secret rotation automation and multi-secret management.

## Dependencies

- Availability of PrivatBank's public Privat24 bonds data and the continued ability of its published
  page logic to complete its access handshake.
- Existing administrator role and authorization mechanism (as used by the providers capability).
- Durable application storage for the bond price records.
- A dedicated general-purpose compute environment for running the collection, separate from the
  application backend.
- Network reachability from the collector to the production backend, including the ability to wake a
  backend that is scaled to zero.
