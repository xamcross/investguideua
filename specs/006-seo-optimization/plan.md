# Implementation Plan: SOTA SEO Foundation

**Branch**: `006-seo-optimization` | **Date**: 2026-06-04 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-seo-optimization/spec.md`

## Summary

Make the InvestGuideUA public surface discoverable and competitive in organic search. Today
the app is a client-rendered Angular 17 SPA on Cloudflare Pages with no `robots.txt`, no
sitemap, one generic title/description for every URL, no structured data, and no editorial
content — effectively invisible to non-JS crawlers and social cards.

The plan delivers three pillars without adding any production server or backend service
(Constitution Principle I): **(1) crawlable rendering** via build-time **static prerendering**
(`@angular/ssr` prerender, static deploy — no live SSR); **(2) per-page optimization** — a
`SeoService` + `StructuredDataService` that bake unique titles/descriptions, canonical +
hreflang, Open Graph/Twitter cards, and JSON-LD (Organization, WebSite, Article, Breadcrumb,
FAQ) into each prerendered page; and **(3) content** — an articles section sourced from in-repo
Markdown+frontmatter files (uk-first, en to follow), plus `robots.txt`, an auto-generated
`sitemap.xml`, true-404 handling, search-console verification, and a CI **SEO acceptance audit**
that is the executable definition of done. All decisions are recorded in
[research.md](./research.md).

## Technical Context

**Language/Version**: TypeScript 5.4 / Angular 17.3 (frontend only); Node 18+ for build-time
prerender + generators. No backend (Java) changes.
**Primary Dependencies**: Angular 17 (standalone), `@angular/router`, `ngx-translate`
(existing); **add** `@angular/ssr` + `@angular/platform-server` (first-party, current v17 line)
for prerendering; a maintained Markdown parser with frontmatter for article content
(build-time). No deprecated `@nguniversal/*`.
**Storage**: Articles as in-repo content files (`frontend/src/content/articles/*.md`); no
database. (Backend MongoDB untouched.)
**Testing**: Existing Karma + Jasmine unit runner (`ng test`) for services/initializers/
generators; a Node post-build **SEO audit script** run in CI; lab Lighthouse for Core Web
Vitals.
**Target Platform**: Static site on Cloudflare Pages (prerendered HTML + SPA fallback for
private routes). HTTPS, single canonical origin.
**Project Type**: Web application — **frontend-only change** for this feature (plus static SEO
assets). No new service.
**Performance Goals**: Core Web Vitals "good" at p75 — LCP <= 2.5s, INP <= 200ms, CLS <= 0.1
(SC-007). Prerendering improves LCP by shipping HTML.
**Constraints**: No production server runtime, no new infra (Principle I); titles <= 60 chars,
descriptions 110-160 chars; non-JS crawler must see primary content; unknown URLs return true
404; canonical origin via `SEO_SITE_ORIGIN` build config (not hard-coded).
**Scale/Scope**: Public indexable surface = home, articles index, >= 8 seed articles, terms,
privacy, in uk + en (~22-40 prerendered URLs). Private routes excluded from indexing.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

Verified against `.specify/memory/constitution.md` (v1.0.0).

- [x] **I. MVP Discipline**: No new service/instance, queue, or scaling infra. Prerendering runs
      at **build time only**; production stays a static deploy. Single backend + single MongoDB
      + managed LLM untouched. Scope limited to public SEO surface.
- [x] **II. Fixed Stack**: Angular 17 / Java 21 / Spring Boot 3 / MongoDB 7 unchanged. Added
      deps (`@angular/ssr`, `@angular/platform-server`, a maintained markdown+frontmatter parser)
      are current, first-party/supported lines; deprecated `@nguniversal/*` explicitly rejected.
      No provider abstraction touched.
- [x] **III. LLM Guardrails**: No LLM calls added or changed. Articles are human-authored, not
      LLM-served at runtime; the catalog-grounding rule is honored editorially (no off-catalog
      providers in content).
- [x] **IV. Financial Integrity**: No money/token code touched. The standard financial
      disclaimer is **extended** to article pages (consistent with the disclaimer mandate); no
      ledger or currency math involved.
- [x] **V. Encoding & Verification**: Any new Windows-executed `.ps1`/`.cmd` stays pure ASCII;
      Node/TS generators and `.md`/`.xml`/`.conf` assets are LF and may be UTF-8 (Ukrainian
      content). "Done" is gated by the Node SEO audit + `ng test` parse/run, not by reading.
- [x] **VI. Multi-Role Review**: At least two role sub-agents (incl. an actual scan/parse) will
      review this plan and the implementation before each is marked done.

**Result**: PASS, no violations. Complexity Tracking table not required.

## Project Structure

### Documentation (this feature)

```text
specs/006-seo-optimization/
├── plan.md              # This file
├── research.md          # Phase 0 output (decisions)
├── data-model.md        # Phase 1 output (entities)
├── quickstart.md        # Phase 1 output (build/verify/add-article runbook)
├── contracts/           # Phase 1 output (SEO interface contracts)
│   ├── seo-acceptance.md
│   ├── article-frontmatter.schema.md
│   ├── sitemap-robots.contract.md
│   └── structured-data.contract.md
└── tasks.md             # Phase 2 output (/speckit.tasks - NOT created here)
```

### Source Code (repository root)

This feature changes the **frontend** only and adds **static SEO assets**. No backend changes.

```text
frontend/
├── angular.json                 # add prerender/server target; output splits into browser/+server/;
│                                 # CF Pages output dir + audit + sitemap must target dist/.../browser
├── package.json                 # add @angular/ssr@^17.3 + @angular/platform-server@^17.3 (pinned);
│                                 # devDeps gray-matter + markdown-it (build-time only, not in bundle)
├── public/
│   ├── i18n/{uk,en}.json         # existing runtime dictionaries (also read at prerender)
│   ├── og-default.png            # NEW default social-share image
│   ├── google<token>.html        # NEW search-console verification file
│   ├── 404.html                  # NEW static branded 404 (served with HTTP 404 for unknown URLs)
│   └── _redirects                # NEW: private routes (bare + /*) -> /index.html 200; else real 404
├── src/
│   ├── app/
│   │   ├── app.routes.ts         # add /articles, /articles/:slug, /en mirror; mark prerenderable
│   │   ├── app.config.ts         # provide TransferState-aware translate loader, SEO providers
│   │   ├── app.config.server.ts  # NEW server config for prerender (platform-server)
│   │   ├── core/
│   │   │   └── seo/              # NEW: SeoService, StructuredDataService, locale-prefix init,
│   │   │       └── ...           #      server-side filesystem TranslateLoader
│   │   └── features/
│   │       └── articles/        # NEW: articles-index + article-detail components, content loader
│   └── content/
│       └── articles/            # NEW: {slug}.uk.md (+ {slug}.en.md) with SEO frontmatter
├── tools/seo/                    # NEW build-time generators + audit (Node/TS)
│   ├── generate-sitemap.ts       # emits sitemap.xml + robots.txt from route+article source
│   └── seo-audit.ts              # post-build acceptance check (FR-032 / SC-012)
└── main.server.ts                # NEW prerender entry (build-time only; not deployed as server)
```

**Structure Decision**: Web-application repo, but this feature is implemented entirely in
`frontend/` plus build tooling under `frontend/tools/seo/` and static assets under
`frontend/public/`. Production remains a **static Cloudflare Pages deployment**; the server
bundle (`main.server.ts`, `app.config.server.ts`) exists solely to drive build-time
prerendering and is never run as a service. This keeps the deployment topology unchanged and
satisfies Constitution Principle I.

> NOTE: The tree diagrams above are display-only and contain non-ASCII box-drawing characters.
> Do NOT paste them into a Windows-executed script (`.ps1`/`.cmd`/`.bat`) - doing so
> reintroduces the Windows-1252 corruption that Constitution Principle V prevents.

## Phase 1 Design Artifacts

- **[data-model.md](./data-model.md)** — Public Page, Article (frontmatter schema), Sitemap
  Entry, Crawl Directive Set, Structured-Data Record, Search Console Property; fields,
  validation rules (length bounds, status, language), and the article publish state machine.
- **[contracts/](./contracts/)** — the SEO "interfaces" this feature exposes:
  - `seo-acceptance.md` — the machine-checkable acceptance contract (maps each SC/FR to an
    assertion the audit script enforces).
  - `article-frontmatter.schema.md` — required/optional frontmatter fields + validation.
  - `sitemap-robots.contract.md` — sitemap XML shape, hreflang alternates, robots directives,
    inclusion/exclusion rules.
  - `structured-data.contract.md` — JSON-LD shapes per page type and required properties.
- **[quickstart.md](./quickstart.md)** — runbook: build + prerender, run the SEO audit, add a
  new article, set `SEO_SITE_ORIGIN`, register/verify in Search Console.

## Complexity Tracking

No constitution violations to justify. (Table intentionally empty.)
