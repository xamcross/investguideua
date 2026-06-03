# Feature Specification: Search Page UI Fixes

**Feature Branch**: `005-search-ui-fixes`  
**Created**: 2026-06-04  
**Status**: Draft  
**Input**: User description: "checkout this screenshot. logged in user should not see the \"Sign in\" and \"Register\" buttons. Instead there should be a relevant menu. also the form input fields are crammed together to the left of the component, they should be spread out by width. Also, when switching to Ukrainian, the currency should read \"грн\" not \"UAH\""

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Authenticated user sees an account menu, not guest sign-in CTAs (Priority: P1)

A signed-in user lands on any page (including the search page). Instead of "Sign in" and "Register" calls-to-action — which only make sense for visitors who have no account — they see navigation relevant to a logged-in user: their primary destinations and a way to sign out. This must hold even when the session is restored silently on page load (the user did not just re-enter credentials): the navigation must settle on the authenticated menu and must not present guest CTAs as a logged-in user's experience at any point.

**Why this priority**: A logged-in user being shown "Sign in" / "Register" is confusing and erodes trust in the product; it can make the app look broken or insecure ("am I actually logged in?"). The search page is gated to authenticated users, so showing guest CTAs there is a visible correctness defect on the core flow. (The menu *content* for each audience already exists; the defect is that an authenticated session can still surface the guest CTAs — i.e. the navigation does not reliably reflect the restored authenticated state.)

**Independent Test**: Sign in, open the search page, and confirm the top navigation shows account-oriented options (search, history, providers, token balance, account, sign out) and does NOT show "Sign in" or "Register". Sign out and confirm the guest CTAs return.

**Acceptance Scenarios**:

1. **Given** a user is signed in, **When** they view any page's top navigation, **Then** "Sign in" and "Register" are not shown and an account-relevant menu is shown instead.
2. **Given** a user is signed in and viewing the search page, **When** the page renders, **Then** the navigation reflects their authenticated state (including current token balance and a sign-out action).
3. **Given** a signed-in user signs out, **When** the navigation re-renders, **Then** the guest "Sign in" and "Register" options reappear.
4. **Given** a guest (not signed in), **When** they view the top navigation, **Then** "Sign in" and "Register" remain available.
5. **Given** a user with a valid session who reloads the page (session restored silently), **When** the navigation has finished resolving the session, **Then** it shows the authenticated menu and does not present "Sign in"/"Register" as the user's logged-in experience.

---

### User Story 2 - Currency label is localized to Ukrainian (Priority: P2)

A user reading the app in Ukrainian sees the Ukrainian hryvnia presented with its Ukrainian short form "грн" everywhere a currency is shown to them — including the currency selector on the search form — rather than the Latin code "UAH".

**Why this priority**: The product's positioning is "in Ukraine, for Ukrainians." Showing "UAH" to a Ukrainian-language reader is an obvious localization gap on the most prominent input of the primary flow. It is lower than P1 only because it is cosmetic rather than a trust/correctness defect.

**Independent Test**: Switch the interface language to Ukrainian and confirm the currency selector and any displayed currency read "грн" for the hryvnia. Switch to English and confirm it reads "UAH".

**Acceptance Scenarios**:

1. **Given** the interface language is Ukrainian, **When** the user views the currency selector on the search form, **Then** the hryvnia option is labeled "грн".
2. **Given** the interface language is English, **When** the user views the currency selector, **Then** the hryvnia option is labeled "UAH".
3. **Given** the user switches the language at runtime (without reloading), **When** the language changes, **Then** the currency label updates to match the newly selected language immediately.
4. **Given** any currency is shown elsewhere on the page (e.g. in the results heading), **When** the language is Ukrainian, **Then** the hryvnia is shown as "грн" consistently with the selector.
5. **Given** either interface language, **When** the user views the US dollar option/label, **Then** it reads "USD" in both languages (only the hryvnia label is language-dependent; localization must not over-apply to USD).

---

### User Story 3 - Search form fields use the full component width (Priority: P3)

A user filling in the search form sees the input fields laid out to make sensible use of the card's width, rather than crammed into a narrow column on the left with a large empty area on the right.

**Why this priority**: This is a visual-polish/layout issue. The form is fully functional today; the imbalance just looks unfinished. It is the lowest priority of the three but contributes to the overall sense of a credible, well-built product.

**Independent Test**: Open the search form on a typical desktop window and confirm the fields span the available width of the surrounding card in a balanced layout, with no large unused gap to the right of the inputs.

**Acceptance Scenarios**:

1. **Given** the search form is displayed on a desktop-width screen, **When** the user views it, **Then** the input fields make use of the full width of the form card rather than occupying only the left portion.
2. **Given** the existing paired fields (amount/currency, horizon/risk), **When** displayed at desktop width, **Then** they remain grouped sensibly and the row fills the available width (the current paired two-column grouping is acceptable and expected, but the hard requirement is full-width use, not a specific column count).
3. **Given** a narrow (mobile) screen, **When** the form is displayed, **Then** fields stack/reflow legibly and remain usable (no horizontal overflow).
4. **Given** the layout change, **When** the form is used, **Then** all existing fields, labels, validation messages, and the submit action remain present and functional.

---

### Edge Cases

- A user whose session is restored silently on page load (no explicit re-login): the navigation must reflect the authenticated state once the session is confirmed, and must not settle on guest CTAs as the final state for a logged-in user.
- A user whose session expires or is signed out in another tab: navigation should fall back to the guest CTAs on the next render rather than continuing to show an account menu that no longer applies.
- Language switching mid-session must update currency labels live without requiring a page reload.
- Very long token-balance values or account labels must not break the navigation layout.
- The currency localization applies only to the displayed *label*; it must never alter the underlying currency value, amount, or any money calculation.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The top navigation MUST present authenticated users with account-relevant options (at minimum: access to search, history, providers, the user's current token balance, account settings, and a sign-out action) and MUST NOT present "Sign in" or "Register" to them.
- **FR-002**: The top navigation MUST present unauthenticated (guest) users with "Sign in" and "Register" entry points.
- **FR-003**: The navigation MUST reflect the user's current authentication state on every page, including the search page, and MUST update when the user signs in or signs out. This includes sessions restored silently on page load: once the session resolves to authenticated, the navigation MUST present the account menu and MUST NOT leave the guest CTAs as the authenticated user's state.
- **FR-004**: When the interface language is Ukrainian, the system MUST display the hryvnia currency with the label "грн" wherever a currency label is shown to the user, including the currency selector on the search form.
- **FR-005**: When the interface language is English, the system MUST display the hryvnia currency with the label "UAH".
- **FR-006**: Currency labels MUST update immediately when the user switches the interface language at runtime, without requiring a page reload.
- **FR-007**: Localizing the currency label MUST NOT change any underlying currency code, amount, or monetary calculation — it affects display text only.
- **FR-008**: The search form's input fields MUST make balanced use of the full width of their containing card on desktop-width screens, eliminating the large unused area to the right of the fields.
- **FR-009**: On narrow/mobile screens, the search form MUST reflow legibly without horizontal overflow, keeping all fields usable.
- **FR-010**: All existing search form fields, labels, validation messages, and the submit action MUST remain present and functional after the layout change.
- **FR-011**: The currency label localization MUST be applied consistently across the page (the selector and any other place a currency is displayed, e.g. the results heading).

### Key Entities

- **Currency label**: The human-readable display form of a currency, distinct from the stored currency code. For the hryvnia: code is "UAH"; Ukrainian label is "грн"; English label is "UAH". The label is presentation-only and carries no monetary semantics.
- **Navigation state**: The set of navigation options shown to a user, determined by whether the user is authenticated. Guest state exposes sign-in/register; authenticated state exposes account-relevant destinations and sign-out.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of page views by an authenticated user show account-relevant navigation and zero instances of "Sign in" or "Register" CTAs.
- **SC-002**: 100% of currency labels shown to a user reading in Ukrainian display "грн" for the hryvnia (and "UAH" when reading in English), including on the search form's currency selector.
- **SC-003**: Switching language updates all visible currency labels within the same interaction, with no page reload required.
- **SC-004**: On desktop-width screens, the search form fields occupy the full width of their card with no unused horizontal gap wider than normal field spacing.
- **SC-005**: No regression: every search form field, validation message, and the submit flow continue to work exactly as before across the supported screen sizes, and no monetary value or calculation changes as a result of these UI fixes.

## Assumptions

- The application already supports the two interface languages (Ukrainian and English) with a runtime language toggle; this feature reuses that mechanism rather than introducing new localization infrastructure.
- The "relevant menu" for authenticated users is the existing set of account-oriented destinations (search, history, providers, token balance, account, sign out); no new destinations or account-management capabilities are being added — only the correct menu is shown to the correct audience.
- The search page is reachable only by authenticated users; the reported sighting of guest CTAs on that page is the defect this feature corrects (whether via correct auth-state reflection, correct menu content, or both).
- Supported currencies remain UAH and USD (per the MVP scope); USD's label is "USD" in both languages, so only the hryvnia label is language-dependent.
- These are presentation/layout changes only: no API contracts, money handling, or backend behavior change.
- Mobile/responsive behavior of the navigation (existing collapsible menu) is retained; this feature does not redesign the mobile menu, only ensures the correct content per auth state.
