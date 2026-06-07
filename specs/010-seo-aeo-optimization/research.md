# Phase 0 Research: SEO & AI-Search Traffic Maximization

All decisions below resolve the technical unknowns surfaced in the plan's Technical Context. Each is scoped to frontend-only, in-repo changes consistent with the constitution (MVP discipline, fixed stack, encoding discipline).

---

## R1. Single source of truth for sitemap / robots / llms.txt (and the `lastmod` fix)

**Decision**: Have `build-articles.mjs` emit a new build artifact `frontend/seo-manifest.json` — an array of `{ path, lastmod, indexable, title, description, kind }` entries for every public route (static pages from `PUBLIC_PAGES` + each published article). `generate-sitemap.mjs` and the new `llms.txt` generator consume this manifest instead of (or in addition to) the bare `prerender-routes.txt`.

**Rationale**:
- The current sitemap generator only reads `prerender-routes.txt`, which is **paths only** — it has no dates, which is exactly why it stamps the build date as `lastmod` for every URL (the real bug, FR-017). The manifest carries each article's true `dateModified`.
- A single manifest guarantees sitemap, robots, llms.txt, and the prerender list cannot drift (FR-008), satisfying the no-drift edge case from one build.
- Static-page descriptions for `llms.txt` are resolved at build from the i18n dictionaries (the same `descKey` strings the audit already validates to 110-160 chars).

**Alternatives considered**:
- *Read `articles.data.ts` from the generator*: it's a TS file; parsing TS from a plain `.mjs` is fragile. Emitting JSON is clean and language-agnostic.
- *Keep `prerender-routes.txt` as the only source*: cannot fix `lastmod` (no dates) — rejected.

**Notes**: `seo-manifest.json` is a generated artifact → add to `.gitignore` alongside `articles.data.ts`. `prerender-routes.txt` stays (Angular's `routesFile` needs the bare-paths file).

**Staleness guard (added after review)**: `generate-sitemap.mjs` MUST `existsSync(manifest)` and **throw** if missing (today it silently lacks dates → the build-date bug). It SHOULD also assert manifest freshness against the build output (e.g. compare mtime to `dist/.../index.html`, or stamp a shared build id) so a stale manifest from a prior build cannot produce wrong `lastmod`. Audit check A21 depends on this guard. Because the manifest is git-ignored, a fresh checkout/CI that runs `seo:generate` without a preceding `seo:articles` MUST fail loudly, never fall back to the build date.

---

## R2. Cloudflare Web Analytics — site-wide, cookieless (REVISED after DevOps/FE review)

**Decision**: Add an Angular `AnalyticsService` that appends the Cloudflare Web Analytics beacon `<script defer src=".../beacon.min.js" data-cf-beacon='{"token":"<TOKEN>"}'>` to `document.head` **once at app bootstrap** (in `AppComponent` `ngOnInit`, mirroring how `StructuredDataService.setBase()` is already wired), unconditionally for the whole app, guarded only by a present token in `environment`. The beacon therefore bakes into **every** prerendered page and into the SPA-fallback shell.

**Why this changed**: The original plan injected the beacon only on `isIndexable` routes, intending it to be "absent on private routes." The review proved this impossible with the current topology: `public/seo/_redirects` maps every private route to `/index.html 200`, and `/index.html` is the **prerendered home page** — which already contains the beacon. The CDN serves that static file (beacon included) for `/search`, `/account`, etc. **before** Angular boots, so a runtime `isIndexable` guard can never remove it. Achieving true absence would require a separate beacon-less `shell.html` + `_redirects` rewrite (extra build artifact + infra) for no real benefit, since the beacon is cookieless and collects no PII.

**Rationale**:
- Cookieless + no PII ⇒ no consent banner, GDPR-safe (Session 2026-06-06 clarification); a hit counter on a few authenticated routes is harmless and carries no SEO cost (private routes are already `noindex` + sitemap/llms-excluded).
- Bootstrap-time `document.head.appendChild` serializes into prerendered HTML (confirmed: this is exactly how the existing JSON-LD `setBase` works — see R2-serialization note below). So the beacon is audit-detectable on every public page.
- MVP discipline: zero new build artifacts, no `_redirects` changes.

**Serialization note**: Appending a `<script>` to `document.head` during prerender DOES serialize into static HTML — verified against the existing `StructuredDataService.write()` which does the same and whose JSON-LD appears in built `index.html`. Use the `ngOnInit` hook (as `setBase` does), not a router subscription.

**Spec impact**: FR-009 and SC-003 are revised from "absent on private routes" to "present on 100% of public pages (cookieless, no PII); private routes are noindex/excluded." Audit A25 checks beacon presence on every prerendered public page (drops the false "absent on private shell" clause).

**Alternatives considered**:
- *Beacon-less `shell.html` fallback + `_redirects` repoint (Option B)*: the only way to keep true absent-on-private; rejected as unjustified infra for a cookieless beacon (MVP/YAGNI).
- *Runtime-only injection (Option C)*: beacon not in prerendered bytes → not audit-detectable; weaker. Rejected.
- *Cloudflare dashboard auto-injection*: works but not repo-controlled/audit-visible. Rejected in favor of the explicit, config-tokened snippet.
- *GA4*: cookies ⇒ consent banner + CWV/privacy cost — rejected in clarification.

---

## R3. Lab Core Web Vitals measurement in CI

**Decision**: Add `frontend/tools/seo/cwv-audit.mjs` (`npm run seo:cwv`) that runs Lighthouse (programmatic API) with simulated mobile throttling against the three templates served from the prerendered build output via a tiny static file server. Launch Chrome via the **existing `puppeteer`** dependency (`puppeteer.executablePath()`) — the same Chrome `tools/a11y/axe-audit.mjs` already drives — NOT `chrome-launcher` and NOT karma's `ChromeHeadlessCI` (which is only a karma launcher config, not a reusable binary). Add `lighthouse` + a minimal static server as **devDependencies** (current release lines). To avoid lab jitter (TBT/INP-proxy flake on shared runners), take the **median of 3 runs** per URL; thresholds LCP < 2.5s, CLS < 0.1, TBT < 200ms. The CWV check is **advisory / CI-reported, NOT part of the blocking `seo:all`/deploy gate** (this is now a decision, not a follow-up).

**Rationale**:
- Field p75 CWV cannot be produced at build time (no traffic) — the spec already scoped FR-013 to **lab** measurement; TBT is the standard lab proxy for INP.
- Mirrors the existing custom-Node-audit pattern (`a11y/axe-audit.mjs`, `seo-audit.mjs`).
- Serving the prerendered output (not the dev server) measures what users actually receive.

**Alternatives considered**:
- *`@lhci/cli` (Lighthouse CI)*: heavier config/server model; overkill for 3 URLs on a static site (MVP discipline). A thin programmatic script is lighter.
- *PageSpeed Insights API*: needs the site publicly deployed + network + quota; not suitable as a pre-deploy gate. Rejected.

**Open follow-up (non-blocking)**: wire `seo:cwv` into the `seo:all` chain or the deploy CI step. Defaulting to an on-demand + CI-invokable script keeps local builds fast.

---

## R4. Image optimization & alt-text enforcement (FR-014)

**Decision**: (a) Enforce via the audit: scan every prerendered page for `<img>` tags lacking a non-empty `alt` or explicit `width`/`height`, and fail. (b) For actual raster assets, ensure `og-default.png` and any landing/hero image are appropriately sized with intrinsic dimensions; adopt Angular `NgOptimizedImage` (`ngSrc` + `width`/`height`) for any `<img>` in the landing/home template. (c) Do **not** add `sharp` unless an oversized source asset is found — article bodies render with `markdown-it` `html:false` and contain **no `<img>` tags**, so content-image surface is effectively zero.

**Rationale**:
- The FE-lead review confirmed near-zero content-image surface; the real exposure is the few branding/landing rasters and the discipline guard. `NgOptimizedImage` is the framework-native, zero-new-dependency way to get lazy-loading + explicit dimensions (prevents CLS).
- An audit guard makes the requirement testable and prevents future regressions (a new `<img>` without alt fails the build).

**Alternatives considered**:
- *Always add `sharp` + a build-time conversion pipeline*: unjustified complexity for a site with no content images (YAGNI / MVP discipline). Add only if a concrete oversized asset demands it.

---

## R5. `llms.txt` format

**Decision**: Generate `/llms.txt` following the emerging community convention: an `# InvestGuideUA` H1, a one-line blockquote/summary, then Markdown sections (e.g. `## Guides`, `## About`) listing `- [Title](absolute-url): description`. Include only `indexable` manifest entries (articles + key static pages); exclude all private/utility routes. Absolute URLs use the same `SEO_SITE_ORIGIN` as the sitemap.

**Rationale**: Matches the `llms.txt` proposal LLM crawlers/readers expect; reuses the manifest's titles/descriptions; single-source with sitemap so it cannot drift (FR-007/FR-008).

**Alternatives considered**: *Ad-hoc plain URL list* — less useful to LLMs, no titles/descriptions for citation context. Rejected.

---

## R6. Editorial Policy/About + Contact pages

**Decision**: Add two prerendered, indexable public routes — `/editorial-policy` (the About + editorial-standards footprint) and `/contact`. Implement as lightweight standalone content components following the existing `LegalDocumentComponent` pattern (body copy sourced from i18n keys), registered in: `PUBLIC_PAGES` (with `titleKey`/`descKey`, descriptions 110-160 chars), `BASE_ROUTES` in `build-articles.mjs` (so they prerender + enter sitemap/manifest), and `app.routes.ts`. Link both from the footer/nav. Contact exposes an email (`mailto:`) — no form, no backend (MVP discipline).

**Rationale**: Reuses an established pattern; no backend needed; satisfies FR-002/FR-003 and the E-E-A-T authoritativeness footprint. Keeping copy in i18n keeps the audit's title/description length checks applicable and supports the future `/en` mirror.

**Alternatives considered**: *A contact form with backend endpoint* — adds an API + spam handling for no MVP benefit; `mailto:` is sufficient. Rejected.

---

## R7. Real Search Console verification + audit tightening

**Decision**: Replace `public/seo/googleREPLACE_WITH_REAL_TOKEN.html` with the real `google<token>.html` provided by Search Console (supplied operationally per Assumptions). Tighten audit check A16: it must (a) find a `google*.html` file AND (b) fail if a filename or contents still contain `REPLACE_WITH` / a placeholder marker.

**Rationale**: The placeholder currently makes A16 pass without real ownership verification (the defect the spec calls out). The tightened check turns "verified" into a real signal.

**Important (added after review)**: the existing placeholder `public/seo/googleREPLACE_WITH_REAL_TOKEN.html` is copied to `dist/` by the assets glob. It MUST be **deleted** when the real token file is added, otherwise tightened check A16' (fail on any `REPLACE_WITH` filename/contents) will fail on the leftover. This is an explicit task.

**Alternatives considered**: *Meta-tag verification in `index.html`* — works but is easy to leave stubbed; the file-based token is already scaffolded. Either is acceptable; keep the existing file approach and just make it real + strict.

---

## R8. Answer-first & cross-link rules as machine checks

**Decision**:
- **Answer-first (FR-022)**: add an optional-but-required-when-published `answer` frontmatter field (plain text, ~1-3 sentences, length-bounded e.g. 40-300 chars). `build-articles.mjs` fails a published article lacking it. Additionally require at least one `##` heading whose text ends with `?` (question-style). Render `answer` as a lead `<p class="ig-article__answer">` above the body in `article-detail` (visible + extractable).
- **No-orphan (FR-023)**: in `loadArticles()`, after resolving `relatedSlugs`, compute inbound counts per published article (number of *other* same-language articles whose `relatedSlugs` include it). Fail the build for any article with `< 2` inbound references (the `/articles` index does not count). Threshold = 2 per Session 2026-06-06 clarification.

**Rationale**: Turns two previously-subjective standards into deterministic build gates (testable, no crawler needed). The `answer` lead doubles as the snippet/AI-Overview extractable answer. Existing articles already carry `relatedSlugs`; raising density to >=2 inbound is a content edit, not new infrastructure.

**Alternatives considered**: *Infer the answer from the first paragraph* — brittle and unenforceable; an explicit field is unambiguous and reusable for meta/AI. Rejected.

---

## R9. Organization `@id` graph + `sameAs`

**Decision**: In `structured-data.service.ts`, give the Organization a stable `@id` (e.g. `${origin}/#organization`), add a `sameAs` array sourced from config (`environment` / a small constant; empty array if no official profiles exist yet — deferred gracefully per spec). Reference the same `@id` from the WebSite `publisher`/`Article.publisher` so the graph is consolidated rather than repeating standalone Organization objects.

**Rationale**: `@id` consolidation is the cheap, high-value entity-disambiguation step (FR-021); `sameAs` strengthens brand-entity weighting (US6). Empty `sameAs` is valid and avoids blocking when no profiles exist.

**Alternatives considered**: *Hard-code social URLs now* — none confirmed to exist; config-driven with empty default is safer. Rejected hard-coding.

---

## Summary of new/changed dependencies

| Dependency | Type | Justification | Constitution |
|------------|------|---------------|--------------|
| `lighthouse` (+ a tiny static server; Chrome via existing `puppeteer`) | devDependency | Lab CWV gate (R3) | II: current release line, dev-only, MVP-ok |
| Cloudflare Web Analytics beacon | external runtime `<script>` | Cookieless measurement (R2) | I/II: host-native, no npm dep, no infra |
| `sharp` | devDependency, **only if needed** | Build-time image resize (R4) | Add only on demonstrated need (YAGNI) |

No backend, LLM, payment, or database dependencies are added or changed.
