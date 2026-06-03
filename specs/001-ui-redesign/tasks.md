---
description: "Task list for InvestGuideUA UI/UX Redesign"
---

# Tasks: InvestGuideUA UI/UX Redesign

**Input**: Design documents from `/specs/001-ui-redesign/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: No automated test tasks are included. The spec and plan define verification as the
production build (parse + `anyComponentStyle` budget gate), manual UK/EN runtime toggle, manual
accessibility spot-checks, and the mandatory two-role sub-agent review. No TDD was requested.

**Organization**: Tasks are grouped by user story. Phase 2 (Foundation) is the single blocking
prerequisite for every story (it maps to the spec's Plan Phase A). This is a frontend-only restyle:
only `template:`/`styles:` strings, `index.html` font links, `frontend/src/styles.css`, additive
i18n keys (+ one copy change), and one new display pipe are touched. No TypeScript logic, validators,
services, routing, money math, or i18n mechanism changes (see
[contracts/preservation.contract.md](contracts/preservation.contract.md)).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3 / US4 (setup, foundational, polish carry no story label)
- Exact file paths are included in each task.

## Path note

All source paths are under `frontend/`. The single global stylesheet `frontend/src/styles.css` is a
shared file: tasks that edit it within the same phase are sequential (never `[P]` with each other).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish the verification baseline before any change.

- [x] T001 Run the production build baseline in `frontend/` (`npm ci` then `npm run build`) and record that it currently passes with no `anyComponentStyle` warning, to compare against after each phase
- [x] T002 Record the current i18n key sets of `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json` (confirm they are presently identical) to protect parity through the redesign

**Checkpoint**: Known-good build + i18n parity baseline captured.

---

## Phase 2: Foundational (Blocking Prerequisites) — Plan Phase A

**Purpose**: The canonical design system. **Every user story depends on this phase.** When complete,
every existing component must still compile and render in the new palette via the alias layer with
**zero component edits**.

**⚠️ CRITICAL**: No user-story work begins until this phase is complete and builds under budget.

- [x] T003 [P] Add the two `preconnect` lines + the combined Google Fonts `css2` `<link>` (Playfair Display, Manrope, JetBrains Mono, `display=swap`) inside `<head>` of `frontend/src/index.html` immediately after the viewport meta; do NOT add a BOM (per [contracts/design-tokens.contract.md](contracts/design-tokens.contract.md))
- [x] T004 Replace the `:root` block in `frontend/src/styles.css` with the canonical token ramps (navy/blue/gold incl. `--gold-700`, warm-paper neutrals, risk/status pairs, three `--font-*`, `--radius*`/`--shadow-*`/`--ease`/`--maxw`) plus the back-compat `--ig-*` alias layer
- [x] T005 Replace the global atmosphere in `frontend/src/styles.css` (box-sizing, `html`/`body` warm-paper radial mesh + grain `body::before`, `::selection`, global `:focus-visible` ring, smooth scroll) and bump `.ig-container` `max-width` to `var(--maxw)`
- [x] T006 Add the global heading rules (`h1`/`h2`/`h3` using `--font-display`, clamp sizes) to `frontend/src/styles.css`
- [x] T007 Add the global component classes to `frontend/src/styles.css`: `.ig-card`, `.ig-form`/`.ig-field*`, `.ig-input`/`.ig-select`/`.ig-textarea`/`.ig-input--figure`, `.ig-btn`(+`--primary`/`--ghost`/`--gold`/`--lg`), `.ig-error`/`.ig-muted`/`.ig-hint`, `.ig-kicker`/`.ig-eyebrow`/`.ig-display`(+`--sm`), `.ig-alert`(+`--error`/`--success`/`--info`), `.ig-badge`(+`--cat`/`--risk[data-risk]`/`--ok`/`--warn`), `.ig-chip[data-status]`, `.ig-sr-only`, `.ig-page-head`, `.ig-empty*` (per [contracts/global-classes.contract.md](contracts/global-classes.contract.md))
- [x] T008 Append the motion utilities to `frontend/src/styles.css`: `@keyframes ig-rise`, `.reveal`+`.d1..d5`, `.onscroll`+`.in`+`.s1..s3`, and the `@media (prefers-reduced-motion: reduce)` off-switch that forces `.reveal`/`.onscroll` content visible
- [x] T009 [P] Create the display-only `igCurrency` pipe (impure, standalone, no math) in `frontend/src/app/core/i18n/currency-label.pipe.ts`
- [x] T010 Foundational checkpoint: run `npm run build` in `frontend/`; confirm every existing component still compiles/renders via the alias layer with **no component edits** and **no `anyComponentStyle` warning**; run the mandatory two-role review (FE lead + QA/accessibility) including the build

**Checkpoint**: Design system live; alias layer keeps all current screens compiling. Stories can begin.

---

## Phase 3: User Story 1 — Trustworthy first impression & confident search (Priority: P1) 🎯 MVP — Plan Phase C

**Goal**: Present the core value loop (landing → search → results) in the new trustworthy design
language: editorial hero + trust ledger, instrument-card search form, and instrument-card results
with localized risk/category/currency labels and a verifiable official-source link.

**Independent Test**: Visit the landing page as an anonymous user; sign in; run a valid search;
confirm each option renders as an instrument card with a prominent expected-return figure, localized
risk (color from raw `data-risk`) and category labels, facts, optional rationale, and a working
`rel="noopener noreferrer"` source link — all in the new system and resolving in both UK and EN.

- [x] T011 [P] [US1] Add the Phase-C i18n keys to BOTH `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json` (identical sets): `landing.*` (eyebrow, titleLead, titleAccent, howKicker, howTitle, ledger*), `search.kicker`, `currency.UAH`/`currency.USD`, `category.*`, `risk.LOW`/`MODERATE`/`HIGH` (per [contracts/i18n-keys.contract.md](contracts/i18n-keys.contract.md))
- [x] T012 [US1] Add the global instrument-card block (`.ig-options`, `.ig-opt*`, `.ig-fact*`, `.ig-disclaimer*`, with the `@media (max-width:560px)` single-column facts) to `frontend/src/styles.css`
- [x] T013 [P] [US1] Restyle the landing `template`/`styles` in `frontend/src/app/features/landing/landing.component.ts` (asymmetric editorial hero, italic-blue accent + skewed gold underline with NO `[innerHTML]`, navy trust-ledger with decorative figures, `<ol>` how-it-works, `--surface-2` disclaimer bar, `.reveal .d1..d4`); preserve decorator metadata + `ngOnInit` redirect byte-for-byte
- [x] T014 [P] [US1] Restyle the search `template`/`styles` in `frontend/src/app/features/search/search.component.ts` (`.ig-input`/`.ig-select`/`.ig-textarea` shells, mono amount, `.ig-display` title + `.ig-kicker`, `.ig-btn--lg`, `novalidate` + `aria-invalid`/`aria-describedby`/`role="alert"` on amount error; format `search.optionsFor` currency via `igCurrency`; add `CurrencyLabelPipe` to imports; delete superseded `textarea`/`select`/`.ig-alert--info` local styles, keep `.ig-grid2`/`.ig-form--wide`/`.ig-search__head`/`.ig-results-wrap`); preserve all validators, `submit()`, `mapError()` branches, `igPlural`
- [x] T015 [US1] Restyle the results renderer in `frontend/src/app/features/search/results.component.ts`: render `.ig-opt` cards, localize badge labels via `category.`/`risk.` while keeping `[attr.data-risk]` raw enum for color, split the return figure (`.ig-opt__fig`/`__unit`/`__lbl` gold-700), currency via `igCurrency`, min-amount via unchanged `formatMoney`, liquidity via `common.dash`, entrance `class="reveal d{1..5}"`, add `CurrencyLabelPipe` to imports, **empty the `styles[]` array**; preserve always-on + conditional disclaimers and `target="_blank" rel="noopener noreferrer"` (depends on T012)
- [x] T016 [US1] US1 checkpoint: run `npm run build` (under budget); verify landing/search/results render, UK↔EN toggle resolves every new key, and `ig-results` is visually identical to its History-detail reuse; run the two-role review including the build

**Checkpoint**: Core landing → search → results journey fully restyled and independently demoable (MVP).

---

## Phase 4: User Story 2 — Reachable navigation & account on any device (Priority: P1) — Plan Phase B

**Goal**: A sticky, on-brand app shell with a **working responsive mobile navigation** that keeps
every item — including the token balance — reachable at any width, plus restyled notifications.

**Independent Test**: Narrow the viewport to phone width; confirm the hamburger and UA/EN toggle stay
visible in the bar and the token balance is never `display:none`; open the menu and confirm all items
are reachable; select an item and confirm it navigates and closes the menu; tab the bar and confirm a
visible focus ring on every control.

- [x] T017 [P] [US2] Add the nav i18n keys to BOTH `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json` (identical sets): `nav.menuOpen`, `nav.menuClose` (and `a11y.skipToContent` only if a skip link is added)
- [x] T018 [US2] Restyle the app shell and add the responsive mobile menu in `frontend/src/app/app.component.ts`: sticky blurred paper topbar, `InvestGuide<b>UA</b>` brand, gold-underline nav, mono balance pill, mono UA/EN pill with `[attr.aria-pressed]` + `lang="uk"/"en"` spans, 5px blue/gold flag rule, a real `<button class="ig-nav__toggle">` (≥44×44, `[attr.aria-expanded]="menuOpen()"`, `aria-controls="ig-primary-nav"`, translated `aria-label`), `protected readonly menuOpen = signal(false)`, `menuOpen.set(false)` on nav-item click and in `logout()`, reconcile `.ig-topbar__inner` to `var(--maxw)`, remove the old `≤560px` grid block; **token balance must never be `display:none`**; preserve all `@if` branches, `routerLink`/`routerLinkActive`, `igPlural`, the dynamic lang `aria-label`, `announcement()` region, `ngOnInit`, `logout()` → `/`
- [x] T019 [P] [US2] Restyle the toast host in `frontend/src/app/core/errors/notification-host.component.ts` (warm `--surface`, `--radius-sm`, `--shadow-md`, `--font-mono` request-id, gold `--gold-600` warning border); keep markup, logic, `role`/`aria-live`/`aria-atomic`, dismiss label, and `&times;` glyph unchanged
- [x] T020 [US2] US2 checkpoint: run `npm run build` (under budget); verify at ≤760px the hamburger + UA/EN stay visible, toggling sets `aria-expanded` and reveals all items including the balance, selecting an item closes the menu; run the two-role review including the build

**Checkpoint**: Navigation reachable on every device; the original mobile defect is fixed.

---

## Phase 5: User Story 3 — Accessible, comfortable use for everyone (Priority: P2)

**Goal**: Verify and harden the cross-cutting accessibility guarantees on every surface delivered so
far (and re-run after US4). Accessibility is implemented within each component task; this phase is
the dedicated audit-and-fix gate.

**Independent Test**: Run contrast checks, full keyboard navigation, an OS reduce-motion pass, and a
never-color-alone check across the delivered screens; all must pass.

- [x] T021 [US3] WCAG AA contrast audit across delivered surfaces (body `--ink`/`--muted` on paper, all `--risk-*`/status fg+bg pairs, navy-on-gold buttons, and every small gold text instance using `--gold-700`); fix any failure in the offending component or `frontend/src/styles.css`
- [x] T022 [US3] Keyboard focus audit across delivered screens: confirm the global `:focus-visible` ring (and input focus ring) is visible on every interactive element (brand, nav, balance pill, lang toggle, hamburger, CTAs, form controls, source/router links, toast controls); fix any outline removed without a visible replacement
- [x] T023 [US3] Reduced-motion audit: with `prefers-reduced-motion: reduce`, confirm all animation/transition is neutralized and `.reveal` content is forced visible; confirm any spinner falls back to a static state
- [x] T024 [US3] Never-color-alone + mobile-reachability audit: confirm every risk/status indicator carries a translated text label in addition to color, and the token balance stays reachable (never `display:none`) at all widths
- [x] T025 [US3] US3 checkpoint: run the two-role QA/accessibility review across all delivered surfaces, including a build; apply or report findings

**Checkpoint**: Accessibility verified across everything shipped to date.

---

## Phase 6: User Story 4 — Consistent design across every remaining screen (Priority: P3) — Plan Phases D + E

**Goal**: Restyle the remaining screens (tokens, payment result, auth, history, history-detail,
account, providers, not-found, placeholder) into the same design language while preserving all
existing behavior, and apply the one `notFound.title` copy change.

**Independent Test**: Visit each remaining screen; confirm it adopts the new design language, its
existing behavior is unchanged (purchasing, payment polling, auth flows, history paging, account
actions, provider listings), and all copy resolves in both languages.

- [x] T026 [US4] Add the Phase-D + Phase-E i18n keys to BOTH `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json` (identical sets) — `tokens.kicker`, `tokens.recommended`(+optional `recommendedSr`), `paymentResult.kicker`, `login.eyebrow`, `register.eyebrow`, `register.eyebrowSent`, `verify.eyebrow`, `history.kicker`, `history.paginationLabel`, `account.eyebrow`, `providers.typicalReturn`, `common.currencyUahShort`, `placeholder.badge` — AND apply the `notFound.title` copy change (drop "404 - "; uk `Сторінку не знайдено`, en `Page not found`), leaving `notFound.body` unchanged
- [x] T027 [US4] Add the global `.ig-auth*` shared rules to `frontend/src/styles.css` (`.ig-auth`, `.ig-auth--page` flex-centered `min-height`, `.ig-auth::before` 3px blue/gold flag top-rule, `.ig-auth__eyebrow`, `.ig-auth h1`) so the auth components carry empty `styles[]` (blocks T030–T032)
- [x] T028 [P] [US4] Restyle tokens into a dark institutional pack wall in `frontend/src/app/features/payments/tokens.component.ts` (`.ig-section--dark`, mono-gold count + Playfair price + mono per-token, template-derived recommended `packs().length === 3 && $index === 1` with gold ribbon `aria-hidden` + `.ig-btn--gold` vs `.ig-btn--primary`); preserve `buy()` (`PENDING_PAYMENT_KEY` + `window.location.href`), `price()`/`perToken()` math, signals, `igPlural`
- [x] T029 [P] [US4] Restyle payment-result in `frontend/src/app/features/payments/payment-result.component.ts` (mono `paymentResult.kicker`, Playfair title, per-state accent rail via `[attr.data-state]`, reduced-motion-safe spinner + `ig-spin` keyframe, `.reveal`, `role="status"`/`role="alert"` live regions); preserve the `state`/`ResultState` machine, `@Input() paymentId`, `resolvedId`, `BACKOFF_MS` polling, `finishSuccess()`, `ngOnDestroy`, and all five state router links
- [x] T030 [P] [US4] Restyle login into a centered credential card in `frontend/src/app/features/auth/login.component.ts` (wrap in `.ig-auth--page`, add `.ig-auth__eyebrow` `login.eyebrow`, empty/remove local `styles`); preserve form controls, `serverError()`, disabled-while-`submitting()`, `/register` link, success → `/search`, generic 401/422 message (depends on T027)
- [x] T031 [P] [US4] Restyle register (both states) in `frontend/src/app/features/auth/register.component.ts` (`.ig-auth--page`, `register.eyebrow`/`register.eyebrowSent`, empty local `styles`); preserve `registeredEmail()` two-state flow, `showError`, `PASSWORD_PATTERN`, `EMAIL_TAKEN`/`VALIDATION_ERROR`/fallback, `register.sentLink {email}`, `register.verifyToActivate` (depends on T027)
- [x] T032 [P] [US4] Restyle verify (five states) in `frontend/src/app/features/auth/verify.component.ts` (`.ig-auth--page`, `verify.eyebrow`, empty local `styles`); preserve the `@switch` branches, `verify.success {tokens}` via `igPlural`, `@Input() token`, router links (depends on T027)
- [x] T033 [P] [US4] Restyle history into an editorial ledger in `frontend/src/app/features/history/history.component.ts` (`.ig-page-head` kicker `history.kicker` + Playfair title, rows `class="ig-hist__row reveal d{1..5}"` capped at d5, `.ig-chip [attr.data-status]` reusing risk language incl. a real `pending` rule, decorative chevron `aria-hidden`, pager wrapped in `<nav aria-label>` `history.paginationLabel`, `.ig-empty` empty state with `/search` CTA, `@media (max-width:560px)` row stacking); preserve `statusKey()`, `PAGE_SIZE`, `go()`, `[disabled]` math, `history.pageOf`, `amount/100 | number:'1.2-2'` + currency
- [x] T034 [P] [US4] Restyle history-detail chrome in `frontend/src/app/features/history/history-detail.component.ts` (mono back link with inline SVG chevron replacing literal `←`, Playfair amount + mono currency, reuse unchanged `<ig-results>`, `.ig-empty` for not-found); preserve `@Input() id` and the `status===404`/`parseApiError` mapping; **do NOT modify `results.component.ts`**
- [x] T035 [P] [US4] Restyle account dossier in `frontend/src/app/features/account/account.component.ts` (`.ig-page-head` `account.eyebrow` + Playfair `<h1>`, mono-labeled hairline ledger rows, balance `<dd class="ig-balance">`, quiet `--surface-2` gold-left-ruled danger zone, global `.ig-badge--ok`/`--warn`; delete local `.ig-btn--ghost`/`.ig-badge`/`.ig-alert--info`); preserve `auth.loadMe()` in `ngOnInit`, `deletionMailto()` href, `deletionRequested` toggle, `logout()` → `/`, `<dl>/<dt>/<dd>`, element choices
- [x] T036 [P] [US4] Restyle providers into instrument cards in `frontend/src/app/features/providers/providers.component.ts` (move risk beside the category chip as `.ig-badge--risk [attr.data-risk]`, add Playfair `returnRange` + mono `common.perYear` + gold-700 `providers.typicalReturn` block, drop the duplicated return/risk `<dl>` entries, add display-only `currencyList(p)` mapping `UAH`→`common.currencyUahShort`; delete local `.ig-chip`/`.ig-alert--info`); preserve the service subscription, signals, `categoryLabel`/`riskLabel`/`returnRange`/`minAmount` (→`formatMinorUnits`), external-link `rel`/`target`, back link, `track p.id`
- [x] T037 [P] [US4] Restyle not-found in `frontend/src/app/features/not-found/not-found.component.ts` into the on-brand `.ig-empty` (brand-mark tile, mono gold `404` `.ig-empty__code`, Playfair `<h1>` `notFound.title`, muted body, `.ig-btn--primary` to `/`); no component styles; preserve `routerLink="/"`, keys, OnPush/standalone/imports
- [x] T038 [P] [US4] Restyle placeholder in `frontend/src/app/features/placeholder/placeholder.component.ts` into the same `.ig-empty` shell with a `placeholder.badge` "coming soon" code instead of a numeral, keeping the dynamic `heading` `<h1>`; no component styles; preserve `@Input() heading` + `heading || (...)` fallback and `routerLink="/"`
- [x] T039 [US4] US4 checkpoint: run `npm run build` (under budget); verify every remaining screen renders, behavior is unchanged (purchase/polling/auth/paging/account/providers), `notFound.title` copy change applied in both locales, and UK↔EN parity holds; run the two-role review including the build

**Checkpoint**: All screens restyled; behavior preserved everywhere.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final whole-app verification against the global Definition of Done (source Section 10).

- [x] T040 Run the full production build in `frontend/` (`npm run build`) and confirm **no `anyComponentStyle` warning (>4 kb) or error (>8 kb)** for any component and that the stylesheet parses; state explicitly that the build was actually run (not static-only)
- [x] T041 [P] Verify companion CSS deletions landed: `search.component.ts` (textarea/select/`.ig-alert--info` removed), `results.component.ts` (`styles[]` empty), `account.component.ts` and `providers.component.ts` (local duplicates removed) — no stale lemon `#ffd700`/`#eee` values linger
- [x] T042 [P] Full i18n parity check: confirm `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json` parse and have **identical key sets**, every Section-5.2 key is present in both, the `notFound.title` change is applied in both, and no existing key was removed/renamed
- [x] T043 [P] Encoding verification (Constitution V): confirm no `.ps1`/`.cmd`/`.bat` files were modified (ASCII scan therefore has nothing to flag), `frontend/src/index.html` gained no BOM, and all edited `.html`/`.css`/`.ts`/`.json` files are UTF-8 with Cyrillic only where appropriate
- [x] T044 Re-run the full accessibility audit (contrast, keyboard focus, reduced motion, never-color-alone, mobile balance reachability) across the entire app now that all screens are restyled
- [x] T045 Run the `specs/001-ui-redesign/quickstart.md` validation checklist end-to-end (build, UK/EN toggle, focus, reduced motion, mobile nav)
- [x] T046 Final mandatory two-role sub-agent review (FE lead + QA/accessibility) of the complete redesign, including an actual build/parse; apply or explicitly report all findings before marking the feature done

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies — start immediately.
- **Foundational (Phase 2)**: depends on Setup; **BLOCKS all user stories** (the design system + pipe).
- **US1 (Phase 3, P1, MVP)**: depends on Foundational only.
- **US2 (Phase 4, P1)**: depends on Foundational only; independent of US1 (different files).
- **US3 (Phase 5, P2)**: an audit over surfaces delivered by US1 + US2; re-run in Polish after US4.
- **US4 (Phase 6, P3)**: depends on Foundational only; independent of US1/US2 (different files; the
  history-detail screen reuses the already-restyled `ig-results` from US1 but does not modify it).
- **Polish (Phase 7)**: depends on all desired stories being complete.

### Within Foundational (Phase 2)

`frontend/src/styles.css` is edited by T004→T005→T006→T007→T008 **sequentially** (same file).
T003 (`index.html`) and T009 (pipe) are independent → `[P]`.

### Within each user story

- US1: T012 (global `.ig-opt*` in `styles.css`) must precede T015 (results consumes it). T013/T014
  are different component files → `[P]`. T011 (i18n) is `[P]`.
- US2: T018 and T019 are different files → `[P]`. T017 (i18n) is `[P]`.
- US4: T027 (global `.ig-auth*` in `styles.css`) must precede T030/T031/T032 (auth). T026 (i18n) is a
  single shared-file task. T028–T038 are different component files → `[P]` (after T027 for auth).

### Parallel opportunities

- Foundational: T003 + T009 in parallel (while the `styles.css` chain T004–T008 runs sequentially).
- US1: T011 + T013 + T014 in parallel; then T012 → T015.
- US2: T017 + T018 + T019 in parallel.
- US4: after T026 + T027, run T028, T029, T030, T031, T032, T033, T034, T035, T036, T037, T038 in
  parallel (all distinct component files).
- Polish: T041 + T042 + T043 in parallel.

---

## Parallel Example: User Story 4

```text
# After T026 (i18n) and T027 (global .ig-auth* in styles.css), launch the component restyles together:
Task: "Restyle tokens in frontend/src/app/features/payments/tokens.component.ts"
Task: "Restyle payment-result in frontend/src/app/features/payments/payment-result.component.ts"
Task: "Restyle login in frontend/src/app/features/auth/login.component.ts"
Task: "Restyle register in frontend/src/app/features/auth/register.component.ts"
Task: "Restyle verify in frontend/src/app/features/auth/verify.component.ts"
Task: "Restyle history in frontend/src/app/features/history/history.component.ts"
Task: "Restyle history-detail in frontend/src/app/features/history/history-detail.component.ts"
Task: "Restyle account in frontend/src/app/features/account/account.component.ts"
Task: "Restyle providers in frontend/src/app/features/providers/providers.component.ts"
Task: "Restyle not-found in frontend/src/app/features/not-found/not-found.component.ts"
Task: "Restyle placeholder in frontend/src/app/features/placeholder/placeholder.component.ts"
```

---

## Implementation Strategy

### MVP first (Foundational + User Story 1)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational) — the design system must land first.
2. Complete Phase 3 (US1) — landing → search → results in the new language.
3. **STOP and VALIDATE**: build under budget, UK/EN toggle, `ig-results` parity. Demo the MVP.

### Incremental delivery

1. Foundational ready → every screen already renders in the new palette via the alias layer.
2. Add US1 → test → demo (MVP: the core value loop).
3. Add US2 → test → demo (mobile nav defect fixed).
4. Run US3 audit on what's shipped.
5. Add US4 → test → demo (all remaining screens consistent).
6. Polish → final build, parity, encoding, accessibility re-audit, two-role sign-off.

### Constitution gates (every phase)

- Verification is by running the production build (parse + budget), not by reading rendered text;
  declare static-only explicitly if a runtime is ever unavailable.
- Each phase ends with the mandatory two-role sub-agent review (FE lead + QA/accessibility) that
  includes an actual build/parse. Apply or report findings before closing the phase.

---

## Notes

- `[P]` = different files, no dependency on an incomplete task.
- `frontend/src/styles.css` is shared; never run two `styles.css` edits as `[P]`.
- No automated test tasks (none requested); the build + manual checks + sub-agent review are the gate.
- Preserve everything in [contracts/preservation.contract.md](contracts/preservation.contract.md):
  no logic, validator, service, route, money-math, or i18n-mechanism change.
- Keep every component standalone, `OnPush`, `ig-`-prefixed; add no npm dependency.
- Commit after each task or logical group; stop at any checkpoint to validate the story.
