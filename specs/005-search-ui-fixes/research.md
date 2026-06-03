# Phase 0 Research: Search Page UI Fixes

**Feature**: `005-search-ui-fixes` | **Date**: 2026-06-04

No open `NEEDS CLARIFICATION` markers remained in the spec. The "research" here is root-cause
confirmation against the live code plus the chosen fix approach for each of the three issues.

---

## R1. Why a logged-in user sees guest CTAs (US1 / FR-001, FR-003)

**Decision**: Make authentication state **reactive**. Back the in-memory access token with an
Angular signal and derive `isAuthenticated` from it (or, equivalently, derive `isAuthenticated`
from the existing `_user` signal). The nav markup in `app.component.ts` (which already branches
correctly on `auth.isAuthenticated()`) then re-renders the moment a session is
established/cleared.

**Rationale**: In `auth.service.ts:36`:
```ts
readonly isAuthenticated = computed(() => this.#accessToken !== null);
```
`#accessToken` is a plain class field, **not** a signal. An Angular `computed()` only
recomputes when a signal it read changes; this computed reads no signal, so it captures zero
reactive dependencies and memoizes its first value (`false`, because the access token is null
on a fresh load) **forever**. When `applySession()` later sets `this.#accessToken`, nothing
invalidates the computed, so `isAuthenticated()` stays `false`.

Meanwhile `tokenBalance = computed(() => this._user()?.tokenBalance ?? 0)` reads the `_user`
**signal**, so it updates correctly. This asymmetry exactly reproduces the screenshot: the nav
shows guest CTAs (`isAuthenticated()` frozen `false`) **and** "5 tokens" (`tokenBalance()` live)
at the same time. On the `/search` route the `verifiedGuard` (`auth.guards.ts:45`) successfully
refreshes + loads the profile (hence balance 5 and a rendered form), but the frozen
`isAuthenticated` leaves the nav stuck in the guest branch.

**Chosen fix shape**: Convert the access token to a signal so both the field and the derived
flag are reactive, e.g.:
```ts
readonly #accessToken = signal<string | null>(null);
readonly isAuthenticated = computed(() => this.#accessToken() !== null);
get accessToken() { return this.#accessToken(); }
// applySession: this.#accessToken.set(res.accessToken)
// clearSession: this.#accessToken.set(null)
```
This keeps the public API (`accessToken` getter, `isAuthenticated` computed) identical for all
callers (interceptor, guards) while making the flag reactive. The guard logic in
`auth.guards.ts` is unchanged.

**Alternatives considered**:
- *Derive `isAuthenticated` from `_user()` presence only* — reactive and one line, but `loadMe()`
  sets `_user` **without** an access token, so a profile loaded while the token is null would read
  as "authenticated" in the nav. Rejected: couples the flag to profile load rather than to a real
  session and risks showing the account menu without a usable token. Signal-backed token is the
  truthful source.
- *Add an Angular effect to force change detection* — masks the root cause, adds a moving part,
  violates MVP minimalism. Rejected.
- *Manually trigger change detection in the component* — fragile, doesn't fix the underlying
  non-reactive state, would need repeating everywhere `isAuthenticated` is read. Rejected.

**No-flash consideration (spec edge case / US1 scenario 5) — decision**: The shell nav
(`app.component.ts:32-73`) renders **before** any route guard runs, and on a hard reload the
in-memory access token is null until the startup silent `refresh()` (`ngOnInit`, lines 184-194)
resolves. So even with the reactive fix, an authenticated user can momentarily see the guest
branch before refresh completes — exactly the "flash" US1-5 forbids ("must not present guest CTAs
as the authenticated user's state at any point"). QA review flagged that verifying the *final*
state only (as the first draft did) does not test the no-flash requirement.

**Decision for this feature**: introduce a tri-state auth status — `unknown | authenticated | guest`
— where the status is `unknown` until the startup `refresh()` settles. While `unknown`, the nav
renders **neither** the account menu nor the guest CTAs (a neutral state: e.g. just the brand +
language toggle, or a lightweight placeholder), flipping to `authenticated`/`guest` once known.
This honors US1-5 deterministically rather than relying on race timing. It is a small, contained
addition (one signal + the existing reactive branch), consistent with MVP minimalism.

**Deterministic verification**: a component/`TestBed` test that holds `refresh()` **pending**
(returns an unresolved observable) and asserts the guest CTAs are NOT rendered while pending; then
resolves it authenticated and asserts the account menu appears. A manual throttled-network reload
is a secondary check. (If, during implementation, the team prefers to defer the tri-state, that is
an explicit scope decision to record against US1-5 — it must not be left to chance observation.)

---

## R2. Localizing the currency selector label (US2 / FR-004..FR-007, FR-011)

**Decision**: Render the currency `<option>` text through the **existing** `CurrencyLabelPipe`
(`igCurrency`), which is already imported and used in this component's results heading. No new
pipe, no translation-file edits.

**Rationale**: The defect is purely that the options are hardcoded literals
(`search.component.ts:61-62`):
```html
<option value="UAH">UAH</option>
<option value="USD">USD</option>
```
The localization machinery already exists and is correct:
- `currency-label.pipe.ts` is `pure: false` (re-renders on runtime language switch) and resolves
  `currency.<code>` from ngx-translate.
- `public/i18n/uk.json:186` = `"UAH": "грн"`, `public/i18n/en.json:186` = `"UAH": "UAH"`,
  both files `"USD": "USD"`.
The fix binds the visible text to the pipe while keeping the `value` attribute as the canonical
code:
```html
<option value="UAH">{{ 'UAH' | igCurrency }}</option>
<option value="USD">{{ 'USD' | igCurrency }}</option>
```
Because the pipe is impure, switching language updates the label live (FR-006). The form control
value remains the code `UAH`/`USD`, so no submit/payload/money behavior changes (FR-007).

**Alternatives considered**:
- *`{{ 'currency.UAH' | translate }}`* — works, but bypasses the dedicated currency pipe that the
  rest of the app already uses for currency labels, splitting the convention. Reusing `igCurrency`
  keeps one source of truth. Minor preference; either satisfies the spec.
- *Edit translation JSON* — unnecessary; the keys already hold the right values. Rejected (no
  change needed).
- *Reuse `common.currencyUahShort`* — a second, redundant key for the same label; do not introduce
  a third label source. Stick with `currency.*` via the pipe.

**Consistency (FR-011)**: The results heading (`search.component.ts:115`) already uses
`igCurrency`, so after fixing the selector the page is consistent. USD stays "USD" in both
languages (guards against over-localization, US2 scenario 5).

---

## R3. Spreading the form across the card width (US3 / FR-008..FR-010)

**Decision**: **Override** the `max-width` cap on `.ig-form--wide` (set it to `none` or `100%`) so
the form fills its card, keeping the existing two-column grid (`.ig-grid2`) and the existing mobile
stacking breakpoint. CSS-only; no template or logic change.

**CRITICAL correction (from FE-lead review)**: There is a **global** rule
`styles.css:143` → `.ig-form { display: flex; flex-direction: column; gap: 1rem; max-width: 420px; }`.
The component-scoped `.ig-form--wide { max-width: 560px }` (`search.component.ts:124`) currently
wins only because Angular emulated encapsulation raises its specificity. Therefore **deleting** the
560px line is WRONG — the form would fall back to the global **420px** cap and become *narrower*
than today, failing FR-008. The task MUST change the value to an explicit override
(`.ig-form--wide { max-width: none; }` or `100%`), not remove the rule.

**Rationale**: `search.component.ts:124` caps the form at 560px; the surrounding `.ig-card` is full
page-column width, so the cap leaves a large empty area to the right (the "crammed left" symptom).
The fields already use a responsive two-column grid (`.ig-grid2`, line 122) that collapses to one
column at `max-width: 520px` (line 123). Overriding the cap to `none`/`100%` lets the grid expand
to the card width on desktop while the 520px stack rule keeps mobile legible (FR-009). The card is
itself bounded by the page layout (`--maxw`), so line length stays readable without an explicit
form cap. All fields, labels, validation, and submit are unchanged markup, so FR-010 holds
trivially. Verify there is no awkward zone between the form's 520px stack breakpoint and the nav's
760px breakpoint (cosmetic only).

**Alternatives considered**:
- *Switch to a 3-/4-column grid to "fill" space* — changes the established paired layout
  (amount/currency, horizon/risk) the spec treats as acceptable, and risks cramped fields. The
  spec requires full-width use, not more columns. Rejected.
- *Center the 560px form in the card* — would balance whitespace but not satisfy "spread out by
  width". Rejected.

---

## Cross-cutting decisions

- **Testing (CRITICAL correction from QA review)**: A naive test that just asserts
  `isAuthenticated()` is `true` after `applySession` and `false` after `clearSession` is
  **insufficient** — `auth.service.spec.ts` *already* has those assertions (lines ~53, ~118) and
  they **pass while the bug is live**. Reason: an Angular `computed()` with no signal dependency
  computes lazily on **first read** and caches; the existing spec reads `isAuthenticated()` only
  *after* the token is set, so it never exercises the broken path. The real defect needs
  **read-before-set** ordering (the nav reads `isAuthenticated()` during initial render, *before*
  the startup `refresh()` resolves). The regression test MUST therefore reproduce that ordering,
  e.g.:
  - Read `isAuthenticated()` once **before** applying a session (observe `false`), then
    `applySession`/`login`, then assert it is now `true` **on the same service instance** (no
    re-injection); and symmetrically read `true` before `clearSession`, then assert `false`. With
    the non-reactive impl, the first read memoizes and the post-mutation assert fails; with the
    signal fix it passes. OR
  - A component/`TestBed` test that renders the **guest** nav branch, applies a session, runs
    `fixture.detectChanges()`, and asserts the DOM switched to the account menu (Sign in/Register
    gone). This also covers US1 scenarios 1-4 at the template level.
- **New `search.component.spec.ts` (none exists today)**: add it to cover Fix 2/3 — assert the
  currency `<option>` text resolves through `igCurrency` (not a hardcoded literal) and updates on
  language switch, and that `.ig-form--wide` no longer pins to a left column (e.g. no 560px/420px
  cap). Without this, Fix 2/3 are manual-only; do not claim unit-spec regression guarding for them
  otherwise.
- **Implementation step (make explicit)**: `applySession()` and `clearSession()` must set the new
  access-token **signal** via `.set(...)` (not field assignment); the `accessToken` getter returns
  `this.#accessToken()`. Grep confirms the only synchronous external reader is
  `auth.interceptor.ts:39` (`const token = auth.accessToken;`) — a plain signal read is safe there
  (no reactive/injection context required). Guards read `isAuthenticated()`/`refresh()` only, so
  `auth.guards.spec.ts` is unaffected; re-run to confirm.
- Re-run existing `auth.service.spec.ts`, `auth.guards.spec.ts`, `results.component.spec.ts`.
- **No backend / contract changes**: `/auth/*`, `/me`, `/investments/search` payloads and the
  money model are untouched (see `contracts/ui-contracts.md`).
- **Verification discipline (Constitution V)**: no Windows scripts touched; validate via
  `ng build`/`ng test`/lint, or declare static-only if the Node/Angular runtime is unavailable in
  the execution environment.
