# ОВДП Data Pages (feature 015) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Public ОВДП data pages — live yields table (`/ovdp/rates`), calculator (`/ovdp/calculator`), deposit comparison (`/ovdp/vs-deposit`) — fed by a new public bond-prices endpoint and a build-time snapshot, with full SEO parity (prerender, canonical-200, JSON-LD, OG, audit gates).

**Architecture:** Backend exposes `GET /api/v1/bond-prices/latest` (the `bondPrices` collection already holds exactly one doc per ISIN, so latest = `findAll()`), public + 1h cache header. A build script emits `bond-snapshot.data.ts` (articles.data.ts pattern) from the API with a committed JSON fallback, so prerendered pages always show data; components hydrate from the API browser-side only. Three standalone OnPush components under `features/ovdp/`; SEO via the existing static-page pipeline (PUBLIC_PAGES + STATIC_PAGES + i18n keys), JSON-LD via a new generic page-blocks method on StructuredDataService, per-page OG images, new audit block A30.

**Tech Stack:** Java 21 / Spring Boot 3.4.1 / Mongo (mocked in tests), Angular 17 standalone, plain-Node ESM build scripts, Karma/Jasmine via Edge.

## Global Constraints

- Branch `015-ovdp-data-pages` (based on `014-seo-content`).
- Money integer minor units everywhere (kopiykas; bond prices are **per 1000 face value**); yields follow the repo convention: reference percentages as doubles (NOT basis points — spec corrected).
- Static-page titles in i18n are FULL titles ≤60 chars incl. ` - InvestGuideUA`; descriptions 110–160, globally unique (audit A2/A3).
- The three routes: NO canActivate guards; must be in BOTH `PUBLIC_PAGES` (seo-routes.config.ts) and `STATIC_PAGES` (build-articles.mjs) plus app.routes.ts + uk.json + en.json (build fails if keys missing).
- Prerendered pages must NOT fetch: components render from the bundled snapshot; browser-only hydration guarded by `isPlatformBrowser`, HTTP via `skipGlobalError()`.
- Month-stamp deviation from spec FR-5: titles use «сьогодні» instead of a build month (static i18n can't interpolate build time); the visible «станом на <date>» stamp comes from the snapshot's `fetchedAt` and is audit-asserted (A30).
- Honesty rule: no fabricated prices. If real data can't be sourced at build time, the committed fallback is an EMPTY snapshot and pages render an honest "дані тимчасово недоступні" empty-state (still >4000 bytes via prose; A30 accepts `data-ovdp-asof` OR `data-ovdp-empty`).
- All Karma runs: `CHROME_BIN="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"`. Backend: `mvn -q -B test` in `backend/`.
- Commit per task with the standard trailer.

---

### Task 1: Backend — public `GET /api/v1/bond-prices/latest`

**Files:**
- Modify: `backend/src/main/java/com/investguide/bonds/BondPriceController.java`
- Modify: `backend/src/main/java/com/investguide/common/security/SecurityConfig.java:38-41` (PUBLIC_GET array)
- Test: `backend/src/test/java/com/investguide/bonds/BondPriceControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/bond-prices/latest` → `200 [ {isin, military, currency, maturity, quotationDate, sellPriceMinor, buyPriceMinor, sellYield, buyYield, fetchedAt} ]` (reuses `BondPriceResponse`), header `Cache-Control: max-age=3600, public`. Consumed by Task 2's snapshot script and Task 3's hydration service.

- [ ] **Step 1: Failing tests** in `BondPriceControllerTest` (copy the class's existing idiom):

```java
@Test
void latestIsPublicForAnonymous() throws Exception {
    when(repository.findAll()).thenReturn(List.of(sample())); // sample() = existing fixture helper or inline new BondPrice(...)
    mockMvc.perform(get("/api/v1/bond-prices/latest"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
            .andExpect(jsonPath("$[0].isin").value("UA4000204150"))
            .andExpect(jsonPath("$[0].sellPriceMinor").isNumber());
}

@Test
void latestReturnsEmptyArrayWhenNoData() throws Exception {
    when(repository.findAll()).thenReturn(List.of());
    mockMvc.perform(get("/api/v1/bond-prices/latest"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
}
```

- [ ] **Step 2:** `mvn -q -B test` → the two new tests FAIL (401 for anonymous).
- [ ] **Step 3: Implement** — in `BondPriceController` add:

```java
/**
 * Public latest-per-ISIN snapshot (feature 015). The collection stores exactly one document per
 * ISIN (ingest upserts), so findAll() IS the latest snapshot. Cacheable for an hour: ingest runs
 * on a slow external cadence and the SEO data pages hydrate from this endpoint.
 */
@GetMapping("/bond-prices/latest")
public ResponseEntity<List<BondPriceResponse>> latestBondPrices() {
    List<BondPriceResponse> body = repository.findAll().stream()
            .map(BondPriceResponse::from)
            .toList();
    return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
            .body(body);
}
```

In `SecurityConfig` add `"/api/v1/bond-prices/latest"` to `PUBLIC_GET` (evaluated before the exact-match ADMIN rule on `/api/v1/bond-prices`, so no collision).

- [ ] **Step 4:** `mvn -q -B test` → all green (existing admin 401/403 tests must still pass).
- [ ] **Step 5:** Commit `feat(bonds): public latest bond-prices endpoint for data pages (015)`.

---

### Task 2: Snapshot tooling — `bond-snapshot.data.ts`

**Files:**
- Create: `frontend/tools/seo/fetch-bond-snapshot.mjs`
- Create: `frontend/tools/seo/bond-snapshot.fallback.json` (committed; real data if obtainable via the local scraper `scraper/scrape-bonds.mjs` logic, else `{"fetchedAt": null, "bonds": []}`)
- Modify: `frontend/package.json` (script `seo:snapshot` = `node tools/seo/fetch-bond-snapshot.mjs`; `build` = `seo:snapshot && seo:articles && seo:og && ng build && seo:generate`)
- Modify: `frontend/.gitignore` (ignore `src/app/features/ovdp/bond-snapshot.data.ts`)

**Interfaces:**
- Produces: generated `frontend/src/app/features/ovdp/bond-snapshot.data.ts`:

```ts
export interface BondSnapshotEntry {
  readonly isin: string; readonly military: boolean; readonly currency: string;
  readonly maturity: string;            // ISO date
  readonly sellPriceMinor: number;      // minor units per 1000 face value
  readonly buyPriceMinor: number;
  readonly sellYield: number;           // percent, e.g. 15.25
  readonly buyYield: number;
}
export interface BondSnapshot { readonly fetchedAt: string | null; readonly bonds: readonly BondSnapshotEntry[]; }
export const BOND_SNAPSHOT: BondSnapshot = { ... };
```

- [ ] **Step 1:** Script behavior: `BOND_SNAPSHOT_API` env (default `https://api.investguideua.com/api/v1/bond-prices/latest`), 10s timeout fetch → on 200 with ≥1 bond: emit .data.ts AND overwrite the fallback JSON; on any failure: emit from the fallback JSON with a console note. Sort bonds by maturity asc. Always deterministic output (no Date.now — `fetchedAt` comes from the API payload/fallback).
- [ ] **Step 2:** Try to seed the fallback with real data (run the endpoint if deployed; else check `scraper/scrape-bonds.mjs` for a dry-run path). If neither works, ship the empty fallback per the honesty rule.
- [ ] **Step 3:** Run `npm run seo:snapshot` → .data.ts generated; commit tooling + fallback (not the generated file).
- [ ] **Step 4:** Commit `feat(seo): build-time bond snapshot tooling (015)`.

---

### Task 3: Math util + data services (TDD)

**Files:**
- Create: `frontend/src/app/features/ovdp/ovdp-math.ts` + `ovdp-math.spec.ts`
- Create: `frontend/src/app/features/ovdp/bond-snapshot.service.ts` (root-provided, returns `BOND_SNAPSHOT`, plus `latest()` browser hydration via HttpClient GET `${environment.apiBaseUrl}/bond-prices/latest` with `skipGlobalError()`; maps API → `BondSnapshotEntry[]`)

**Interfaces:**
- Produces (all integer kopiyka math, `Math.round` at the single final step of each product):

```ts
/** Gross interest for amountKop at ratePct over termMonths (simple interest). */
export function grossIncomeKop(amountKop: number, ratePct: number, termMonths: number): number;
/** Net deposit income after 18% PIT + 5% military levy (= x0.77). */
export function depositNetKop(grossKop: number): number;
/** OVDP net = gross (tax-exempt). */
export function ovdpNetKop(grossKop: number): number;
```

- [ ] **Step 1:** Failing spec: `grossIncomeKop(5_000_000, 16, 6) === 400_000` (50 000 грн, 16%, 6 міс → 4 000 грн); `depositNetKop(400_000) === 308_000`; `depositNetKop(1_600_000) === 1_232_000` (16% → 12.32% «на руки»); rounding case `grossIncomeKop(100_001, 15.5, 12)` is an integer.
- [ ] **Step 2:** Run → FAIL. **Step 3:** implement. **Step 4:** run → PASS.
- [ ] **Step 5:** Commit `feat(ovdp): integer bond/deposit math + snapshot service (015)`.

---

### Task 4: `/ovdp/rates` page

**Files:**
- Create: `frontend/src/app/features/ovdp/ovdp-rates.component.ts` + `ovdp-rates.component.spec.ts`
- Modify: `frontend/src/app/app.routes.ts` (3 unguarded lazy routes — add all three here in one edit, pointing at Task 4/5 components), `frontend/src/app/core/seo/seo-routes.config.ts` (+3 PUBLIC_PAGES entries with `ogImage`), `frontend/tools/seo/build-articles.mjs` STATIC_PAGES (+3), `frontend/public/i18n/uk.json` + `en.json` (`seo.ovdpRates/ovdpCalculator/ovdpVsDeposit` + `ovdp.*` body namespace)
- Modify: `frontend/src/app/core/seo/structured-data.service.ts` — add generic page-blocks API:

```ts
/** Write page-scoped JSON-LD blocks (Dataset, WebApplication, FAQPage...). Group 'page'. */
setPageBlocks(blocks: readonly Record<string, unknown>[]): void;  // write('page', blocks)
clearPageBlocks(): void;                                          // remove('page')
get organizationId(): string;                                     // expose this.orgId for publisher refs
```

(call `clearPageBlocks()` on nav-away via component `ngOnDestroy`, mirroring `clearArticle()` usage.)

**i18n titles/descs (uk; en mirrored in English):**
- `seo.ovdpRates`: title «Ставки ОВДП сьогодні: дохідність - InvestGuideUA» (48), desc «Актуальні ціни та дохідність військових облігацій за випусками: ISIN, строк погашення, ціна за 1000 грн номіналу. Дані оновлюються автоматично.» (~140)
- `seo.ovdpCalculator`: title «Калькулятор дохідності ОВДП - InvestGuideUA» (43), desc «Порахуйте дохід від військових облігацій: сума, строк і ставка випуску - скільки ви отримаєте на руки. Без податків, на реальних цінах випусків.» (~140)
- `seo.ovdpVsDeposit`: title «ОВДП чи депозит: калькулятор - InvestGuideUA» (44), desc «Порівняйте однакову суму в ОВДП і на депозиті: облігації не оподатковуються, з депозитних відсотків утримують 23% податків. Рахуємо чесно.» (~135)

**Page content (all three):** kicker + H1 + «станом на <date>» stamp (`data-ovdp-asof`, from snapshot `fetchedAt`, formatted uk-UA; if empty snapshot → `data-ovdp-empty` honest note), main block, 300–500 words of explainer prose (H2 sections, ≥1 question-styled), FAQ section (visible + FAQPage JSON-LD via `setPageBlocks`), links into the article cluster + between the three pages, disclaimer line (informational, not advice).

- [ ] **Step 1:** Failing spec (stub `BondSnapshotService` with 2 fake bonds): renders a `table` with 2 rows, the `data-ovdp-asof` stamp, and links to `/ovdp/calculator`; with empty stub renders `[data-ovdp-empty]`.
- [ ] **Step 2:** FAIL → **Step 3:** implement component (columns: ISIN, валюта, погашення, ціна за 1000 номіналу via `formatMinorUnits`, дохідність `{{sellYield}}%` literal-% convention; browser-only hydration in `ngOnInit` guarded by `isPlatformBrowser`, replacing snapshot signal on success) + Dataset JSON-LD (`@type: Dataset`, name/description from i18n, `creator: {@id: organizationId}`, `dateModified: fetchedAt`) + FAQPage block.
- [ ] **Step 4:** PASS → **Step 5:** Commit `feat(ovdp): live rates page (015)`.

---

### Task 5: Calculator + vs-deposit pages

**Files:**
- Create: `frontend/src/app/features/ovdp/ovdp-calculator.component.ts` + spec, `ovdp-vs-deposit.component.ts` + spec

**Behavior:**
- Calculator: inputs сума (грн, default 25 000), строк (міс., default 12), ставка — select from snapshot issues (label: ISIN + maturity + sellYield) or «своя ставка» manual input (works with empty snapshot); output via `grossIncomeKop`/`ovdpNetKop` + deposit comparison line via `depositNetKop`. All conversions via `toMinorUnits`/`formatMinorUnits`.
- Vs-deposit: same-amount side-by-side panels (ОВДП net vs deposit net), rate inputs defaulting to snapshot median sellYield and the deposit ledger range midpoint (15.25); delta line «ОВДП дають на X грн більше»; CTA button to `/search` («після реєстрації») styled `ig-btn ig-btn--gold`.
- Both: `WebApplication` JSON-LD (`applicationCategory: 'FinanceApplication'`, publisher `{@id: organizationId}`) + FAQPage + explainer prose + FAQ per Task 4's pattern. Signals + computed for reactive math; no forms module needed (ngModel-free: input events → signals).

- [ ] **Step 1:** Failing specs: calculator with stubbed snapshot renders result «4 000,00» for 25 000/16%/12міс… (assert computed netto text via fixture); vs-deposit renders both panels and the delta; both render `data-ovdp-asof`/`data-ovdp-empty`.
- [ ] **Step 2:** FAIL → **Step 3:** implement → **Step 4:** PASS.
- [ ] **Step 5:** Commit `feat(ovdp): calculator and deposit-comparison pages (015)`.

---

### Task 6: OG images + audit A30 + workflow hook

**Files:**
- Modify: `frontend/tools/seo/generate-og-images.mjs` — add static OG list: entries from manifest for the three `/ovdp/*` paths → render `public/seo/og/ovdp-rates.png`, `og/ovdp-calculator.png`, `og/ovdp-vs-deposit.png` (flat filenames; set matching `ogImage: '/og/ovdp-rates.png'` etc. in PUBLIC_PAGES).
- Modify: `frontend/tools/seo/seo-audit.mjs` — new block modeled on A20:

```js
group('A30 (015): /ovdp data pages prerendered, indexable, stamped');
for (const r of ['ovdp/rates', 'ovdp/calculator', 'ovdp/vs-deposit']) {
  const html = read(`${r}.html`) ?? read(`${r}/index.html`);
  check(html != null, `${r}: not prerendered`);
  if (!html) continue;
  check(/name="robots" content="index,follow"/.test(html), `${r}: not indexable`);
  check(/data-ovdp-asof|data-ovdp-empty/.test(html), `${r}: missing as-of stamp / honest empty state`);
  check(html.includes('"FAQPage"'), `${r}: FAQPage JSON-LD missing`);
  const og = attr(html, /<meta property="og:image" content="([^"]*)"/);
  check(og != null && og.includes('/og/ovdp-'), `${r}: per-page og:image missing (got ${og})`);
}
check(read('og/ovdp-rates.png') != null || existsSync(join(OUT, 'og/ovdp-rates.png')), 'og/ovdp-rates.png asset missing');
```

- Modify: `.github/workflows/refresh-bond-prices.yml` + `refresh-metal-prices.yml` — final step:

```yaml
- name: Trigger Cloudflare Pages rebuild (data pages)
  if: ${{ success() && secrets.CF_PAGES_DEPLOY_HOOK != '' }}
  run: curl -fsS -X POST "$CF_PAGES_DEPLOY_HOOK" -o /dev/null
  env:
    CF_PAGES_DEPLOY_HOOK: ${{ secrets.CF_PAGES_DEPLOY_HOOK }}
```

- [ ] **Step 1:** Implement OG + PUBLIC_PAGES ogImage. **Step 2:** implement A30. **Step 3:** `npm run seo:all` → PASS incl. A30. **Step 4:** workflow edits (YAML lint by eye + `git diff`; note workflows are Linux-side, LF).
- [ ] **Step 5:** Commit `feat(seo): og images, audit A30, rebuild hook for data pages (015)`.

---

### Task 7: Full verification + reviews + ship

- [ ] `cd backend && mvn -q -B test` → green.
- [ ] `npm run seo:all` → `[audit] PASS` (23 prerendered pages).
- [ ] `npm run test:ci` (Edge) → green (72 + new specs).
- [ ] Role sub-agent reviews (CLAUDE.md): BE-lead (endpoint/security/cacheability/test coverage) + QA/FE (page content uk quality, math correctness, empty-state honesty, i18n parity, a11y). Apply findings, re-verify.
- [ ] Push `015-ovdp-data-pages`, open PR against main (or stacked on #28 if unmerged — check `gh pr list` first).
