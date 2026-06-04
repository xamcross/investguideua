# Contract: Sitemap & robots.txt

**Feature**: `006-seo-optimization`

Both files are generated at build time by `frontend/tools/seo/generate-sitemap.ts` from one
route-classification + published-article source, and emitted to the site root. `{ORIGIN}` =
`SEO_SITE_ORIGIN` (default `https://investguideua.com`).

## sitemap.xml

- Sitemaps 0.9 namespace + `xhtml` namespace for hreflang alternates.
- One `<url>` per published Public Page (every language version is its own `<url>`).
- Each `<url>` carries `<loc>`, `<lastmod>` (article `dateModified`, else build date), and an
  `xhtml:link rel="alternate" hreflang="..."` for `uk`, `en`, and `x-default`.

```xml
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"
        xmlns:xhtml="http://www.w3.org/1999/xhtml">
  <url>
    <loc>{ORIGIN}/articles/ovdp-war-bonds</loc>
    <lastmod>2026-06-10</lastmod>
    <xhtml:link rel="alternate" hreflang="uk" href="{ORIGIN}/articles/ovdp-war-bonds"/>
    <xhtml:link rel="alternate" hreflang="en" href="{ORIGIN}/en/articles/ovdp-war-bonds"/>
    <xhtml:link rel="alternate" hreflang="x-default" href="{ORIGIN}/articles/ovdp-war-bonds"/>
  </url>
  <!-- ... home, articles index, terms, privacy, each article, both languages ... -->
</urlset>
```

### Inclusion / exclusion (SC-003)

- INCLUDE: `/`, `/articles`, `/articles/{slug}` (status=published), `/terms`, `/privacy`, and
  the `/en/...` mirror of each.
- EXCLUDE (must never appear): all private/utility routes and their `/en` mirrors; any
  `draft`/`unpublished` article; duplicate/parameterized variants.

## robots.txt

```text
User-agent: *
Allow: /
Allow: /articles
Allow: /en/articles
Disallow: /login
Disallow: /register
Disallow: /verify
Disallow: /search
Disallow: /history
Disallow: /tokens
Disallow: /payments
Disallow: /account
Disallow: /providers
Disallow: /en/login
Disallow: /en/register
Disallow: /en/verify
Disallow: /en/search
Disallow: /en/history
Disallow: /en/tokens
Disallow: /en/payments
Disallow: /en/account
Disallow: /en/providers

Sitemap: {ORIGIN}/sitemap.xml
```

## Cloudflare Pages routing (`_redirects`)

Avoid the catch-all `200` SPA rewrite. Map only private prefixes to the SPA shell; let unknown
paths fall through to a real `404` (FR-006 / SC-011).

> **CRITICAL deploy change.** The current deploy (`docs/DEPLOY-cloud.md`) post-build writes a
> catch-all `printf '/*  /index.html  200' > .../browser/_redirects`. That is the soft-404 trap
> itself and MUST be removed. Also note Angular 17.3's `application` builder does **not**
> auto-copy `public/` (that arrived in Angular 18), so `_redirects`/`404.html`/`og-default.png`/
> verification file must reach `dist/<app>/browser/` via explicit `angular.json` `assets` globs
> or be written there by the build/generate step. The generator (and the deploy command) MUST
> emit the private-only `_redirects` below into the output root and must NOT emit any `/* 200`
> rule.

```text
# Private routes: serve SPA shell so client routing + guards can run (status 200).
# Each private area needs BOTH the bare path AND its /* children mapped, or a direct
# deep-link to the bare path (e.g. /history) falls through to a real 404 and never boots
# the SPA. List bare-then-wildcard for every multi-segment area.
/login            /index.html   200
/register         /index.html   200
/verify           /index.html   200
/search           /index.html   200
/history          /index.html   200
/history/*        /index.html   200
/tokens           /index.html   200
/payments/result  /index.html   200
/account          /index.html   200
/providers        /index.html   200
/en/login         /index.html   200
/en/register      /index.html   200
/en/verify        /index.html   200
/en/search        /index.html   200
/en/history       /index.html   200
/en/history/*     /index.html   200
/en/tokens        /index.html   200
/en/payments/result  /index.html   200
/en/account       /index.html   200
/en/providers     /index.html   200
# Public routes are prerendered static files served directly (static-asset match takes
# precedence over _redirects). Everything else -> a committed static 404.html served with
# HTTP 404 (NO catch-all `/* /index.html 200` rule, and the CF Pages project must NOT have
# the SPA framework preset that auto-injects one).
```

**Canonical private-path set** (single source of truth across robots `Disallow`, `_redirects`,
and the noindex classification): `/login`, `/register`, `/verify`, `/search`, `/history`
(+ `/history/:id`), `/tokens`, `/payments/result`, `/account`, `/providers`, and each `/en/...`
mirror. robots uses path-prefix `Disallow` (so `Disallow: /payments` covers `/payments/result`);
`_redirects` maps both the bare path and the `/*` children so authenticated deep-links boot the
SPA. `404.html` is generated at build time and emitted to the site root.

> NOTE: `_redirects` and `robots.txt` are LF, UTF-8, consumed by Cloudflare/crawlers — not
> Windows-executed scripts. If a generator is ever written as `.ps1`, that script must be pure
> ASCII (Constitution Principle V); prefer the Node/TS generator here.
