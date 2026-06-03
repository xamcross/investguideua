# Phase 0 Research: InvestGuideUA UI/UX Redesign

All decisions below are grounded in the authoritative source doc
[docs/UI-REDESIGN-SPEC.md](../../docs/UI-REDESIGN-SPEC.md) and the project constitution. There were
no open `NEEDS CLARIFICATION` items in the spec; this phase records the design/engineering decisions
the redesign depends on and the alternatives weighed.

## D1. Style-budget strategy (shared CSS placement)

- **Decision**: Put every shared rule (tokens, fonts wiring, `.ig-btn*`, `.ig-card`,
  `.ig-field*`/`.ig-input*`, `.ig-alert*`, `.ig-badge*`, `.ig-chip`, `.ig-kicker`/`.ig-eyebrow`/
  `.ig-display`, `.ig-empty*`, `.ig-page-head`, `.ig-sr-only`, motion utilities, focus ring) in the
  single global `frontend/src/styles.css`. Component `styles[]` carry only genuinely component-local
  structure.
- **Rationale**: Angular's production `anyComponentStyle` budget is 4 kb warn / 8 kb error per
  component. The only reliable way to restyle 13 screens richly without tripping the budget is to
  centralize shared CSS globally and keep each component lean. Confirmed in `frontend/angular.json`
  (`anyComponentStyle` 4kb/8kb) and `styles.css` is currently only 79 lines, leaving ample room.
- **Alternatives considered**: Per-component duplication of shared rules (rejected: blows the budget
  and produces drift between old lemon/`#eee` values and the new palette); a CSS preprocessor or
  utility framework (rejected: violates "no new dependencies").

## D2. Back-compat token alias layer

- **Decision**: Define the canonical bare-ramp tokens (`--navy-*`, `--blue-*`, `--gold-*`, `--paper`,
  `--surface*`, `--ink`, `--muted`, `--line*`, `--risk-*`, `--font-*`, `--radius*`, `--shadow-*`,
  `--ease`, `--maxw`) and re-point every existing `--ig-*` name onto the new ramp as an alias layer.
- **Rationale**: Lets Phase A land the entire palette with **zero component edits** - every existing
  component keeps compiling and immediately renders in the new colors. Later phases migrate
  intentionally to canonical names. This de-risks the foundation phase and makes it independently
  verifiable.
- **Alternatives considered**: Renaming all component CSS to canonical tokens in one pass (rejected:
  large blast radius, not independently buildable, conflicts with the phased plan).

## D3. Web fonts loading

- **Decision**: Load Playfair Display, Manrope, and JetBrains Mono via a single combined Google
  Fonts `css2` `<link>` plus two `preconnect` lines in `index.html`, with `display=swap`. Cyrillic
  is served automatically via the returned `unicode-range`.
- **Rationale**: One request keeps it cheap; `display=swap` prevents invisible-text flash (FR-002);
  preconnects fire the connection early. `<link>` (not an npm font package) honors "no new
  dependencies." `index.html` is read by the Angular/Linux build chain so Cyrillic is safe and **no
  BOM** must be added.
- **Alternatives considered**: Self-hosting font files (rejected: more build wiring, no clear MVP
  benefit); `@fontsource` npm packages (rejected: new dependency).

## D4. Display-only currency label pipe

- **Decision**: Add one new pure-by-intent (impure for runtime language switch) pipe
  `core/i18n/currency-label.pipe.ts` (`igCurrency`) that maps an ISO code to a localized display
  word (UAH -> `грн`(uk)/`UAH`(en); USD -> `USD`) via `translate.instant`. It performs no math.
- **Rationale**: The redesign needs a localized currency *word* in several places, but money
  formatting and minor-unit math must stay untouched (Constitution IV, FR-015/FR-022). A separate
  display-only pipe cleanly isolates the label from `money.util`, which stays read-only.
- **Alternatives considered**: Extending `formatMinorUnits` to localize the suffix (rejected: changes
  money utility output, violates "do not change math"); inline template `@switch` on currency
  (rejected: duplicated, not reusable, harder to keep in i18n).

## D5. Responsive mobile navigation (the defect fix)

- **Decision**: Add a disclosure `<button class="ig-nav__toggle">` (real `<button>`,
  `aria-expanded`/`aria-controls`, translated `aria-label`, >=44x44 touch target) plus a
  `menuOpen` signal in `app.component.ts`. Below 760px, collapse nav links into a dropdown panel
  while keeping the hamburger and UA/EN toggle visible in the bar and revealing **every** item -
  including the token balance - as full-width rows. The old `<=560px` `display:none` grid block is
  removed.
- **Rationale**: The current concept hides nav and the token balance on narrow screens with no
  fallback - a genuine accessibility/usability defect (FR-020, SC-003). A signal + CSS panel is the
  minimal standalone/OnPush-compatible fix with no new dependency. Token balance must never be
  `display:none`.
- **Alternatives considered**: A CDK overlay/menu (rejected: heavier, and the simple panel suffices);
  leaving the concept's hide rules (rejected: forbidden by spec, fails accessibility gate).

## D6. Motion + reduced-motion

- **Decision**: CSS-only load reveal (`.reveal` + `.d1..d5`) sharing a single `ig-rise` keyframe,
  with a hard `@media (prefers-reduced-motion: reduce)` off-switch that also forces revealed content
  visible. No `IntersectionObserver` is needed for MVP scope (all sections use load reveal, not
  scroll reveal).
- **Rationale**: Keeps motion subtle and fully optional (FR-019, SC-006) with zero JS and no risk of
  content stuck invisible. Normalizes the draft's `.ig-reveal--dN` naming to canonical `.reveal .dN`.
- **Alternatives considered**: Angular animations module (rejected: heavier, unnecessary for entrance
  fades); scroll-triggered `IntersectionObserver` reveals (rejected: out of MVP scope, adds a
  JS-disabled-fallback burden).

## D7. Accessibility approach (contrast, focus, never-color-alone)

- **Decision**: Small gold text uses the darker `--gold-700` only; `--gold-500/600` are restricted
  to fills/rules/icons/large display figures. Global `:focus-visible` ring on every interactive
  element. Risk/status always render a translated text label in addition to a color pair. Decorative
  marks are `aria-hidden` + `focusable="false"`.
- **Rationale**: Directly satisfies FR-008/FR-017/FR-018 and SC-004/005/007; a financial product
  must meet WCAG AA. Centralizing focus and contrast rules globally keeps them consistent and
  testable.
- **Alternatives considered**: Per-component focus styling (rejected: inconsistent, easy to miss);
  color-only risk indication (rejected: fails never-color-alone).

## D8. i18n parity strategy

- **Decision**: All new copy added as additive keys to both `uk.json` and `en.json`, kept
  key-for-key identical; only two intentional value changes (`notFound.title` drops "404 - "). New
  strings use ASCII punctuation to match existing files. The full new-key set is enumerated in
  `contracts/i18n-keys.contract.md`.
- **Rationale**: Satisfies FR-014/FR-016 and SC-002; identical key sets prevent runtime gaps when
  toggling locales. JSON is UTF-8 so Cyrillic values are fine.
- **Alternatives considered**: Hardcoding new labels (rejected: violates i18n rule); renaming
  existing keys (rejected: forbidden, breaks other references).

## D9. Verification method

- **Decision**: Treat the production build (`npm run build`) as the authoritative parse + style-budget
  gate; supplement with a manual UK/EN runtime toggle check and accessibility spot-checks; then run
  the mandatory two-role sub-agent review (FE lead + QA/accessibility) including an actual build.
  Node 24 / npm 11 are available in this environment, so the build can be run for real (not
  static-only).
- **Rationale**: Constitution V/VI require parse/compile verification, not reading. The build is the
  only reliable detector of a budget regression or a CSS parse error. No `.ps1/.cmd/.bat` are touched,
  so the non-ASCII Windows-script scan has nothing to flag.
- **Alternatives considered**: Visual review only (rejected: cannot catch budget/parse/encoding
  defects, explicitly disallowed by the constitution).

## Open questions

None. The source doc resolved every prior contradiction (canonical token names, page width 1120px,
`.reveal` naming) and the spec carried no `NEEDS CLARIFICATION` markers.
