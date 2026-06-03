# Phase 0 Research: Landing marketing sections

Decisions grounded in the design concept (`design/redesign-concept.html`), the existing codebase,
and the constitution. The spec carried one explicit open choice (pricing-preview data source),
resolved below, plus a few defaults worth recording.

## D1. Pricing-preview data source — illustrative figures (RESOLVED)

- **Decision**: The transparent-pricing preview shows **hard-coded, clearly-labelled illustrative**
  pack figures (matching the concept: 10 / 30 / 100 tokens with example prices and per-token value),
  framed as an example, with CTAs that route to **registration**. It makes **no API call** and starts
  no purchase.
- **Rationale**: The authoritative `/tokens/packs` endpoint is **authenticated** — confirmed in
  `backend/.../security/SecurityConfig.java` (`PUBLIC_GET` is only `/api/v1/ping` + `/actuator/health`;
  everything else is `anyRequest().authenticated()`), and `TokenPackController` is documented
  "Authenticated, read-only", with the `/tokens` route gated by `verifiedGuard`. An anonymous landing
  page therefore cannot read real packs without **relaxing the security model** — a backend change
  that is out of presentation scope and contrary to Constitution I (MVP) and the security model.
  Illustrative figures keep the page anonymous-safe, add no network dependency, and (clearly labelled
  + linking to register where live prices are shown) are honest marketing.
- **Guardrail (Constitution IV)**: the figures MUST be unmistakably framed as an example (label +
  the section/landing disclaimer), never presented as an authoritative quote; no float math.
- **Alternatives considered**: (a) Expose `/tokens/packs` publicly and fetch real packs — rejected:
  backend security change, widens the anonymous attack surface, out of scope. (b) Show token counts
  without prices — rejected: weaker than the concept and less informative. (c) Server-render real
  prices behind auth on the landing — rejected: the landing is for signed-out visitors.

## D2. Footer scope — site-wide in the shell (RESOLVED)

- **Decision**: Render the footer once in `app.component` beneath the `<router-outlet>`, so it appears
  on **every** page (public and authenticated) for consistency.
- **Rationale**: A single shell-level component (mirroring the existing `ig-notification-host`) is the
  least code, guarantees consistency, and matches the concept. Authenticated pages benefit from the
  legal/links footer too. Stacking order stays clear (footer is normal flow; toasts remain fixed
  `z-index:1000` above it).
- **Alternatives considered**: Landing-only footer (rejected: inconsistent, more conditional logic);
  a footer per feature component (rejected: duplication, drift).

## D3. Footer legal links — additive placeholder routes (RESOLVED)

- **Decision**: Footer "Product" links reuse existing routes (`/search`, `/providers`, `/tokens`).
  "Legal" links (`Terms`, `Privacy`) point to **new additive routes** `/terms` and `/privacy` that
  render the existing `PlaceholderComponent` ("coming soon") via route `data.heading`.
- **Rationale**: Avoids broken links without authoring real legal pages now; `PlaceholderComponent`
  already supports a `heading` input and a back-home link. Adding new routes is additive and does not
  change any existing route (Constitution-safe). When real Terms/Privacy pages are written they simply
  replace the placeholder targets.
- **Alternatives considered**: External URLs (rejected: none exist yet); hiding the legal group
  (rejected: the concept shows it and a financial site is expected to have legal links); linking to
  `#` (rejected: broken link, FR-011 violation).

## D4. Sample-results data — hard-coded illustrative options (RESOLVED)

- **Decision**: The sample-results section renders 3 **hard-coded illustrative** options (e.g. a bank
  deposit, war bonds, a money-market fund) as display constants in the landing component, reusing the
  global `.ig-opt` instrument-card classes from `001-ui-redesign`. The section header labels it a
  "sample result" and carries a disclaimer that figures are indicative and not financial advice.
- **Rationale**: A live advisor call would cost a token, require auth, and invoke the LLM — none of
  which suit an anonymous marketing teaser (Constitution III/IV). Static, clearly-labelled examples
  match the concept and the existing landing "trust ledger" precedent, need no API, and reuse the
  redesign's card language so they look identical to real results.
- **Alternatives considered**: Live search preview (rejected: token cost, auth, LLM exposure);
  a screenshot image (rejected: not localizable, not responsive, worse a11y).

## D5. Shared CSS placement + style budget

- **Decision**: Promote a global `.ig-section--dark` (the navy marketing-section shell) to
  `styles.css` and reuse the global `.ig-opt`/card and pack-preview rules; the footer carries its own
  component `styles[]`; the two landing sections keep only thin layout-local CSS so the landing
  component stays well under the 4 kb warn / 8 kb error budget.
- **Rationale**: The redesign already centralizes shared CSS globally; `.ig-section--dark` was even
  referenced (then dropped) during 001, so defining it globally now serves both the landing pricing
  preview and the tokens page. Keeps each component lean and the budget green.
- **Alternatives considered**: All-in landing component styles (rejected: risks the per-component
  budget and duplicates the dark-section rules already implied by tokens).

## D6. i18n parity

- **Decision**: Add footer (`footer.*`), sample-results (`landing.sample*`), and pricing
  (`landing.pricing*`) keys to BOTH `uk.json` and `en.json`, kept key-for-key identical, ASCII
  punctuation; ground the copy in the concept's UK/EN strings (`ft.*`, `res.*`, `pk.*`). Reuse
  existing keys where possible (`nav.search`/`nav.providers`, `common.officialSource`, `risk.*`,
  `category.*`, `currency.*`, the landing disclaimer).
- **Rationale**: Satisfies FR-013/SC-002; identical key sets prevent runtime gaps. The concept
  already provides bilingual copy, so translation is grounded, not invented.
- **Alternatives considered**: Hardcoding strings (rejected: violates i18n rule); reusing the live
  results/tokens keys verbatim (partially — reuse where identical, add marketing-specific keys
  otherwise to avoid coupling marketing copy to functional screens).

## D7. Verification method

- **Decision**: Production build (`npm run build`) is the authoritative parse + budget gate; plus a
  manual UK/EN toggle and accessibility spot-checks; then the mandatory two-role review including a
  real build. Node 24/npm 11 are available, so the build runs for real (not static-only).
- **Rationale**: Constitution V/VI require parse/compile verification, not reading. No scripts are
  touched, so the non-ASCII Windows scan has nothing to flag.

## Open questions

None blocking. The pricing-preview data source (D1) was the one flagged choice and is resolved to
illustrative. If product later wants live public pricing, that is a separate backend change (expose a
public read of active packs) and a new spec.
