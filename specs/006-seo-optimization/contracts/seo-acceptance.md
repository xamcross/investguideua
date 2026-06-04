# Contract: SEO Acceptance Check

**Feature**: `006-seo-optimization`

The executable definition of done (FR-032 / SC-012). Implemented by `frontend/tools/seo/seo-audit.ts`,
run in CI against the prerendered build output. Each assertion below maps to a spec criterion
and MUST pass (exit non-zero on any failure). Verification is by parsing build bytes, never by
visual review (Constitution Principle V).

## Inputs

- The prerendered build output directory (static HTML files + `robots.txt`, `sitemap.xml`).
- The route classification (public vs private) and the published-article index.
- `SEO_SITE_ORIGIN` (canonical origin).

## Assertions

| # | Assertion | Spec |
|---|---|---|
| A1 | Every public URL has a generated HTML file whose `<main>`/article body contains non-empty primary text | FR-007, SC-001, SC-008 |
| A2 | Within each language, every public page `<title>` is unique and 1-60 chars | FR-010/013, SC-002 |
| A3 | Within each language, every public page meta description is unique and 110-160 chars | FR-010/013, SC-002 |
| A4 | Every public page has `<link rel="canonical">` equal to its own absolute URL | FR-004 |
| A5 | Every bilingual public page has hreflang alternates for uk, en, and x-default (incl. self) | FR-019/021, SC-009 |
| A6 | Every public page has OG (`og:title/description/image/type/url`) and Twitter Card tags; image resolves | FR-011/012, SC-005 |
| A7 | Org + WebSite JSON-LD present on all public pages and schema-valid (0 errors) | FR-014/018, SC-004 |
| A8 | Each article page has Article JSON-LD with headline, inLanguage, datePublished, dateModified, image, publisher | FR-015, SC-004 |
| A9 | Articles index + article pages have BreadcrumbList JSON-LD; pages with `faq` have valid FAQPage | FR-016/017 |
| A10 | `robots.txt` exists, is well-formed, references `sitemap.xml`, allows public, disallows every private prefix | FR-001 |
| A11 | `sitemap.xml` is schema-valid, contains 100% of public URLs (both languages) and 0 private/draft/duplicate URLs | FR-002, SC-003 |
| A12 | No private/utility route is present in the prerender output index or sitemap; each carries noindex | FR-005 |
| A13 | Each published article: non-empty body, `reviewedBy`+`reviewedOn` set, disclaimer rendered, cited figures are links, `providersReferenced` subset of curated catalog, `relatedSlugs` resolve | FR-024/025/026, SC-006/013 |
| A14 | A request to an unknown path returns HTTP 404 (verified against deploy config `_redirects`/`404.html`; private prefixes return 200 SPA shell) | FR-006, SC-011 |
| A15 | At least the agreed minimum number of published articles exist (>= 8, per Assumptions) | FR-023, SC-006 |
| A16 | The search-console verification file exists at the build-output root (and the home page carries the verification meta fallback) | FR-031 |

## Out-of-band checks (not in the audit script)

- **Hydration / interactivity (FR-009)**: a smoke check confirms a prerendered public route
  hydrates and stays interactive (e.g. headless load of `/` and one article, assert no Angular
  hydration error in console and that navigation works). Part of the DoD, not a static
  HTML assertion.

- **CWV (SC-007)**: lab Lighthouse pass pre-release (LCP<=2.5s, INP<=200ms, CLS<=0.1); field
  data confirmed post-launch in Search Console.
- **Indexing/impressions (SC-010)** and **soft-404 count (SC-011 field)**: monitored in Search
  Console after launch.

## Pass/fail

The audit prints a per-assertion report and exits non-zero if any of A1-A15 fails. The feature
is not "done" until the audit passes for every public URL.
