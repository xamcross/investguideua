# Feature Specification: Terms & Conditions and Privacy Statement Content

**Feature Branch**: `004-terms-privacy-content`  
**Created**: 2026-06-03  
**Status**: Draft  
**Input**: User description: "add appropriate terms and conditions text to the corresponding screen as well as privacy statement to the corresponding screen"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read the Terms & Conditions (Priority: P1)

A visitor or registered user opens the Terms & Conditions screen (linked from the site footer) and reads the rules that govern their use of InvestGuideUA: what the service does and does not do, the token-based usage model, the explicit "this is information, not individualised financial advice" disclaimer, acceptable-use rules, and the limits of the operator's liability.

**Why this priority**: The screen is currently a "coming soon" placeholder. Operating a paid, monetised service that surfaces investment-related information without published terms exposes the operator to legal and trust risk and leaves users unable to understand the rules they are bound by. Publishing real terms is the core ask and a prerequisite for launch.

**Independent Test**: Navigate to the Terms screen via the footer link; verify it shows complete, readable Terms & Conditions content (not the placeholder) covering the required topics, with a visible effective date. Delivers value on its own even if the Privacy screen is still pending.

**Acceptance Scenarios**:

1. **Given** a visitor on any page, **When** they click the "Terms" link in the footer, **Then** the Terms & Conditions screen opens showing complete legal content instead of a "coming soon" placeholder.
2. **Given** the Terms & Conditions screen is open, **When** the user reads it, **Then** it covers, at minimum: a description of the service, the token/payment usage model, the no-individualised-financial-advice disclaimer, acceptable use, intellectual-property and liability limits, how terms may change, and operator/contact identification.
3. **Given** the Terms & Conditions screen is open, **When** the user looks for currency, **Then** an effective/last-updated date is clearly shown.

---

### User Story 2 - Read the Privacy Statement (Priority: P1)

A visitor or registered user opens the Privacy Statement screen (linked from the site footer) and reads what personal data InvestGuideUA collects (e.g. email and account data, search history, payment-related data handled via the payment provider), why it is collected, how long it is kept, with whom it is shared, and what rights the user has over their data.

**Why this priority**: The service collects personal data at registration, stores search history, and processes payments. A published privacy statement is both a legal expectation and a trust requirement. The screen is currently a placeholder, so this is core to the request and to launch readiness.

**Independent Test**: Navigate to the Privacy screen via the footer link; verify it shows complete, readable Privacy Statement content (not the placeholder) covering the categories of data, purposes, retention, sharing, and user rights, with a visible effective date. Delivers value on its own even if the Terms screen is still pending.

**Acceptance Scenarios**:

1. **Given** a visitor on any page, **When** they click the "Privacy" link in the footer, **Then** the Privacy Statement screen opens showing complete content instead of a "coming soon" placeholder.
2. **Given** the Privacy Statement screen is open, **When** the user reads it, **Then** it covers, at minimum: what personal data is collected, the purposes of processing, retention periods, third parties data is shared with (including the payment provider and the AI processing used for recommendations), the user's rights over their data, and a contact point for privacy requests.
3. **Given** the Privacy Statement screen is open, **When** the user looks for currency, **Then** an effective/last-updated date is clearly shown.

---

### User Story 3 - Read legal content in the chosen language (Priority: P2)

A user who has selected Ukrainian or English sees the Terms & Conditions and Privacy Statement in that same language, consistent with the rest of the interface.

**Why this priority**: InvestGuideUA is a Ukrainian-first product that also supports English, and all interface text is localised. Legal content that appeared only in one language would be inconsistent and reduce comprehension for part of the audience. It builds on Stories 1 and 2 rather than standing alone, so it is P2.

**Independent Test**: With the language set to Ukrainian, open both screens and confirm the content is in Ukrainian; switch to English and confirm the content is in English, with equivalent meaning and coverage in both.

**Acceptance Scenarios**:

1. **Given** the interface language is Ukrainian, **When** the user opens the Terms or Privacy screen, **Then** the content is presented in Ukrainian.
2. **Given** the interface language is English, **When** the user opens the Terms or Privacy screen, **Then** the content is presented in English with the same topics covered as the Ukrainian version.
3. **Given** the user switches language while viewing either screen, **When** the new language takes effect, **Then** the content updates to the selected language without losing the user's place in a broken or empty state.

---

### Edge Cases

- **Direct deep-link**: A user who opens the Terms or Privacy URL directly (not via the footer) still sees the full content, since both screens are public and require no sign-in.
- **Language with missing translation**: If content for the active language were unavailable, the screen must still render usable legal content (falling back to the default language) rather than showing blank or key-like placeholder text.
- **Long content readability**: The content is substantially longer than the previous placeholder; it must remain readable and navigable on small/mobile viewports without horizontal scrolling or clipped text.
- **Stale date perception**: The displayed effective date must reflect the content actually shown so users are not misled about how current the terms are.
- **Cross-references**: Where the Terms reference privacy handling (or vice versa), the user can reach the other screen.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Terms & Conditions screen MUST present complete, human-readable Terms & Conditions content in place of the current "coming soon" placeholder.
- **FR-002**: The Terms & Conditions content MUST address, at minimum: identification of the service operator; a description of what the service provides; the token-based usage and payment model; an explicit disclaimer that recommendations are general information and not individualised financial, investment, tax, or legal advice; acceptable-use / prohibited-use rules; intellectual-property ownership of the service content; limitation of liability and disclaimers of warranty; how and when the terms may be updated; and a contact point.
- **FR-003**: The Privacy Statement screen MUST present complete, human-readable privacy content in place of the current "coming soon" placeholder.
- **FR-004**: The Privacy Statement content MUST address, at minimum: the categories of personal data collected (including account/email data, usage and search history, and payment-related data); the purposes for which data is processed; the legal basis or justification for processing; data retention periods or criteria; categories of third parties data is shared with (including the payment provider used for purchases and the AI service used to generate recommendations); international transfer considerations where relevant; the user's rights regarding their data and how to exercise them; and a privacy contact point.
- **FR-005**: Both screens MUST display a clearly visible effective/last-updated date that corresponds to the content shown.
- **FR-006**: Both screens MUST remain publicly accessible without requiring the user to sign in.
- **FR-007**: Both screens MUST be reachable from the existing footer "Terms" and "Privacy" links.
- **FR-008**: The content of both screens MUST be available in both supported interface languages (Ukrainian and English) and MUST follow the user's currently selected interface language, with equivalent topic coverage in each language.
- **FR-009**: The content MUST be presented in a structured, readable format (e.g. headings/sections) so users can scan and locate specific topics.
- **FR-010**: Both screens MUST render legibly and without content clipping or horizontal overflow on common mobile and desktop viewport sizes.
- **FR-011**: The content MUST be accurate to how InvestGuideUA actually operates (information/discovery only, no movement of user funds, token-metered searches, AI-assisted recommendations grounded in a vetted catalogue), and MUST NOT promise capabilities the service does not provide.
- **FR-012**: Where one document references the subject matter of the other (terms referencing data handling, or privacy referencing the terms of use), the user MUST be able to navigate to the related screen.

### Key Entities

- **Terms & Conditions document**: The body of legal text governing use of the service, organised into titled sections, associated with an effective date, and available per supported language.
- **Privacy Statement document**: The body of legal text describing personal-data handling, organised into titled sections, associated with an effective date, and available per supported language.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of visits to the Terms screen and the Privacy screen display complete legal content; the "coming soon" placeholder appears on neither screen.
- **SC-002**: Both screens are fully available in both supported languages, with 100% of required topic areas (per FR-002 and FR-004) covered in each language.
- **SC-003**: A user can locate any required topic on either screen (e.g. "how long is my data kept", "is this financial advice") within 30 seconds of opening it, aided by clear section headings.
- **SC-004**: Both screens render without clipped text or horizontal scrolling across viewport widths from 320px (small mobile) to large desktop.
- **SC-005**: Each screen shows an effective/last-updated date that matches the published content, verifiable on inspection.
- **SC-006**: Both screens load and are readable for unauthenticated visitors, confirmed by accessing them while signed out and via direct URL.

## Assumptions

- **Scope is content for the two existing screens.** This feature replaces the placeholder content on the existing `/terms` and `/privacy` screens. Adding a registration-time acceptance checkbox, recording per-user consent, or versioned consent tracking is considered out of scope for this feature and may be handled separately.
- **Bilingual parity is required.** Because the product is Ukrainian-first with English support and all UI text is localised, both documents are authored in Ukrainian and English. Ukrainian is treated as the canonical default.
- **Good-faith draft pending legal review.** The authored text is a reasonable, domain-appropriate draft based on how the service operates; it is assumed a qualified legal reviewer will validate and finalise wording before public launch. The specification does not assert the text constitutes legal advice or guarantees regulatory compliance.
- **Operator and contact details.** A contact point (e.g. a support/privacy email) is assumed to be available for inclusion; concrete operator legal-entity details are assumed to be provided or stubbed with a clearly marked placeholder pending finalisation.
- **No new data collection introduced.** The Privacy Statement describes data the service already handles (account/email, search history, payment processing via the existing provider, AI processing for recommendations); this feature does not add new data collection or processing.
- **Reuses existing presentation conventions.** The screens follow the application's existing layout, navigation (footer links), localisation mechanism, and public-route accessibility; no new access model is introduced.
