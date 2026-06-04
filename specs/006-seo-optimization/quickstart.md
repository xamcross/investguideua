# Quickstart: SOTA SEO Foundation

**Feature**: `006-seo-optimization`

A runbook for building, verifying, and operating the SEO foundation. All commands run from
`frontend/`. Windows note: any `.ps1`/`.cmd` helper must be pure ASCII (Constitution
Principle V); the generators here are Node/TS and are invoked via npm scripts.

## 1. One-time setup

```bash
# Add prerender deps. MUST match the existing Angular 17.3 line - an unpinned `npm i` pulls
# the latest major (18/19/20) and will fail to build. Prefer `ng add @angular/ssr` (it
# version-matches automatically) or pin explicitly:
npm i @angular/ssr@^17.3.0 @angular/platform-server@^17.3.0
# Build-time-only markdown + frontmatter parser as devDependencies (NOT shipped to the
# browser bundle - article bodies are rendered to static HTML at prerender, keeping the
# parser out of the client bundle so the 500kb/1mb budgets in angular.json are not blown):
npm i -D gray-matter markdown-it     # currently-supported, actively-maintained (Principle II)

# Set the canonical origin (config, not hard-coded). Confirm apex vs www before launch.
# Defaults to https://investguideua.com if unset.
export SEO_SITE_ORIGIN="https://investguideua.com"   # PowerShell: $env:SEO_SITE_ORIGIN="..."
```

`angular.json` is configured so the production build prerenders public routes and emits the SEO
assets (robots.txt, sitemap.xml, _redirects, 404.html, og-default.png, verification file) to the
build output root. Note: enabling prerender splits output into `dist/<app>/browser/` (deploy
this) and `dist/<app>/server/` (build-time only, do NOT deploy). Point the Cloudflare Pages
"build output directory" and the `seo:generate`/`seo:audit` scripts at `dist/<app>/browser/`.

## 2. Build (with prerender) + generate sitemap/robots

```bash
npm run build           # production build; prerenders public routes (uk + /en)
npm run seo:generate    # writes sitemap.xml + robots.txt from routes + published articles
```

Production stays a **static** Cloudflare Pages deploy — the server bundle is used only to
prerender at build time and is never deployed as a running server.

## 3. Verify (the definition of done)

```bash
npm run seo:audit       # runs tools/seo/seo-audit.ts against the build output (A1-A15)
ng test                 # Karma/Jasmine units for SeoService, StructuredDataService, generators
npm run lighthouse      # lab Core Web Vitals check (LCP<=2.5s, INP<=200ms, CLS<=0.1)
```

`seo:audit` exits non-zero on any failed assertion; CI blocks merge on failure. See
[contracts/seo-acceptance.md](./contracts/seo-acceptance.md) for the full assertion list.

## 4. Add or edit an article

1. Create `src/content/articles/{slug}.uk.md` (and `{slug}.en.md` when the English version is
   ready) with frontmatter per
   [contracts/article-frontmatter.schema.md](./contracts/article-frontmatter.schema.md).
2. Write the body. Link every cited rate/figure to its provider source. Only reference providers
   in the curated catalog. Fill `primaryTopic`, `reviewedBy`, `reviewedOn`.
3. Set `status: published`. Add `relatedSlugs`.
4. `npm run build && npm run seo:generate && npm run seo:audit` — the new URL is prerendered,
   added to the sitemap, and validated. No manual sitemap editing (FR-003/028).

To retire an article, set `status: unpublished` (it drops from prerender, index, sitemap, and
returns 404 if hit directly).

## 5. Search Console (post-deploy)

1. Add the property for `SEO_SITE_ORIGIN` in Google Search Console.
2. Verify via the static `public/google<token>.html` file (already deployed) or DNS TXT.
3. Submit `sitemap.xml`.
4. Monitor: index coverage, query impressions (SC-010), soft-404s (SC-011), Core Web Vitals
   field data (SC-007).

## 6. Definition of done (gates)

- [ ] `npm run seo:audit` passes for every public URL (A1-A16).
- [ ] `ng test` passes (no parse/compile errors).
- [ ] Hydration smoke check passes: a prerendered public route loads with no Angular hydration
      error and stays interactive (FR-009).
- [ ] `404.html` is emitted and an unknown URL returns HTTP 404; the CF Pages project has no SPA
      catch-all preset.
- [ ] Lab Lighthouse meets CWV thresholds.
- [ ] >= 8 published Ukrainian articles, each with topic mapping + sign-off + disclaimer.
- [ ] Two role sub-agents reviewed, including an actual scan/parse (Constitution Principle VI).
- [ ] `SEO_SITE_ORIGIN` set to the confirmed canonical host; non-canonical host 301-redirects.
