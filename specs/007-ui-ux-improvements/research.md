# Phase 0 Research: Frontend UI/UX Audit & Improvements

**Feature**: 007-ui-ux-improvements | **Date**: 2026-06-04

The specification has no open `[NEEDS CLARIFICATION]` markers. This document resolves the
*implementation* decisions needed before design, each grounded in the existing codebase
(`frontend/src/styles.css`, `app.component.ts`, and the feature components) and current
front-end accessibility practice (WCAG 2.2, WAI-ARIA Authoring Practices).

---

## D1. Automated accessibility scanning tool

- **Decision**: Add a dev-only `@axe-core/puppeteer` helper and a Node ESM script at
  `frontend/tools/a11y/axe-audit.mjs` that **launches headless Chrome, navigates to each
  served journey, waits for hydration, and runs** the axe-core WCAG 2.1/2.2 A+AA rule set,
  exiting non-zero on any violation. Expose it as an `a11y:audit` npm script and wire it into
  CI (mirroring how `seo:audit` is CI-gated).
  - **Serving model (explicit)**: the script targets a server the caller has already started
    (`npm start` for the dev/CSR DOM is sufficient for axe's DOM-level rules). It performs its
    own readiness wait (poll the URL) and owns browser launch/teardown. It does **not** read
    static files - this is the opposite execution model from `tools/seo/seo-audit.mjs` (which
    statically parses prerendered HTML off `dist/.../browser` and launches no browser); the two
    scripts are *siblings in `tools/`, not the same pattern*.
  - **Route scope (explicit)**: the automated scan covers only **public, unauthenticated**
    routes that render fully without a backend session - landing, search form, login, register,
    articles (+ a sample article). Authenticated/data-backed screens (account, history, and the
    populated search-results path) would scan an error/redirect state headlessly and produce a
    false pass, so they are verified by the manual keyboard/SR pass instead (or, optionally, by
    a future authed-session harness - out of scope here).
- **Rationale**: `puppeteer` (^24) is already installed as a devDependency - it is consumed by
  `karma.conf.js` (which sets `CHROME_BIN` from `puppeteer.executablePath()`), so the headless
  Chrome binary is already present and no new browser engine is added. `@axe-core/puppeteer`
  (4.11.x) peer-requires `puppeteer >=1.10.0`, satisfied by ^24. axe-core is the de-facto
  standard automated WCAG engine. Dev-only, no client-bundle impact - consistent with
  Constitution Principle II.
- **Alternatives considered**:
  - *Playwright + @axe-core/playwright*: rejected - introduces a second browser-automation
    framework when puppeteer already covers the need (Principle I / minimal footprint).
  - *Lighthouse CI*: rejected as the primary gate - broader but shallower a11y coverage than
    axe and heavier to wire; may be a later add-on, not needed now.
  - *Manual only*: rejected - not repeatable; SC-001 explicitly wants an automated layer plus
    the manual authoritative bar.
- **Notes**:
  - Automated scans catch only a portion of WCAG issues; per SC-001 the manual keyboard +
    screen-reader pass (documented in quickstart.md) is the authoritative AA bar.
  - `npm start` serves the **CSR** DOM (there is no `serve:ssr` script today). axe runs against
    that DOM, which is correct for DOM-level a11y rules; the SSR-specific focus behavior is
    covered by unit specs + the manual pass, not the automated scan.

## D2. Skip link + focus management on SPA route changes

- **Decision**: Add a visually-hidden-until-focused skip link as the first focusable element
  in `app.component.ts`, targeting `<main id="main-content" tabindex="-1">`. Add a small
  SSR-safe `core/a11y/route-focus.service.ts` that, on Angular Router `NavigationEnd`, moves
  focus to the main landmark (or its first heading) so SPA navigations behave like full page
  loads for assistive tech.
- **Rationale**: Satisfies FR-001 (bypass blocks, WCAG 2.4.1) and the SPA focus-loss problem
  that automated scanners miss. `tabindex="-1"` makes the landmark programmatically focusable
  without adding it to the tab order. Router-event focusing is the WAI-recommended SPA pattern.
- **Alternatives considered**:
  - *Skip link only, no route focus*: rejected - after client-side navigation focus stays on
    the activated link/stale element; keyboard users lose place.
  - *CDK `LiveAnnouncer`/`FocusMonitor` (`@angular/cdk/a11y`)*: rejected for now - pulls in
    Angular CDK as a new dependency for a small need; a ~15-line service plus existing
    `.ig-sr-only` covers it without expanding the dependency set.
- **SSR safety**: guard all `document`/`window`/`focus()` access behind
  `isPlatformBrowser(...)`; the service is a no-op during server render.

## D3. Announcing dynamic content (search results, async status)

- **Decision**: Wrap the populated search-results region in a polite live region
  (`role="status" aria-live="polite" aria-atomic="false"`) that announces a concise result
  summary (e.g., "N options for {amount} {currency}") when results arrive, and set
  `aria-busy="true"` on the region while the search request is in flight. The existing toast
  host already announces politely; extend the same pattern to async status changes.
- **Rationale**: Satisfies FR-003 (WCAG 4.1.3 Status Messages). The empty/"no options" branch
  already uses `role="status"`; this brings the populated path to parity. `aria-atomic="false"`
  avoids re-reading the whole region on minor updates.
- **Alternatives considered**:
  - *`aria-live="assertive"`*: rejected - interrupts the user; results are not an emergency.
  - *Moving focus to the results heading*: rejected as the default - more disruptive than a
    polite announcement; focus stays on the submit action for fast iteration.
- **Coordination note**: the search **amount field error** already uses `role="alert"`
  (assertive). Ensure the new polite results-summary region and that assertive field error do
  not both fire for the same submit (e.g., only announce the results summary on a successful
  result render), to avoid double-announcement on one screen.

## D4. Touch-target technique (44x44 CSS px)

- **Decision**: Establish a 44px minimum via the shared control classes rather than per-screen
  overrides: add `min-height: 44px` (and adequate inline padding) to `.ig-btn`, `.ig-field`
  controls, `.ig-input`, the language toggle, the burger toggle (already 44px), and the toast
  dismiss/reference controls. For small inline controls, expand the hit area with padding or a
  pseudo-element overlay so the visual size is preserved where the design intends it small.
- **Rationale**: Satisfies FR-009/SC-003 centrally (one change covers most controls), keeping
  the visual language intact (FR-022) by growing hit area via padding, not by restyling.
- **Alternatives considered**:
  - *Per-component min-size patches*: rejected - scatters the rule and drifts (the exact
    inconsistency this feature is removing).
  - *Global `* { min-height }`*: rejected - breaks inline text and layout.

## D5. Design-scale tokenization (spacing / type / radius)

- **Decision**: Add explicit CSS custom-property scales to `:root` in `styles.css`:
  a spacing scale (e.g., `--space-1..--space-8` ~ 4/8/12/16/24/32/48/64px), a type scale
  aligned to the existing heading clamps plus a small set of body sizes, and reuse the existing
  `--radius`/`--radius-sm` for corners (replacing hardcoded `6px`/`16px`). Map current ad-hoc
  values to the nearest scale step; the back-compat `--ig-*` alias layer stays.
- **Rationale**: Satisfies FR-016. The audit found ~7 gap values and several hardcoded radii
  and font sizes; a named scale makes consistency enforceable and reviewable without changing
  the visual identity (values chosen to match what's already on screen).
- **Alternatives considered**:
  - *Adopt a CSS framework / utility lib (Tailwind, Open Props)*: rejected - new dependency and
    a paradigm shift against Principle I/II for a system that already has hand-tuned tokens.
  - *Leave values inline, document only*: rejected - does not make FR-016 testable.

## D6. Reactive-forms error association pattern (Angular)

- **Decision**: Standardize a template pattern: each field sets `[attr.aria-invalid]` when its
  control is invalid-and-touched and `[attr.aria-describedby]` to a stable error-element id;
  the error element carries the message and is rendered in the DOM adjacent to its input. Bring
  **login** (currently no per-field validation/association) and **register** (inline errors not
  linked) up to the **search** form's existing standard. Required fields get a visible required
  indicator with an accessible-name counterpart.
- **Rationale**: Satisfies FR-002/FR-018/FR-019 (WCAG 1.3.1, 3.3.1, 3.3.2). The search form is
  the reference implementation already in the repo, so this is a consistency/port task, not new
  invention. Errors surface on blur (format) and on submit (required), not only post-submit.
- **Alternatives considered**:
  - *A custom form-field wrapper component/directive*: viable and DRY, but heavier; given only
    a few forms, the shared template pattern (optionally a tiny attribute directive) is the
    minimal step. Left as an implementation choice for tasks, not mandated here.

## D7. Shared empty / loading / error state components

- **Decision**: Extract three small standalone presentational components under
  `features/shared/` - `empty-state`, `loading-state`, `error-state` - that wrap the existing
  `.ig-empty`, a consistent loading indicator, and `.ig-alert--error role="alert"`. Refactor
  providers, tokens, articles-index, history, account, and search-results to use them.
- **Rationale**: Satisfies FR-013/014/015 and removes the audit-confirmed divergence (history
  uses the rich `.ig-empty`; providers/tokens/articles use plain alerts or muted text;
  account's error alert lacks `role="alert"`). Standalone components fit the app's existing
  standalone-only architecture and are content-projection friendly for bilingual text.
- **Alternatives considered**:
  - *CSS-class-only convention (no components)*: rejected - relies on every screen remembering
    the markup; a component enforces the contract and the ARIA roles.
  - *One mega "state" component with a mode input*: rejected - three focused components read
    clearer and keep each ARIA role correct by construction.

## D8. Reduced-motion verification scope

- **Decision**: Treat reduced motion as an **audit-and-confirm** task, not new implementation.
  The global `@media (prefers-reduced-motion: reduce)` block in `styles.css` already neutralizes
  `*` animations/transitions and force-reveals `.reveal`/`.onscroll`. Verify (via the manual
  checklist + a quick DevTools emulation) that every component-level transition (e.g., history
  row scale, back-link icon) is actually covered, and remove/guard any outlier. Note the
  `.onscroll`/IntersectionObserver scroll-reveal CSS is currently unused (dead) - leave it out
  of scope unless adopted.
- **Rationale**: Satisfies FR-004 accurately without re-doing solved work (a point the spec
  review explicitly corrected). Avoids chasing a non-existent scroll-reveal feature.
- **Alternatives considered**:
  - *Rewrite all transitions behind per-component media queries*: rejected - redundant given the
    global `*` rule already applies; would add noise without behavior change.

## D9. Contrast verification (not guesswork)

- **Decision**: Programmatically measure contrast for the flagged pairs - gold-on-light text
  (`--gold-700` and brighter golds), disabled `.ig-btn` at `opacity: .55`, risk/status badge
  fg/bg pairs (esp. MODERATE tan-on-gold), footer `--blue-300` on navy, and pricing fine print
  using opacity on muted text on navy. Fix any pair below 4.5:1 (normal text) / 3:1 (large
  text/UI) by adjusting the token value, not by ad-hoc per-use overrides. axe-core covers most;
  opacity-composited and gradient cases are checked manually with a contrast tool.
- **Rationale**: Satisfies FR-007/SC-008 by measurement (SC-008 explicitly forbids visual-only
  judgment). Fixing at the token level keeps consistency (FR-016/FR-017).
- **Alternatives considered**:
  - *Trust the design comments* (some tokens are annotated "AA"): rejected - SC-008 requires
    measured verification, including the opacity-composited cases axe cannot see.

## D10. Verification & regression strategy

- **Decision**: Three layers: (1) Karma/Jasmine unit specs asserting the new a11y
  attributes/behaviors (skip link present and focuses main; results region has the live role;
  invalid login fields expose `aria-invalid`/`aria-describedby`; shared state components render
  the right role); (2) the `tools/a11y/axe-audit.mjs` scan across the 8 journeys; (3) a manual
  keyboard + screen-reader + responsive (320/375/768/1024/1280/1440) + bilingual checklist in
  quickstart.md. Regression guard: confirm currency/money rendering and all translated strings
  are byte-for-byte unchanged (FR-023/SC-009) via existing search/results specs and a UK/EN
  switch pass.
- **Rationale**: Matches the spec's success criteria split (automated + authoritative manual)
  and Constitution Principle V/VI (parse/scan + multi-role review, not reading).
- **Alternatives considered**:
  - *Add Playwright E2E for keyboard journeys*: deferred - valuable but a new framework; the
    manual checklist plus unit specs meet the gate at minimal footprint for this pass.

## D11. In-flight form locking (FR-020)

- **Decision**: While a submit request is pending, disable the whole field group, not just the
  submit button. For Angular reactive forms, call `form.disable({emitEvent:false})` on submit
  and `form.enable()` on completion/error; for template-driven spots, wrap controls in a
  `fieldset [disabled]="submitting()"`. Keep the existing visible pending state on the button.
- **Rationale**: Satisfies FR-020/SC-007. The audit confirmed every form today disables only
  the submit button, leaving values editable mid-request - this is net-new behavior the spec
  explicitly flags, so it needs a design decision, not just "polish". `disable()` also removes
  the controls from the tab order while pending, which is the desired AT behavior.
- **Cautions**: `form.disable()` strips control values from `form.value` (use
  `form.getRawValue()` if the in-flight payload is read after disabling); re-enable in a
  `finally`/error path so a failed request does not leave the form permanently locked. SSR: no
  impact (submission is browser-only).
- **Alternatives considered**:
  - *Readonly inputs instead of disabled*: rejected - `readonly` does not apply to selects and
    keeps controls in the tab order; `disabled` is the consistent cross-control choice.
  - *Button-only disable (current)*: rejected - does not meet FR-020 (values stay editable).

## D12. Responsive reflow & indicator consistency (FR-010/011/017) - audit-and-confirm

- **Decision**: Treat horizontal-overflow prevention (FR-010), breakpoint reflow (FR-011), and
  status/risk/category indicator consistency (FR-017) primarily as **verification** tasks layered
  on the token work (D4/D5/D9), not as separate net-new design. Confirm via the responsive
  width sweep (320-1440px) and a grep that indicators all resolve from the shared badge/chip
  classes and token colors. Add breakpoints only where the sweep finds cramping (the audit
  flagged the ~560-900px range for two-column forms).
- **Rationale**: These requirements are satisfied by consistent tokens + the existing responsive
  grids; calling them out explicitly prevents the false assumption that token work alone proves
  them. Keeps footprint minimal (Principle I).

---

## Resolved unknowns summary

| # | Topic | Decision |
|---|-------|----------|
| D1 | Automated a11y scan | `@axe-core/puppeteer` script in `tools/a11y/`, dev-only |
| D2 | Skip link + route focus | Skip link to `#main-content`; SSR-safe route-focus service |
| D3 | Announce dynamic content | Polite live region + `aria-busy` on search results |
| D4 | Touch targets | 44px floor via shared control classes; hit area via padding |
| D5 | Design scale | Add spacing/type scales + reuse radius tokens; map ad-hoc values |
| D6 | Form error association | Port login/register to search form's `aria-invalid`/`describedby` standard |
| D7 | Empty/loading/error | Three shared standalone state components; refactor screens to adopt |
| D8 | Reduced motion | Audit-and-confirm global rule covers all transitions; ignore dead scroll-reveal |
| D9 | Contrast | Measure flagged pairs (incl. opacity/gradient); fix at token level |
| D10 | Verification | Unit specs + axe scan + manual keyboard/SR/responsive/bilingual checklist |
| D11 | In-flight form locking (FR-020) | Disable whole field group while pending; re-enable in finally; `getRawValue()` for payload |
| D12 | Responsive/indicator consistency (FR-010/011/017) | Audit-and-confirm via width sweep + token-sourced indicators; add breakpoints only where cramping found |

No unresolved `[NEEDS CLARIFICATION]` remain. Ready for Phase 1 design.
