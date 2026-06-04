# Feature Specification: SOTA SEO Foundation

**Feature Branch**: `006-seo-optimization`
**Created**: 2026-06-04
**Status**: Draft
**Input**: User description: "Prepare SOTA SEO for this webapp. Make sure that google search ranks high up their rating, add necessary files for the crawlers, generate relevant articles etc"

## Overview

InvestGuideUA today is a single-page web application whose public surface is almost invisible to search engines: pages are assembled in the visitor's browser after JavaScript loads, so a crawler that does not execute scripts sees a near-empty shell with one generic title and description for every URL. There is no `robots.txt`, no sitemap, no per-page titles or social-share previews, no structured data, and no editorial content that answers the questions Ukrainians actually type into Google when they want to invest a sum of money at home.

This feature makes InvestGuideUA discoverable and competitive in organic search for Ukrainian-investing queries. It covers three pillars: (1) **crawler infrastructure** — the files and signals search engines and social platforms expect; (2) **per-page optimization** — every public page returns crawler-readable content with unique, descriptive metadata; and (3) **content** — a library of high-quality, locally relevant articles that attract and satisfy search traffic.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A searcher finds InvestGuideUA on Google (Priority: P1)

A person in Ukraine searches Google for something like "куди вкласти 50000 гривень" ("where to invest 50,000 hryvnia") or "as a Ukrainian how to invest savings safely". InvestGuideUA appears in the results with a clear, relevant title and a compelling description. They click through and land on a page whose content matches what the search result promised.

**Why this priority**: This is the entire point of the feature. If public pages are not crawlable and indexable with meaningful per-page metadata, nothing else matters — the site cannot rank or earn clicks. This story delivers the foundational "the site is in the index and shows up well" value on its own.

**Independent Test**: Fetch each public URL the way a non-JavaScript crawler does and confirm the response already contains the page's main textual content, a unique title, and a unique meta description. Validate the live URLs in Google Search Console's URL Inspection / "Live Test" and confirm "Page is indexable" with the rendered content visible. Run a search-result-preview check and confirm distinct snippets per page.

**Acceptance Scenarios**:

1. **Given** a crawler that does not execute JavaScript requests the home page, **When** it reads the response, **Then** the main headline, value proposition, and primary copy are present in the delivered markup (not only injected later by client scripts).
2. **Given** any two different public pages, **When** their titles and meta descriptions are compared, **Then** each is unique and accurately describes that page.
3. **Given** the home page and a published article, **When** inspected in Google Search Console's live URL test, **Then** both report as indexable with the expected rendered content.
4. **Given** a public page is requested, **When** the response is returned, **Then** it declares a single canonical URL for that page.

### User Story 2 - Search engines and social platforms can crawl the whole site (Priority: P1)

Googlebot, Bingbot, and social-platform crawlers (when a link is shared) can discover every public URL, understand the relationships between pages, see which pages should and should not be indexed, and render a rich preview card when someone shares a link in Telegram, Facebook, X, or messaging apps.

**Why this priority**: Discoverability infrastructure is a prerequisite for ranking at scale. Without a sitemap and crawl directives, indexing is slow and incomplete; without social-share metadata, shared links look broken and earn fewer clicks (and links/shares are themselves ranking and traffic signals).

**Independent Test**: Retrieve `robots.txt` and confirm it is well-formed, references the sitemap, and allows public pages while disallowing private/utility ones. Retrieve the sitemap, validate it against the sitemap schema, and confirm every public URL (in every supported language) is listed and resolves to a live page. Paste public URLs into social-card validators and confirm a correct title, description, and image render.

**Acceptance Scenarios**:

1. **Given** a crawler requests `/robots.txt`, **When** it reads the file, **Then** it finds valid directives and a reference to the sitemap URL.
2. **Given** the sitemap is requested, **When** it is validated, **Then** it conforms to the sitemap standard and lists all public URLs with appropriate metadata, and contains no private, authenticated, or duplicate URLs.
3. **Given** a public URL is shared on a social or messaging platform, **When** the platform fetches it, **Then** a preview card with the page-specific title, description, and a representative image is shown.
4. **Given** private or utility routes (login, register, account, history, search results, payment status), **When** a crawler evaluates them, **Then** they are excluded from indexing and absent from the sitemap.

### User Story 3 - A reader discovers and learns from educational articles (Priority: P2)

A visitor reaches an InvestGuideUA article (for example, "How to invest in Ukrainian government war bonds (OVDP)" or "Investing 100,000 UAH safely as a Ukrainian: a beginner's guide") from search or a shared link, reads a genuinely useful, accurate piece, browses related articles, and is invited to try the app's investment-discovery tool.

**Why this priority**: Editorial content is what actually ranks for the long tail of informational queries and builds topical authority for the domain, but it depends on the crawlability and metadata foundation (P1) being in place first. It is the growth engine layered on top of the foundation.

**Independent Test**: Publish a set of articles, confirm each is reachable from an articles index and listed in the sitemap, crawlable without JavaScript, carries article-appropriate metadata and structured data, and links onward to related articles and the core app flow. Verify content accuracy against the site's own provider catalog and the financial-content guardrails.

**Acceptance Scenarios**:

1. **Given** the articles section, **When** a visitor or crawler opens the articles index, **Then** all published articles are listed with title, summary, and a working link.
2. **Given** a published article, **When** it is opened, **Then** the full article text is present in the crawlable response, with a unique title, description, publication date, and language.
3. **Given** any article, **When** a reader finishes it, **Then** they are offered related articles and a clear path into the investment-discovery flow.
4. **Given** an article that names investment options or providers, **When** its content is reviewed, **Then** it stays consistent with the curated provider catalog and the project's financial-content guardrails (no individualized professional financial advice; only catalog-grounded examples).

### User Story 4 - Bilingual audience is served and not penalized for duplication (Priority: P2)

Ukrainian- and English-speaking visitors each get content in their language, and search engines understand that the Ukrainian and English versions of a page are translations of one another rather than duplicate content.

**Why this priority**: The product is explicitly bilingual (Ukrainian + English). Without correct language signaling, the two language versions can compete with or cannibalize each other in ranking, and the wrong-language version can be served to a searcher. It matters, but the single-language foundation must work first.

**Independent Test**: For a page available in both languages, confirm each language version declares its own language and points to the alternate-language version, and that a self-referencing alternate is present. Confirm both versions appear in the sitemap. Validate alternate-language annotations with a standard checker.

**Acceptance Scenarios**:

1. **Given** a page exists in Ukrainian and English, **When** either version is crawled, **Then** it declares its own language and references the equivalent page in the other language.
2. **Given** a searcher's language preference, **When** the page is shown in results, **Then** the language version matching that preference is preferred.
3. **Given** both language versions, **When** the sitemap is inspected, **Then** both are listed with their language relationship expressed.

### User Story 5 - The site demonstrates quality and trust signals to search engines (Priority: P3)

Search engines reading InvestGuideUA encounter the structured-data and quality signals associated with a trustworthy financial-information site: a defined organization identity, site-search capability, breadcrumb trails, FAQ answers, and fast, stable, mobile-friendly pages.

**Why this priority**: These signals improve how results are displayed (rich results, sitelinks) and contribute to ranking, especially for a "Your Money or Your Life" finance topic where trust weighs heavily. They are valuable refinements once the crawlable, content-rich foundation exists.

**Independent Test**: Run each public page through a structured-data validator and confirm valid Organization, WebSite, BreadcrumbList, Article, and FAQ markup where applicable, with zero errors. Run pages through a Core Web Vitals / page-experience assessment and confirm passing mobile scores. Confirm a single, consistent organization identity across pages.

**Acceptance Scenarios**:

1. **Given** any public page, **When** its structured data is validated, **Then** the applicable entity types are present and pass validation with no errors.
2. **Given** the home page, **When** it is assessed for page experience on mobile, **Then** it meets the defined Core Web Vitals thresholds.
3. **Given** a page with breadcrumbs or FAQ content, **When** validated, **Then** the corresponding structured data is present and eligible for rich results.

### Edge Cases

- **JavaScript-disabled / non-rendering crawler**: a crawler that never runs scripts must still receive the page's primary content and metadata for every public URL.
- **Unknown or removed URL**: a request for a non-existent page returns a proper "not found" status (so it is dropped from the index) rather than a success status with empty content (a "soft 404").
- **Private content leakage**: authenticated pages, search results, payment-status pages, and per-user history must never appear in the sitemap, must not be indexed, and must not expose user data to crawlers.
- **Duplicate URLs**: the same content reachable via multiple paths (e.g., with/without trailing slash, query parameters, or language prefix) must resolve to one canonical URL to avoid duplicate-content dilution.
- **Stale sitemap**: when an article is added, edited, unpublished, or removed, the sitemap and crawl signals reflect the change without manual file editing.
- **Outdated or inaccurate article**: financial facts (rates, instruments, providers) can change; articles must carry a visible last-updated signal and a defined review path so the site does not publish stale financial guidance.
- **Wrong-language preview**: a shared link must produce a preview card in the language of the shared URL, not a default language.
- **Crawl-budget waste**: infinite or low-value URL spaces (filtered/parameterized variants) must not be exposed to crawlers.

## Requirements *(mandatory)*

### Functional Requirements

#### Crawler infrastructure

- **FR-001**: The site MUST serve a valid `robots.txt` at the root that allows crawling of public pages, disallows private/utility paths, and references the sitemap location.
- **FR-002**: The site MUST publish an XML sitemap (or sitemap index) that lists every public, indexable URL with last-modified information, and MUST exclude private, authenticated, utility, and duplicate URLs.
- **FR-003**: The sitemap MUST update automatically when public content (notably articles) is added, changed, or removed, without manual editing of a static file.
- **FR-004**: Every public page MUST declare a single canonical URL for itself.
- **FR-005**: Private, authenticated, and utility pages MUST signal that they should not be indexed and MUST be excluded from the sitemap.
- **FR-006**: Requests for non-existent URLs MUST return a true "not found" response status, not a success status with empty or placeholder content.

#### Crawlable rendering

- **FR-007**: Every public page MUST deliver its primary textual content in the initial response in a form that a crawler which does not execute JavaScript can read, including the home page, the articles index, every article, and the public legal pages.
- **FR-008**: The crawler-readable content of a page MUST match what a human visitor sees (no cloaking / no content shown only to crawlers).
- **FR-009**: Pages MUST remain fully functional and interactive for human visitors after the initial response (the crawlability solution must not degrade the existing app experience).

#### Per-page metadata

- **FR-010**: Every public page MUST have a unique, descriptive title and meta description appropriate to its content.
- **FR-011**: Every public page MUST provide social-share metadata (Open Graph and equivalent) so that shared links render a preview with a title, description, and representative image.
- **FR-012**: The site MUST define a default share image and allow per-page (especially per-article) share images.
- **FR-013**: Titles MUST be no longer than 60 characters and meta descriptions MUST be between 110 and 160 characters, measured per language version, so search and social platforms display them without truncation. Pages exceeding these bounds fail the SEO acceptance check (FR-032).

#### Structured data / quality signals

- **FR-014**: The site MUST include valid Organization and WebSite structured data establishing a single, consistent brand identity.
- **FR-015**: Article pages MUST include valid Article structured data (title, author/publisher, publication and last-updated dates, language, image).
- **FR-016**: Pages with hierarchical navigation MUST include breadcrumb structured data.
- **FR-017**: Pages that present question-and-answer content MUST include FAQ structured data where the content genuinely qualifies.
- **FR-018**: All structured data MUST validate against the relevant standard with zero errors.

#### Bilingual / internationalization signals

- **FR-019**: For any page available in more than one language, each language version MUST declare its language and reference the equivalent page(s) in the other supported language(s), including a self-reference and an `x-default` (no-language-preference) reference. Each language version MUST have its own distinct, stable, directly-resolvable URL (a per-language URL strategy is required, not a runtime language toggle on one URL).
- **FR-020**: Both language versions of a page MUST be included in the sitemap with their language relationship expressed.
- **FR-021**: Language variants of the same content MUST NOT be treated as duplicate content (correct alternate-language signaling required).

#### Content / articles

- **FR-022**: The site MUST provide an articles section with an index page listing all published articles (title, summary, link) and individual article pages.
- **FR-023**: An initial set of articles MUST be authored and published (count and topic list per Assumptions). Each article MUST be mapped to exactly one primary target search topic/keyword phrase, and that mapping MUST be recorded so coverage is verifiable.
- **FR-024**: Each article MUST satisfy a documented editorial acceptance checklist before publication: original (not duplicated from another source), factually reviewed and signed off, addressing the search intent of its mapped target topic, and free of the disallowed financial-advice framing in FR-025. Sign-off MUST be recorded per article.
- **FR-025**: Article content that references investment options or providers MUST stay consistent with the curated provider catalog and MUST NOT present individualized professional financial advice (consistent with the project's financial-content guardrails). Every article page MUST display the project's standard financial disclaimer.
- **FR-026**: Each article MUST display a publication date and a last-updated date, and MUST link to related articles and to the core investment-discovery flow. Any cited rate, yield, or numeric financial figure MUST link to its provider/source rather than stating a bare number that can silently go stale.
- **FR-027**: Articles MUST be available in Ukrainian, with English versions provided per the bilingual requirements (priority and coverage per Assumptions).
- **FR-028**: There MUST be a repeatable, documented way to add, edit, and unpublish articles that keeps metadata, structured data, sitemap, and crawlability automatically correct.

#### Performance / page experience

- **FR-029**: Public pages MUST meet defined Core Web Vitals thresholds for mobile (see Success Criteria) so page-experience signals support, rather than harm, ranking.
- **FR-030**: Public pages MUST be mobile-friendly and pass standard mobile-usability checks.

#### Verification & operations

- **FR-031**: Search-engine ownership of the site MUST be verifiable (the site must expose whatever verification signal the chosen search consoles require) so indexing and performance can be monitored.
- **FR-032**: There MUST be a way to confirm, before release, that each public URL is crawlable, indexable, uniquely titled, and carries valid structured data (an SEO acceptance check).

### Key Entities *(include if feature involves data)*

- **Public Page**: Any URL intended for organic discovery (home, articles index, article, public legal pages). Attributes: canonical URL, title, description, language, alternate-language URLs, share image, indexability flag, last-modified date, structured-data type(s).
- **Article**: A piece of editorial content. Attributes: slug/URL, title, summary, body, author/publisher, target search topic/keywords, language, publication date, last-updated date, share image, related-article links, status (draft/published/unpublished), and provider/catalog references it must stay consistent with.
- **Sitemap Entry**: A listed public URL with last-modified date and language-alternate relationships; generated from published public pages and articles.
- **Crawl Directive Set** (`robots.txt`): Allow/disallow rules per path plus the sitemap reference.
- **Structured-Data Record**: The machine-readable description attached to a page (Organization, WebSite, Article, BreadcrumbList, FAQ).
- **Search Console Property**: The verified ownership record used to monitor indexing, queries, and page experience.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of public URLs (home, articles index, every published article, public legal pages, in every supported language) are reported as indexable in a live URL inspection, with their main content visible in the rendered result.
- **SC-002**: Within each language, 100% of public URLs return unique titles and unique meta descriptions (no two same-language public pages share either), and every title is at most 60 characters and every meta description is 110-160 characters, verified by an automated crawl. (A page and its other-language equivalent legitimately differ and are not counted as collisions.)
- **SC-003**: A valid `robots.txt` and a schema-valid sitemap are reachable, and the sitemap contains 100% of public URLs and 0 private/authenticated/duplicate URLs.
- **SC-004**: 100% of public pages pass structured-data validation with zero errors for every structured-data type they declare.
- **SC-005**: 100% of public URLs produce a correct social-share preview (page-specific title, description, and image) in standard card validators.
- **SC-006**: At least the agreed minimum set of articles (see Assumptions) is published, each crawlable without JavaScript, each with article structured data and a last-updated date.
- **SC-007**: Public pages meet "good" Core Web Vitals on mobile — Largest Contentful Paint at or under 2.5 seconds, Interaction to Next Paint at or under 200 milliseconds, and Cumulative Layout Shift at or under 0.1 — for the 75th-percentile assessment.
- **SC-008**: 100% of public URLs requested without JavaScript already contain their primary textual content (a non-rendering crawler sees the real page, not an empty shell).
- **SC-009**: Every bilingual page declares correct alternate-language relationships, verified with zero errors by an alternate-language checker.
- **SC-010**: Within 90 days of launch, the site is indexed for its brand name, and the search console reports recorded impressions for at least 5 of the targeted non-brand Ukrainian-investing queries (impressions, not a guaranteed ranking position — ranking depends on external factors outside this feature's control).
- **SC-011**: Requests for non-existent URLs return a "not found" status 100% of the time (zero soft-404s reported by the search console).
- **SC-012**: A pre-release SEO acceptance check exists and passes for every public URL before the feature is considered done. Pre-release verification uses lab measurement (e.g., a synthetic page-experience audit); field-data targets in SC-007 are confirmed post-launch once real traffic accrues.
- **SC-013**: 100% of published articles have a recorded primary-topic mapping (FR-023) and a recorded editorial sign-off (FR-024), display the standard financial disclaimer, and source-link every cited financial figure; 0 articles present individualized professional financial advice or reference providers/instruments outside the curated catalog.

## Assumptions

- **Audience & languages**: Primary audience is Ukraine-based users; primary content language is Ukrainian, with English as the secondary language (matching the existing uk/en bilingual product). Ukrainian article coverage is prioritized; English versions follow for the same topics.
- **Public vs. private surface**: Only the home page, an articles section, and the public legal pages (terms, privacy) are intended for indexing. Authenticated and utility routes — register, login, verify, search, history, tokens, payments/result, account, providers — must be excluded from indexing and the sitemap. (Derived from the current route map. Note: `register`, `login`, and `verify` are unauthenticated utility routes; the rest are auth-guarded. All are non-indexable regardless.)
- **Seed article set**: A minimum of 8 high-quality articles in Ukrainian for the initial launch, targeting common Ukrainian-investing intents such as: investing in government war bonds (OVDP), bank deposits vs. alternatives, how to invest a specific sum safely (e.g., 50,000 / 100,000 UAH), UAH vs. USD savings, beginner's guide to investing in Ukraine, understanding risk, choosing a provider, and avoiding investment scams. Exact topics and final count are a content-planning decision to be confirmed during planning.
- **Catalog grounding**: Article content that names providers or instruments must stay consistent with the project's curated provider catalog and the financial-content guardrails defined in the project specification (no individualized professional advice; catalog-grounded examples only).
- **Single canonical domain**: The site is served from one canonical domain over HTTPS; one host variant is canonical and others redirect to it. The exact production domain is confirmed during planning.
- **Crawlable rendering approach is an implementation choice**: This spec requires that public pages are crawler-readable without JavaScript and that the human experience is preserved; whether that is achieved via server-side rendering, static prerendering, or another technique is decided in planning, not here. Whatever approach is chosen MUST be capable of: returning a true "not found" HTTP status for unknown URLs (avoiding the SPA-fallback soft-404 trap), serving a live/auto-generated sitemap, and emitting per-page metadata and structured data in the initial response. The current app is a client-rendered SPA on static hosting with no server runtime, so closing these gaps is the central planning decision and the largest implementation risk.
- **Tooling for monitoring**: Google Search Console (and optionally Bing Webmaster Tools) will be used to verify ownership and monitor indexing and page experience; ownership verification is feasible on the production hosting.
- **No paid search / off-site link building in scope**: This feature covers on-site/technical SEO and owned content only. Paid advertising, backlink campaigns, and third-party PR are out of scope.
- **Analytics**: Basic search-performance monitoring is via the search console; a full web-analytics platform is not required by this feature (and any analytics added must not regress the Core Web Vitals targets).

## Out of Scope

- Paid search advertising (Google Ads, etc.) and any paid placement.
- Off-site SEO: backlink acquisition, guest posting, PR, directory submissions.
- A full content management UI for non-technical editors (article authoring workflow may remain developer/seed-file based for this iteration, per the project's MVP admin posture).
- Expanding supported languages beyond Ukrainian and English.
- Indexing or optimizing authenticated/in-app screens.
- A/B testing of titles/snippets and conversion-rate optimization beyond the SEO basics above.
