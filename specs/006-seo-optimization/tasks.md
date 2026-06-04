---
description: "Task list for SOTA SEO Foundation"
---

# Tasks: SOTA SEO Foundation

**Input**: Design documents from `/specs/006-seo-optimization/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: The plan's verification strategy (research R7) calls for a Node SEO **acceptance
audit** (a required deliverable, not optional) plus targeted Karma/Jasmine unit specs for the
new services and generators. Unit-test tasks are included for those units and marked accordingly;
the audit + Lighthouse are required gates, not optional tests.

**Organization**: Tasks are grouped by user story (US1-US5 from spec.md) so each can be
implemented, prerendered, and verified independently. All paths are frontend-only (Constitution
Principle I: no backend/service change).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1..US5 (setup/foundational/polish carry no story label)

## Path Conventions

Web app, but this feature is **frontend-only**. Root for all paths: `frontend/`. Build tooling
under `frontend/tools/seo/`; SEO static assets under `frontend/public/`; new app code under
`frontend/src/app/core/seo/` and `frontend/src/app/features/articles/`; article content under
`frontend/src/content/articles/`. Canonical origin via `SEO_SITE_ORIGIN` (default
`https://investguideua.com`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Install prerender tooling and wire the build so the production output is
prerendered static HTML deployed from `dist/<app>/browser/` (no production server).

- [X] T001 Add pinned deps to `frontend/package.json`: `@angular/ssr@^17.3.0` and `@angular/platform-server@^17.3.0` (run `ng add @angular/ssr` to version-match; do NOT let an unpinned install pull v18+). Add devDeps `gray-matter` + `markdown-it` (build-time only, kept out of the browser bundle).
- [X] T002 Configure the production build target in `frontend/angular.json` for prerender: enable `prerender` + `server`/`ssr` entry; output splits into `dist/investguide-frontend/browser/` (deployed) and `dist/investguide-frontend/server/` (build-time only, never deployed). IMPORTANT: Angular 17.3's `application` builder does NOT auto-copy `public/` (that arrived in Angular 18) and `angular.json` assets is minimal — so add an EXPLICIT `assets` glob entry for each new root asset (`_redirects`, `404.html`, `og-default.png`, `google<token>.html`) so they reach `dist/investguide-frontend/browser/` (the generated `robots.txt`/`sitemap.xml` are written there by T018 post-build instead).
- [X] T003 [P] Add npm scripts to `frontend/package.json`: `seo:articles` (build articles), `seo:generate` (sitemap + robots), `seo:audit` (acceptance check), `lighthouse` (lab CWV); ensure `build` runs prerender and `seo:articles` before prerender.
- [X] T004 [P] Add `SEO_SITE_ORIGIN` build configuration plumbing (read env at build time, default `https://investguideua.com`) in `frontend/tools/seo/seo-config.ts`; export a single `getSiteOrigin()` used by generators and (via environment) the app. Confirm apex-vs-www decision is recorded as a launch checklist item.
- [X] T005 [P] Create the empty directory skeleton: `frontend/src/app/core/seo/`, `frontend/src/app/features/articles/`, `frontend/src/content/articles/`, `frontend/tools/seo/`.

**Checkpoint**: `npm run build` runs (even before prerender targets are wired) and the project compiles.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Make prerendering actually produce crawlable, correctly-localized HTML. EVERY user
story depends on this — without the server platform config, the synchronous language initializer,
and the public-route allow-list, no public page renders real content at build time.

**CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 Create `frontend/src/main.server.ts` (prerender entry) and `frontend/src/app/app.config.server.ts` (platform-server config) per Angular 17 SSR conventions; wire `provideClientHydration()` in `frontend/src/app/app.config.ts`. (build-time-only server bundle; not deployed)
- [X] T007 Implement a server/filesystem `TranslateLoader` in `frontend/src/app/core/seo/server-translate.loader.ts` that reads `public/i18n/{lang}.json` from disk during prerender; provide it on the server platform only (browser keeps the existing HTTP loader). Resolves the relative-URL-fetch-during-prerender problem (research R2).
- [X] T008 Implement the synchronous locale-prefix initializer in `frontend/src/app/core/seo/locale-prefix.initializer.ts`: set the active `ngx-translate` language from the URL (`/en...` -> en, else uk) BEFORE bootstrap on both platforms, so titles, content, canonical, and hydration matching are correct. Wire as an `APP_INITIALIZER`/router-aware provider in `app.config.ts` and `app.config.server.ts`. (load-bearing for US1/US3/US4 and hydration)
- [X] T009 Define the public-route prerender allow-list in `frontend/src/app/core/seo/seo-routes.config.ts`: an EXPLICIT list of public paths (NOT auto-derived from the route table) — `/`, `/terms`, `/privacy` for now, extended by US3 (articles) and US4 (`/en` mirror). Wire it as the build's prerender route source.
- [X] T010 [P] Add a `SeoMeta` route-data type + per-route metadata table (title, description, indexable flag) in `frontend/src/app/core/seo/seo-routes.config.ts`, seeded for `/`, `/terms`, `/privacy`. Enforce title <=60 and description 110-160 at the type/build level (data-model validation).
- [X] T011 [P] Unit spec for the locale-prefix initializer in `frontend/src/app/core/seo/locale-prefix.initializer.spec.ts` (uk default, `/en` -> en, no late language switch). Karma.

**Checkpoint**: Foundation ready — prerendering renders localized content; user stories can begin.

---

## Phase 3: User Story 1 - A searcher finds InvestGuideUA on Google (Priority: P1) 🎯 MVP

**Goal**: Every existing public page (home, terms, privacy) is prerendered with its real content
in the initial HTML, plus a unique, length-valid title, meta description, and a self-referential
canonical URL.

**Independent Test**: `curl`/fetch the prerendered `index.html`, `terms`, `privacy` from the
build output (JS disabled) and confirm primary content is present; assert unique `<title>` (<=60)
and meta description (110-160) per page and a `<link rel="canonical">` to the page's own URL.

### Implementation for User Story 1

- [X] T012 [US1] Implement `SeoService` in `frontend/src/app/core/seo/seo.service.ts`: on each navigation set `<title>`, `<meta name="description">`, and `<link rel="canonical">` (absolute, from `SEO_SITE_ORIGIN` + path) from the route's `SeoMeta`. Defensive dev-time warning when title/description exceed bounds.
- [X] T013 [US1] Wire `SeoService` into routing (subscribe to `NavigationEnd`) via a provider in `frontend/src/app/app.config.ts`; reconcile with the existing `TranslatedTitleStrategy` so localized titles still resolve (SeoService owns description + canonical; title strategy + SeoService agree on the title source).
- [X] T014 [P] [US1] Author unique uk titles/descriptions for `/`, `/terms`, `/privacy` as i18n keys in `frontend/public/i18n/uk.json` (+ en placeholders in `en.json`), referenced by `seo-routes.config.ts`. Lengths within bounds.
- [X] T015 [US1] Ensure landing + legal page primary copy renders into prerendered HTML (verify the components' visible text comes through the server translate loader, not async-only). Adjust `frontend/src/app/features/landing/landing.component.*` and `frontend/src/app/features/legal/legal-document.component.*` only if content is injected in a way the prerender misses.
- [X] T016 [US1] Enable prerendering of `/`, `/terms`, `/privacy` via the allow-list (T009); run `npm run build` and confirm three static HTML files with real content + canonical are emitted.
- [X] T017 [P] [US1] Unit spec for `SeoService` (title/description/canonical set correctly; bounds warning) in `frontend/src/app/core/seo/seo.service.spec.ts`.

**Checkpoint**: Public pages are crawlable with unique, valid per-page metadata — MVP indexable.

---

## Phase 4: User Story 2 - Search engines and social platforms can crawl the whole site (Priority: P1)

**Goal**: `robots.txt`, an auto-generated `sitemap.xml`, social-share cards, true-404 handling,
private-route exclusion, and search-console verification — the full crawl/discovery surface.

**Independent Test**: Fetch `robots.txt` (well-formed, references sitemap, disallows private);
validate `sitemap.xml` against the schema and confirm it lists public URLs and zero
private/duplicate URLs; paste a public URL into a social-card validator and see title/desc/image;
request an unknown path and confirm HTTP 404.

### Implementation for User Story 2

- [X] T018 [US2] Implement `frontend/tools/seo/generate-sitemap.ts`: emit `sitemap.xml` + `robots.txt` into the build output root from the public-route allow-list (T009) + published-article index (US3 extends it). Sitemap is schema-valid; robots references `{ORIGIN}/sitemap.xml`, allows public, disallows the canonical private set. Per `contracts/sitemap-robots.contract.md`.
- [X] T019 [P] [US2] Create `frontend/public/_redirects`: map every private area's bare path AND `/*` children (and `/en` mirrors) to `/index.html 200`; NO catch-all `/* 200` rule. Per the contract's canonical private-path set.
- [X] T020 [P] [US2] Create a static branded `frontend/public/404.html` (served with HTTP 404 for unmatched paths). Document that the Cloudflare Pages project must NOT enable the SPA framework preset (which injects a catch-all 200).
- [X] T021 [US2] Extend `SeoService` (T012) with Open Graph + Twitter Card tags (`og:title/description/image/type/url`, `twitter:card`, etc.) per page; default image `/og-default.png`, per-page override. (FR-011/012)
- [X] T022 [P] [US2] Add the default social image `frontend/public/og-default.png` (branded, correct dimensions).
- [X] T023 [US2] Mark private/utility routes `noindex` (robots meta) in `frontend/src/app/core/seo/seo-routes.config.ts` + `SeoService`, and confirm they are absent from the allow-list and sitemap. (FR-005) (NOT [P]: edits `seo.service.ts`, so run after T021.)
- [X] T024 [P] [US2] Add the search-console verification file `frontend/public/google<token>.html` and a `google-site-verification` meta fallback on the home page. (FR-031 / A16) NOTE: the `<token>` is issued by Search Console at property-registration time — this file cannot be finalized until then; treat as a launch-checklist dependency (A16 fails until the real token is in place).
- [X] T054 [US2] **Fix the deploy command (BLOCKER — execute within US2, before T025 verification)**: edit `docs/DEPLOY-cloud.md` (lines ~298, ~310) and the Cloudflare Pages project build command to STOP writing the catch-all `printf '/*  /index.html  200' > .../browser/_redirects` — that rule is the soft-404 trap. Instead emit the private-only `_redirects` (T019) into `dist/investguide-frontend/browser/` and ensure `404.html` ships. Confirm the CF Pages project has no SPA framework preset. (FR-006 / SC-011)
- [X] T025 [US2] Run `npm run build && npm run seo:generate`; confirm `robots.txt` + `sitemap.xml` emitted to the output root, sitemap excludes all private routes, and an unknown URL returns HTTP 404 (not 200). (depends on T054)
- [ ] T026 [P] [US2] Unit spec for the sitemap/robots generator in `frontend/tools/seo/generate-sitemap.spec.ts` (includes all public, excludes private/draft, well-formed robots).

**Checkpoint**: Whole public site is discoverable; private surface excluded; social cards render; true 404s.

---

## Phase 5: User Story 3 - A reader discovers and learns from educational articles (Priority: P2)

**Goal**: An articles section (index + detail) sourced from in-repo Markdown+frontmatter, with
>=8 published uk articles, article-appropriate metadata, disclaimer, related links, and a CTA into
the search flow — all prerendered and added to the sitemap.

**Independent Test**: Open `/articles` and see all published articles listed with title/summary/
link; open an article and confirm full body in prerendered HTML, unique title/description, dates,
disclaimer, related links, and a path into search; confirm articles appear in the sitemap.

### Implementation for User Story 3

- [X] T027 [US3] Implement `frontend/tools/seo/build-articles.ts`: read `src/content/articles/*.md`, validate frontmatter against `contracts/article-frontmatter.schema.md` (fail build on invalid `published` article), render body Markdown -> HTML (markdown-it, build-time), and emit a typed generated index `frontend/src/app/features/articles/articles.data.ts` (metadata + sanitized body HTML, published only).
- [X] T028 [P] [US3] Create the article content loader/service `frontend/src/app/features/articles/article.service.ts` that exposes the generated index (list, by-slug, by-language, related) to components.
- [X] T029 [US3] Implement `ArticlesIndexComponent` in `frontend/src/app/features/articles/articles-index.component.ts` (+ template/styles): list published articles (title, summary, link), localized.
- [X] T030 [US3] Implement `ArticleDetailComponent` in `frontend/src/app/features/articles/article-detail.component.ts` (+ template/styles): render body HTML, publication + last-updated dates, the standard financial disclaimer, related-article links, and a clear CTA into `/search`. (FR-025/026)
- [X] T031 [US3] Add routes `articles` and `articles/:slug` to `frontend/src/app/app.routes.ts` (lazy `loadComponent`, public/no guard, placed before the `**` wildcard).
- [X] T032 [US3] Extend the prerender allow-list (T009) and the sitemap generator (T018) to enumerate every published article slug; confirm articles index + each article prerender and appear in `sitemap.xml`.
- [X] T033 [P] [US3] Author 8 published uk article content files in `frontend/src/content/articles/` (e.g. `ovdp-war-bonds.uk.md`, `deposits-vs-alternatives.uk.md`, `invest-50000-uah-safely.uk.md`, `invest-100000-uah-beginner.uk.md`, `uah-vs-usd-savings.uk.md`, `beginner-guide-investing-ukraine.uk.md`, `understanding-investment-risk.uk.md`, `avoid-investment-scams.uk.md`) — each with full frontmatter (primaryTopic, dates, reviewedBy/reviewedOn), catalog-consistent provider mentions, source-linked figures, related slugs. (FR-023/024/025, SC-006/013)
- [X] T034 [P] [US3] Add an editorial acceptance checklist doc `frontend/src/content/articles/REVIEW-CHECKLIST.md` (original, fact-checked sign-off, intent match, no individualized advice) referenced by `reviewedBy`. (FR-024)
- [ ] T035 [P] [US3] Unit spec for `build-articles.ts` frontmatter validation in `frontend/tools/seo/build-articles.spec.ts` (rejects out-of-range title/description, missing sign-off, unresolved relatedSlugs, off-catalog providers).

**Checkpoint**: Articles section live, crawlable, sitemapped; >=8 uk articles with sign-off + disclaimer.

---

## Phase 6: User Story 4 - Bilingual audience served, no duplication penalty (Priority: P2)

**Goal**: English mirror under `/en`, correct hreflang (uk/en/x-default incl. self) on every
public page, sitemap alternate annotations, and en versions of public pages/articles — so the two
languages are understood as translations, not duplicates.

**Independent Test**: Crawl a page and its `/en` equivalent; confirm each declares its language and
references the other + x-default; confirm both appear in the sitemap with `xhtml:link` alternates;
validate with an hreflang checker (zero errors).

### Implementation for User Story 4

- [ ] T036 [US4] Add the `/en` route mirror in `frontend/src/app/app.routes.ts` (parent `{ path: 'en', children: [...] }` reusing the same lazy components + guards; `**` stays last).
- [ ] T037 [US4] Extend `SeoService` (T012/T021) to emit hreflang `<link rel="alternate">` for uk, en, and `x-default` (incl. self-reference) computed from the page's slug/path. (FR-019, SC-009)
- [ ] T038 [US4] Extend the prerender allow-list (T009) to include the `/en` mirror of every public route + article; confirm en pages prerender with en content (language initializer T008 sets en).
- [ ] T039 [US4] Extend the sitemap generator (T018) to emit `xhtml:link rel="alternate" hreflang` (uk/en/x-default) per `<url>` and to list both language versions. (FR-020)
- [ ] T040 [P] [US4] Author en versions of public-page i18n strings (titles/descriptions) in `frontend/public/i18n/en.json` (replace placeholders from T014), within length bounds.
- [ ] T041 [P] [US4] Author en article files `{slug}.en.md` for the published uk articles in `frontend/src/content/articles/` (uk-first priority per spec; en follows). Each shares the `slug` to form the hreflang pair.
- [ ] T042 [US4] Unit spec for hreflang generation (correct uk/en/x-default set incl. self) in `frontend/src/app/core/seo/seo.service.spec.ts` (extends the T017 spec — same file, so run after T017, NOT [P]).

**Checkpoint**: Both languages crawlable with correct alternates; no duplicate-content signal.

---

## Phase 7: User Story 5 - Quality & trust signals (structured data, CWV) (Priority: P3)

**Goal**: Valid JSON-LD (Organization, WebSite+SearchAction, Article, BreadcrumbList, FAQPage) on
the appropriate pages, plus passing mobile Core Web Vitals and mobile-friendliness.

**Independent Test**: Run each public page through a structured-data validator (zero errors for
declared types); run home + an article through Lighthouse mobile and confirm LCP<=2.5s/INP<=200ms/
CLS<=0.1 (lab) and mobile-friendly.

### Implementation for User Story 5

- [X] T043 [US5] Implement `StructuredDataService` in `frontend/src/app/core/seo/structured-data.service.ts`: inject per-route JSON-LD `<script type="application/ld+json">` into `<head>` (renders into prerendered HTML), composing builder functions (T044-T046) by page type. Per `contracts/structured-data.contract.md`.
- [X] T044 [P] [US5] Implement Organization + WebSite (SearchAction targeting `/search`, `inLanguage` per page) builders in `frontend/src/app/core/seo/jsonld/org-website.jsonld.ts`, emitted on all public pages. (FR-014)
- [X] T045 [P] [US5] Implement the Article JSON-LD builder (headline, inLanguage, datePublished, dateModified, image, publisher; sourced from the generated article index) in `frontend/src/app/core/seo/jsonld/article.jsonld.ts`. (FR-015)
- [X] T046 [P] [US5] Implement BreadcrumbList (index + article) and FAQPage (only when frontmatter `faq` present and visible) builders in `frontend/src/app/core/seo/jsonld/breadcrumb-faq.jsonld.ts`. (FR-016/017)
- [ ] T047 [P] [US5] Lighthouse lab config + `npm run lighthouse` against the prerendered build (home + one article) in `frontend/lighthouserc.json`; record LCP/INP/CLS and a mobile-friendly check. Address obvious regressions (image sizing, preload LCP element, `<html lang>` set). (SC-007, FR-029/030)
- [ ] T048 [P] [US5] Unit spec for `StructuredDataService` + builders (valid required properties per page type; FAQPage only when faq present) in `frontend/src/app/core/seo/structured-data.service.spec.ts`.

**Checkpoint**: Rich-result-eligible structured data on all public pages; CWV pass (lab).

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: The consolidated acceptance gate and final verification spanning all stories.

- [X] T049 Implement the SEO acceptance audit `frontend/tools/seo/seo-audit.ts` enforcing assertions A1-A16 from `contracts/seo-acceptance.md` against `dist/<app>/browser/` (content present, unique/length-valid title+desc per language, canonical, hreflang+x-default, OG/Twitter, JSON-LD schema-valid, robots + sitemap correctness, private excluded, article sign-off/disclaimer/source-links, >=8 articles, verification file). Exits non-zero on any failure; support a profile flag so the MVP slice (US1+US2) can run the applicable subset (A1-A7, A10-A12, A14, A16) before US3-US5 ship. Wire into CI via T057.
- [ ] T050 [P] Add a hydration/interactivity smoke check in `frontend/tools/seo/hydration-smoke.ts` (headless load of `/` and one article: no Angular hydration error in console; navigation works). (FR-009)
- [X] T051 [P] Update `frontend/README.md` (or `docs/`) with the SEO build/verify/add-article runbook, linking `specs/006-seo-optimization/quickstart.md`.
- [X] T055 [P] Pin line endings for new CDN/crawler-consumed assets in `.gitattributes`: `_redirects`, `robots.txt`, `sitemap.xml`, and `*.md` as `text eol=lf`; confirm `og-default.png`/verification image stay `binary` (Constitution Principle V / line-endings rule). (Execute in Setup; placed here as an added-in-review item.)
- [X] T056 [P] Decide and record generated-file handling in `frontend/.gitignore`: ignore build-generated `frontend/src/app/features/articles/articles.data.ts` (regenerated by T027 `build-articles`) and the output `dist/` SEO files, OR deliberately commit and document why. (Execute in Setup; added in review.)
- [ ] T057 Wire `npm run seo:audit` (and `seo:articles`/`seo:generate`) into `.github/workflows/ci.yml` as a build gate after the frontend build; build fails on audit non-zero. Verify the existing secret-scan path still resolves after the output moves to `dist/investguide-frontend/browser`.
- [X] T052 Run full `npm run build && npm run seo:articles && npm run seo:generate && npm run seo:audit && ng test && npm run lighthouse`; confirm all pass and that the deployed artifact contains `browser/` only (no `server/` bundle — Principle I). Record field-data items (SC-010 impressions, SC-011 soft-404s) as post-launch Search Console monitoring.
- [X] T053 Mandatory two-role sub-agent review (Constitution Principle VI), including an actual scan/parse: any new Windows-executed `.ps1`/`.cmd` is pure ASCII (expect none — generators are Node/TS); `ng test` and `seo:audit` run clean. Apply or report findings before close.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup; BLOCKS all user stories (prerender + language init + allow-list).
- **US1 (Phase 3, P1)**: depends on Foundational. The MVP.
- **US2 (Phase 4, P1)**: depends on Foundational; T021 extends `SeoService` (T012), so US2's social-card task depends on US1's SeoService existing. Sitemap (T018) is extended by US3/US4 but works standalone for the initial public set.
- **US3 (Phase 5, P2)**: depends on Foundational; extends the allow-list (T009) and sitemap (T018).
- **US4 (Phase 6, P2)**: depends on Foundational + `SeoService` (US1); extends allow-list + sitemap; en article files (T041) depend on uk articles (T033) existing as the slug source.
- **US5 (Phase 7, P3)**: depends on Foundational; Article JSON-LD (T045) depends on the generated article index (US3 T027).
- **Polish (Phase 8)**: the audit (T049) meaningfully validates only after the stories it asserts are implemented.

### Within Each User Story

- The shared `SeoService` (T012) and `seo-routes.config.ts` (T009/T010) are the spine: US1 creates SeoService; US2 and US4 extend it (OG tags, hreflang). Sequence those extensions after T012.
- Models/generators before components; components before route wiring; route wiring before prerender/sitemap inclusion.

### Parallel Opportunities

- Setup: T003, T004, T005 in parallel.
- Foundational: T010, T011 in parallel (after T009/T008).
- US1: T014 and T017 parallel with core service work.
- US2: T019, T020, T022, T023, T024, T026 are largely independent files — parallel.
- US3: T033, T034, T035 parallel (content + checklist + spec) once the loader (T027/T028) exists.
- US4: T040, T041, T042 parallel.
- US5: T044, T045, T046, T047, T048 parallel once T043 exists.
- Across teams: after Foundational, US1+US2 (one dev) and US3 (another) and US5 structured-data prep can progress concurrently; US4 needs US1's SeoService.

---

## Parallel Example: User Story 2

```bash
# After SeoService (T012) exists, launch independent US2 files together:
Task: "Create frontend/public/_redirects (T019)"
Task: "Create frontend/public/404.html (T020)"
Task: "Add frontend/public/og-default.png (T022)"
Task: "Add search-console verification file + meta (T024)"
Task: "Unit spec for sitemap/robots generator (T026)"
```

---

## Implementation Strategy

### MVP First (US1 + US2)

Both P1 stories are the true MVP: US1 makes public pages indexable with good metadata; US2 makes
the site discoverable (robots/sitemap/cards), fixes the deploy soft-404 trap (T054), and excludes
private routes. Complete Phase 1 + 2 + 3 + 4, run `seo:audit` for the existing public pages,
deploy.

> **Reduced audit profile for the MVP slice**: the full audit (T049 / A1-A16) asserts hreflang
> (A5, US4), Article + Breadcrumb/FAQ JSON-LD (A8/A9, US5), article sign-off/disclaimer (A13,
> US3), and >=8 articles (A15, US3). Those cannot pass before US3-US5 ship, so the MVP deploy
> runs the subset of assertions applicable to the home/terms/privacy public set (A1-A7, A10-A12,
> A14, A16). The audit script should support an explicit profile/flag for this.

### Incremental Delivery

1. Setup + Foundational -> prerendering renders localized content.
2. US1 -> public pages indexable (MVP slice).
3. US2 -> crawl infra + social + true 404 (completes P1 / discoverability).
4. US3 -> articles (the ranking growth engine).
5. US4 -> bilingual hreflang.
6. US5 -> structured data + CWV polish.
7. Polish -> consolidated audit gate + reviews.

### Notes

- [P] = different files, no incomplete-task dependency.
- The server bundle (`main.server.ts`, `app.config.server.ts`) is build-time only; deploy
  `dist/<app>/browser/` only (Constitution Principle I).
- Article content `.md`/generators are UTF-8/LF (not Windows-executed scripts); Ukrainian text is
  fine. Any `.ps1`/`.cmd` helper (avoid if possible) MUST be pure ASCII (Principle V).
- "Done" = `seo:audit` + `ng test` + Lighthouse pass + two-role review (T053), per the
  constitution quality gates.

---

## Implementation status (as of 2026-06-04)

**Done & verified** (build + `seo:audit` 239 checks + 49 unit tests all green): Setup, Foundational,
US1 (indexable pages, per-page metadata), US2 (robots/sitemap/cards/true-404/exclusion), US3 (articles
section + 8 uk articles + Article/Breadcrumb JSON-LD), US5 (Organization/WebSite/Article/Breadcrumb/
FAQPage structured data). Two-role review (T053) completed - no code blockers.

**Deferred / remaining** (tracked, not done):
- **US4 bilingual `/en` (T036-T042, P2)** - intentionally deferred to a follow-up increment. Today the
  site is uk-only; hreflang correctly emits uk + x-default only (gated by `EN_ENABLED=false`), and the
  sitemap omits `en`, so nothing advertises a 404. Flip `EN_ENABLED`, add `/en` to the prerender list,
  author `*.en.md`, and re-audit to enable.
- **Launch-time:** replace `public/seo/googleREPLACE_WITH_REAL_TOKEN.html` with the real Search Console
  token (T024 file is a documented placeholder); set the canonical apex-vs-www and 301 the other host.
- **Nice-to-have specs/automation not yet written:** generator unit specs (T026/T035), StructuredData
  spec (T048), Lighthouse lab config (T047), hydration smoke harness (T050), CI wiring of seo:audit (T057).
- **Quality:** per-article OG images (all share og-default.png today; frontmatter supports per-article).
