# Feature Specification: Frontend UI/UX Audit & Improvements

**Feature Branch**: `007-ui-ux-improvements`
**Created**: 2026-06-04
**Status**: Draft
**Input**: User description: "check the web app for UI/UX issues, layout and/or styling issues. suggest improvements based on SOTA practices and guidelines"

## Overview

The web app already ships a deliberate "institutional trust + Ukrainian identity" design language (warm paper background, institutional navy, Ukraine-blue interactive accents, wheat-gold highlights, editorial serif headings). A full-app audit confirms the visual foundation is strong, but surfaced a set of accessibility, responsive, and consistency gaps that fall short of current state-of-the-art (SOTA) practice — most importantly WCAG 2.2 AA conformance, which is a baseline expectation for a financial-trust product. This feature collects those findings into a prioritized improvement effort.

The work is a **presentation-layer hardening pass**: it changes how existing screens look and behave, not what data they show or what the product does. No new business capabilities, pages, or backend changes are introduced.

Note on baseline: parts of the accessibility foundation already exist (a global keyboard focus ring, a global reduced-motion suppression block, a screen-reader-only utility, and correct error association on the primary search form). Requirements below are therefore framed as **audit-and-close-the-remaining-gaps**, not greenfield implementation. Where a capability is already largely in place, the requirement names the specific screens or controls still falling short rather than implying the whole app is broken.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Accessible to keyboard and assistive-technology users (Priority: P1)

A person who navigates with a keyboard, screen reader, or other assistive technology can perceive, operate, and understand every screen of the app: jump past repeated navigation, reach and operate every control, know when a field is invalid and why, be told when search results or notifications appear, and use the app without motion that could trigger discomfort.

**Why this priority**: For a product that helps Ukrainians make money decisions, exclusion of users with disabilities is both an ethical and a legal/credibility risk. WCAG 2.2 AA is the recognized baseline and the single highest-impact gap found in the audit. Every other improvement builds on a usable, perceivable foundation.

**Independent Test**: Run an automated accessibility scan plus a manual keyboard-and-screen-reader pass over the primary journeys (landing -> search -> results, login/register, account, history, articles). The story is satisfied when those journeys report zero WCAG 2.2 A/AA violations and can be completed end-to-end with keyboard only.

**Acceptance Scenarios**:

1. **Given** the app has loaded on any page, **When** a keyboard user presses Tab from the top of the page, **Then** the first stop offers a visible way to skip directly to the main content, bypassing the header and navigation.
2. **Given** a form field has failed validation, **When** a screen-reader user moves focus to (or submits) that field, **Then** the error is announced and the field is programmatically marked invalid and linked to its error message.
3. **Given** a user has submitted the investment search, **When** results render, **Then** an assistive-technology user is notified that results are available without having to hunt for them.
4. **Given** a user has enabled "reduce motion" in their operating system, **When** any page loads or they interact with animated elements, **Then** no non-essential motion (reveal, scale, slide) plays.
5. **Given** any interactive control receives keyboard focus, **When** it is focused, **Then** a clearly visible focus indicator is shown that meets minimum contrast and is not obscured by hover/transform effects.
6. **Given** the mobile menu is open, **When** a keyboard user moves focus past the navigation, **Then** the menu does not trap focus or block the content beneath it.

---

### User Story 2 - Comfortable on phones and tablets (Priority: P2)

A person using the app on a phone or tablet can tap every control without mis-hitting, read content without horizontal scrolling, and sees layouts that adapt sensibly across small, medium, and large screens.

**Why this priority**: Mobile is the dominant access mode for a consumer financial-guidance product in Ukraine. Touch-target and breakpoint gaps directly cause input errors and frustration, but they sit on top of the accessibility foundation in Story 1.

**Independent Test**: Exercise the primary journeys at representative widths (e.g., 320px, 375px, 768px, 1024px, 1280px) and confirm every interactive element meets the minimum touch-target size, no content overflows horizontally, and column/grid layouts reflow without cramping.

**Acceptance Scenarios**:

1. **Given** any screen on a touch device, **When** a user taps a button, link, input, select, language toggle, menu icon, or notification-dismiss control, **Then** the tappable area meets the minimum recommended touch-target size.
2. **Given** a viewport as narrow as 320px, **When** any page is displayed, **Then** content fits within the viewport with no horizontal scrolling and no clipped text or figures.
3. **Given** a viewport in the tablet range (roughly 560-900px), **When** multi-column forms and grids are shown, **Then** columns reflow or resize so fields are not cramped or overlapping.
4. **Given** the mobile navigation menu is open, **When** the user taps a navigation link, **Then** the menu closes and the destination is shown.

---

### User Story 3 - Visually consistent and cohesive (Priority: P3)

A person moving between screens experiences one coherent design system: empty states, loading states, and error states look and behave the same way everywhere; spacing, type sizes, and rounded corners follow a consistent rhythm rather than ad-hoc per-screen values.

**Why this priority**: Consistency reinforces trust and reduces cognitive load, but the underlying screens are already individually usable — this is polish layered on top of correctness (P1) and comfort (P2).

**Independent Test**: Review every data-driven screen (history, providers, tokens, articles, search results, account) and confirm that empty, loading, and error presentations follow one shared pattern, and that spacing/typography/radius values resolve from the shared design tokens rather than one-off hardcoded values.

**Acceptance Scenarios**:

1. **Given** a screen has no data to show, **When** its empty state renders, **Then** it uses the same shared empty-state pattern (icon/mark, message, optional call-to-action) as every other empty screen.
2. **Given** a screen is fetching data, **When** the request is in flight, **Then** a consistent loading indication is shown across screens.
3. **Given** any two screens, **When** their spacing, font sizes, and corner radii are compared, **Then** the values come from the shared design scale rather than divergent ad-hoc values.
4. **Given** a status, risk, or category indicator is shown, **When** it appears on any screen, **Then** its color and contrast are consistent and legible against its background.

---

### User Story 4 - Clear, forgiving form interactions (Priority: P3)

A person filling in a form (search, login, register, account) understands which fields are required, gets timely feedback when something is wrong, sees helper text tied to the right field, and is protected from accidentally submitting twice.

**Why this priority**: Forms are the primary interaction surface, but the highest-severity form gaps (error association, labels) are already captured under accessibility in Story 1; this story covers the remaining usability refinements.

**Independent Test**: Walk each form through empty, invalid, valid, and submitting states and confirm required fields are indicated, validation feedback is timely and field-associated, character counters/hints are linked to their field, and controls cannot trigger a duplicate submission while a request is pending.

**Acceptance Scenarios**:

1. **Given** a form with required fields, **When** the form is displayed, **Then** required fields are visibly indicated before the user submits.
2. **Given** a user enters an invalid value, **When** they leave the field, **Then** an inline, field-specific message explains the problem.
3. **Given** the login form (the current worst case, with no per-field feedback), **When** the user submits with an empty or malformed email or password, **Then** each offending field shows an inline, programmatically associated error rather than only a form-level message.
4. **Given** a form is being submitted, **When** the request is in flight, **Then** the user cannot change values or resubmit until it resolves, and the pending state is visible.
5. **Given** a field has helper text or a character counter, **When** an assistive-technology user focuses the field, **Then** that helper text is announced as part of the field.

---

### Edge Cases

- **Very long content / large figures**: long provider names, multi-digit money figures, or long Ukrainian words must wrap rather than force horizontal scroll, including at 320px.
- **Slow or failed network**: every data-driven screen must show a clear loading indication and a recoverable error state (not a blank screen) when a request is slow or fails.
- **Reduced-motion + on-load reveal**: content that animates in on load must be fully visible and reachable when motion is disabled. (A scroll-reveal mechanism is defined in styles but currently unused; if it is ever adopted, the same guarantee must apply.)
- **Bilingual text length shifts**: switching between Ukrainian and English changes text length; layouts and touch targets must not break when labels grow or shrink.
- **Browser zoom up to 200%**: text and controls must remain usable and not clip or overlap when the page is zoomed.
- **Notification timing**: if a notification auto-dismisses, assistive-technology users must still be informed of its content before it disappears.

## Requirements *(mandatory)*

### Functional Requirements

**Accessibility (P1)**

- **FR-001**: Every page MUST provide a mechanism to bypass repeated navigation and move directly to the main content as the first keyboard-focusable action.
- **FR-002**: All form fields MUST programmatically associate their validation errors and helper text with the field, and MUST expose an invalid state to assistive technology when validation fails. The primary search form already does this; the audit MUST bring the remaining forms to parity — in particular the login form (currently no per-field validation or error association) and the register form (inline errors not linked to their fields) MUST be brought up to the same standard.
- **FR-003**: Dynamically appearing content that matters to the user (search results, notifications/toasts, async status changes) MUST be announced to assistive technology when it appears. The populated search-results region in particular currently has no live-region announcement and MUST gain one.
- **FR-004**: Non-essential motion MUST be fully suppressed when the user has requested reduced motion at the OS level. A global suppression rule already exists; the audit MUST verify that every animated element (including component-level hover/focus scale and slide transitions) is covered by it, and close any element that is not. Motion driven by an unused scroll-reveal mechanism is out of scope unless that mechanism is actually adopted.
- **FR-005**: Every interactive element MUST present a clearly visible keyboard-focus indicator that meets minimum contrast and is not hidden or obscured by hover or transform effects. A global focus indicator already exists; the audit MUST identify and fix the specific cases where it is clipped or obscured (e.g., containers that clip overflow or apply transforms on focus).
- **FR-006**: The mobile navigation MUST be dismissible and MUST NOT trap focus or obscure content when a keyboard user moves focus away from it.
- **FR-007**: Text and meaningful non-text elements (including disabled controls, status/risk/category indicators, and gold-on-light text) MUST meet WCAG 2.2 AA contrast minimums; any indicator currently relying on opacity alone MUST be verified or adjusted to meet the minimum.
- **FR-008**: Heading structure and landmarks on each page MUST form a correct, logical outline (no skipped or mis-nested levels) so assistive technology can navigate by structure.

**Responsive & touch (P2)**

- **FR-009**: Every interactive control (buttons, links acting as controls, inputs, selects, language toggle, menu icon, dismiss controls) MUST be at least 44x44 CSS pixels in tappable area on touch devices. Known shortfalls to close include the language toggle (currently 40px), desktop/compact nav links, and the notification dismiss/reference controls.
- **FR-010**: All pages MUST render without horizontal scrolling or clipped content from 320px wide up to and including 1440px.
- **FR-011**: Multi-column forms and grids MUST reflow or resize across small, medium (tablet), and large breakpoints without cramping or overlap.
- **FR-012**: Opening a navigation link from the mobile menu MUST close the menu and navigate.

**Consistency (P3)**

- **FR-013**: Empty states across all data-driven screens MUST use one shared, recognizable pattern.
- **FR-014**: Loading states across all data-driven screens MUST use one consistent indication.
- **FR-015**: Error states across all data-driven screens MUST use one consistent, recoverable presentation, including a consistent assistive-technology role so the error is announced (e.g., the account-screen error currently lacks the alert role that other screens have).
- **FR-016**: Spacing, font sizes, and corner radii across screens MUST come from one shared, limited design scale rather than divergent ad-hoc values.
- **FR-017**: Status, risk, and category indicators MUST be visually and semantically consistent wherever they appear.

**Forms UX (P3)**

- **FR-018**: Required fields MUST be visibly indicated before submission.
- **FR-019**: Validation feedback MUST be field-specific and appear before submit: format/pattern errors surface when the user leaves the field (on blur), and required-field errors surface on blur or submit — not only as a form-level message after submit (the login form's current behavior).
- **FR-020**: While a request is pending, forms MUST visibly show the pending state and prevent both duplicate submission and changes to the submitted values. Today only the submit button is disabled; locking the field values during submit is net-new work, not just polish.

**Scope guardrails**

- **FR-021**: Changes MUST be limited to the presentation layer (layout, styling, markup semantics, and client-side interaction); no business logic, data model, pricing, or backend behavior may change.
- **FR-022**: The existing "institutional trust + Ukrainian identity" design language, brand colors, and typography MUST be preserved; improvements refine and systematize it rather than replacing it.
- **FR-023**: All existing bilingual (Ukrainian default / English) content and language switching MUST continue to work unchanged, with no layout breakage when text length shifts between languages.

### Key Entities

Not applicable — this feature changes presentation and interaction only and introduces no new data entities.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Automated accessibility scans of the primary journeys (landing, search, results, login, register, account, history, articles) report zero automatically-detectable WCAG 2.2 A/AA violations, AND a manual keyboard + screen-reader audit of those journeys finds no A/AA blocker (automated scanners catch only a portion of issues, so the manual pass is the authoritative bar).
- **SC-002**: Each primary journey can be completed end-to-end using only a keyboard, with a visible focus indicator at every step.
- **SC-003**: 100% of interactive controls measure at least 44x44 CSS pixels of tappable area on a touch viewport.
- **SC-004**: No page produces horizontal scrolling or clipped content at any width from 320px to 1440px.
- **SC-005**: With reduced motion enabled, no non-essential animation plays on any page or interaction.
- **SC-006**: 100% of data-driven screens present empty, loading, and error states using the shared, consistent patterns.
- **SC-007**: Every form indicates required fields before submission, surfaces field-specific validation feedback, and prevents duplicate submission while pending.
- **SC-008**: All text and meaningful indicators meet WCAG 2.2 AA contrast minimums, verified by measurement rather than visual inspection.
- **SC-009**: The redesign's brand identity (colors, typography, signature elements) and all bilingual content remain intact, confirmed by before/after visual comparison and a full Ukrainian/English switch test.
- **SC-010**: In a moderated usability check with at least 5 representative participants, at least 90% complete the core "enter amount -> view investment options" task on a phone on the first attempt without tapping the wrong control.

## Assumptions

- The current "institutional trust + Ukrainian identity" design system (defined in `frontend/src/styles.css`) is the agreed visual direction and is to be preserved and systematized, not redesigned.
- WCAG 2.2 Level AA is the target accessibility conformance level (AAA target-size guidance is treated as a strong recommendation, applied where practical).
- The project-standard minimum touch-target size is 44x44 CSS pixels.
- The supported viewport range is 320px (small phone) to 1440px (desktop); no specific legacy-browser support beyond current evergreen browsers is required.
- The audit covers the existing Angular frontend screens only; no new pages or flows are added.
- Bilingual Ukrainian-default/English content and the existing translation mechanism remain the source of truth for all visible text.
- This is presentation-layer work; the backend, data model, and pricing/money handling are out of scope and unchanged.
