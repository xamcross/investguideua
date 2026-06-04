# Phase 1 Data Model: SOTA SEO Foundation

**Feature**: `006-seo-optimization` | **Date**: 2026-06-04

These entities are **build-time / content** structures (no database). They map the spec's Key
Entities to concrete fields, validation rules, and (for articles) a publish state machine. The
SEO audit (contracts/seo-acceptance.md) validates instances against these rules.

---

## 1. Article

One editorial piece, authored as a Markdown file with YAML frontmatter, **one file per language**:
`frontend/src/content/articles/{slug}.{lang}.md` (e.g. `ovdp-war-bonds.uk.md`).

### Frontmatter fields

| Field | Type | Required | Validation |
|---|---|---|---|
| `slug` | string | yes | kebab-case, `^[a-z0-9-]+$`, unique across articles; same slug links uk/en versions |
| `lang` | enum `uk\|en` | yes | must match the filename `{lang}` |
| `title` | string | yes | 1-60 chars (becomes `<title>` and Article.headline) |
| `description` | string | yes | 110-160 chars (meta description + OG/Twitter description) |
| `summary` | string | yes | <= 300 chars; shown on the articles index card |
| `primaryTopic` | string | yes | the one target search phrase this article maps to (FR-023) |
| `keywords` | string[] | no | secondary phrases; informational only |
| `datePublished` | date `YYYY-MM-DD` | yes | not in the future |
| `dateModified` | date `YYYY-MM-DD` | yes | >= `datePublished`; not in the future |
| `status` | enum `draft\|published\|unpublished` | yes | only `published` is prerendered/sitemapped/indexed |
| `ogImage` | path | no | defaults to `/og-default.png`; if set, file must exist |
| `relatedSlugs` | string[] | no | each must resolve to a published article in the same language |
| `reviewedBy` | string | yes (to publish) | editorial sign-off owner (FR-024 / SC-013) |
| `reviewedOn` | date | yes (to publish) | sign-off date; must be >= `dateModified` minus review window |
| `faq` | array of `{q, a}` | no | if present, drives FAQPage JSON-LD; each `a` must be visible on the page (FR-017) |
| `providersReferenced` | string[] | no | provider ids/names cited; each MUST exist in the curated catalog (FR-025) |

### Body rules

- Body MUST contain the primary textual content (prerendered into `<main>`/article body) — no
  empty published articles (SC-008).
- Any cited rate/yield/figure MUST be a Markdown link to the provider/source (FR-026).
- The standard financial disclaimer is rendered by the article-detail component (not authored
  per file) on every article page (FR-025).

### Relationships

- `slug` joins the uk and en versions -> hreflang alternates (FR-019/020).
- `relatedSlugs` -> related-articles links (FR-026).
- `providersReferenced` -> curated provider catalog (consistency only; no runtime join).

### Publish state machine

```
draft --(content complete + reviewedBy/reviewedOn set + audit passes)--> published
published --(content correction)--> published   (bump dateModified)
published --(retire)--> unpublished
unpublished --(re-review)--> published
```

- `draft` / `unpublished`: excluded from prerender, articles index, sitemap; returns 404 if
  requested directly (not a soft-200).
- `published`: prerendered, listed, sitemapped, indexable.
- Any transition that changes visible content MUST bump `dateModified` (freshness, SC-006).

---

## 2. Public Page

Any URL intended for organic discovery. Not stored — derived at build time from routes +
published articles. Drives prerender targets, metadata, and sitemap entries.

| Field | Source | Rule |
|---|---|---|
| `path` | route / article slug | absolute path under canonical origin |
| `lang` | uk (root) / en (`/en` prefix) | one Public Page per language version |
| `canonical` | computed | `SEO_SITE_ORIGIN` + path; self-referential (FR-004) |
| `alternates` | uk/en/x-default | hreflang set incl. self + x-default (FR-019) |
| `title` | route data / frontmatter | unique per language, <= 60 chars (FR-010/013) |
| `description` | route data / frontmatter | unique per language, 110-160 chars |
| `ogImage` | frontmatter / default | absolute URL; default `/og-default.png` (FR-012) |
| `indexable` | route classification | public=true; private/utility=false (FR-005) |
| `lastmod` | article `dateModified` / build date | ISO date in sitemap |
| `structuredDataTypes` | page type | see Structured-Data Record |

**Public set**: `/`, `/articles`, `/articles/{slug}` (published), `/terms`, `/privacy`, and the
`/en/...` mirror of each.
**Private/non-indexable set** (never prerendered/sitemapped, `noindex`): `/login`, `/register`,
`/verify`, `/search`, `/history`, `/history/{id}`, `/tokens`, `/payments/result`, `/account`,
`/providers`, and `/en/...` mirrors.

---

## 3. Sitemap Entry

Generated per published Public Page by `tools/seo/generate-sitemap.ts`.

| Field | Rule |
|---|---|
| `loc` | absolute canonical URL |
| `lastmod` | article `dateModified` or build date for static pages |
| `alternates` | `<xhtml:link rel="alternate" hreflang>` for uk, en, x-default (FR-020) |

Constraints: contains 100% of public URLs; **0** private/auth/draft/unpublished/duplicate URLs
(SC-003). Validates against the sitemap 0.9 schema.

---

## 4. Crawl Directive Set (`robots.txt`)

| Directive | Value |
|---|---|
| `User-agent` | `*` |
| `Allow` | public paths |
| `Disallow` | each private/utility path prefix (both languages) |
| `Sitemap` | `{SEO_SITE_ORIGIN}/sitemap.xml` |

Generated alongside the sitemap from the same route classification source (single source of
truth; FR-001/003).

---

## 5. Structured-Data Record (JSON-LD per page)

| Page type | Required JSON-LD |
|---|---|
| All public pages | `Organization`, `WebSite` (with `inLanguage` + SearchAction) |
| Articles index | + `BreadcrumbList` |
| Article | + `Article` (`headline`, `inLanguage`, `datePublished`, `dateModified`, `image`, `publisher`=Organization) + `BreadcrumbList` (+ `FAQPage` if `faq` present) |
| Legal pages | base only |

All records MUST validate with zero errors (FR-018/SC-004). Field shapes in
`contracts/structured-data.contract.md`.

---

## 6. Search Console Property

Operational, not code-stored.

| Field | Value |
|---|---|
| `property` | the canonical origin |
| `verification` | static file `public/google<token>.html` (+ home-page meta fallback) |
| `monitored` | indexing coverage, query impressions (SC-010), soft-404s (SC-011), CWV (SC-007) |

---

## Validation summary (enforced by the SEO audit)

- Title <= 60; description 110-160; both unique per language.
- Every published article: non-empty body, Article JSON-LD, `datePublished`+`dateModified`,
  `reviewedBy`+`reviewedOn`, disclaimer present, source-linked figures, `providersReferenced`
  subset of catalog, `relatedSlugs` resolve.
- Every public page: canonical (self), full hreflang set incl. x-default, OG/Twitter with image.
- Sitemap: 100% public, 0 private/draft; schema-valid. robots.txt references sitemap.
- Unknown URL -> 404 status; private route -> noindex and absent from sitemap.
