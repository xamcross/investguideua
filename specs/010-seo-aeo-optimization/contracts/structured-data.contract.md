# Contract: Structured Data (extended `structured-data.service.ts`)

Existing JSON-LD blocks (Organization, WebSite+SearchAction, Article, BreadcrumbList, FAQPage) are retained. Changes are **entity consolidation + brand signals**. No `Person` node (organization-byline clarification).

## Organization (changed)

```jsonc
{
  "@context": "https://schema.org",
  "@type": "Organization",
  "@id": "https://investguideua.com/#organization",   // NEW: stable id
  "name": "InvestGuideUA",
  "url": "https://investguideua.com/",
  "logo": { "@type": "ImageObject", "url": "https://investguideua.com/og-default.png", "width": 2000, "height": 2000 }, // dimensioned
  "sameAs": []   // NEW: official profile URLs from config; [] if none yet (deferred per spec)
}
```

## Article (changed — publisher by reference)

```jsonc
{
  "@context": "https://schema.org",
  "@type": "Article",
  "headline": "...",
  "description": "...",
  "inLanguage": "uk",
  "datePublished": "2026-06-04",
  "dateModified": "2026-06-04",
  "image": "https://investguideua.com/og-default.png",
  "author":    { "@id": "https://investguideua.com/#organization" },  // org byline, by reference
  "publisher": { "@id": "https://investguideua.com/#organization" },  // by reference (was inline object)
  "mainEntityOfPage": "<canonical-url>"
}
```

## WebSite (changed — publisher reference)
Add `"publisher": { "@id": ".../#organization" }`. SearchAction unchanged.

## Rules
- **R1**: Organization `@id` is stable and identical across all pages.
- **R2**: `Article.author` and `Article.publisher`, and `WebSite.publisher`, reference the Organization by `@id` (no repeated inline Organization object beyond the canonical one).
- **R3**: `sameAs` is always present as an array; empty allowed.
- **R4**: No `Person`/`reviewedBy` JSON-LD node (per Session 2026-06-06 clarification). Review attribution is rendered in the page's editorial trust block, not as a schema Person.
- **R5**: Logo carries `width`/`height` (entity completeness + avoids ambiguity).

## Acceptance
- Audit A24: `@id` present + publisher linked by `@id` + `sameAs` is an array.
- Existing A7/A8 (Organization+WebSite present; Article+BreadcrumbList on articles) still pass.
