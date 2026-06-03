---
description: "Task list for Terms & Conditions and Privacy Statement content"
---

# Tasks: Terms & Conditions and Privacy Statement Content

**Input**: Design documents from `/specs/004-terms-privacy-content/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/legal-content.contract.md, quickstart.md

**Tests**: NOT requested in the spec (no TDD / automated-test ask). No test tasks are generated.
Verification is via JSON parse + `ng build` + manual quickstart checks + the mandatory two-role
review (Constitution Principles V & VI). This is a frontend-only feature.

**Organization**: Tasks are grouped by user story. US1 (Terms) and US2 (Privacy) are each an
independently testable, deployable increment once the shared foundation is in place.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3 (maps to spec.md user stories)

## Path Conventions

Web app; frontend only. Real paths:
- Component: `frontend/src/app/features/legal/legal-document.component.ts`
- Routes: `frontend/src/app/app.routes.ts`
- i18n (canonical): `frontend/public/i18n/uk.json`
- i18n (English): `frontend/public/i18n/en.json`

> **Shared-file note**: `uk.json` and `en.json` are each edited by Foundational, US1, and US2 (each
> adds a *different* key block). Tasks that touch the **same** JSON file MUST be serialized (not
> parallel) to avoid edit conflicts; `uk.json` vs `en.json` are different files and CAN be done in
> parallel within a phase.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the feature location.

- [X] T001 Create the feature folder `frontend/src/app/features/legal/` (created implicitly by T002; ensure no stray/duplicate folder).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared renderer, route wiring, and shared label keys that BOTH US1 and US2 depend
on. Until this is done, neither legal screen can render content.

**⚠️ CRITICAL**: No user story work can complete until this phase is done.

- [X] T002 [P] Implement the shared `LegalDocumentComponent` (selector `ig-legal`) in `frontend/src/app/features/legal/legal-document.component.ts`: standalone, `ChangeDetectionStrategy.OnPush`, imports `RouterLink` + `TranslateModule`, injects `TranslateService`. Declare `@Input() doc: 'terms' | 'privacy'`. Read the document object via `TranslateService.get('legal.' + doc)` for first paint (NOT `instant()`) and re-read on `onLangChange`. Runtime defense (VR-5): if the resolved value is not an object with a non-empty `sections` array, render a safe fallback (loading/empty), never a raw key. Template renders: single `<h1>` = `title`; the `effectiveLabel` + `effectiveDate`; optional `intro`; then each `sections[]` item as a sequential `<h2>` heading + one `<p>` per `body[]` entry + optional `<ul>` from `bullets[]`; a cross-link to the sibling doc (`/privacy` from terms, `/terms` from privacy) and a back-to-home link, both as focusable `<a routerLink>`. (Ref: contracts/legal-content.contract.md §2; research.md Decisions 2-3.)
- [X] T003 Add inline component styles in `frontend/src/app/features/legal/legal-document.component.ts` (same file, depends on T002): readable max-width text column, `overflow-wrap: break-word` so long strings/URLs do not cause horizontal scroll at 320px (SC-004), reuse existing `ig-*`/`ig-btn` conventions, no skipped heading levels. (Ref: contracts §2, check 9-10.)
- [X] T004 Wire routes in `frontend/src/app/app.routes.ts` (depends on T002): point `/terms` and `/privacy` at `LegalDocumentComponent` via `loadComponent`, add `data: { doc: 'terms' }` / `{ doc: 'privacy' }`, and change `title` to `title.terms` / `title.privacy`. Keep both routes public (no guard). Remove the now-stale "reuse the placeholder" comment for these two routes. (Ref: contracts §1.)
- [X] T005 [P] Add shared label keys to `frontend/public/i18n/uk.json` (canonical): `title.terms`, `title.privacy`, and a `legal` block with `effectiveLabel`, `seeAlsoTerms`, `seeAlsoPrivacy` (reuse `common.backToHome` for back-home). Do NOT add document bodies yet. (Ref: contracts §3.)
- [X] T006 [P] Add the same shared label keys to `frontend/public/i18n/en.json` (English parity for T005 keys). (Ref: contracts §3, FR-008.)

**Checkpoint**: Visiting `/terms` and `/privacy` resolves the new component (showing the fallback/
empty state until content is authored) with a proper localized browser title; footer links reach
them. Foundation ready - US1 and US2 can begin.

---

## Phase 3: User Story 1 - Read the Terms & Conditions (Priority: P1) 🎯 MVP

**Goal**: The `/terms` screen shows complete, bilingual Terms & Conditions content (not the
placeholder), with an effective date.

**Independent Test**: Open `/terms` via the footer link (signed out and via direct URL); confirm full
content covering all FR-002 topics in the active language, a visible effective date, and a working
cross-link to `/privacy` and back-to-home. Toggle language and confirm the Terms content appears in
both Ukrainian and English.

### Implementation for User Story 1

- [X] T007 [P] [US1] Author the `legal.terms` content object in `frontend/public/i18n/uk.json` (canonical Ukrainian): `title`, `effectiveDate`, optional `intro`, and a `sections[]` array covering ALL 10 required Terms topics from data-model.md (operator/contact, what the service is - information only never moves funds, NOT individualized financial advice disclaimer, accounts, tokens & payments via monobank, acceptable use, intellectual property, disclaimers & liability, changes to terms, contact). Content must be accurate to how the platform operates (FR-011) and carry the information-not-advice disclaimer (Constitution IV). Use clearly-marked placeholders for unknown operator/contact specifics. (Serialize after T005 - same file.)
- [X] T008 [P] [US1] Author the English `legal.terms` content in `frontend/public/i18n/en.json` with identical key/section structure and equivalent topic coverage to T007 (FR-008, SC-002). (Serialize after T006 - same file.)
- [X] T009 [US1] Manually verify the Terms screen per quickstart.md (depends on T007, T008, and Phase 2): full content renders in both languages, effective date shown, no raw `legal.` keys, cross-link + back-home work, no horizontal overflow at 320px. (SC-001, SC-003, SC-005, SC-006, FR-002, FR-005, FR-012.)

**Checkpoint**: User Story 1 (Terms) is fully functional and independently testable. MVP-shippable.

---

## Phase 4: User Story 2 - Read the Privacy Statement (Priority: P1)

**Goal**: The `/privacy` screen shows complete, bilingual Privacy Statement content (not the
placeholder), with an effective date.

**Independent Test**: Open `/privacy` via the footer link (signed out and via direct URL); confirm
full content covering all FR-004 topics in the active language, a visible effective date, and working
cross-link to `/terms` and back-to-home. Toggle language and confirm content in both languages.

### Implementation for User Story 2

- [X] T010 [P] [US2] Author the `legal.privacy` content object in `frontend/public/i18n/uk.json` (canonical Ukrainian): `title`, `effectiveDate`, optional `intro`, and a `sections[]` array covering ALL 10 required Privacy topics from data-model.md, including the review-added ones: a **Cookies & local storage** section (HttpOnly refresh-token cookie + `ig.lang` localStorage), **named third parties** (monobank; Anthropic Claude AI; the email-delivery processor; hosting), **international transfers** as non-optional (search text to the US AI provider), and a **retention** section that accurately says data is kept until account/data deletion on request with NO fixed TTL on search history (FR-011 / VR-8). Use clearly-marked placeholders for unknown controller/contact specifics. (Serialize after T007 - same file `uk.json`.)
- [X] T011 [P] [US2] Author the English `legal.privacy` content in `frontend/public/i18n/en.json` with identical key/section structure and equivalent topic coverage to T010 (FR-008, SC-002). (Serialize after T008 - same file `en.json`.)
- [X] T012 [US2] Manually verify the Privacy screen per quickstart.md (depends on T010, T011): full content in both languages, effective date, named third parties + cookies/localStorage + accurate retention present, no raw keys, cross-link + back-home, no overflow at 320px. (SC-001, SC-005, SC-006, FR-004, FR-005, FR-012.)

**Checkpoint**: User Stories 1 AND 2 both work independently. Both legal screens fully populated.

---

## Phase 5: User Story 3 - Read legal content in the chosen language (Priority: P2)

**Goal**: Both documents follow the active interface language and switch live, with full uk/en
parity.

**Independent Test**: With language = Ukrainian, open both screens and confirm Ukrainian content;
switch to English and confirm English content with the same topics; switch back while viewing and
confirm the content swaps in place with no blank/broken state.

### Implementation for User Story 3

- [X] T013 [US3] Manually verify live language switching on both `/terms` and `/privacy` (depends on US1 + US2): toggling language re-renders content in place (driven by the `onLangChange` read from T002) with no blank screen, no loss of place into a broken state, and no raw-key render. (FR-008, edge case.)
- [X] T014 [P] [US3] Verify uk/en parity in `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json`: `legal.terms` and `legal.privacy` have identical key structure and matching section count/order across both files, every required topic present in both languages, and `legal.terms.effectiveDate` == `legal.privacy.effectiveDate` representing the same calendar date in both files (VR-1, VR-7, SC-002).

**Checkpoint**: All three user stories independently functional. Bilingual, live-switching legal
screens complete.

---

## Phase 6: Polish, Verification & Mandatory Review (Cross-Cutting)

**Purpose**: Required verification gates (Constitution Principles V & VI) and final polish.

- [X] T015 [P] Validate both dictionaries parse as valid JSON: `node -e "require('./public/i18n/uk.json'); require('./public/i18n/en.json'); console.log('JSON OK')"` from `frontend/` (VR-6).
- [X] T016 Build the frontend: `npm run build` (`ng build`) from `frontend/`. If no runtime is available in the environment, state explicitly that verification is static-only - do not imply it builds (Constitution Principle V).
- [X] T017 [P] Accessibility & responsiveness pass on both screens: exactly one `<h1>`, sequential `<h2>` (no skipped levels), focusable cross-link/back-home, and no horizontal scroll / clipped text from 320px to large desktop (SC-004, FR-009, FR-010).
- [X] T018 Confirm no Windows-executed script (`.ps1`/`.cmd`/`.bat`) was created or modified by this feature (it touches only `.ts` and `.json`); therefore the ASCII-only scan does not apply and `.ts`/`.json` correctly carry UTF-8 Ukrainian text (Constitution Principle V).
- [X] T019 MANDATORY two-role sub-agent review before "done" (Constitution Principle VI): (a) Frontend lead - Angular standalone/OnPush correctness, route + input binding, `get()`/`onLangChange` read, runtime defense, a11y, responsiveness, no hardcoded strings; (b) QA/Business Analyst - content completeness vs. FR-002/FR-004 required topics, uk/en parity, disclaimer presence + platform accuracy (FR-011), retention accuracy, effective date. The review MUST include the actual JSON parse (T015) and build (T016), not just reading. Apply or explicitly report findings.
- [X] T020 Run the full quickstart.md manual verification checklist end-to-end and confirm all items pass (or report failures plainly).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: After Setup. BLOCKS US1, US2, US3.
- **US1 (Phase 3)** and **US2 (Phase 4)**: After Foundational. Independently testable. They share the
  JSON files, so their `uk.json`/`en.json` edits are serialized (T007 before T010 on `uk.json`; T008
  before T011 on `en.json`), but each is a self-contained increment.
- **US3 (Phase 5)**: After US1 and US2 (it verifies switching/parity across both populated docs).
- **Polish (Phase 6)**: After all desired stories.

### Within Each User Story

- US1: T007/T008 (content, parallel across the two files) → T009 (verify).
- US2: T010/T011 (content, parallel across the two files) → T012 (verify).
- US3: T013 (live-switch verify) + T014 (parity check, parallel).

### Parallel Opportunities

- T002 is the critical path; T005/T006 (label keys, different files) run in parallel with it.
- Within a story, the `uk.json` and `en.json` content tasks are different files → parallel
  (T007‖T008; T010‖T011).
- T015 and T017 can run in parallel in Polish.
- Cross-story: US1 and US2 are independent in intent but serialize on the shared JSON files; if two
  developers split them, coordinate edits to `uk.json`/`en.json` (distinct key blocks) to avoid
  merge conflicts.

---

## Parallel Example: User Story 1

```text
# Author both language versions of the Terms content together (different files):
Task: "Author legal.terms content in frontend/public/i18n/uk.json"   (T007)
Task: "Author legal.terms content in frontend/public/i18n/en.json"   (T008)
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 Setup → Phase 2 Foundational (shared component + routes + label keys).
2. Phase 3 US1 (Terms content, uk + en).
3. **STOP and VALIDATE**: Terms screen works independently in both languages.
4. Deploy/demo if ready.

### Incremental Delivery

1. Setup + Foundational → foundation ready (both routes resolve the new component).
2. US1 (Terms) → verify → ship.
3. US2 (Privacy) → verify → ship.
4. US3 → verify live switching + parity.
5. Phase 6 verification gates + mandatory review → done.

---

## Notes

- [P] = different files, no dependency on an incomplete task.
- The biggest serialization point is the two shared i18n JSON files - keep distinct key blocks and
  sequence same-file edits.
- No automated tests were requested; verification is JSON parse + build + manual quickstart + the
  mandatory two-role review (T019), which is required, not optional.
- Commit after each task or logical group; stop at any checkpoint to validate a story independently.
