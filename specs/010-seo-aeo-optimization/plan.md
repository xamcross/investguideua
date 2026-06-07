# Implementation Plan: SEO & AI-Search Traffic Maximization (Gap Closure)

**Branch**: `010-seo-aeo-optimization` | **Date**: 2026-06-07 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/010-seo-aeo-optimization/spec.md`

## Summary

Close the genuine SEO/AEO gaps on top of the existing mature foundation, frontend-only, with **no backend, LLM, payment, or database changes**. The work extends the existing build-time SEO pipeline (`tools/seo/*.mjs`), the Angular SEO services (`core/seo/*`), the article Markdown pipeline, and the Cloudflare Pages deploy assets:

1. **E-E-A-T (org-level)** — render an editorial trust block on articles; add Editorial Policy/About + Contact as prerendered indexable routes; consolidate the Organization entity via `@id` (no `Person` per clarification).
2. **AI access** — explicit AI-crawler allow stanzas in `robots.txt` + a build-generated `llms.txt`.
3. **Measurement** — Cloudflare Web Analytics beacon (cookieless, site-wide via bootstrap injection — see research R2 re: SPA-fallback topology); replace AND delete the Search Console placeholder with a real token.
4. **Core Web Vitals** — a lab CWV check in CI on 3 templates; enforce image alt/dimensions via the audit.
5. **Crawl hygiene** — fix the sitemap `lastmod` bug (true per-article `dateModified`); add a no-`en`-alternate regression guard.
6. **Entity** — Organization `sameAs` + stable `@id`.
7. **Content** — build-enforced answer-first lead + question heading; `>=2` inbound cross-link (no-orphan) rule.

The unifying architectural move is a **single build-time SEO manifest** emitted by `build-articles.mjs` (path + `lastmod` + indexable + title/description) that the sitemap, `robots.txt`, and `llms.txt` generators all consume, eliminating drift and fixing the `lastmod` bug at the source. The extended `seo-audit.mjs` is the executable definition of done.

## Technical Context

**Language/Version**: TypeScript 5.4 / Angular 17.3 (standalone components); Node.js ESM build tooling (existing pattern in `tools/seo/*.mjs`). No Java/backend changes.
**Primary Dependencies**: existing — `@angular/ssr` (prerender), `@ngx-translate/core`, `markdown-it`, `gray-matter`, `puppeteer` (already a devDependency, drives `a11y/axe-audit.mjs`). New dev-only — `lighthouse` for the lab CWV runner (launches Chrome via the existing `puppeteer.executablePath()`); optional `sharp` only if a raster asset actually needs build-time resizing (decided in research). Runtime — Cloudflare Web Analytics beacon (external `<script>`, no npm dependency).
**Storage**: N/A — content lives in-repo as Markdown frontmatter (`src/content/articles/*.md`) and i18n JSON (`public/i18n/*.json`); no MongoDB involvement.
**Testing**: existing Karma/Jasmine (`ng test`) for components; Node SEO scripts as executable acceptance (`npm run seo:audit`), extended with the new assertions; new `npm run seo:cwv` lab check. Verification by parsing build bytes (Constitution V).
**Target Platform**: Cloudflare Pages static hosting — prerendered HTML for public routes + SPA fallback (`/index.html 200`) for private routes. Mobile-first modern browsers.
**Project Type**: Web — frontend only for this feature.
**Performance Goals**: lab CWV "good" on home, `/articles`, and an article-detail template — LCP < 2.5s, INP proxy (TBT) < 200ms, CLS < 0.1 (mobile profile). Field p75 is post-launch monitoring, not a gate.
**Constraints**: cookieless, no-consent analytics; beacon present on 100% of prerendered public pages (site-wide; see research R2 — true absent-on-private is infeasible under the SPA-fallback topology and unnecessary for a cookieless beacon); build-time generation from a single source (no manual drift, manifest existence/freshness guarded); any new Windows-executed script must be pure ASCII (Constitution V — but new tooling is Node `.mjs`, UTF-8/LF, not Windows-executed); no new backend service/instance.
**Scale/Scope**: ~9 published UK articles + 4 existing static public pages + 2-3 new static pages (Editorial/About, Contact); a small static site. No scaling concerns.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verified against `.specify/memory/constitution.md` (v1.0.0).

- [x] **I. MVP Discipline**: Frontend-only; no new service/instance, queue, or scaling infra. Single backend + Mongo + LLM topology untouched. Lighthouse is a dev-only tool, not deployment infra. Cloudflare Web Analytics is a host-native feature, not new infrastructure. Scope is bounded to the spec's in-repo gaps.
- [x] **II. Fixed Stack**: Angular 17+/Java 21/Spring Boot/Mongo unchanged. No EOL/deprecated dependency added; the Lighthouse runner (and `sharp` if used) are current release lines, dev-only. No provider abstraction touched.
- [x] **III. LLM Guardrails**: Not applicable — this feature makes no LLM calls and does not touch `InvestmentAdvisorService`, prompts, catalog grounding, or token budgets.
- [x] **IV. Financial Integrity**: Not applicable — no money, tokens, ledger, or payment paths are touched. The mandatory financial disclaimer on article pages is preserved (existing audit check A8 retained).
- [x] **V. Encoding & Verification**: New build tooling is Node ESM (`.mjs`, UTF-8/LF) — not Windows-executed, so the Windows-1252 hazard does not apply; no new `.ps1/.cmd/.bat`. Verification is by running `seo:audit`/`seo:cwv` and `ng test` (parse/run), not by reading. Ukrainian/non-ASCII content stays in `.md`/`.json`/`.ts` (UTF-8), never in executed Windows scripts.
- [x] **VI. Multi-Role Review**: At least two role sub-agents (SEO/AEO specialist + FE-lead/QA, plus DevOps for the deploy/analytics surface) will review, including an actual scan/parse of the generated artifacts, before the work is marked done.

**Result**: PASS — no violations. Complexity Tracking left empty.

## Project Structure

### Documentation (this feature)

```text
specs/010-seo-aeo-optimization/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (build-artifact + audit contracts)
│   ├── seo-manifest.contract.md
│   ├── llms-txt.contract.md
│   ├── robots-txt.contract.md
│   ├── structured-data.contract.md
│   └── seo-audit.contract.md
├── checklists/
│   └── requirements.md  # from /speckit.specify
└── tasks.md             # /speckit.tasks output (NOT created here)
```

### Source Code (repository root)

All changes are under `frontend/`. No `backend/` changes.

```text
frontend/
├── tools/
│   └── seo/
│       ├── build-articles.mjs      # EXTEND: emit seo-manifest.json; enforce answer-first + >=2 inbound; add `answer` frontmatter
│       ├── generate-sitemap.mjs    # EXTEND: consume manifest (THROW if missing/stale); per-URL true lastmod; AI-crawler stanzas; emit llms.txt
│       ├── seo-audit.mjs           # EXTEND: new assertions (AI stanzas, llms.txt, real token, trust block, lastmod, no-en, img alt, sameAs/@id, new routes)
│       └── cwv-audit.mjs           # NEW: Lighthouse lab CWV on 3 templates (Chrome via existing puppeteer; median-of-3; advisory, not a blocking deploy gate)
├── src/
│   ├── index.html                  # (analytics injected at runtime, not hard-coded here)
│   ├── content/articles/*.md       # EDIT: add `answer` frontmatter; raise inbound cross-links to >=2
│   └── app/
│       ├── app.routes.ts           # ADD: /editorial-policy (or /about), /contact routes
│       ├── core/seo/
│       │   ├── seo-routes.config.ts        # ADD new PUBLIC_PAGES; keep EN_ENABLED guard
│       │   ├── structured-data.service.ts  # EDIT: Organization @id + sameAs; Article publisher @id ref
│       │   └── analytics.service.ts        # NEW: inject Cloudflare beacon site-wide at app bootstrap (ngOnInit, like setBase)
│       └── features/
│           ├── articles/article-detail.component.ts  # EDIT: render editorial trust block + answer lead
│           └── pages/ (editorial-policy, contact)    # NEW: content components (reuse LegalDocument pattern)
└── public/
    ├── i18n/{uk,en}.json           # ADD: seo + page copy keys (titles 110-160 desc, trust block, editorial/contact body)
    └── seo/
        ├── google<REAL_TOKEN>.html # ADD real verification file AND delete googleREPLACE_WITH_REAL_TOKEN.html
        ├── _redirects             # ADD SPA fallbacks for any new private route (none expected; new pages are public/prerendered)
        └── _headers               # (unchanged; new HTML inherits revalidate policy)
```

**Structure Decision**: Single existing Angular project under `frontend/`. The feature is delivered by **extending** the established build-time SEO toolchain and SEO services rather than adding new architecture. The one new structural element is a generated `seo-manifest.json` (build artifact, git-ignored) acting as the single source of truth for sitemap/robots/llms generation.

## Complexity Tracking

> No constitution violations — section intentionally empty.
