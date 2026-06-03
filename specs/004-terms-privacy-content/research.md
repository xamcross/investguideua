# Phase 0 Research: Terms & Conditions and Privacy Statement Content

**Feature**: 004-terms-privacy-content | **Date**: 2026-06-03

The spec introduced no `NEEDS CLARIFICATION` markers (three defaults were resolved in the spec's
Assumptions). The Technical Context likewise has no unknowns - the stack, i18n mechanism, and
routing are all already established in the codebase. Research below records the few design decisions
that shape implementation.

## Decision 1: How to store the legal text

**Decision**: Store all legal copy as **structured i18n content** under a new `legal.*` namespace in
the existing dictionaries (`public/i18n/uk.json`, `en.json`). Each document is an object with
`title`, `effectiveDate`, an optional `intro`, and an ordered `sections` array; each section has a
`heading` and a `body` array of paragraph strings (plus an optional `bullets` array). The component
reads the whole `legal.terms` / `legal.privacy` object via `TranslateService` and re-reads it on
language change.

**Rationale**:
- The codebase mandates that *all* visible copy is a translation key (no hardcoded UI strings); the
  language switch is live with no rebuild. Putting legal text in the i18n JSON keeps both screens
  consistent with this convention and gives Ukrainian/English parity for free in the same files.
- Legal documents are long and multi-paragraph. A structured object (sections -> paragraphs/bullets)
  read as a whole avoids exploding the file into hundreds of individually-piped flat keys and keeps
  translators editing coherent blocks. ngx-translate's service API can return a nested object, so
  the structure is read once and iterated in the template.
- No new asset pipeline (e.g. per-language Markdown/HTML files) is introduced, honoring MVP
  discipline (Principle I) and the fixed stack (Principle II).
- **Proven in-repo**: `frontend/src/app/core/i18n/plural.pipe.ts` already reads a *nested object*
  from the dictionary via `TranslateService.instant('units.<noun>') as Record<string,string>`
  (against `units.token.{one,few,many}` in `uk.json`). This confirms ngx-translate 15.0 returns
  nested structures (including arrays) as-is - the central assumption is validated, not just
  plausible.

**Alternatives considered**:
- *Hundreds of flat translation keys with the `translate` pipe per paragraph*: technically works but
  is unwieldy to author/maintain and bloats both dictionaries with sequential keys; rejected.
- *Per-language Markdown/HTML files loaded at runtime and rendered via innerHTML*: introduces a new
  content-loading path and an HTML-sanitization/XSS surface for no benefit on static first-party
  copy; rejected (extra moving part, Principle I).
- *Hardcoded text in the component template*: violates the no-hardcoded-strings convention and makes
  bilingual parity impossible; rejected.

## Decision 2: One shared component vs. two components

**Decision**: A **single** standalone, OnPush `LegalDocumentComponent` (selector `ig-legal`) used by
both routes. The route picks the document via `data: { doc: 'terms' }` / `{ doc: 'privacy' }`, bound
to an `@Input() doc: 'terms' | 'privacy'` through the already-enabled
`withComponentInputBinding()`.

**Rationale**:
- The two screens are structurally identical (title, effective date, sections, cross-link, back
  home); only the content namespace differs. One component is the minimal footprint (Principle I)
  and matches how the existing `PlaceholderComponent` already takes a routed `@Input`.
- Component-input binding is already configured app-wide (used by the Verify page for `?token=`), so
  no new wiring is needed.

**Alternatives considered**:
- *Two near-duplicate components (`TermsComponent`, `PrivacyComponent`)*: duplicates template and
  styles for no functional gain; rejected.
- *A thin shared presentational component plus two wrapper route components*: more files than the
  feature warrants; rejected in favor of the single data-driven component.

## Decision 3: Live language switching

**Decision**: The component reads the document object reactively - initial read via
`TranslateService.get('legal.' + doc)` (Observable, fires once the dictionary has loaded) and re-read
on `TranslateService.onLangChange` - so switching language while viewing a screen swaps the content
in place without a blank/broken state (spec edge case + FR-008). It MUST NOT rely on `instant()` for
first paint: these routes are lazy-loaded and directly URL-addressable, so a cold deep-link can run
the component before the active dictionary has loaded, at which point `instant()` would return the
raw key. The component also defends against a non-object/missing resolution (renders a safe fallback,
never a raw key - VR-5).

**Rationale**: Matches the app's existing live-language behavior (titles re-localize on lang change
via `TranslatedTitleStrategy`); avoids a stale or empty render after a switch and avoids the
cold-load raw-key pitfall that a one-shot `instant()` would hit on a deep link.

**Alternatives considered**: *Read once at construction with `instant()`* - would leave stale content
after a language switch and risk a raw-key render on a cold deep-link; rejected.

## Decision 4: Page title localization

**Decision**: Add dedicated `title.terms` and `title.privacy` keys and point the route `title` at
them (currently the routes reuse `footer.terms` / `footer.privacy`, which are short nav labels).
This gives the browser tab a proper "<Document> - InvestGuideUA" title consistent with every other
route's `title.*` pattern.

**Rationale**: Consistency with the established `title.*` convention and clearer browser/SEO titles
for two now-substantive public pages.

**Alternatives considered**: *Keep reusing `footer.terms`/`footer.privacy`* - functional but
inconsistent with the title pattern and yields a bare one-word tab title; minor improvement taken.

## Decision 5: Content sourcing and legal accuracy

**Decision**: Author a good-faith, domain-appropriate draft grounded in how the platform actually
operates, derived from `SPECIFICATION.md` and the constitution (information-only discovery;
token-metered, catalog-grounded AI recommendations; monobank payments; email+auth data only;
account/data deletion on request; FOP operator). Required topic coverage is enumerated in the spec
(FR-002, FR-004) and mapped in `data-model.md`. The text explicitly carries the
information-not-individualized-advice disclaimer (Principle IV consistency). Concrete operator legal
entity / contact specifics are included as clearly-marked placeholders where real values are not yet
available, pending qualified legal review before public launch (spec Assumptions).

**Rationale**: Keeps the content truthful to the system (FR-011) and consistent with the
platform-wide disclaimer and privacy posture, while not overstating that the draft is finalized
legal advice.

**Alternatives considered**: *Generic boilerplate terms/privacy templates* - risk of describing
capabilities the service does not have (e.g. moving user funds) and missing service-specific points
(tokens, AI catalog grounding); rejected in favor of platform-grounded copy.

## Open items

None. All decisions resolved; ready for Phase 1 design (already captured in `data-model.md`,
`contracts/`, and `quickstart.md`).
