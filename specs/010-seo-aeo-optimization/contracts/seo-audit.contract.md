# Contract: Extended SEO Acceptance Audit (`tools/seo/seo-audit.mjs`)

The audit is the **executable definition of done**. It runs against the prerendered build output (`dist/investguide-frontend/browser/`) and **exits non-zero on any violation** (verification by parsing bytes — Constitution V). Existing checks A1-A16 are retained; the checks below are **added/tightened**. Each maps to a spec FR/SC.

## Retained (must keep passing)
- A1-A9 per-page: content present, unique title (<=60) + description (110-160), self canonical, hreflang `uk`+`x-default`, OG/Twitter, Organization+WebSite JSON-LD, `index,follow`; article pages: Article+BreadcrumbList JSON-LD, datePublished/dateModified, financial disclaimer.
- A10-A15: robots.txt (sitemap ref + private Disallow), sitemap 0.9 + URL count + no private URLs, true 404 + no SPA catch-all, article count >= 8, og-default.png.

## New / tightened checks

| ID | Assertion | Maps to | Fail message intent |
|----|-----------|---------|---------------------|
| **A16'** | Verification via Search Console **Domain property (DNS TXT, off-repo)** — no HTML file required. If any `google*.html` IS shipped, none (name or contents) may contain `REPLACE_WITH`. The placeholder `googleREPLACE_WITH_REAL_TOKEN.html` and the sample file are deleted. | FR-010, SC-003 | "placeholder verification token (REPLACE_WITH)" |
| **A17** | `robots.txt` contains an explicit allow stanza for each of `GPTBot`, `Google-Extended`, `PerplexityBot`, `ClaudeBot` | FR-006, SC-002 | "robots.txt missing AI-crawler allow for X" |
| **A18** | `llms.txt` exists; lists every `indexable` manifest path as an absolute URL; contains **zero** private-prefix paths | FR-007/FR-008, SC-002 | "llms.txt missing/incomplete or leaks private route X" |
| **A19** | Every prerendered article page contains the editorial trust-block marker + the review date + a link to `/editorial-policy` | FR-001, SC-001 | "article X missing editorial trust block / policy link" |
| **A20** | `/editorial-policy` and `/contact` are present as prerendered indexable pages (own index.html, valid title+desc, `index,follow`) | FR-002, SC-001 | "missing prerendered route /editorial-policy or /contact" |
| **A21** | Each sitemap article `<url>` `<lastmod>` equals that article's `dateModified` from the manifest (NOT all the same build date). Depends on the generator's manifest existence+freshness guard (R1) — a missing/stale manifest must fail the generate step, not silently emit build dates. | FR-017, SC-007 | "sitemap lastmod for X is build date, not dateModified" |
| **A22** | While `EN_ENABLED=false`: no `hreflang="en"` and no `/en` `<loc>`/alternate anywhere in pages or sitemap | FR-018, SC-007 | "en alternate emitted while EN_ENABLED=false" |
| **A23** | No `<img>` in any prerendered page lacks a non-empty `alt`; flagged imgs also need `width`+`height` | FR-014, SC-006 | "img without alt/dimensions on page X" |
| **A24** | Organization JSON-LD has a stable `@id`; `Article.publisher` references it by `@id`; `sameAs` is an array (may be empty) | FR-021, SC-009 | "Organization missing @id / publisher not linked by @id" |
| **A25** | Cloudflare Web Analytics beacon snippet (cookieless) present on 100% of prerendered public pages. (Revised after review: beacon is intentionally site-wide because the SPA fallback serves the prerendered home `index.html`; "absent on private" was dropped as infeasible/unnecessary for a cookieless beacon - see research R2.) | FR-009, SC-003 | "analytics beacon missing on public page X" |

## Build-stage gates (in `build-articles.mjs`, fail before audit)
| ID | Assertion | Maps to |
|----|-----------|---------|
| B1 | published article has `answer` (40-300 chars) | FR-022 |
| B2 | published article has >=1 `##` heading ending with `?` | FR-022 |
| B3 | published article has >= 2 inbound `relatedSlugs` references from peers | FR-023, SC-010 |
| B4 | (retained) published article has `reviewedBy`+`reviewedOn`, `dateModified>=datePublished` | FR-005 |

## Lab CWV gate (`tools/seo/cwv-audit.mjs`, separate `npm run seo:cwv`)
| ID | Assertion | Maps to |
|----|-----------|---------|
| C1 | Lighthouse mobile: LCP < 2.5s on `/`, `/articles`, one `/articles/<slug>` | FR-013, SC-005 |
| C2 | CLS < 0.1 on the same three | FR-013, SC-005 |
| C3 | Total Blocking Time (INP lab proxy) < 200ms on the same three | FR-013, SC-005 |

## Invocation
- `npm run seo:all` ⇒ `build` → `seo:generate` → `seo:audit` (extend to also surface manifest + llms.txt). Add `seo:cwv` to CI (and optionally to `seo:all`).
- Exit code 0 only when **all** retained + new assertions pass.
