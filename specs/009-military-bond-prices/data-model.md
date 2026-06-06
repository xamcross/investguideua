# Phase 1 Data Model: Military Bond Price Refresh

## Entity: BondPrice (MongoDB collection `bondPrices`)

The latest known quote for one bond instrument. One document per instrument, keyed by ISIN.
All monetary fields are **integer minor units** of the document's own `currency` (kopiykas for UAH,
cents for USD/EUR) per Constitution IV. Prices are quoted **per 1000 face value** and stored as the
quote in minor units (no rescaling).

| Field            | Type (Java)      | Mongo            | Required | Notes / Validation |
|------------------|------------------|------------------|----------|--------------------|
| `isin`           | `String`         | `_id`            | yes      | Document identity; unique. 12-char ISIN. Upsert key. |
| `military`       | `boolean`        | `military`       | yes      | From the source `military` flag. |
| `currency`       | `String`         | `currency`       | yes      | One of `UAH`, `USD`, `EUR`. |
| `maturity`       | `LocalDate`      | `maturity`       | yes      | Bond maturity date. |
| `quotationDate`  | `LocalDate`      | `quotationDate`  | yes      | Date the source quoted these prices. |
| `sellPriceMinor` | `long`           | `sellPriceMinor` | yes      | Sell price, integer minor units, `>= 0`. |
| `buyPriceMinor`  | `long`           | `buyPriceMinor`  | yes      | Buy price, integer minor units, `>= 0`. |
| `sellYield`      | `double`         | `sellYield`      | yes      | Annual yield %, reference (not money). |
| `buyYield`       | `double`         | `buyYield`       | yes      | Annual yield %, reference (not money). |
| `fetchedAt`      | `Instant`        | `fetchedAt`      | yes      | When the scraper fetched this record (set by backend at ingest). |

### Indexes

- Uniqueness on `isin` is **implicit**: it is the document `_id`, and the `_id` index always exists.
  No `@Indexed(unique = true)` is needed or wanted on the id (that annotation/`auto-index-creation`
  only matters for *secondary* indexes). Upsert-by-ISIN guarantees one record per instrument (SC-002).
- Optional secondary `@Indexed` on `military` if later filtering server-side; not required for v1
  (the read endpoint returns all rows and the client filters — see spec Assumptions). Only this
  secondary index would rely on `auto-index-creation: true` (already enabled application-wide).

### Lifecycle / state transitions

- **Insert**: first time an ISIN is ingested -> new document, `fetchedAt` = the batch timestamp.
- **Update (upsert)**: subsequent ingest of the same ISIN -> all quote fields and `fetchedAt`
  overwritten in place. No history kept (latest-quote-only, spec Assumptions).
- **`fetchedAt` is a single `Instant.now()` captured once per ingest batch** and applied to every
  record in that batch, so a batch reads as one coherent "as of" time (not smeared across records).
- **Never deleted by ingest**: an instrument absent from a later batch keeps its last-known document
  (preserve-on-partial, R7 / FR-015). There is no delete path in v1.

## Transient: IngestBondRequest (one element of the ingest batch)

Wire shape the scraper POSTs (validated; not persisted as-is). Mirrors `BondPrice` minus
`fetchedAt` (the backend stamps `fetchedAt` at ingest, not the client).

**All fields are boxed object types (not primitives) so an omitted JSON field is a rejectable
`null`, never a silent `false`/`0`/`0.0`.** This matters for financial integrity (Principle IV): an
omitted price must be rejected, not stored as 0.

| Field            | Type      | Validation |
|------------------|-----------|------------|
| `isin`           | `String`  | `@NotBlank`; trimmed; expected 12 chars. |
| `military`       | `Boolean` | `@NotNull` (boxed so absence -> rejection, not silent `false`). |
| `currency`       | `String`  | `@NotBlank` + `@Pattern("UAH|USD|EUR")` (independent backend enforcement, defense in depth). |
| `maturity`       | `String`  | `@NotBlank`; ISO `yyyy-MM-dd`; parsed in the per-record loop (a bad date -> that record rejected). |
| `quotationDate`  | `String`  | `@NotBlank`; ISO `yyyy-MM-dd`; parsed in the per-record loop. |
| `sellPriceMinor` | `Long`    | `@NotNull` + `@Min(0)` (boxed so omission -> rejection, not silent 0). |
| `buyPriceMinor`  | `Long`    | `@NotNull` + `@Min(0)`. |
| `sellYield`      | `Double`  | `@NotNull`; finiteness guaranteed by Jackson (rejects `NaN`/`Infinity` tokens by default). |
| `buyYield`       | `Double`  | `@NotNull`; finiteness as above. |

**Validation mechanism (important):** the request body is a JSON array of these objects. Spring's
`@Valid` does **not** cascade to elements of a `List<T>` body, and a Bean Validation failure would
otherwise 400 the *whole* request — which contradicts the per-record drop semantics (FR-010).
Therefore `BondPriceService` validates each element **programmatically** (inject
`jakarta.validation.Validator`, loop, collect violations, skip invalid records, upsert valid ones)
and returns `{accepted, rejected}`. The DTO annotations above drive that manual loop, not `@Valid`.

`fail-on-unknown-properties: true` (already configured) rejects unexpected fields at **parse time**,
which 400s the *entire* batch before the per-record loop runs (it is not a per-record `rejected`).
Likewise a body that is not a JSON array, or an **empty** array, is a `400` (empty-batch guard, R7).

## Transient: IngestResult (ingest response)

| Field      | Type | Notes |
|------------|------|-------|
| `accepted` | int  | Number of bonds upserted. |
| `rejected` | int  | Number of bonds dropped for validation failure. |

## Read DTO: BondPriceResponse

Returned by `GET /api/v1/bond-prices` (array). Same fields as `BondPrice` including `isin`,
`military`, `currency`, `maturity`, `quotationDate`, `sellPriceMinor`, `buyPriceMinor`, `sellYield`,
`buyYield`, `fetchedAt`. Prices remain integer minor units; any major-unit formatting is the client's
concern.

## Relationships

`BondPrice` is standalone — no foreign keys to `Provider` or other collections in v1. (A future
linkage from a military-bond provider row to live prices is out of scope.)
