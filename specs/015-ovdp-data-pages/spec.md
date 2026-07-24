# Feature 015 — ОВДП data pages: live yields, calculator, comparison

Status: **draft** (spec only; produced from the 2026-07-24 SEO audit)
Depends on: 009-military-bond-prices (bondPrices ingestion), 011-precious-metals-prices, 012-bond-price-grounding, 013-seo-improvements (prerender/canonical infrastructure), 014-seo-content.

## Why

SERP research (July 2026) shows the calculator / live-rates query cluster is the highest-leverage open opportunity in the niche:

- «калькулятор дохідності ОВДП», «ставки ОВДП сьогодні», «ОВДП чи депозит калькулятор» are won today by one-person sites (finomo.com.ua, investor24.com.ua, ovdp.in.ua) — no bank, portal, or media outlet ships a real retail tool.
- minfin covers ОВДП auctions as *news*; mof.gov.ua publishes raw data; bankchart's #1-ranking article has **no yield table at all**.
- InvestGuideUA already ingests real per-ISIN bond prices (GH Actions scraper → `POST /internal/bond-prices` → `bondPrices` collection) and metal prices. Nobody else in the content space has this data. Publishing it is a moat.

## What

Three new public, indexable, prerendered routes (uk):

| Route | Working title (≤44 chars before suffix) | Target queries |
|---|---|---|
| `/ovdp/rates` | «Ставки ОВДП сьогодні: дохідність випусків» | ставки ОВДП сьогодні, дохідність ОВДП 2026, ОВДП таблиця |
| `/ovdp/calculator` | «Калькулятор дохідності ОВДП» | калькулятор ОВДП, скільки можна заробити на ОВДП |
| `/ovdp/vs-deposit` | «ОВДП чи депозит: калькулятор порівняння» | ОВДП чи депозит, депозит чи облігації калькулятор |

### FR-1 Public prices API
- New endpoint `GET /api/v1/bond-prices/latest` (backend): returns the latest stored price/yield snapshot per ISIN: `{ isin, name, maturityDate, yieldPct (integer basis points), priceKop (integer kopiykas), asOf }`.
- Read-only, unauthenticated, cacheable (`Cache-Control: public, max-age=3600`); no token cost (marketing surface, not the advisor).
- Money stays integer minor units end-to-end (kopiykas / basis points) per project rules; formatting is a frontend concern.

### FR-2 `/ovdp/rates` — live yields table
- Prerendered shell with the table server-rendered from the snapshot bundled at build time (so crawlers always see data), then hydrated client-side from FR-1 for freshness.
- Columns: випуск/ISIN, погашення, дохідність, орієнтовна ціна, де купити (Дія / банк / брокер).
- `asOf` timestamp visible («дані станом на …») — honesty requirement, mirrors bond-grounding feature 012.
- Below the table: 300–500 words of explainer prose + FAQ block (reuses article FAQ machinery) + links to the ОВДП article cluster.

### FR-3 `/ovdp/calculator`
- Inputs: сума (грн), випуск (select from FR-1 list) or manual ставка, строк.
- Output: дохід на руки, порівняння з депозитом за тією самою ставкою після 23% податків.
- All arithmetic in integer kopiykas; display formatting via existing i18n number utils.
- Works fully offline of the API (manual-rate mode) so the prerendered page is useful even if fetch fails.

### FR-4 `/ovdp/vs-deposit`
- Side-by-side: same amount/term through ОВДП (0% tax) vs deposit (23% tax), with sliders; defaults seeded from FR-1 median yield and the deposit range already shown on the landing ledger.
- CTA into `/search` (the paid advisor) — this page is the top-of-funnel for the core product.

### FR-5 SEO plumbing (all three pages)
- Added to `PUBLIC_PAGES` (`seo-routes.config.ts`), `prerender-routes.txt` generation, sitemap/llms.txt, per-page OG image, canonical-200 flattening — i.e., full parity with articles in `seo-audit.mjs` (new audit assertions A30+: pages exist, have unique title/desc, JSON-LD, visible asOf stamp).
- JSON-LD: `Dataset` (rates page) + `FAQPage`; calculator pages `WebApplication` + `FAQPage`.
- Title freshness: month-stamping happens at build time (builds run at least monthly via the price-refresh cadence); the title template includes the build month resolved during prerender, e.g. «Ставки ОВДП: липень 2026» — length budget verified by the audit.

### FR-6 Freshness cadence
- The existing GH Actions price-refresh workflows gain a step that triggers a Cloudflare Pages rebuild (deploy hook) after successful ingest, so prerendered data is at most a day stale and sitemap lastmod moves with real changes.

## Non-goals
- EN mirrors (blocked on EN_ENABLED increment).
- Historical charts, auction calendar (candidate feature 016).
- Metals price pages (candidate feature 016; same architecture, `metalPrices` source).

## Acceptance
1. `npm run seo:all` passes with the three new routes (new A30+ checks green).
2. `GET /api/v1/bond-prices/latest` returns ≥1 ISIN with integer money fields; backend tests cover empty-collection and staleness cases.
3. Lighthouse (mobile) ≥90 perf on all three pages; CLS < 0.1 with the table server-rendered.
4. Each page renders meaningful content with JavaScript disabled (prerendered table + prose).
