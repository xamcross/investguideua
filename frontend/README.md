# InvestGuideUA — Frontend

This directory is the Angular 17+ (standalone components) SPA.

**X1 status:** placeholder. X1 only requires a static SPA that builds and is served by Docker
Compose, with the backend reachable at `/api/v1`. The full Angular application — routing, auth
interceptors, feature pages — is delivered by the **FE-CORE1** ticket and the rest of the FE-*
epic in `TASKS.md`.

The current `public/index.html` is a minimal static shell that calls `GET /api/v1/ping` to
prove backend connectivity. When FE-CORE1 lands, replace this folder with the generated Angular
workspace and have `Dockerfile` run `ng build`, serving `dist/` via nginx with the same
`/api/v1` proxy configured in `nginx.conf`.

## SEO (feature 006-seo-optimization)

Public pages are **prerendered to static HTML** at build time (Angular `@angular/ssr`, SSG mode -
no production server). Crawlers and social cards see real content, per-page titles/descriptions,
canonical + hreflang, Open Graph/Twitter tags, and JSON-LD (Organization, WebSite, Article,
BreadcrumbList, FAQPage).

Commands:

- `npm run build` - runs `seo:articles` then prerenders public routes to `dist/investguide-frontend/browser/`.
- `npm run seo:generate` - writes `robots.txt` + `sitemap.xml` into the build output (reads `SEO_SITE_ORIGIN`, default `https://investguideua.com`).
- `npm run seo:audit` - the SEO acceptance check (fails the build on any violation).
- `npm run seo:all` - build + generate + audit.

Add an article: create `src/content/articles/<slug>.uk.md` with frontmatter per
`specs/006-seo-optimization/contracts/article-frontmatter.schema.md` and the editorial checklist
`src/content/articles/REVIEW-CHECKLIST.md`, set `status: published`, then `npm run seo:all`. The
new URL is prerendered, added to the sitemap, and validated automatically.

Static SEO assets live in `public/seo/` (`_redirects`, `404.html`, `og-default.png`, the
search-console verification file) and are copied to the output root by the `angular.json` assets
glob. See `specs/006-seo-optimization/quickstart.md` for the full runbook.
