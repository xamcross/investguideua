# Contract: Structured Data (JSON-LD)

**Feature**: `006-seo-optimization`

Emitted by `StructuredDataService` into each page `<head>` and prerendered into static HTML.
All records MUST validate against schema.org with zero errors (FR-018 / SC-004). `{ORIGIN}` =
`SEO_SITE_ORIGIN`.

## Organization (all public pages)

```json
{
  "@context": "https://schema.org",
  "@type": "Organization",
  "name": "InvestGuideUA",
  "url": "{ORIGIN}/",
  "logo": "{ORIGIN}/og-default.png"
}
```

## WebSite (all public pages)

```json
{
  "@context": "https://schema.org",
  "@type": "WebSite",
  "name": "InvestGuideUA",
  "url": "{ORIGIN}/",
  "inLanguage": "uk",
  "potentialAction": {
    "@type": "SearchAction",
    "target": "{ORIGIN}/search?amount={amount}",
    "query-input": "required name=amount"
  }
}
```

> The SearchAction target resolves for users (the SPA boots the search route); `/search` stays
> `noindex` and out of the sitemap. Advertising the action while not indexing the results page
> is permitted and not a contradiction.

## Article (article pages)

```json
{
  "@context": "https://schema.org",
  "@type": "Article",
  "headline": "<= 110 chars, from title",
  "description": "from frontmatter description",
  "inLanguage": "uk",
  "datePublished": "2026-06-10",
  "dateModified": "2026-06-10",
  "image": "{ORIGIN}/og/ovdp.png",
  "author":   { "@type": "Organization", "name": "InvestGuideUA" },
  "publisher": { "@type": "Organization", "name": "InvestGuideUA",
                 "logo": { "@type": "ImageObject", "url": "{ORIGIN}/og-default.png" } },
  "mainEntityOfPage": "{ORIGIN}/articles/ovdp-war-bonds"
}
```

## BreadcrumbList (articles index + article pages)

```json
{
  "@context": "https://schema.org",
  "@type": "BreadcrumbList",
  "itemListElement": [
    { "@type": "ListItem", "position": 1, "name": "Home", "item": "{ORIGIN}/" },
    { "@type": "ListItem", "position": 2, "name": "Articles", "item": "{ORIGIN}/articles" },
    { "@type": "ListItem", "position": 3, "name": "<article title>", "item": "{ORIGIN}/articles/ovdp-war-bonds" }
  ]
}
```

## FAQPage (only when frontmatter `faq` present and visible in body)

```json
{
  "@context": "https://schema.org",
  "@type": "FAQPage",
  "mainEntity": [
    { "@type": "Question", "name": "...",
      "acceptedAnswer": { "@type": "Answer", "text": "..." } }
  ]
}
```

## Rules

- `inLanguage` reflects the page language (`uk` or `en`).
- Article `image`, Organization `logo`, and all `item`/`url` values are absolute URLs under
  `{ORIGIN}`.
- `headline` is taken verbatim from `title`; since `title` is already capped at <= 60 chars it
  is always within Google's <= 110 guidance (no truncation needed in practice).
- FAQPage emitted only when answers are genuinely on the page (FR-017 / FR-008 no-cloaking).
- The audit (A7-A9) parses each `<script type="application/ld+json">` and validates required
  properties + zero schema errors.
