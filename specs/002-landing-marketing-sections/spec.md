# Feature Specification: Landing marketing sections (footer, pricing preview, sample results)

**Feature Branch**: `002-landing-marketing-sections`
**Created**: 2026-06-03
**Status**: Draft
**Input**: User description: "Add the concept's footer, transparent-pricing preview, and sample-results sections to the public landing/shell"

## Overview

The UI redesign (feature `001-ui-redesign`) restyled every existing screen but deliberately scoped
itself to *restyling existing content only*. Three sections that exist in the design concept
(`design/redesign-concept.html`) were therefore left out of the application:

1. A site **footer** (brand + tagline, product links, legal links, copyright, "Made in Ukraine").
2. A **"transparent pricing" preview** on the public landing page — a marketing teaser of the token
   packs with a call to register.
3. A **"sample results" ("Examples") section** on the public landing page — an illustrative set of
   instrument cards showing what a real search returns.

This feature adds those three sections so the public landing page and the shell match the concept's
completeness and better communicate value to anonymous visitors before they sign up. All three are
**marketing / informational** surfaces: the sample results and pricing preview are clearly-labelled
illustrative content, not a live search and not the live purchase flow. The real, authoritative
search and pricing remain behind authentication on the existing `/search` and `/tokens` pages.

This work depends on the `001-ui-redesign` design language (tokens, instrument-card and dark-section
visual patterns, motion, accessibility baseline) and reuses it; it introduces no new business logic.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - An anonymous visitor sees concrete examples before signing up (Priority: P1)

A first-time, signed-out visitor lands on the home page and, in addition to the hero and "how it
works" steps, sees a **"sample result"** section: three realistic instrument cards (e.g. a bank
deposit, war bonds, a money-market fund) with expected-return figures, risk and category labels,
key facts, a short rationale, and an "official source" affordance — clearly marked as an
illustrative example. This lets them judge the product's usefulness before creating an account.

**Why this priority**: The examples section is the strongest conversion driver — it shows, rather
than tells, what the product delivers. It is the most valuable of the three additions and is
independently demonstrable on the landing page.

**Independent Test**: Open the landing page signed out; confirm a clearly-labelled "sample result"
section renders three example instrument cards with returns, badges, facts, rationale, and a
source affordance, plus a visible "figures are illustrative" disclaimer; confirm it resolves in
both Ukrainian and English and costs nothing / requires no sign-in.

**Acceptance Scenarios**:

1. **Given** a signed-out visitor on the landing page, **When** they scroll past "how it works",
   **Then** they see a "sample result" section header and three example instrument cards in the
   redesign's card style.
2. **Given** the sample section, **When** it is shown, **Then** each card displays an expected-return
   range, a localized category and risk label (risk color matches severity), key facts, a short
   rationale, and an "official source" affordance, and the section carries a disclaimer that the
   figures are indicative/illustrative and not financial advice.
3. **Given** any language, **When** the visitor toggles Ukrainian/English, **Then** every label and
   the disclaimer update correctly with no missing or hardcoded text.
4. **Given** the sample section, **When** a visitor interacts with it, **Then** no token is consumed
   and no sign-in is required (it is static illustrative content, not a live search).

---

### User Story 2 - An anonymous visitor understands pricing and is invited to register (Priority: P2)

A signed-out visitor sees a **"transparent pricing"** section previewing the token packs (e.g. 10 /
30 / 100 tokens) with a clear per-token value and a note that the first tokens are free after email
verification, plus a "Register" call to action. The middle pack is highlighted as the best value.

**Why this priority**: Pricing transparency builds trust and drives registration, but it is
secondary to showing the product working (US1). It is independently demonstrable.

**Independent Test**: Open the landing page signed out; confirm a pricing section shows the pack
options with per-token value and a free-tokens note, the middle/best-value pack is visually
highlighted, and the CTAs route to registration; confirm both languages resolve and any monetary
figures are accurate and consistent with the real `/tokens` packs (or clearly illustrative).

**Acceptance Scenarios**:

1. **Given** a signed-out visitor, **When** they reach the pricing section, **Then** they see the
   available token packs with a per-token value, a "first tokens free after verification" note, and
   a fineprint that payments are handled securely by the configured provider.
2. **Given** three packs are shown, **When** the section renders, **Then** the middle/best-value pack
   is visually highlighted and its CTA is emphasized.
3. **Given** any pack CTA, **When** the visitor activates it, **Then** they are taken to
   registration (anonymous) — the section does not start a real purchase from the public page.
4. **Given** monetary figures are displayed, **When** they are shown, **Then** they are exact to the
   minor unit (no rounding errors) and the pricing shown is consistent with the authoritative
   `/tokens` packs (single source of truth) or is unambiguously labelled as illustrative.

---

### User Story 3 - Every visitor can reach key links and trust signals via a footer (Priority: P2)

Any visitor, on any public page, sees a consistent **footer**: the brand and tagline, product links
(Search, Providers, Tokens), legal links (Terms, Privacy), a copyright line, and a "Made in Ukraine"
trust mark.

**Why this priority**: A footer is a baseline expectation for a credible financial site and provides
navigation/legal reachability, but it supports rather than drives the core conversion, so it sits
alongside pricing at P2.

**Independent Test**: Load any public page; confirm a footer renders with brand + tagline, grouped
product and legal links that navigate correctly, a copyright line, and the "Made in Ukraine" mark,
all resolving in both languages and reachable by keyboard.

**Acceptance Scenarios**:

1. **Given** a visitor on a public page, **When** the page loads, **Then** a footer appears at the
   bottom with the brand, tagline, product links, legal links, copyright, and "Made in Ukraine".
2. **Given** the footer links, **When** a visitor selects one, **Then** it navigates to the correct
   destination (existing routes for product links; Terms/Privacy per the resolved target).
3. **Given** any language, **When** the visitor toggles Ukrainian/English, **Then** all footer text
   updates with no hardcoded strings.
4. **Given** a keyboard user, **When** they tab into the footer, **Then** every link shows a visible
   focus indicator and the footer meets AA contrast on its dark background.

---

### Edge Cases

- **Illustrative vs. real**: the sample-results and pricing-preview figures must be unmistakably
  framed as examples/illustrative so no visitor mistakes them for personalized advice or a live
  quote; the financial-information disclaimer must be present.
- **Pricing accuracy**: if the pricing preview shows real prices, they must match the authoritative
  packs exactly (minor-unit integers, no float); stale or inconsistent prices are a defect.
- **Terms/Privacy not yet built**: legal links must resolve to a sensible destination (an existing
  page, a placeholder "coming soon" page, or an external document) rather than a broken link.
- **Authenticated context**: the landing marketing sections are for signed-out visitors; signed-in
  users are already redirected off the landing page, so the sections need not target them. The
  footer's behavior on authenticated app pages is governed by the resolved footer-scope decision.
- **Language toggle at runtime** must update every new label and disclaimer immediately.
- **Responsive + reduced motion**: all three sections must collapse gracefully on small screens and
  honor reduced-motion (no content gated behind animation).
- **Empty/short content**: the footer and sample section must render correctly regardless of
  viewport height and not overlap the existing notification/toast layer.

## Requirements *(mandatory)*

### Functional Requirements

**Sample results ("Examples") section (landing)**

- **FR-001**: The public landing page MUST include a clearly-labelled "sample result" section showing
  at least three illustrative investment options presented in the redesign's instrument-card style.
- **FR-002**: Each sample card MUST show an expected-return range, a localized category label, a
  localized risk label whose color reflects severity, key facts, a short rationale, and an "official
  source" affordance, consistent with how real results are presented.
- **FR-003**: The sample section MUST carry a visible disclaimer that the figures are
  indicative/illustrative and that the product provides information, not financial advice.
- **FR-004**: The sample section MUST require no sign-in and consume no tokens (it is static
  illustrative content, not a live search).

**Transparent-pricing preview (landing)**

- **FR-005**: The public landing page MUST include a "transparent pricing" section previewing the
  token packs with, for each pack, the token count, the price, and a per-token value.
- **FR-006**: The pricing section MUST state that the first tokens are free after email verification
  and MUST show a fineprint that payments are handled securely by the configured payment provider.
- **FR-007**: When three packs are shown, the section MUST visually highlight the middle/best-value
  pack and emphasize its call to action.
- **FR-008**: Pricing CTAs on the public page MUST lead to registration and MUST NOT initiate a real
  purchase from the anonymous landing page.
- **FR-009**: Any monetary amount shown MUST be exact to the minor unit (no float rounding) and MUST
  be consistent with the authoritative `/tokens` packs, OR be unambiguously labelled as illustrative.

**Footer (shell)**

- **FR-010**: The application MUST present a footer containing the brand and tagline, a "Product"
  link group (Search, Providers, Tokens), a "Legal" link group (Terms, Privacy), a copyright line,
  and a "Made in Ukraine" mark.
- **FR-011**: Footer product links MUST navigate to the existing in-app destinations; legal links
  MUST resolve to a valid destination (existing page, placeholder, or external) with no broken link.
- **FR-012**: The footer MUST appear on the public landing page at minimum; its presence on other
  pages follows the resolved footer-scope decision (see Assumptions).

**Cross-cutting**

- **FR-013**: All visible text in the three sections MUST be provided through the existing translation
  mechanism in both Ukrainian and English, with the two language sets kept identical in coverage;
  no user-facing string may be hardcoded.
- **FR-014**: All three sections MUST reuse the `001-ui-redesign` design system (tokens, card and
  dark-section patterns, motion, focus) and MUST meet the same accessibility baseline: WCAG AA
  contrast, visible keyboard focus, reduced-motion support, never-color-alone status/risk, decorative
  elements hidden from assistive tech.
- **FR-015**: The additions MUST NOT change existing business logic, validators, services, routing of
  existing screens, money math, or the i18n mechanism; money stays integer minor units.
- **FR-016**: The additions MUST be responsive (graceful single-column collapse on small screens) and
  MUST NOT introduce a new third-party dependency or violate the production per-component style
  budget; components stay standalone and on the existing change-detection strategy.

### Key Entities

This feature adds presentation/marketing content. The only data concepts are:

- **Sample option (illustrative)**: a hard-coded, clearly-labelled example investment option used for
  display on the landing page. It is not sourced from the live advisor and carries no token cost.
- **Pricing-preview pack**: the token-pack figures shown in the pricing section. Either the
  authoritative pack data (single source of truth, amounts in integer minor units) or clearly
  illustrative figures — resolved per Assumptions.
- **Footer links**: a static set of navigation/legal links (labels via i18n, destinations as routes
  or external URLs).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The public landing page presents all three new sections (sample results, pricing
  preview, footer) in the redesign's visual language, in addition to the existing hero and "how it
  works" content.
- **SC-002**: 100% of new visible text resolves in both Ukrainian and English with identical key
  coverage and zero hardcoded user-facing strings.
- **SC-003**: The sample-results and pricing-preview sections each carry an unmistakable
  "illustrative / not financial advice" framing (verified present on every render).
- **SC-004**: Any monetary figure shown is exact to the minor unit and, where real pricing is shown,
  matches the authoritative `/tokens` packs (0 discrepancies).
- **SC-005**: All three sections meet WCAG AA contrast, show a visible keyboard focus indicator on
  every interactive element, and suppress animation under reduced-motion (0 failures spot-checked).
- **SC-006**: Footer and pricing CTAs navigate to the correct destinations with 0 broken links.
- **SC-007**: No behavioral regression to existing screens; the production build passes with no
  per-component style-budget warning and no new dependency.
- **SC-008**: On a phone-width viewport, all three sections collapse to a single readable column with
  nothing clipped or overlapping the toast layer.

## Assumptions

- **Depends on `001-ui-redesign`**: this feature builds on the merged redesign design system and its
  instrument-card / dark-section / footer-ready tokens. It should land after, or on top of, that work.
- **Audience**: the landing pricing-preview and sample-results sections target signed-out visitors
  (authenticated users are redirected off the landing page). They are marketing/informational, not
  the authoritative search or purchase flows.
- **Sample data is illustrative**: the example option cards use hard-coded, clearly-labelled sample
  figures (consistent with the existing landing "trust ledger" treatment) — not a live advisor call —
  so the public page needs no API call and incurs no token cost.
- **Pricing-preview source (default)**: the pricing preview shows illustrative pack figures matching
  the concept (10 / 30 / 100 tokens) and is clearly framed as a preview, with CTAs to register; if a
  public read of the real packs is preferred later, amounts must come from the authoritative source
  in integer minor units. (Decision flagged as the main open choice — see below.)
- **Footer scope (default)**: the footer renders site-wide in the shell beneath the router outlet, on
  both public and authenticated pages, for consistency; if it should be public-pages-only, that is a
  small scoping change.
- **Terms/Privacy targets (default)**: legal links point to the existing placeholder "coming soon"
  page (or are hidden until those pages exist) rather than producing a broken link; final targets to
  be confirmed when the legal pages are authored.
- **i18n**: new keys are additive in both `uk.json` and `en.json`, kept key-for-key identical, using
  ASCII punctuation consistent with existing entries; Cyrillic values are fine (JSON is UTF-8).
- **No new dependencies / encoding**: only Angular templates/styles, the global stylesheet, and the
  two i18n files are touched (all UTF-8). No `.ps1/.cmd/.bat` scripts are involved, so the
  Windows-script ASCII rule does not apply here.

> Open choice to confirm before/at planning (does not block this spec): **pricing-preview data
> source** — illustrative figures (default) vs. a live read of the authoritative packs. This affects
> whether the public landing page makes a data request and how price accuracy is guaranteed.
