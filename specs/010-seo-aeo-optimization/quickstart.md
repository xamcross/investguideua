# Quickstart: SEO & AI-Search Traffic Maximization

Frontend-only feature. All commands run from `frontend/`. Windows PowerShell or bash.

## Prerequisites
- Node 20+, the existing `frontend/` deps installed (`npm ci`).
- Chrome available via the existing `puppeteer` devDependency (`puppeteer.executablePath()`) for the CWV lab check.
- Operationally supplied: the real Google Search Console token file; the Cloudflare Web Analytics token.

## Build & verify loop

```bash
# 1. Compile articles -> articles.data.ts + prerender-routes.txt + seo-manifest.json (NEW)
npm run seo:articles      # fails if any published article violates answer-first / >=2 inbound / reviewer gates

# 2. Full build (prerender all public routes)
npm run build

# 3. Generate sitemap.xml + robots.txt (with AI stanzas) + llms.txt (NEW) from the manifest
npm run seo:generate

# 4. Run the acceptance audit (executable definition of done)
npm run seo:audit         # exits non-zero on any violation (existing + new A16'-A25)

# 5. Lab Core Web Vitals (NEW)
npm run seo:cwv           # Lighthouse mobile on /, /articles, one /articles/<slug>

# convenience: build + generate + audit
npm run seo:all
```

## What "done" looks like
- `npm run seo:articles` passes with every published article carrying an `answer`, a `?`-ending `##` heading, and `>= 2` inbound `relatedSlugs`.
- `seo-manifest.json` exists with true per-article `lastmod`.
- `sitemap.xml` article `<lastmod>` == each article's `dateModified` (no uniform build date).
- `robots.txt` has explicit `GPTBot` / `Google-Extended` / `PerplexityBot` / `ClaudeBot` allow stanzas.
- `llms.txt` lists all indexable pages, no private routes.
- Every article page renders the editorial trust block (reviewer + review date + link to `/editorial-policy`).
- `/editorial-policy` and `/contact` are prerendered, indexable, with valid title/description.
- A real `google<token>.html` is present AND the `googleREPLACE_WITH_REAL_TOKEN.html` placeholder is deleted (audit fails on any `REPLACE_WITH` file).
- The Cloudflare beacon (cookieless) appears in every prerendered public page (site-wide; private routes share the prerendered home shell — acceptable per research R2).
- Organization JSON-LD has a stable `@id`, dimensioned logo, and a `sameAs` array; Article publisher references it by `@id`.
- No `<img>` without alt/dimensions; `seo:cwv` meets LCP/CLS/TBT thresholds on the 3 templates.

## Key files to touch
| Area | File |
|------|------|
| Article gates + manifest | `tools/seo/build-articles.mjs` |
| sitemap/robots/llms | `tools/seo/generate-sitemap.mjs` |
| Audit | `tools/seo/seo-audit.mjs` |
| CWV (new) | `tools/seo/cwv-audit.mjs` |
| Routes config | `src/app/core/seo/seo-routes.config.ts` |
| Structured data | `src/app/core/seo/structured-data.service.ts` |
| Analytics (new) | `src/app/core/seo/analytics.service.ts` |
| Article UI | `src/app/features/articles/article-detail.component.ts` |
| New pages | `src/app/features/pages/editorial-policy.*`, `contact.*` |
| Routes | `src/app/app.routes.ts` |
| Copy | `public/i18n/uk.json`, `public/i18n/en.json` |
| Verification token | `public/seo/google<token>.html` |
| Article content edits | `src/content/articles/*.md` (add `answer`, raise cross-links) |

## Operational launch checklist (set before/at deploy)

- **Google Search Console**: verified via the **Domain property (DNS TXT record)**, added in the DNS provider (Cloudflare DNS) — off-repo, no HTML file. No `google*.html` is shipped; the audit (A16') only fails if a stray `REPLACE_WITH` placeholder file appears. Ensure the `google-site-verification=...` TXT record is present on the apex domain.
- **Cloudflare Web Analytics token**: set in `src/environments/environment.ts` (`cloudflareAnalyticsToken`). When set, the beacon bakes into every prerendered page and A25 is a hard check; empty ⇒ A25 only WARNs.
- **Organization `sameAs`**: add official profile URLs to `environment.orgSameAs` when they exist (empty `[]` is shipped and valid).

## Advisory findings (not blocking)

- **Lab CWV (`npm run seo:cwv`)**: CLS (0.000) and TBT (within 200ms) pass; **LCP exceeds the 2.5s lab threshold on a dev machine under simulated mobile throttling** (~3.4-5.9s). Non-blocking (advisory gate, not in `seo:all`); the non-render-blocking font load is applied. Confirm real field p75 via Search Console/CrUX after launch (FR-013 defers field CWV); investigate further only if field data is poor.

## Notes
- The `.mjs` SEO tools are Node scripts (UTF-8/LF) — NOT Windows-executed; Ukrainian text in them and in generated `llms.txt`/`sitemap.xml` is fine (Constitution V applies only to `.ps1/.cmd/.bat`).
- `seo-manifest.json` is generated — add to `.gitignore` next to `articles.data.ts`.
- No backend/Mongo/LLM/payment changes — do not touch those modules.
