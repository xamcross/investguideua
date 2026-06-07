# Feature Specification: SEO & AI-Search Traffic Maximization (Gap Closure)

**Feature Branch**: `010-seo-aeo-optimization`
**Created**: 2026-06-06
**Status**: Draft
**Input**: User description: "Reconcile a list of 15 state-of-the-art measures to maximize organic search and AI-search traffic against the current InvestGuideUA implementation, identify gaps, and implement them."

## Context: Reconciliation of the 15 measures against current state

InvestGuideUA already has a mature SEO foundation: server-side prerendering of all public routes, per-route title/description/canonical/hreflang/Open Graph/Twitter meta, a rich JSON-LD layer (Organization, WebSite + SearchAction, Article, BreadcrumbList, FAQPage), a Markdown article pipeline with frontmatter validation (which already **fails the build** when a published article lacks a reviewer), build-time `robots.txt` + `sitemap.xml` generation (with presence and private-route exclusion already asserted by an SEO audit), correct `hreflang` gating behind an `EN_ENABLED` flag, and a Cloudflare Pages deploy with sensible cache headers and a no-soft-404 routing policy.

This feature does **not** rebuild that foundation. It closes the **genuine gaps** found when the 15 measures are checked against the live implementation, corrects two real defects, and tightens the existing audit so the new signals are enforced. It is bounded to changes that can be made **inside this repository**. Purely off-site/marketing measures (earning backlinks, brand-mention campaigns, building a Wikidata entity, YouTube/Reddit/forum presence) are documented as **Out of Scope**.

### Reconciliation summary

| # | Measure | Current state | This feature |
|---|---------|---------------|--------------|
| 1 | Topical authority & depth | 9 UK articles with `relatedSlugs` | Enforce pillar/cluster cross-linking standard (US7) |
| 2 | Answer-first / extractable | FAQPage schema present | Add answer-first lead + heading standard, build-checked (US7) |
| 3 | E-E-A-T | Review status in frontmatter but **never rendered**; no editorial/about/contact footprint | Render org editorial trust block + Editorial/About/Contact pages (US1) |
| 4 | High-quality backlinks | n/a | **Out of scope** (off-site) |
| 5 | Search-intent alignment | Per-route metadata | Covered by content standard (US7) |
| 6 | Core Web Vitals | Lazy load, caching, budgets | Lab-measured CWV in CI + image alt/optimization (US4) |
| 7 | Structured data | Org, WebSite+SearchAction, Article, Breadcrumb, FAQ | Add `@id` entity graph + `sameAs` (US1, US6) |
| 8 | Crawlability hygiene | robots/sitemap generated & asserted; `/en` already gated | Fix sitemap `lastmod` bug; add regression guard + new audit assertions (US5) |
| 9 | Content freshness | `dateModified` in frontmatter & JSON-LD | Surface "Last updated"; emit real per-URL `lastmod` (US4/US5) |
| 10 | **AI crawler access** | Default-allow only; **no `llms.txt`, no explicit AI rules** | Add explicit AI allowances + generated `llms.txt` (US2) |
| 11 | Brand/entity presence | Org schema, no `sameAs` | Add `sameAs` + `@id` entity consolidation (US6) |
| 12 | Internal linking | routerLink, breadcrumbs, relatedSlugs | Enforce descriptive anchors / no-orphan rule (US7) |
| 13 | On-page fundamentals | Titles, meta, headers | Enforce image alt text (US4) |
| 14 | **Engagement signals** | **No analytics; SC verification is an un-replaced stub** | Add cookieless analytics + real verification token (US3) |
| 15 | Multi-format/channel | n/a | **Out of scope** (off-site) |

### Genuine gaps and defects this feature addresses

- **E-E-A-T footprint is invisible (real gap).** Review metadata exists and is build-enforced, but it is **never rendered** to readers and there is **no About / Editorial Policy / Contact page**, so the organizational authoritativeness footprint is missing. Highest-value gap for a YMYL finance site. (Attribution is organization-level per the Session 2026-06-06 clarification.)
- **AI-search inclusion is implicit (real gap).** AI crawlers are merely "not blocked"; there is no explicit allow policy and no `llms.txt` to help LLMs find and cite the right pages.
- **No engagement measurement (real gap).** There is no analytics. The Search Console verification artifact exists only as an un-replaced placeholder file, so ownership is not actually verified even though the audit currently passes on the placeholder filename.
- **Core Web Vitals unverified (real gap).** Lazy loading and caching are configured, but there is no CWV measurement in CI and image alt text is not enforced.
- **Entity presence is thin (real gap).** The Organization entity has no `sameAs` links and JSON-LD blocks are not linked via `@id`.
- **Content depth & answer-first structure unenforced (real gap).** No build standard requires an answer-first lead, question-style headings, or pillar/cluster cross-linking.
- **Sitemap `lastmod` defect (real bug).** The sitemap stamps the build date as `lastmod` on every URL instead of each article's true `dateModified`, emitting a false freshness signal.

### Already implemented — verify/tighten only (NOT new build work)

- `hreflang`/`x-default` for the unpublished `/en` mirror is already correctly suppressed by `EN_ENABLED=false` in both the runtime and the sitemap generator. Scope here is a **regression-guard assertion**, not a fix.
- `robots.txt` + `sitemap.xml` presence, mutual reference, and private-route exclusion are already asserted by the audit.
- The build already **fails** when a published article lacks reviewer attribution. Scope here is rendering + schema, not the gate.

## Clarifications

### Session 2026-06-06

- Q: Reviewer identity model for E-E-A-T (US1)? → A: Organization byline only — reviewer/author stays the InvestGuideUA editorial organization; no named-person reviewer record, no `Person` JSON-LD. The visible trust block, Editorial Policy/About/Contact pages, review date, and `@id` entity consolidation are retained; person-level expertise signals are dropped.
- Q: Analytics approach for US3? → A: Cloudflare Web Analytics — cookieless, no PII, no consent banner, native to the existing Cloudflare Pages host. Provides per-page visit/pageview counts and referrer breakdowns; engagement is proxied by visit/pageview and page-load metrics (no cookie-based dwell/bounce).
- Q: Minimum inbound peer cross-links before an article is flagged an orphan (FR-023)? → A: At least 2 inbound `relatedSlugs` references from other articles (the auto-generated index does not count); the build fails below this threshold.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reader and search engines can verify who stands behind the advice (Priority: P1)

A prospective investor reads an article about war bonds. Before trusting it with a financial decision, they want to know who stands behind it and how it was reviewed. Search engines and LLMs assessing a YMYL finance page look for the same authoritativeness and trust signals before ranking or citing it.

**Why this priority**: E-E-A-T is the most heavily weighted quality dimension for YMYL finance content and the org-level trust footprint is currently invisible (review status hidden; no editorial/about/contact footprint). Fully implementable in-repo; strengthens organizational trust signals for both readers and machines.

**Independent Test**: Open any published article and confirm a visible, machine-detectable editorial trust block (reviewed-by InvestGuideUA editorial team + review date) with a link to an Editorial Policy page; parse the page's JSON-LD and confirm the publishing Organization is consolidated via `@id`. Delivers a complete trust upgrade even if no other story ships.

**Acceptance Scenarios**:

1. **Given** a published article, **When** a reader opens it, **Then** the page renders a visible, machine-detectable editorial trust block (attribution to the InvestGuideUA editorial team and the review date) with a link to the Editorial Policy page.
2. **Given** the same article, **When** its JSON-LD is parsed, **Then** it expresses `author` and `publisher` as the InvestGuideUA Organization, with Article→publisher linked to the Organization entity via `@id` (organization byline; no `Person` reviewer per clarification).
3. **Given** the site, **When** a reader looks for who runs it, **Then** an About/Editorial Policy page and a Contact path are prerendered, indexable, and reachable from global navigation or the footer.
4. **Given** the existing build gate already blocks publishing an article without `reviewedBy`/`reviewedOn`, **When** the build runs, **Then** that gate still holds (regression check), ensuring every published article carries a review date that the trust block can render.

---

### User Story 2 - AI assistants can find, crawl, and cite the right pages (Priority: P1)

A user asks an AI assistant "how do I safely invest 50,000 UAH in Ukraine?" The AI's crawler should be explicitly welcomed, and an `llms.txt` index should point it at the canonical, citable guide pages so InvestGuideUA is surfaced and attributed.

**Why this priority**: AI-search inclusion is the headline goal and is currently only implicit. Small, high-leverage, in-repo change that widens the discovery surface.

**Independent Test**: Fetch the deployed crawl-policy file and confirm major AI crawlers are explicitly permitted; fetch `llms.txt` and confirm it lists the canonical indexable pages with descriptions and absolute URLs. Delivers AI discoverability on its own.

**Acceptance Scenarios**:

1. **Given** the deployed site, **When** `robots.txt` is fetched, **Then** it contains explicit allow stanzas for the major AI crawlers (at minimum GPTBot, Google-Extended, PerplexityBot, ClaudeBot) while continuing to disallow private/utility routes.
2. **Given** the deployed site, **When** `llms.txt` is fetched at the site root, **Then** it returns a valid document (following the emerging `llms.txt` convention: an H1 site name, a short summary, then sectioned lists of `[title](absolute-url): description` links) covering the indexable articles and key static pages.
3. **Given** a newly published article, **When** a single build runs, **Then** `llms.txt`, `robots.txt`, `sitemap.xml`, and the prerender list all reflect it with no manual edits (no-drift negative test).
4. **Given** a private/utility route (login, account, payments, etc.), **When** the crawl policy and `llms.txt` are inspected, **Then** that route appears in neither.

---

### User Story 3 - The team can measure organic and AI-search traffic (Priority: P2)

A maintainer wants to know which articles attract organic and AI-referred visitors and whether content updates move the needle, so freshness and depth effort can be directed where it pays off.

**Why this priority**: Without measurement, the impact of every other measure is invisible. It also converts the placeholder Search Console verification into real, verified ownership. Slightly lower than P1 because it enables optimization rather than being a ranking signal itself.

**Independent Test**: Load any prerendered public page and confirm the analytics beacon request fires; confirm a real (non-placeholder) verification artifact is present in the deployed output and the tightened audit rejects an un-replaced placeholder.

**Acceptance Scenarios**:

1. **Given** the deployed site, **When** a visitor loads a public page, **Then** the Cloudflare Web Analytics beacon (cookieless, no PII) fires without a consent banner; and the snippet is present on 100% of prerendered public pages (site-wide; private routes are noindex and excluded from sitemap/llms.txt).
2. **Given** the deployed output, **When** the Search Console verification artifact is inspected, **Then** it contains a real token (not the `REPLACE_WITH_*` placeholder), and the SEO audit **fails** if an un-replaced placeholder is present.
3. **Given** analytics is active, **When** a maintainer reviews the Cloudflare dashboard, **Then** per-page visit/pageview counts and referrer breakdowns are available.
4. **Given** the privacy policy, **When** a reader reads it, **Then** the analytics approach is disclosed accurately.

---

### User Story 4 - Pages load fast and pass Core Web Vitals (Priority: P2)

A mobile visitor on a typical Ukrainian connection opens an article and sees content render quickly, without layout shifts or input lag, so the page is not suppressed in rankings and the reader stays.

**Why this priority**: Core Web Vitals are a baseline gate — failures suppress everything else. The basics are in place; this story adds measurement and closes image/freshness gaps.

**Independent Test**: Run a lab CWV measurement (mobile profile) in CI on the home, article-index, and article-detail templates and confirm all three metrics meet good thresholds; confirm every rendered image has descriptive alt text and explicit dimensions; confirm a visible "Last updated" date on articles.

**Acceptance Scenarios**:

1. **Given** the home, article-index, and article-detail templates, **When** a lab CWV tool runs them with a mobile profile in CI, **Then** LCP, INP (or its lab proxy, e.g. Total Blocking Time), and CLS meet "good" thresholds (LCP < 2.5s, INP < 200ms, CLS < 0.1).
2. **Given** any rendered content or branding image, **When** the prerendered HTML is inspected, **Then** the image has descriptive non-empty alt text, explicit width/height, and is served in an optimized, appropriately sized form; a missing alt attribute **fails the build/audit**.
3. **Given** a published article, **When** it is rendered, **Then** a human-readable "Last updated" date matching the article's `dateModified` is visible.
4. **Given** the production build, **When** bundle budgets are evaluated, **Then** the build stays within its configured size budgets.

---

### User Story 5 - Crawl and indexation hygiene has no traps (Priority: P2)

A search-engine crawler follows the site's own signals and never hits a dead end: every advertised language alternate resolves, every canonical is correct, freshness signals are truthful, and the sitemap/robots files are reliably present.

**Why this priority**: A latent crawl trap or a false freshness signal wastes crawl budget and erodes trust. Lower than analytics because the foundation is largely correct; this hardens it and fixes one real bug.

**Independent Test**: Inspect the generated sitemap and confirm each URL's `lastmod` equals its article's true `dateModified` (not the build date); confirm no `en` alternate is advertised while `EN_ENABLED=false`; confirm self-referential canonicals; run the audit and confirm the new assertions pass.

**Acceptance Scenarios**:

1. **Given** `EN_ENABLED=false`, **When** any page's `hreflang` set and the sitemap are inspected, **Then** only `uk` + `x-default` are advertised and the audit **fails** if an `en` alternate is emitted (regression guard).
2. **Given** the generated `sitemap.xml`, **When** each article URL's `<lastmod>` is checked, **Then** it equals that article's `dateModified` from frontmatter, not the build date.
3. **Given** any public page, **When** its canonical is inspected, **Then** it is absolute, self-referential, and matches the served URL.
4. **Given** the build pipeline, **When** the SEO audit runs, **Then** it asserts presence/validity of `robots.txt`, `sitemap.xml` (with truthful `lastmod`), the real verification token, `llms.txt`, the AI-crawler allowances, the no-`en`-alternate guard, and the editorial trust block + review date on every article — and passes only when all hold.

---

### User Story 6 - The brand is a recognizable entity to machines (Priority: P3)

Search engines and LLMs encountering "InvestGuideUA" can disambiguate it as a single, consistent entity with known official profiles, strengthening how brand prevalence is weighted.

**Why this priority**: Entity/brand signals help AI weighting but are lower leverage than trust, AI access, and measurement, and partly depend on off-site presence.

**Independent Test**: Parse the Organization JSON-LD and confirm it declares `sameAs` official-profile links, a stable `@id`, and consistent name/URL/logo (with logo dimensions) across pages.

**Acceptance Scenarios**:

1. **Given** any page, **When** the Organization JSON-LD is parsed, **Then** it includes `sameAs` links to the brand's official/social profiles, a stable `@id` referenced by Article `publisher`, and a consistent name, URL, and dimensioned logo.
2. **Given** brand identity details (name, contact, logo), **When** compared across all pages, **Then** they are consistent everywhere they appear.

---

### User Story 7 - Content is answer-first and demonstrates topical depth (Priority: P3)

A reader (and an AI Overview) gets a direct, extractable answer at the top of each guide, supported by deeper sections, with clear topical relationships between related guides, so the content earns featured snippets and LLM citations.

**Why this priority**: High potential impact, but it is an editorial standard applied as content is created/edited; encoding and enforcing it is valuable but less urgent than the structural gaps.

**Independent Test**: Build the site and confirm a machine check flags any published article that lacks an answer-first lead, lacks question-style headings, or falls below the minimum cross-link count.

**Acceptance Scenarios**:

1. **Given** a published article, **When** the build runs its content check, **Then** the article must satisfy a concrete answer-first rule (e.g. a required frontmatter `answer` lead of 1-3 sentences, and at least one question-style `H2`), or the build flags it.
2. **Given** the set of published articles, **When** their cross-links are evaluated, **Then** every article has at least **2** inbound peer `relatedSlugs` references (orphan = fewer than 2; the auto-generated index listing does not count), and related links use descriptive anchor text.
3. **Given** an article that violates the answer-first or cross-link rule, **When** the build runs, **Then** it reports the specific violation so it can be corrected before publish.

---

### Edge Cases

- **Missing review date**: a published article lacking `reviewedBy`/`reviewedOn` must fail the build (existing gate retained).
- **English mirror enabled later**: when `EN_ENABLED=true`, alternates, sitemap entries, `llms.txt`, and crawl policy must extend to `/en` from one build with no manual drift; the no-`en` guard must invert automatically.
- **AI crawler policy reversal**: excluding a specific AI crawler later must be a single-source change that does not affect human-search crawlers.
- **Analytics blocked by the visitor**: blocked or failed analytics must not break page rendering or degrade Core Web Vitals.
- **New article added**: `sitemap.xml`, `llms.txt`, prerender list, and crawl policy update from one build with no manual edits.
- **Image missing alt text**: the build/audit fails rather than silently shipping it.
- **Brand has no official profile yet**: if no official profile URL exists, `sameAs` may be empty and US6's `sameAs` requirement is deferred for that profile rather than blocking (the `@id`/logo consolidation still applies).
- **Soft 404 / orphan page**: unknown paths return a real 404; no published article has fewer than 2 inbound peer cross-links.

## Requirements *(mandatory)*

### Functional Requirements

**E-E-A-T (US1)**

- **FR-001**: The site MUST render, on every published article, a machine-detectable editorial trust block attributing review to the InvestGuideUA editorial team and showing the review date, with a link to the Editorial Policy page.
- **FR-002**: The site MUST provide an Editorial Policy / About page and a Contact path, both as **prerendered, indexable** routes registered in the public-pages config, prerender list, and sitemap, each with a unique title and a 110-160 character description (so the existing audit passes), reachable from global navigation or the footer.
- **FR-003**: The Editorial Policy / About page MUST describe the organization's editorial team, the review workflow, and sourcing standards as the authoritativeness footprint (organization-level; per clarification there is no per-person reviewer registry).
- **FR-004**: Article JSON-LD MUST express `author` and `publisher` as the InvestGuideUA Organization, with Article→publisher linked to the Organization entity via `@id` (organization byline; no `Person` reviewer node).
- **FR-005**: The build MUST continue to block publishing an article without `reviewedBy`/`reviewedOn` (existing behavior), guaranteeing every published article has a review date for the editorial trust block to render.

**AI-search access (US2)**

- **FR-006**: The generated `robots.txt` MUST contain explicit allow stanzas for the major AI crawlers (at minimum GPTBot, Google-Extended, PerplexityBot, ClaudeBot), configured from a single source so the policy can be reversed in one place.
- **FR-007**: The build MUST generate an `llms.txt` at the site root following the emerging convention (H1 site name, short summary, sectioned `[title](absolute-url): description` lists) covering indexable articles and key static pages.
- **FR-008**: `llms.txt` and the AI-crawler policy MUST be derived from the same source of indexable pages as the sitemap/prerender list, regenerated each build, and MUST exclude private/utility routes.

**Engagement measurement (US3)**

- **FR-009**: The site MUST load the Cloudflare Web Analytics beacon (cookieless, no PII, no consent banner); the snippet MUST be present on 100% of prerendered public pages. The beacon is site-wide (it is also present on private routes, which are served the prerendered home shell via SPA fallback); this is acceptable because the beacon is cookieless/PII-free and private routes are already `noindex` and excluded from the sitemap/`llms.txt`. (See plan research R2 — a true "absent on private" guarantee would require a separate beacon-less fallback shell, rejected as unjustified infra.)
- **FR-010**: The site MUST be verified in Search Console. Verification uses the **Domain property (DNS TXT record, managed off-repo in the DNS provider)**; an HTML verification file is therefore not required in the deployed output. The SEO audit MUST fail if any un-replaced `REPLACE_WITH_*` placeholder `google*.html` is present.
- **FR-011**: Cloudflare Web Analytics MUST expose per-page visit/pageview counts and referrer breakdowns; engagement is measured via visit/pageview volume and page-load metrics (cookie-based dwell/bounce is out of scope given the cookieless choice).
- **FR-012**: The privacy policy MUST accurately disclose the analytics approach.

**Core Web Vitals (US4)**

- **FR-013**: A lab CWV measurement (mobile profile) MUST run in CI on the home, article-index, and article-detail templates and meet "good" thresholds (LCP < 2.5s, lab INP proxy < 200ms, CLS < 0.1). Field (p75) CWV is a post-launch monitoring goal, not a release gate.
- **FR-014**: Every rendered content/branding image MUST have descriptive non-empty alt text and explicit dimensions and be served optimized; a missing alt attribute MUST fail the build/audit.
- **FR-015**: Every published article MUST display a human-readable "Last updated" date matching its `dateModified`.
- **FR-016**: The production build MUST stay within its configured bundle-size budgets.

**Crawlability hygiene (US5)**

- **FR-017**: The sitemap MUST emit each article URL's `<lastmod>` as that article's true `dateModified` (not the build date).
- **FR-018**: While `EN_ENABLED=false`, no `en` `hreflang` alternate or `/en` sitemap entry may be emitted, and the audit MUST fail if one is; when `EN_ENABLED=true` the alternates MUST appear automatically.
- **FR-019**: Every public page MUST expose an absolute, self-referential canonical that matches its served URL (existing behavior; retain under regression check).
- **FR-020**: The SEO audit MUST be extended to assert, and MUST pass on: AI-crawler allow stanzas in robots.txt; presence + coverage + private-route exclusion of `llms.txt`; a real (non-placeholder) verification token; the editorial trust block + review date on every article; the no-`en`-alternate guard; truthful sitemap `lastmod`; presence of the About/Editorial/Contact indexable routes; image alt-text presence; and Organization `sameAs`/`@id`.

**Entity presence (US6)**

- **FR-021**: Organization JSON-LD MUST include `sameAs` official-profile links (where they exist), a stable `@id` referenced by Article `publisher`, and a consistent name, URL, and dimensioned logo across all pages.

**Answer-first & topical depth (US7)**

- **FR-022**: Published articles MUST satisfy a concrete answer-first rule (a required frontmatter answer lead of 1-3 sentences and at least one question-style heading), enforced at build.
- **FR-023**: Every published article MUST have at least **2** inbound peer cross-links (other articles referencing it via `relatedSlugs`), using descriptive anchor text; the auto-generated index does not satisfy this. Articles with fewer than 2 inbound references MUST fail the build (no-orphan rule).

### Key Entities *(include if feature involves data)*

- **Editorial trust block**: The org-level review attribution shown on each article — "reviewed by the InvestGuideUA editorial team" plus the review date (from the existing `reviewedBy`/`reviewedOn` frontmatter), linking to the Editorial Policy page. No per-person reviewer record (per clarification).
- **Editorial Policy / About / Contact pages**: Prerendered, indexable static routes stating who produces the content, the review workflow, sourcing standards, and how to make contact; back the trust signals shown on articles.
- **Crawl Policy**: Single-source machine-readable rules (rendered into `robots.txt`) for which agents may access which paths, including explicit AI-crawler allowances and private-route disallows.
- **LLM Index (`llms.txt`)**: Build-generated, convention-following list of canonical citable pages with descriptions, derived from the same indexable-page source as the sitemap.
- **Engagement Metric**: A cookieless, PII-free page-view/visit record held in Cloudflare Web Analytics, attributable to a page and a referrer.
- **Brand Entity**: The Organization identity (stable `@id`, name, URL, dimensioned logo, `sameAs` profile links) presented consistently to machines.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of published articles render a machine-detectable editorial trust block (editorial-team attribution + review date) linking to the Editorial Policy page, and the Editorial Policy/About and Contact routes are present and indexable — asserted by the audit.
- **SC-002**: `robots.txt` contains explicit allow stanzas for 100% of the target AI crawlers, and `llms.txt` covers 100% of indexable public pages with zero private routes leaked — asserted by the audit.
- **SC-003**: The site is verified in Search Console via a DNS TXT (Domain property); the audit fails on any `REPLACE_WITH_*` placeholder `google*.html`; and the cookieless analytics beacon snippet is present on 100% of prerendered public pages.
- **SC-004**: The extended SEO audit enumerates and passes every new assertion in FR-020; a build that omits any new signal fails the audit.
- **SC-005**: Lab CWV (mobile, CI) on the home, article-index, and article-detail templates meets all "good" thresholds (LCP < 2.5s, lab INP proxy < 200ms, CLS < 0.1).
- **SC-006**: 100% of rendered content/branding images have descriptive alt text and explicit dimensions (audit-asserted), and 100% of articles show a "Last updated" date matching `dateModified`.
- **SC-007**: 100% of generated sitemap article entries carry a `lastmod` equal to the article's true `dateModified`, and zero `en` alternates are emitted while `EN_ENABLED=false`.
- **SC-008**: 100% of public pages expose a correct self-referential canonical, and `robots.txt`/`sitemap.xml` are present and valid in every deploy.
- **SC-009**: Organization JSON-LD on 100% of pages includes a stable `@id`, consistent identity details, and `sameAs` for every known official profile.
- **SC-010**: 100% of published articles pass the build's answer-first check and have >=2 inbound peer cross-links (zero orphans), asserted at build.
- **SC-011**: The production build stays within configured bundle-size budgets.

## Out of Scope

- Earning high-quality editorial **backlinks** (off-site outreach/PR).
- **Brand-mention campaigns** and building a Wikipedia/Wikidata knowledge-graph entity (this feature only prepares on-site `sameAs`/`@id` signals).
- **Multi-channel presence** (YouTube videos, Reddit/forum participation, review-site profiles).
- Building the **English (`/en`) language mirror** — deferred; this feature only adds the regression guard around its already-correct suppression.
- Net-new **article authoring** beyond what is needed to bring existing content up to the answer-first / cross-link / E-E-A-T standards.
- **Live-site link crawling** of the deployed domain (broken-link/alternate-resolution checks are performed against the prerendered build output, not a remote crawl).

## Assumptions

- **Off-site measures are out of scope** (see Out of Scope); this feature prepares the on-site signals that make them effective.
- **Analytics is Cloudflare Web Analytics** (cookieless, native to the Cloudflare Pages host), per the Session 2026-06-06 clarification — chosen to avoid consent-banner friction and match the site's privacy stance. Cookie-based metrics (dwell time, bounce) are accepted as out of scope.
- **Reviewer attribution is organization-level** (InvestGuideUA editorial team), per the Session 2026-06-06 clarification; no named-person reviewer records or `Person` JSON-LD are in scope. Content still cannot be published without a review date (`reviewedBy`/`reviewedOn`).
- **Ukrainian remains the only published language for now**; the `/en` mirror stays deferred and correctly suppressed.
- The existing prerender/SSR pipeline, JSON-LD services, article Markdown pipeline, SEO audit, and Cloudflare Pages deploy are **reused, corrected, and extended**, not replaced.
- Search-Console and analytics accounts/credentials (and the real verification token) will be made available; the repo provides the artifacts and integration points.
- **Official brand profile URLs for `sameAs`** will be supplied where they exist; if none exist yet, `sameAs` is deferred for that profile without blocking the rest of US6.
- "Major AI crawlers" is the current well-known set (GPTBot, Google-Extended, PerplexityBot, ClaudeBot) and is extensible from a single source.
