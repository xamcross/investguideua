# Contract: Article Frontmatter Schema

**Feature**: `006-seo-optimization`

Every article content file (`frontend/src/content/articles/{slug}.{lang}.md`) begins with a YAML
frontmatter block conforming to this schema. The content loader and the SEO audit both validate
against it; an invalid or under-filled frontmatter fails the build for a `published` article.

## Schema (YAML frontmatter)

```yaml
slug: ovdp-war-bonds          # required, ^[a-z0-9-]+$, unique; joins uk/en versions
lang: uk                      # required, uk | en ; must equal filename {lang}
title: "..."                  # required, 1-60 chars
description: "..."            # required, 110-160 chars
summary: "..."               # required, <= 300 chars (index card)
primaryTopic: "..."          # required, the single target search phrase
keywords: ["...", "..."]     # optional
datePublished: 2026-06-10     # required, YYYY-MM-DD, not future
dateModified: 2026-06-10      # required, >= datePublished, not future
status: published             # required, draft | published | unpublished
ogImage: /og/ovdp.png         # optional, defaults to /og-default.png; if set, file must exist
relatedSlugs: ["deposits-vs-alternatives"]  # optional, each resolves to a published same-lang article
reviewedBy: "Editor Name"    # required to publish
reviewedOn: 2026-06-09        # required to publish
providersReferenced: ["privatbank", "oschadbank"]  # optional; each MUST exist in curated catalog
faq:                          # optional; if present -> FAQPage JSON-LD; answers MUST appear in body
  - q: "..."
    a: "..."
```

## Validation rules

1. `published` articles MUST have every required field plus `reviewedBy` and `reviewedOn`.
2. `title` <= 60; `description` 110-160 (hard fail outside range).
3. `slug` unique; the uk and en files with the same `slug` form one hreflang pair.
4. `dateModified` >= `datePublished`; neither in the future.
5. `relatedSlugs` resolve to published articles in the same language.
6. `providersReferenced` is a subset of the curated provider catalog (FR-025).
7. If `faq` present, each answer text MUST also appear in the rendered body (no FAQ-only content
   that is invisible to users — FR-008/017).
8. Body non-empty; any rate/yield/figure cited as a Markdown link to its source (FR-026).

## Notes

- Frontmatter is the single source for the page's SEO metadata, sitemap `lastmod`, and Article
  JSON-LD — so adding/editing a file keeps metadata, structured data, and sitemap in sync
  automatically (FR-003/028).
- Content files are UTF-8, LF. They are NOT Windows-executed scripts, so Ukrainian text is fine
  (Constitution Principle V applies only to `.ps1`/`.cmd`/`.bat`).
