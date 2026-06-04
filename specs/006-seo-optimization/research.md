# Phase 0 Research: SOTA SEO Foundation

**Feature**: `006-seo-optimization` | **Date**: 2026-06-04

This document resolves the open technical decisions the spec deferred to planning. The
binding constraint throughout is Constitution Principle I (MVP discipline: **no new service,
instance, queue, or scaling infra; no production server runtime added**) and Principle II
(fixed, current stack; no deprecated dependency).

---

## R1. Crawlable rendering approach (the central decision)

**Decision**: **Build-time static prerendering (SSG)** of public routes using Angular 17's
first-party `@angular/ssr` + `@angular/platform-server`, driven by the existing
`@angular-devkit/build-angular:application` builder's `prerender` option. The production
deployment stays a **static site on Cloudflare Pages** — no Node server runs in production.

**Rationale**:
- The public, indexable surface is tiny and fully enumerable at build time: home, articles
  index, each article (slugs known from in-repo content files), terms, privacy — in two
  languages. SSG produces real HTML per URL with content + metadata + JSON-LD baked in,
  satisfying FR-007/008, SC-001/008.
- Prerendering emits static `.html` files; Cloudflare Pages serves them with no server
  runtime. This honors Principle I (no new service, no SSR server to operate/pay for) far
  better than live SSR.
- The builder is already `:application` (confirmed in `angular.json`), which has built-in
  `prerender` support — no builder migration needed.
- Private/auth routes (search, account, history, tokens, payments, providers, login,
  register, verify) are **not** prerendered; they remain a client-rendered SPA bootstrapped
  from `index.html`. They must not be indexed anyway (FR-005), so they lose nothing.

**Alternatives considered**:
- **Live SSR (Angular Universal-style, Node server on Cloudflare Pages Functions/Workers)**:
  rejected — adds a production server runtime and operational surface, violating Principle I
  for zero benefit here (content is static, not per-request personalized).
- **Dynamic-rendering / prerender proxy (serve a snapshot to bots only)**: rejected — this is
  cloaking-adjacent, Google discourages it, it adds a service, and it fails FR-008.
- **Keep CSR + rely on Googlebot's JS rendering**: rejected — unreliable, slow to index, no
  content for non-JS crawlers/social cards, fails SC-008 and FR-011.
- **Deprecated `@nguniversal/*` packages**: rejected — EOL; superseded by `@angular/ssr`
  (Principle II forbids deprecated deps).

**Key implementation notes captured for Phase 1**:
- Add `@angular/ssr` + `@angular/platform-server` (current v17 line, first-party Angular).
- Enable `"prerender": true` and a server build (`"ssr"`/server entry) in the production
  build target. The server bundle is used **only** at build time to generate HTML; it is not
  deployed as a running server.
- Provide an explicit **route list to prerender** (public routes x languages + every article
  URL) -- an explicit public allow-list, NOT auto-derived from the route table (guarded private
  routes must never be prerender targets). Article URLs are discovered by reading the in-repo
  article content index (see R4) so the set updates automatically when articles change (FR-003).
- **Output path**: enabling prerender/server on the `:application` builder splits the build into
  `dist/<app>/browser/` (static, deployed) and `dist/<app>/server/` (build-time only, NOT
  deployed). The Cloudflare Pages "build output directory" and the sitemap/audit scripts MUST
  target `dist/<app>/browser/`; the current flat `outputPath` is updated accordingly. Deploy the
  `browser/` folder only -- shipping `server/` or adding a CF Pages Function would reintroduce a
  runtime and violate Principle I.
- **Language initializer is load-bearing and must be synchronous**: a route-prefix language
  initializer MUST set the active language (from the `/en` prefix, else uk) *before* bootstrap
  on both the server (prerender) and browser platforms, because `TranslatedTitleStrategy`,
  canonical/hreflang, article content, and **hydration matching** all depend on it. A
  server/client language mismatch triggers Angular hydration errors and a full re-render. Choose
  one i18n-transfer approach explicitly: (a) TransferState (no client re-fetch, more code), or
  (b) plain client re-fetch of `/i18n/{lang}.json` (less code, requires the synchronous
  initializer to guarantee no hydration mismatch). Either is acceptable; (b) is simpler.

---

## R2. Per-language URL strategy & hreflang

**Decision**: **Locale path prefix with Ukrainian unprefixed (default) and English under
`/en`.** Ukrainian (primary) keeps the existing URLs (`/`, `/articles`, `/terms`, ...);
English mirrors them under `/en/...` (`/en`, `/en/articles`, `/en/terms`, ...).

`hreflang` annotations per page:
- `uk` -> the unprefixed URL
- `en` -> the `/en/...` URL
- `x-default` -> the unprefixed (Ukrainian) URL

**Rationale**:
- Preserves all existing Ukrainian URLs (no redirects/link equity loss); uk is the primary
  audience per spec.
- Distinct, stable, directly-resolvable URLs per language are required for hreflang
  (FR-019) — a runtime language toggle on one URL cannot satisfy this. Path prefix is the
  most crawler-legible scheme and the one Google documents first.
- Each language version can be prerendered independently to its own HTML file.

**Alternatives considered**:
- `/uk` + `/en` (both prefixed): rejected — needlessly breaks current uk URLs and adds a
  redirect from `/`.
- Subdomains / ccTLD per language: rejected — heavier infra, no benefit at this scale.
- Query param (`?lang=en`): rejected — weak canonicalization, poor crawler treatment.

**i18n-during-prerender wrinkle (resolved)**: the app translates at runtime via `ngx-translate`
loading `/i18n/{lang}.json` over HTTP. During prerender (Node), provide a **server/filesystem
TranslateLoader** that reads `public/i18n/{lang}.json` from disk, and a route-level language
initializer that sets the active language from the URL prefix before render. Hand the loaded
dictionary to the browser via Angular **TransferState** so the client does not re-fetch or
flash untranslated keys. Single source of i18n truth is preserved.

---

## R3. robots.txt, sitemap, canonical, true 404 on Cloudflare Pages

**Decision**:
- **robots.txt**: a generated static file at the site root that `Allow`s public paths,
  `Disallow`s private/utility paths (`/login`, `/register`, `/verify`, `/search`, `/history`,
  `/tokens`, `/payments`, `/account`, `/providers`, and their `/en/...` mirrors), and points
  to `Sitemap: https://investguideua.com/sitemap.xml`.
- **sitemap.xml**: generated at build time by a Node script that enumerates the same public
  route + article-slug source the prerenderer uses, emitting `<url>` entries with `<lastmod>`
  and `xhtml:link rel="alternate" hreflang` pairs for uk/en/x-default (FR-002/003/020).
- **canonical**: each prerendered page emits `<link rel="canonical">` to its own absolute URL
  via the SeoService (FR-004).
- **true 404**: do **not** install a catch-all `/* -> /index.html 200` SPA rewrite. Instead,
  add a Cloudflare Pages `_redirects` file that maps only the **known private route prefixes**
  to `/index.html` with status `200` (so the SPA can boot and client-route them), and let all
  other unknown paths fall through to Cloudflare Pages' default `404.html` served with a real
  **404 status** (FR-006, SC-011). Public routes are served as their own prerendered files.

**Rationale**: This is the standard way to avoid the SPA soft-404 trap on static hosting while
still letting authenticated deep-links boot the SPA. Everything is static files + a small
`_redirects` manifest — no server, consistent with Principle I.

**Required details (from review)**:
- Ship a committed **`404.html`** at the site root (generated branded page); CF Pages serves it
  with a real 404 status for any path that matches neither a static asset nor a `_redirects`
  rule. Without it there is no 404 body.
- Map **both the bare path and `/*` children** for every multi-segment private area (e.g.
  `/history` AND `/history/*`); mapping only `/history/*` makes a direct hit to `/history`
  fall through to a 404 instead of booting the SPA.
- Static-asset match takes precedence over `_redirects`, so prerendered public routes are served
  directly (200) and deep-linking to them is unaffected.
- Ensure the CF Pages project has **no SPA framework preset** active — that preset auto-injects a
  catch-all `/* -> /index.html 200` rewrite that would silently re-introduce soft-404s. 404
  behavior is governed by static-asset presence + the committed `_redirects`, NOT by
  `_routes.json` (which is a Functions manifest and irrelevant here).

**Open config**: the canonical origin (`https://investguideua.com`) is treated as build
configuration (`SEO_SITE_ORIGIN`), not hard-coded in components, per the "config not
hard-coding" workflow rule. Default derived from the existing `api.investguideua.com` backend
host; confirm the exact apex/`www` choice and 301-redirect the non-canonical host before
launch.

**Assets wiring note**: `angular.json` currently copies only `public/i18n -> /i18n`. The build
must also emit `robots.txt`, `sitemap.xml`, the default OG image, the search-console
verification file, and `_redirects`/`404.html` to the site root. Add these to the build
`assets`/output (generated files written into the build output directory before deploy).

---

## R4. Article authoring & storage

**Decision**: Articles live as **in-repo content files in the frontend** — Markdown with
YAML frontmatter (one file per article per language, e.g.
`frontend/src/content/articles/{slug}.{lang}.md`) plus a generated typed index. They are
consumed at **build time** for prerendering and sitemap generation, and rendered in the app.
**No backend/MongoDB changes.**

**Rationale**:
- The spec and constitution put admin/content on a seed-file footing for the MVP (no CMS UI).
  Prerendering needs the content available at build time; in-repo files are the simplest
  source that satisfies both, with zero new backend service (Principle I).
- Frontmatter carries the per-article SEO fields the data model requires (title, description,
  topic mapping, dates, OG image, related slugs, status) — making FR-023/024/026 and the
  acceptance check mechanical to verify.
- Adding/editing/removing a file updates prerender targets, the articles index, and the
  sitemap from one source (FR-003/028).

**Alternatives considered**:
- **MongoDB collection + backend API**: rejected for MVP — adds backend endpoints and couples
  build-time prerender to a running API; more moving parts than the content justifies. Can be
  migrated later behind the same article-index abstraction without changing URLs.
- **Headless CMS**: rejected — new external service, against Principle I and out of scope.

**Content guardrails (FR-024/025, SC-013)**: each article frontmatter records `primaryTopic`
(keyword mapping) and `reviewedBy`/`reviewedOn` (editorial sign-off). A documented editorial
checklist gates publication. Provider/instrument mentions must match the curated catalog and
link to the provider source for any cited figure; the standard financial disclaimer component
is rendered on every article page. Accuracy is a human sign-off, recorded in frontmatter, and
asserted (presence, not truth) by the acceptance check.

---

## R5. Structured data (JSON-LD)

**Decision**: Inject JSON-LD `<script type="application/ld+json">` per route via a
`StructuredDataService` writing into `<head>` (rendered into static HTML during prerender):
- **Organization** + **WebSite** (with `inLanguage` and a `potentialAction` SearchAction
  pointing at the public search entry) — site-wide, on every public page.
- **Article** (`headline`, `inLanguage`, `datePublished`, `dateModified`, `image`,
  `author`/`publisher`=Organization) — on each article.
- **BreadcrumbList** — on articles and the articles index.
- **FAQPage** — only on pages whose visible content is genuinely Q&A (FR-017), driven by an
  optional `faq` block in article frontmatter.

**Rationale**: Matches FR-014-018; all types validate against schema.org and are
Google-rich-result eligible. SearchAction is advertised even though the internal search route
is noindex — the action target resolves for users; this is allowed and is not a contradiction.

**Alternatives**: Microdata/RDFa — rejected; JSON-LD is Google's recommended format and easiest
to render/validate.

---

## R6. Per-page metadata service

**Decision**: A single `SeoService` sets, per navigation, the document title, meta description,
canonical link, hreflang alternates, and Open Graph + Twitter Card tags, sourced from route
`data` + (for articles) frontmatter + ngx-translate for localized strings. Enforces the
length bounds (title <=60, description 110-160) defensively and logs violations in dev.

**Rationale**: Centralizes FR-010-013/019 and makes the values prerender into static HTML.
Title length is also enforced by the acceptance check (R7).

**Default OG image**: a static branded image shipped at the site root; per-article images via
frontmatter (FR-012).

---

## R7. SEO acceptance check (FR-032 / SC-012) and testing

**Decision**: A **Node-based post-build SEO audit script** that runs against the prerendered
build output directory (`dist/.../browser`) and **fails the build/CI** on any violation:
- every public URL's HTML contains its primary text (non-empty `<main>`/article body),
- unique title (<=60 chars) and description (110-160) per language,
- `<link rel="canonical">` present and self-referential,
- correct hreflang set incl. `x-default`,
- JSON-LD present and schema-valid for the declared types (zero errors),
- OG/Twitter tags present with an image,
- `robots.txt` well-formed and references the sitemap,
- `sitemap.xml` schema-valid, contains 100% of public URLs and **0** private/auth/duplicate
  URLs,
- article pages carry Article JSON-LD + last-updated + disclaimer + topic mapping + sign-off.

Plus Angular **Karma/Jasmine unit specs** (existing test runner, Principle II) for SeoService,
StructuredDataService, the language-prefix initializer, and the sitemap/robots generators.
Core Web Vitals (SC-007) verified with a **lab Lighthouse run** pre-release; field data
confirmed post-launch in Search Console (per SC-012's lab-vs-field split).

**Rationale**: This audit is the executable, repeatable definition of done for the SEO gate,
runnable in CI with no live server. It directly maps to the success criteria so verification is
mechanical, not visual (Constitution Principle V).

---

## R8. Search Console verification & monitoring

**Decision**: Verify ownership via a **static HTML verification file** at the site root (and a
fallback `google-site-verification` meta tag on the home page). Use Google Search Console
(optionally Bing Webmaster Tools) for indexing + performance + page-experience monitoring
(FR-031, SC-010/011). DNS TXT verification is an acceptable alternative if domain DNS is
manageable; file-based avoids DNS coupling and is the default.

**Rationale**: File-based verification is a static asset (no server), aligns with Principle I,
and is independent of the DNS provider.

---

## Resolved unknowns summary

| Spec deferral | Resolution |
|---|---|
| Crawlable rendering technique | Build-time SSG via `@angular/ssr` prerender; static deploy, no prod server |
| Per-language URL strategy | uk at root (default/x-default), en under `/en`; path-prefix hreflang |
| Sitemap generation owner | Build-time Node generator from the article/route source |
| True 404 vs SPA fallback | `_redirects` maps only private routes to index 200; unknown -> real 404 |
| Article storage | In-repo Markdown+frontmatter content files; no backend change |
| Canonical origin | `SEO_SITE_ORIGIN` build config, default `https://investguideua.com` (confirm apex/www) |
| Search-console verification | Static verification file at root (+ meta fallback) |
| Acceptance check | Node post-build SEO audit script + Karma unit specs + lab Lighthouse |
