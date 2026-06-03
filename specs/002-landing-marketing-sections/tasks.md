---
description: "Task list for Landing marketing sections (footer, pricing preview, sample results)"
---

# Tasks: Landing marketing sections (footer, pricing preview, sample results)

**Input**: Design documents from `/specs/002-landing-marketing-sections/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: No automated test tasks. Per spec and plan, verification is the production build (parse +
`anyComponentStyle` budget), manual UK/EN toggle, accessibility spot-checks, and the mandatory
two-role sub-agent review. No TDD was requested.

**Organization**: Tasks are grouped by user story (US1 sample results P1 / MVP, US2 pricing preview
P2, US3 footer P2). This is a frontend-only, additive marketing feature that **depends on
`001-ui-redesign`** (it reuses that design system). Implement on top of 001 — see Setup.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3 (setup, foundational, polish carry no story label)
- Exact file paths included.

## Shared-file note

`frontend/src/app/features/landing/landing.component.ts` is edited by **both** US1 and US2 (same
file → sequential), and `frontend/public/i18n/{uk,en}.json` are edited by all three stories (same
files → sequential). Never run two same-file edits as `[P]`.

---

## Phase 1: Setup

**Purpose**: Put the implementation on the 001 design system and capture a green baseline.

- [ ] T001 Ensure the implementation branch is based on `001-ui-redesign` (merge it into / rebase `002-landing-marketing-sections` onto it) so the redesign design system is present, then run `npm run build` in `frontend/` and record a green baseline with no `anyComponentStyle` warning
- [ ] T002 Record the current i18n key sets of `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json` (confirm identical) to protect the parity invariant through this feature

**Checkpoint**: Building green on top of 001; i18n parity baseline captured.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Confirm the shared design-system primitives the marketing sections reuse exist. The
three stories are otherwise independent vertical slices.

- [ ] T003 Verify the 001 global primitives the marketing sections reuse are present in `frontend/src/styles.css`: instrument-card classes (`.ig-options`/`.ig-opt*`/`.ig-fact*`), motion (`.reveal`/`.d1..d5`, `ig-rise`), badges (`.ig-badge--cat`/`--risk[data-risk]`), `.ig-kicker`, the canonical tokens, and the dark-section navy tokens. If absent, the branch is not on top of 001 — stop and rebase (see T001)

**Checkpoint**: Shared primitives confirmed — user stories can proceed.

---

## Phase 3: User Story 1 - Sample-results ("Examples") section (Priority: P1) 🎯 MVP

**Goal**: A signed-out visitor sees a clearly-labelled "sample result" section on the landing page —
three illustrative instrument cards in the redesign card style — so they can judge the product
before signing up.

**Independent Test**: Open the landing page signed out; confirm a labelled "sample result" section
renders three `.ig-opt` cards with localized category/risk (risk color from `data-risk`), a return
figure, facts, rationale, and a source affordance, plus an "example / not advice" disclaimer; confirm
it resolves in UK and EN and consumes no token / needs no sign-in.

- [ ] T004 [US1] Add the sample-results i18n keys to BOTH `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json` (identical sets): `landing.sampleKicker`, `landing.sampleTitle`, `landing.sampleDisclaimer`, and `landing.sampleProvider1..3` / `landing.sampleInstrument1..3` / `landing.sampleRationale1..3` (per [contracts/i18n-keys.contract.md](contracts/i18n-keys.contract.md))
- [ ] T005 [US1] Add three hard-coded illustrative sample-option display constants and render the sample-results section in `frontend/src/app/features/landing/landing.component.ts` (after "how it works", before the existing disclaimer): mono `.ig-kicker` + serif `<h2>`, three global `.ig-opt` cards (provider/instrument, localized `category.`/`risk.` labels with `[attr.data-risk]` keeping the raw enum, serif return figure + mono `% / yr`, 2-col facts, one-line rationale, "official source" affordance), `.reveal` entrance, decorative SVGs `aria-hidden`, and the `landing.sampleDisclaimer`; NO advisor/API call, NO token cost; keep the landing `styles[]` lean (reuse globals)
- [ ] T006 [US1] US1 checkpoint: run `npm run build` (under budget); verify the sample section renders, UK↔EN resolves all new keys, no token is spent and no request is made; run the mandatory two-role review including the build

**Checkpoint**: The MVP marketing example is live and demoable on the landing page.

---

## Phase 4: User Story 2 - Transparent-pricing preview section (Priority: P2)

**Goal**: A signed-out visitor sees a "transparent pricing" section previewing the token packs with a
free-tokens note and Register CTAs; the middle pack is highlighted as best value.

**Independent Test**: Open the landing page signed out; confirm a dark pricing section shows the pack
options with per-token value and a free-tokens note, the middle pack is highlighted, CTAs route to
registration, both languages resolve, and figures are clearly marked as illustrative.

- [ ] T007 [US2] Add the pricing-preview i18n keys to BOTH `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json` (identical sets): `landing.pricingKicker`, `landing.pricingTitle`, `landing.pricingNote`, `landing.pricingCta`, `landing.pricingFine`, `landing.pricingExample`
- [ ] T008 [US2] Add the global `.ig-section--dark` navy marketing-section shell (and any shared pack-preview rules) to `frontend/src/styles.css`
- [ ] T009 [US2] Add three illustrative pricing-pack display constants and render the pricing-preview section in `frontend/src/app/features/landing/landing.component.ts` (wrapped in `.ig-section--dark`): gold `.ig-kicker` + serif `<h2>` + note, three packs (token count via `igPlural`, example price, per-token value, `landing.pricingExample` marker), the MIDDLE pack highlighted with a gold CTA and the others standard, every CTA `routerLink="/register"`, and the `landing.pricingFine` fineprint; illustrative figures only (no float math, no purchase, no API). Depends on T008; same file as T005 so runs after the US1 landing edit
- [ ] T010 [US2] US2 checkpoint: run `npm run build` (under budget); verify the dark pricing section renders, the middle pack is highlighted, CTAs route to `/register`, the example framing is present, and UK↔EN resolves; run the two-role review including the build

**Checkpoint**: Pricing transparency + registration funnel live on the landing page.

---

## Phase 5: User Story 3 - Site footer (Priority: P2)

**Goal**: Every visitor sees a consistent footer (brand + tagline, Product/Legal links, copyright,
"Made in Ukraine") reachable by keyboard, on a credible dark background.

**Independent Test**: Load any public page; confirm a footer renders with brand+tagline, grouped
Product and Legal links that navigate correctly (`/terms` + `/privacy` resolve, not broken), a
copyright line, and the "Made in Ukraine" mark; confirm both languages resolve and links are keyboard
focusable with AA contrast.

- [ ] T011 [US3] Add the footer i18n keys to BOTH `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json` (identical sets): `footer.about`, `footer.product`, `footer.tokens`, `footer.legal`, `footer.terms`, `footer.privacy`, `footer.made`, `footer.rights` (reuse existing `nav.search`/`nav.providers` for product links)
- [ ] T012 [P] [US3] Create the new `ig-footer` standalone OnPush component in `frontend/src/app/core/layout/footer.component.ts` (dark `--navy-900` footer with a blue/gold top rule, brand mark + `InvestGuideUA` + tagline, "Product" group [`/search`, `/providers`, `/tokens`], "Legal" group [`/terms`, `/privacy`], base row with copyright + "Made in Ukraine"; a `<footer>` landmark, AA contrast on dark, visible `:focus-visible` on every link, decorative mark `aria-hidden`; component `styles[]` under budget, shared dark rules may go global)
- [ ] T013 [US3] Render `<ig-footer />` in `frontend/src/app/app.component.ts` beneath `<main class="ig-container">`/the router outlet and add it to the shell component imports; keep it in normal flow (must not overlap the fixed toast layer or sticky topbar). Depends on T012
- [ ] T014 [P] [US3] Add the additive `/terms` and `/privacy` routes (with `data.heading`) pointing to the existing `PlaceholderComponent` in `frontend/src/app/app.routes.ts`; do NOT change any existing route or guard
- [ ] T015 [US3] US3 checkpoint: run `npm run build` (under budget); verify the footer renders site-wide, Product/Legal links navigate (`/terms` + `/privacy` resolve to placeholder pages), UK↔EN resolves, keyboard focus is visible and AA contrast holds on the dark background; run the two-role review including the build

**Checkpoint**: Footer present on every page with working links and trust signals.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Whole-feature verification against the Definition of Done.

- [ ] T016 Run the full production build in `frontend/` (`npm run build`) and confirm no `anyComponentStyle` warning/error for any component (footer + landing stay lean) and that the stylesheet parses; state explicitly that the build was actually run (not static-only)
- [ ] T017 [P] i18n parity check: confirm `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json` parse and have identical key sets, every new `footer.*`/`landing.sample*`/`landing.pricing*` key is present in both, and no existing key was removed/renamed
- [ ] T018 [P] Scope + encoding + safety check: confirm no `.ps1`/`.cmd`/`.bat` touched, no BOM added, only `.ts`/`.css`/`.json`/`.html` edited; confirm `/tokens/packs` remains authenticated (no public packs read introduced) and the marketing sections spend no token and make no API call
- [ ] T019 Accessibility audit across all three sections: AA contrast (small gold uses `--gold-700`/`--gold-300` per light/dark context; footer links light enough on navy), visible keyboard focus on every CTA/link, reduced-motion suppresses entrance with nothing hidden, responsive single-column collapse at phone width with nothing clipped or overlapping the toast layer
- [ ] T020 Run the `specs/002-landing-marketing-sections/quickstart.md` validation end-to-end and the final mandatory two-role sub-agent review (FE lead + QA/accessibility) including an actual build; apply or explicitly report findings before marking done

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: rebase/merge onto `001-ui-redesign`; no other dependency.
- **Foundational (Phase 2)**: depends on Setup; a verification gate (confirms 001 primitives).
- **US1 (Phase 3, P1, MVP)**: depends on Foundational only.
- **US2 (Phase 4, P2)**: depends on Foundational; its landing edit (T009) follows US1's landing edit
  (T005) because they share `landing.component.ts`. Otherwise independent of US1's outcome.
- **US3 (Phase 5, P2)**: depends on Foundational only; independent of US1/US2 (separate files, except
  the shared i18n files).
- **Polish (Phase 6)**: depends on all desired stories being complete.

### Within / across stories

- Same-file serialization: `landing.component.ts` — T005 (US1) before T009 (US2). `uk.json`/`en.json`
  — T004, T007, T011 are sequential (same files). `styles.css` — only T008 edits it.
- US3 internal: T012 (new footer file) and T014 (routes) are independent `[P]`; T013 (render in shell)
  depends on T012; T011 (i18n) is sequential vs other i18n tasks.

### Parallel opportunities

- US3: T012 (footer component) and T014 (routes) run in parallel; T011 (i18n) runs alongside them
  only if no other i18n task is mid-edit.
- Polish: T017 + T018 run in parallel.
- If staffed in parallel, US3 (footer — mostly separate files) can proceed alongside US1/US2; just
  serialize the shared i18n-file edits.

---

## Parallel Example: User Story 3

```text
# After T011 (footer i18n), the independent-file US3 tasks run together:
Task: "Create ig-footer component in frontend/src/app/core/layout/footer.component.ts"   # T012
Task: "Add /terms + /privacy routes in frontend/src/app/app.routes.ts"                    # T014
# then:
Task: "Render <ig-footer/> in frontend/src/app/app.component.ts"                          # T013 (needs T012)
```

---

## Implementation Strategy

### MVP first (Foundational + User Story 1)

1. Complete Setup (on top of 001) and Foundational.
2. Complete US1 — the sample-results section (the strongest conversion driver).
3. **STOP and VALIDATE**: build under budget, UK/EN toggle, no token/API. Demo the MVP.

### Incremental delivery

1. Setup + Foundational → ready on the 001 design system.
2. US1 (sample results) → test → demo (MVP).
3. US2 (pricing preview) → test → demo.
4. US3 (footer) → test → demo.
5. Polish → full build, parity, scope/encoding, accessibility, two-role sign-off.

### Constitution gates (every phase)

- Verification by running the production build (parse + budget), not by reading; declare static-only
  if a runtime is ever unavailable.
- Each phase ends with the mandatory two-role sub-agent review (FE lead + QA/accessibility) including
  an actual build. Keep `/tokens/packs` authenticated; pricing/sample figures stay illustrative and
  clearly labelled; money paths untouched.

---

## Notes

- `[P]` = different files, no dependency on an incomplete task.
- No automated test tasks (none requested); the build + manual checks + sub-agent review are the gate.
- Preserve everything in [contracts/preservation.contract.md](contracts/preservation.contract.md):
  no backend/security/money/LLM/guard change; `/tokens/packs` stays private; only `/terms` + `/privacy`
  routes are added.
- Keep new/edited components standalone, `OnPush`, `ig-`-prefixed; add no npm dependency.
- Commit after each task or logical group; stop at any checkpoint to validate the story.
