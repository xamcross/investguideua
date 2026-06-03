# Feature Specification: InvestGuideUA UI/UX Redesign

**Feature Branch**: `001-ui-redesign`
**Created**: 2026-06-03
**Status**: Draft
**Input**: User description: "check this file docs/UI-REDESIGN-SPEC.md and take it as a spec for a feature"

## Overview

A complete visual and experience restyle of the InvestGuideUA web application under one design
language - "institutional trust + Ukrainian identity." The product advises Ukrainians on regulated
investments, so the interface must feel like a credible financial institution while carrying a
dignified, refined Ukrainian identity (not a flag-themed novelty). The redesign replaces the
current flat blue / lemon-yellow look with a warm "document paper" surface, institutional navy
text, Ukraine blue as the single interactive color, a refined wheat-gold accent, an editorial
type system, layered depth, and restrained, optional motion.

This is a presentation-layer redesign only. It does not change what the product does, what data it
stores, how money is calculated, who can sign in, or which screens exist - it changes how every
existing screen looks and how reachable and accessible it is. The one behavioral improvement
permitted is a working responsive mobile navigation, because the current experience hides primary
navigation and the user's token balance on small screens with no fallback - a real defect this
redesign corrects.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A trustworthy first impression and confident search (Priority: P1)

A prospective or returning user opens the application and is met with a cohesive, editorial,
financial-document aesthetic that signals credibility. They read the landing page, understand how
the advisor works in three steps, move into the search experience, submit an investment request,
and read back clearly-structured investment options - each option presented as a polished
"instrument card" with a prominent expected-return figure, risk and category labels, key facts,
and a link to an official source.

**Why this priority**: The landing -> search -> results path is the core value loop of the product
and the most-visited surface. If only this slice ships, the application already presents its
primary journey in the new trustworthy design language and delivers immediate perceived-quality
value. Everything the product promises ("catalog-grounded advice you can verify") is communicated
here.

**Independent Test**: Can be fully tested by visiting the landing page as an anonymous user,
reading the hero and "how it works" section, signing in, running a search with a valid amount and
currency, and confirming the returned options render as instrument cards with localized risk and
category labels, an expected-return figure, facts, and a working official-source link - all in the
new visual system, in both Ukrainian and English.

**Acceptance Scenarios**:

1. **Given** an anonymous visitor, **When** they open the landing page, **Then** they see an
   editorial hero with a clear value proposition, a three-step "how it works" explanation, and a
   visible disclaimer, all in the warm-paper institutional style.
2. **Given** a signed-in user with available search tokens, **When** they submit a valid search,
   **Then** each returned option is shown as an instrument card with a prominent expected-return
   range, a localized risk label whose color matches its severity, a localized category label, key
   facts, an optional rationale, and an official-source link that opens safely in a new tab.
3. **Given** a signed-in user with zero tokens, **When** they open search, **Then** they see a
   clear notice with a path to buy more tokens, and the submit action is unavailable.
4. **Given** any screen, **When** the user switches the interface language between Ukrainian and
   English, **Then** every visible label, heading, badge, and currency word updates correctly with
   no missing or hardcoded text.

---

### User Story 2 - Reachable navigation and account on any device (Priority: P1)

A user on a phone or narrow window needs to move between search, history, providers, their account,
and their token balance, and to switch language - without any of those being hidden or unreachable.

**Why this priority**: The current experience hides primary navigation and the token balance below
a certain width with no fallback, which makes core functions unreachable on mobile. This is a real
defect; fixing it is required for the redesign to be acceptable, and it gates the usefulness of
every other screen on mobile.

**Independent Test**: Can be tested by narrowing the viewport to phone width, confirming a menu
control and the language toggle remain visible in the bar, opening the menu, and confirming every
navigation item - including the token balance - is reachable, and that selecting an item navigates
and closes the menu.

**Acceptance Scenarios**:

1. **Given** a narrow (mobile) viewport, **When** the page loads, **Then** a menu control and the
   language toggle are visible in the top bar, and the token balance is never hidden.
2. **Given** the mobile menu is closed, **When** the user activates the menu control, **Then** all
   navigation items including the token balance are revealed as reachable rows.
3. **Given** the mobile menu is open, **When** the user selects a navigation item, **Then** the app
   navigates to that destination and the menu closes.
4. **Given** a keyboard-only user, **When** they tab through the top bar, **Then** the brand, links,
   balance, language toggle, and menu control each show a clearly visible focus indicator.

---

### User Story 3 - Accessible, comfortable use for everyone (Priority: P2)

A user who relies on a keyboard, a screen reader, high color contrast, or reduced motion can
perceive and operate every screen comfortably: text meets contrast standards, status and risk are
never communicated by color alone, focus is always visible, and animations can be turned off.

**Why this priority**: Accessibility is a first-class principle of this redesign and a legal/ethical
baseline for a financial product, but it layers onto the screens delivered in P1, so it is verified
as a cross-cutting quality rather than a standalone first slice.

**Independent Test**: Can be tested by running contrast checks on text and status colors, navigating
the entire app by keyboard, enabling the operating-system "reduce motion" setting and confirming
animations are suppressed while all content stays visible, and confirming every risk/status
indicator carries a text label in addition to its color.

**Acceptance Scenarios**:

1. **Given** any screen, **When** body text, status colors, and accent text are measured, **Then**
   they meet WCAG AA contrast.
2. **Given** a user with "reduce motion" enabled, **When** they navigate the app, **Then** entrance
   and hover animations are suppressed and no content is hidden or stuck invisible.
3. **Given** any risk or status indicator, **When** it is shown, **Then** it carries a translated
   text label in addition to its color.
4. **Given** a keyboard user, **When** they move focus across any interactive element, **Then** a
   visible focus indicator is always present.

---

### User Story 4 - Consistent design across every remaining screen (Priority: P3)

A user moving through token purchase, payment result, sign-in / registration / verification,
history list and detail, account, providers, and the not-found / placeholder screens experiences
the same cohesive design language, with each screen restyled to its purpose (e.g. an institutional
"pack wall" for buying tokens, a centered "credential document" for auth, an editorial "ledger" for
history) while behaving exactly as before.

**Why this priority**: These screens complete the consistency of the redesign but sit off the core
value loop; they can be delivered after the primary journey and cross-cutting accessibility are in
place. Each is independently demonstrable.

**Independent Test**: Can be tested by visiting each remaining screen and confirming it adopts the
new design language, that its existing behavior (purchasing, payment polling, auth flows, history
paging, account actions, provider listings) is unchanged, and that all copy resolves in both
languages.

**Acceptance Scenarios**:

1. **Given** the buy-tokens screen with exactly three packs, **When** it renders, **Then** it
   appears as a dark institutional section and the middle pack is highlighted as recommended, while
   purchasing behavior is unchanged.
2. **Given** the payment-result screen, **When** a payment is processing, succeeding, or failing,
   **Then** the correct state is shown with an appropriate live status message and the existing
   navigation links.
3. **Given** sign-in, registration, and verification, **When** they render, **Then** each appears as
   a centered credential card and all existing validation, messages, and flows behave as before.
4. **Given** the history list, **When** it renders, **Then** rows show amount, currency, date, a
   color-and-text status chip, and option count, and paging works exactly as before; an empty
   history shows an editorial empty state with a path to search.
5. **Given** the not-found screen, **When** it renders, **Then** it shows an on-brand empty state
   with the page-not-found message (the "404" presented as a separate badge) and a link home.

---

### Edge Cases

- **Language switch at runtime**: Every restyled surface (eyebrows, titles, badges, currency words,
  sample labels) must update immediately when the user toggles language; no string may be left
  hardcoded or untranslated.
- **Unknown or unexpected status value** on a history row must fall back to a neutral chip without
  breaking the page.
- **No options returned** from a search must still show the existing clear "no options" notice in
  the new style.
- **Reduced-motion users and users with scripting limitations** must always see content that would
  otherwise be revealed by animation; nothing critical may be gated solely behind motion.
- **Very narrow or very short viewports** must keep navigation, the token balance, and auth cards
  reachable and scrollable without clipping.
- **Currency display**: the localized currency word (e.g. Ukrainian "грн" vs "UAH"/"USD") must be a
  display label only and must never alter the numeric amount or its formatting.
- **Slow font loading** must not produce invisible text; fallback fonts must render meanwhile.

## Requirements *(mandatory)*

### Functional Requirements

**Design system & consistency**

- **FR-001**: The application MUST present every existing screen under a single, consistent design
  language ("institutional trust + Ukrainian identity"): warm paper surfaces, institutional navy
  text, Ukraine blue as the one interactive color, and a refined wheat-gold accent.
- **FR-002**: The interface MUST use a coordinated editorial type system (a display serif for
  headings and figures, a humanist sans for UI and body, and a monospace for figures, codes, and
  micro-labels), loaded once without causing invisible text during load.
- **FR-003**: The lemon flag-yellow MUST be retired in favor of the refined wheat-gold accent;
  blue/gold flag motifs MUST appear only as thin decorative accents, never as large fills.
- **FR-004**: Shared visual elements (cards, buttons, form fields, alerts, badges, status chips,
  headings, eyebrows, empty states, page headers, focus styling, motion) MUST be visually
  consistent everywhere they appear.

**Core journey presentation**

- **FR-005**: The landing page MUST present an editorial hero with a clear value proposition, a
  three-step explanation of how the advisor works, a representative sample "trust ledger" visual,
  and a visible disclaimer.
- **FR-006**: The search form MUST present its inputs (amount, currency, horizon, risk, goals) in
  the new style while keeping all existing input behavior, limits, and validation messages.
- **FR-007**: Each returned investment option MUST be presented as an "instrument card" showing the
  provider/instrument, localized category and risk labels, a prominent expected-return figure with
  a per-year unit, key facts, an optional rationale, and an official-source link that opens safely.
- **FR-008**: Risk and status indicators MUST convey meaning by both color and a translated text
  label, never by color alone.

**Remaining screens**

- **FR-009**: The buy-tokens screen MUST be presented as a dark institutional section; when exactly
  three packs are offered, the middle pack MUST be visually highlighted as recommended.
- **FR-010**: The payment-result screen MUST clearly present each existing outcome state (polling/
  processing, success, failed, missing) with an appropriate live status message.
- **FR-011**: Sign-in, registration, and verification MUST each be presented as a centered
  credential card carrying the brand identity, with all existing states and messages preserved.
- **FR-012**: The history list MUST be presented as an editorial ledger where each row shows amount,
  localized currency, date, a color-and-text status chip, and option count, with existing paging
  preserved; an empty history MUST show an editorial empty state offering a path to search.
- **FR-013**: The history detail, account, providers, not-found, and placeholder screens MUST each
  adopt the consistent design language appropriate to their purpose while preserving all existing
  behavior, links, and data.

**Localization**

- **FR-014**: All visible text MUST be provided through the existing translation mechanism in both
  Ukrainian and English, with the two language sets kept identical in coverage; no user-facing
  string may be hardcoded.
- **FR-015**: The currency display word MUST be localized (Ukrainian "грн" vs "UAH"; "USD"
  unchanged) as a display label only, without changing any numeric amount or its formatting.
- **FR-016**: Only additive translation keys plus the two explicitly intended copy changes are
  permitted; no existing key may be removed or renamed. The not-found title MUST drop its leading
  "404 - " prefix (the code is shown as a separate badge).

**Accessibility (cross-cutting)**

- **FR-017**: All text, status color pairs, and accent text MUST meet WCAG AA contrast; small gold
  text MUST use the darker gold tone reserved for that purpose.
- **FR-018**: Every interactive element MUST show a clearly visible keyboard focus indicator;
  outlines MUST never be removed without a visible replacement.
- **FR-019**: The interface MUST honor the user's reduced-motion preference by suppressing animation
  and transitions while keeping all content visible.
- **FR-020**: A working responsive mobile navigation MUST keep all navigation items - including the
  token balance - reachable at every viewport width; the token balance MUST never be hidden, and a
  visible menu control and language toggle MUST remain in the bar on narrow screens.

**Constraints (what MUST NOT change)**

- **FR-021**: No business or application logic may change: search/validation rules, services,
  authentication and authorization, payment polling, and error handling behave exactly as before.
- **FR-022**: Money handling MUST remain unchanged: all amounts stay integer minor units and all
  existing amount formatting is preserved; the localized currency label is display-only.
- **FR-023**: The set of screens, navigation destinations, and routing MUST remain unchanged (aside
  from the additive mobile-menu behavior).
- **FR-024**: No new third-party dependencies may be introduced and the existing framework/platform
  choices MUST remain unchanged.
- **FR-025**: The shared options renderer MUST render identically wherever it is reused (search
  results and history detail).

### Key Entities

This is a presentation-layer redesign; it introduces no new data entities and changes no existing
data shapes. The following are display concepts only:

- **Investment option ("instrument card")**: an existing search result, re-presented with provider/
  instrument identity, localized category and risk labels, an expected-return figure, key facts, an
  optional rationale, and an official-source link. No underlying data changes.
- **Token pack**: an existing purchasable pack, re-presented in a dark "pack wall"; the "recommended"
  emphasis on the middle of three packs is a display-only treatment, not a data attribute.
- **History entry / status chip**: an existing history record, re-presented with a color-and-text
  status chip (completed / pending / failed) reusing the shared risk language.
- **Currency label**: a display-only localized word mapped from an existing currency code; carries
  no amount or math.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the application's existing screens are presented in the new design language
  with no screen left in the legacy style.
- **SC-002**: 100% of visible text resolves correctly in both Ukrainian and English, with the two
  language sets identical in coverage and zero hardcoded user-facing strings.
- **SC-003**: All navigation items, including the token balance, are reachable at every viewport
  width down to common phone widths; the token balance is never hidden (0 unreachable items).
- **SC-004**: All text, status-color pairs, and accent text meet WCAG AA contrast (spot-checked
  across every screen, 0 failures).
- **SC-005**: Every interactive element shows a visible keyboard focus indicator (0 elements with a
  removed-and-unreplaced outline).
- **SC-006**: With reduced motion enabled, all animation and transitions are suppressed and 0
  pieces of content are left hidden or stuck invisible.
- **SC-007**: Every risk and status indicator carries a translated text label in addition to its
  color (0 color-only indicators).
- **SC-008**: No behavioral regressions: search, validation, authentication, payment, history
  paging, account actions, and amount formatting behave identically to before the redesign (0
  observed behavior changes), and amounts remain exact to the minor unit.
- **SC-009**: The production build succeeds with no per-component style-budget warning or error, and
  the shared stylesheet parses cleanly.
- **SC-010**: The core journey (open landing -> understand the offering -> run a search -> read an
  option's expected return, risk, and official source) is completable by a first-time user without
  external help.

## Assumptions

- The redesign restyles the existing application only; no new screens, navigation destinations, or
  capabilities are added beyond the responsive mobile-navigation defect fix.
- The existing translation mechanism and language toggle are reused; the redesign only adds keys
  (and the two explicitly intended copy changes) and keeps the two language sets identical.
- The "recommended" highlight applies specifically when exactly three token packs are offered; with
  any other count, no pack is specially highlighted.
- Decorative sample figures on the landing "trust ledger" are illustrative only and are not real
  money calculations.
- WCAG AA is the accepted accessibility conformance target.
- Common phone widths (around and below typical handset breakpoints) are the lower bound for the
  responsive-navigation requirement; ultra-narrow and very short viewports must still keep
  navigation and key content reachable and scrollable.
- Verification of layout/style budget and parsing is performed by running the production build, not
  by reviewing rendered text alone.
- The source technical specification at `docs/UI-REDESIGN-SPEC.md` is the authoritative
  implementation reference; this document captures the user-facing intent and requirements derived
  from it.
