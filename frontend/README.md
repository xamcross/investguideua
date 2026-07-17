# InvestGuideUA — Frontend

This directory is the Angular 17+ (standalone components) SPA: routing, auth interceptors and the
feature pages delivered by the FE-* epic in `TASKS.md` (the X1-era static placeholder shell has
been removed).

## SEO (features 006, 010, 013)

Public pages are **prerendered to static HTML** at build time (Angular `@angular/ssr`, SSG mode -
no production server). Crawlers and social cards see real content, per-page titles/descriptions,
canonical + hreflang, Open Graph/Twitter tags, per-article OG images (`/og/<slug>.png`), and
JSON-LD (Organization, WebSite, Article, BreadcrumbList, FAQPage - every published article
carries a visible FAQ that mirrors the FAQPage markup).

After prerendering, the output is **flattened** (`<route>/index.html` -> `<route>.html`) so
Cloudflare Pages serves the canonical non-slash URLs (`/articles/x`) with `200` instead of a
`308` to the slash variant. Fonts are **self-hosted** (`public/fonts/`, latin+cyrillic woff2
subsets, `src/fonts.css`) - no third-party font requests.

Commands:

- `npm run build` - the full pipeline: `seo:articles` (compile/validate markdown articles) ->
  `seo:og` (render per-article OG images) -> `ng build` (prerender) -> `seo:generate` (flatten +
  `robots.txt`/`sitemap.xml`/`llms.txt`; reads `SEO_SITE_ORIGIN`, default
  `https://investguideua.com`).
- `npm run seo:audit` - the SEO acceptance check (fails the build on any violation, including
  non-production canonical origins and un-flattened output).
- `npm run seo:all` - build + generate + audit.
- `node tools/seo/fetch-fonts.mjs` - one-time font refetch (only when the family/weight set changes).

Add an article: create `src/content/articles/<slug>.uk.md` with frontmatter per
`specs/006-seo-optimization/contracts/article-frontmatter.schema.md` and the editorial checklist
`src/content/articles/REVIEW-CHECKLIST.md` (published articles need `answer`, at least one
question-style `## ...?` heading, `faq` with >= 2 entries, and >= 2 inbound `relatedSlugs` from
peer articles), set `status: published`, then `npm run seo:all`. The new URL is prerendered,
added to the sitemap/llms.txt, gets an OG image, and is validated automatically.

Static SEO assets live in `public/seo/` (`_redirects`, `_headers`, `404.html`, `og-default.png`,
`site.webmanifest`) and are copied to the output root by the `angular.json` assets glob. See
`specs/006-seo-optimization/quickstart.md` for the full runbook.
