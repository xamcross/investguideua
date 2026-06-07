---
description: "Task list for SEO & AI-Search Traffic Maximization (Gap Closure)"
---

# Tasks: SEO & AI-Search Traffic Maximization (Gap Closure)

**Input**: Design documents from `/specs/010-seo-aeo-optimization/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (all present)
**Branch**: `010-seo-aeo-optimization`

**Tests**: This feature's "tests" are the executable build-byte audits (`seo:audit` checks A16'-A25 + build gates B1-B4, and `seo:cwv` checks C1-C3) per `contracts/seo-audit.contract.md`. No separate unit-test suite is requested; verification is by running the audits/build (Constitution V). Audit-extension tasks are embedded in each story as that story's acceptance gate.

**Organization**: Tasks are grouped by the 7 user stories from spec.md (US1-US7), preceded by Setup and Foundational phases.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task).
- **[Story]**: US1-US7 (omitted for Setup/Foundational/Polish).

## ⚠️ Shared-file sequencing (read first)

Three build tools are edited by multiple stories and therefore those edits are **sequential, never `[P]` with each other**:

- `frontend/tools/seo/build-articles.mjs` → T004 (Foundational), T008 (US1), T033/T034 (US7)
- `frontend/tools/seo/generate-sitemap.mjs` → T005 (Foundational), T015/T016 (US2), T028 (US5)
- `frontend/tools/seo/seo-audit.mjs` → T014 (US1), T017 (US2), T023 (US3), T027 (US4), T029 (US5), T032 (US6)
- `frontend/src/app/features/articles/article-detail.component.ts` → T012 (US1), T025 (US4), T035 (US7)
- `frontend/src/app/app.component.ts` → T013 (US1), T020 (US3)

All paths are under `frontend/` unless noted.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Tooling/config prerequisites for the new build steps.

- [x] T001 Add devDependencies `lighthouse` and a tiny static file server (e.g. `sirv-cli`) on current release lines to `frontend/package.json`; confirm `puppeteer` is already present (it is — drives `tools/a11y/axe-audit.mjs`).
- [x] T002 Add npm scripts to `frontend/package.json`: `"seo:cwv": "node tools/seo/cwv-audit.mjs"`; keep `seo:all` = `build && seo:generate && seo:audit` (CWV stays advisory, NOT in the blocking chain per research R3).
- [x] T003 [P] Add `seo-manifest.json` to `frontend/.gitignore` (generated build artifact, next to the existing `articles.data.ts` ignore).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The single build-time SEO manifest that the sitemap, robots, and llms generators all consume. Fixes the `lastmod` data gap at the source. **US2 and US5 depend on this.**

**⚠️ CRITICAL**: T004 and T005 must complete before US2 and US5.

- [x] T004 Extend `frontend/tools/seo/build-articles.mjs` to emit `frontend/seo-manifest.json` per `contracts/seo-manifest.contract.md`: one entry `{path,lastmod,indexable,kind,title,description}` per public route — each `PUBLIC_PAGES` static page + the `/articles` index + every published UK article; `lastmod` = `dateModified` for articles and build date for static pages; resolve static `title`/`description` from `public/i18n/uk.json` (`titleKey`/`descKey`); deterministic sort by `path`.
- [x] T005 Extend `frontend/tools/seo/generate-sitemap.mjs` to load `frontend/seo-manifest.json` as its data source, with a hard guard: `existsSync(manifest)` else throw, and assert freshness vs `dist/.../browser/index.html` mtime (a stale/missing manifest MUST fail, never silently fall back to build date) per research R1.

**Checkpoint**: Manifest is generated and consumed; generators fail loudly on stale data.

---

## Phase 3: User Story 1 - Editorial trust footprint / E-E-A-T (Priority: P1) 🎯 MVP

**Goal**: Render an organization-level editorial trust block on every article and add prerendered, indexable Editorial Policy/About + Contact pages.

**Independent Test**: Open any published article → a machine-detectable trust block (editorial-team attribution + review date) links to `/editorial-policy`; `/editorial-policy` and `/contact` are reachable, prerendered, and indexable. `seo:audit` A19/A20 pass.

- [x] T006 [P] [US1] Add i18n copy to `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json`: `seo.editorial.title`/`seo.editorial.desc`, `seo.contact.title`/`seo.contact.desc` (descriptions 110-160 chars), `articles.reviewedBy` trust-block label, and the editorial-policy + contact page body keys.
- [x] T007 [P] [US1] Add `/editorial-policy` and `/contact` entries (path + titleKey + descKey) to `PUBLIC_PAGES` in `frontend/src/app/core/seo/seo-routes.config.ts`.
- [x] T008 [US1] Add `/editorial-policy` and `/contact` to `BASE_ROUTES` in `frontend/tools/seo/build-articles.mjs` so they prerender and enter the manifest/sitemap (depends on T004).
- [x] T009 [P] [US1] Create `frontend/src/app/features/pages/editorial-policy.component.ts` (standalone, reuses the `LegalDocumentComponent` i18n-body pattern) describing the editorial team, review workflow, and sourcing standards.
- [x] T010 [P] [US1] Create `frontend/src/app/features/pages/contact.component.ts` (standalone) exposing a `mailto:` contact (no form, no backend).
- [x] T011 [US1] Register lazy `loadComponent` routes for `/editorial-policy` and `/contact` in `frontend/src/app/app.routes.ts` (depends on T009, T010).
- [x] T012 [US1] Render the editorial trust block in `frontend/src/app/features/articles/article-detail.component.ts`: a machine-detectable element (e.g. `data-trust-block`) showing `reviewedBy` + `reviewedOn` and a `routerLink="/editorial-policy"`.
- [x] T013 [P] [US1] Add footer/nav links to `/editorial-policy` and `/contact` in `frontend/src/app/app.component.ts`.
- [x] T014 [US1] Extend `frontend/tools/seo/seo-audit.mjs` with A19 (every article page has the trust-block marker + review date + `/editorial-policy` link) and A20 (`/editorial-policy` and `/contact` prerendered, indexable, valid title/desc, `index,follow`).

**Checkpoint**: US1 fully functional; trust block + new pages live and audited.

---

## Phase 4: User Story 2 - AI crawler access + llms.txt (Priority: P1)

**Goal**: Explicitly permit major AI crawlers and publish a build-generated `llms.txt`.

**Independent Test**: Fetch `robots.txt` → explicit allow stanzas for GPTBot/Google-Extended/PerplexityBot/ClaudeBot; fetch `llms.txt` → all indexable pages, zero private routes. `seo:audit` A17/A18 pass. (Depends on Foundational T004/T005.)

- [x] T015 [US2] Add `AI_CRAWLERS = ['GPTBot','Google-Extended','PerplexityBot','ClaudeBot']` and emit an explicit allow stanza per crawler (each inheriting the private-prefix Disallows) in `buildRobots()` of `frontend/tools/seo/generate-sitemap.mjs` per `contracts/robots-txt.contract.md`.
- [x] T016 [US2] Add `llms.txt` generation to `frontend/tools/seo/generate-sitemap.mjs` (write to the build output root) from the manifest's `indexable` entries, grouped by `kind` (Guides/About), per `contracts/llms-txt.contract.md`.
- [x] T017 [US2] Extend `frontend/tools/seo/seo-audit.mjs` with A17 (AI allow stanza per `AI_CRAWLERS` UA) and A18 (`llms.txt` exists, covers 100% of indexable paths as absolute URLs, leaks zero private routes).

**Checkpoint**: AI access + llms.txt live and audited.

---

## Phase 5: User Story 3 - Engagement measurement + Search Console (Priority: P2)

**Goal**: Cookieless Cloudflare Web Analytics beacon + real Search Console verification.

**Independent Test**: Load a public page → cookieless beacon snippet present in prerendered HTML, no consent banner; a real `google<token>.html` is deployed and the placeholder is gone. `seo:audit` A16'/A25 pass.

- [x] T018 [P] [US3] Add `cloudflareAnalyticsToken` to `frontend/src/environments/environment.ts` and `environment.prod.ts` (overridable per build; empty default = no beacon).
- [x] T019 [US3] Create `frontend/src/app/core/seo/analytics.service.ts` that appends the Cloudflare beacon `<script defer ... data-cf-beacon='{"token":"..."}'>` to `document.head` once, guarded by a non-empty token, using the same head-append pattern as `StructuredDataService` (serializes into prerendered HTML) — site-wide per research R2.
- [x] T020 [US3] Invoke `AnalyticsService` injection at app bootstrap in `frontend/src/app/app.component.ts` `ngOnInit` (mirroring `setBase`).
- [x] T021 [P] [US3] Add the real `frontend/public/seo/google<token>.html` AND delete `frontend/public/seo/googleREPLACE_WITH_REAL_TOKEN.html` (token supplied operationally).
- [x] T022 [P] [US3] Update the privacy-policy copy in `frontend/public/i18n/uk.json` and `en.json` to disclose Cloudflare Web Analytics (cookieless, no PII).
- [x] T023 [US3] Extend `frontend/tools/seo/seo-audit.mjs` with A16' (a `google*.html` exists and NO `google*.html` name/contents contains `REPLACE_WITH`) and A25 (beacon snippet present on 100% of prerendered public pages).

**Checkpoint**: Measurement live; ownership really verified; audit green on A16'/A25.

---

## Phase 6: User Story 4 - Core Web Vitals + images (Priority: P2)

**Goal**: Lab CWV gate on 3 templates; enforced image alt/dimensions; visible "Last updated".

**Independent Test**: `npm run seo:cwv` meets LCP/CLS/TBT thresholds on `/`, `/articles`, one `/articles/<slug>`; every `<img>` has alt + dimensions; article shows a "Last updated" date. `seo:audit` A23 passes.

- [x] T024 [US4] Create `frontend/tools/seo/cwv-audit.mjs`: serve `dist/.../browser/` via the static server, launch Chrome via `puppeteer.executablePath()`, run Lighthouse (simulated mobile, median of 3) on the 3 templates, fail if LCP>2.5s / CLS>0.1 / TBT>200ms (C1-C3); advisory (not in `seo:all`).
- [x] T025 [P] [US4] Verify/ensure the "Last updated" (`dateModified`) date is visibly rendered on the article page in `frontend/src/app/features/articles/article-detail.component.ts` (already shown — confirm it remains after the T012 trust-block edit).
- [x] T026 [P] [US4] Audit landing/home and shared templates for `<img>` tags; add descriptive `alt` + explicit `width`/`height` (adopt `NgOptimizedImage`/`ngSrc` where an `<img>` exists); confirm `og-default.png`/favicons are appropriately sized (no `sharp` unless an oversized asset is found).
- [x] T027 [US4] Extend `frontend/tools/seo/seo-audit.mjs` with A23 (no prerendered `<img>` lacks non-empty `alt`; flagged imgs also need `width`+`height`).

**Checkpoint**: CWV measured + image discipline enforced.

---

## Phase 7: User Story 5 - Crawl & indexation hygiene (Priority: P2)

**Goal**: True per-article sitemap `lastmod`; regression guard against advertising an unpublished `/en` mirror.

**Independent Test**: Each sitemap article `<lastmod>` equals its `dateModified` (not a uniform build date); no `hreflang="en"`/`/en` while `EN_ENABLED=false`; canonicals self-referential. `seo:audit` A21/A22 pass. (Depends on Foundational T004/T005.)

- [x] T028 [US5] In `buildSitemap()` of `frontend/tools/seo/generate-sitemap.mjs`, set each URL's `<lastmod>` from the manifest entry's `lastmod` (replace the uniform `today`); keep `EN_ENABLED`-gated hreflang behavior.
- [x] T029 [US5] Extend `frontend/tools/seo/seo-audit.mjs` with A21 (sitemap article `<lastmod>` == manifest `dateModified`) and A22 (no `hreflang="en"` and no `/en` `<loc>`/alternate anywhere while `EN_ENABLED=false`); retain A4 canonical checks.

**Checkpoint**: Truthful freshness signals; no `/en` crawl trap.

---

## Phase 8: User Story 6 - Brand entity signals (Priority: P3)

**Goal**: Stable Organization `@id` + `sameAs` + dimensioned logo; publisher-by-reference.

**Independent Test**: Organization JSON-LD has a stable `@id`, a `sameAs` array (may be empty), dimensioned logo; Article/WebSite publisher reference it by `@id`. `seo:audit` A24 passes.

- [x] T030 [P] [US6] Add an `ORG_SAME_AS` config (constant or `environment` field; `[]` default for graceful deferral) listing official profile URLs.
- [x] T031 [US6] Update `frontend/src/app/core/seo/structured-data.service.ts` per `contracts/structured-data.contract.md`: Organization gets a stable `@id` (`${origin}/#organization`), dimensioned `logo` ImageObject, and `sameAs` from config; `Article.author`/`Article.publisher` and `WebSite.publisher` reference the Organization by `@id`.
- [x] T032 [US6] Extend `frontend/tools/seo/seo-audit.mjs` with A24 (Organization `@id` present, `Article.publisher` references it by `@id`, `sameAs` is an array).

**Checkpoint**: Brand entity consolidated for machines.

---

## Phase 9: User Story 7 - Answer-first content + topical depth (Priority: P3)

**Goal**: Build-enforced answer-first lead + question heading; `>=2` inbound cross-links (no orphans).

**Independent Test**: A published article lacking an `answer`, a `?`-ending `##` heading, or `<2` inbound `relatedSlugs` fails `npm run seo:articles`; the `answer` lead renders above the body. Build gates B1-B3 enforced.

- [x] T033 [US7] Add the `answer` field to the generated `Article` interface and validate it in `frontend/tools/seo/build-articles.mjs`: required when `status: published`, ~40-300 chars (B1); also require `>=1` `##` heading whose trimmed text ends with `?` (B2).
- [x] T034 [US7] In `loadArticles()` of `frontend/tools/seo/build-articles.mjs`, compute inbound `relatedSlugs` counts per published same-language article and fail any with `<2` inbound references (B3; the `/articles` index does not count).
- [x] T035 [US7] Render the `answer` as a lead `<p class="ig-article__answer">` above the body in `frontend/src/app/features/articles/article-detail.component.ts`.
- [x] T036 [US7] Edit every published article in `frontend/src/content/articles/*.md`: add an `answer` frontmatter field (40-300 chars) and ensure each has at least one `##` question heading ending in `?`.
- [x] T037 [US7] Adjust `relatedSlugs` across `frontend/src/content/articles/*.md` so every published article is referenced by `>=2` peers (raise cluster density; same files as T036, so run after it).

**Checkpoint**: Content meets enforced answer-first + cross-link standards.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Full-chain verification and the mandatory review gate.

- [x] T038 Run the full chain from `frontend/`: `npm run seo:articles && npm run build && npm run seo:generate && npm run seo:audit`; resolve every audit violation until exit 0 (all A1-A25 + B1-B4 pass).
- [x] T039 [P] Run `npm run seo:cwv`; confirm LCP/CLS/TBT thresholds on the 3 templates (advisory — report results).
- [x] T040 [P] Run `npm run test:ci` to confirm new/edited Angular components compile and existing specs pass.
- [x] T041 Mandatory multi-role sub-agent review (Constitution VI): SEO/AEO specialist + FE-lead/QA + DevOps, INCLUDING a byte-level scan/parse of the generated `robots.txt`/`sitemap.xml`/`llms.txt`/article HTML (not just reading); apply or report findings before closing.
- [x] T042 [P] Update `specs/010-seo-aeo-optimization/quickstart.md` and `frontend` docs if implementation deviated from plan.

---

## Dependencies & Execution Order

**Phase order**: Setup (P1) → Foundational (P2) → US1 → US2 → US3 → US4 → US5 → US6 → US7 → Polish.

**Hard dependencies**:
- T004, T005 (Foundational) block US2 (T015-T017) and US5 (T028-T029) — both consume the manifest.
- T008 depends on T004 (manifest shape / BASE_ROUTES).
- T011 depends on T009, T010.
- T037 runs after T036 (same `.md` files).
- All `seo-audit.mjs` tasks (T014, T017, T023, T027, T029, T032) are sequential — same file.
- All `generate-sitemap.mjs` tasks (T005, T015, T016, T028) are sequential — same file.
- All `build-articles.mjs` tasks (T004, T008, T033, T034) are sequential — same file.
- T012, T025, T035 are sequential — same `article-detail.component.ts`.
- T013, T020 are sequential — same `app.component.ts`.

**Story independence**: US1, US3, US4, US6, US7 are independently testable. US2 and US5 require Foundational first but are independent of each other (different functions in the shared file — sequence the file edits).

## Parallel Execution Examples

Within US1 (different files):
```
T006 (i18n json)  ||  T007 (seo-routes.config.ts)  ||  T009 (editorial component)  ||  T010 (contact component)
# then T008, T011, T012, T013, T014 sequentially per their file/dep constraints
```

Within US3:
```
T018 (environment)  ||  T021 (verification file)  ||  T022 (privacy i18n)
# then T019 -> T020 -> T023
```

Polish:
```
T039 (cwv)  ||  T040 (ng test)  ||  T042 (docs)   # T038 first, T041 last
```

## Implementation Strategy

- **MVP = US1** (P1): the E-E-A-T trust footprint delivers standalone value and is the highest-weighted YMYL gap.
- **Recommended first increment = US1 + US2** (both P1): trust footprint + AI discoverability — the two headline goals — after the small Foundational manifest step.
- **Then P2 (US3-US5)**: measurement, CWV, crawl hygiene — each independently shippable.
- **Then P3 (US6-US7)**: entity signals + content standards.
- Run T038 (audit) after each story to keep the build green incrementally; T041 review gate before final close.

## Task summary

- **Total**: 42 tasks
- **Setup**: 3 (T001-T003) · **Foundational**: 2 (T004-T005)
- **US1**: 9 (T006-T014) · **US2**: 3 (T015-T017) · **US3**: 6 (T018-T023) · **US4**: 4 (T024-T027) · **US5**: 2 (T028-T029) · **US6**: 3 (T030-T032) · **US7**: 5 (T033-T037)
- **Polish**: 5 (T038-T042)
