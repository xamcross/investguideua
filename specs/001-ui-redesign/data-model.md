# Phase 1 Data Model: InvestGuideUA UI/UX Redesign

This is a presentation-layer redesign. **It introduces no new persisted entities and changes no
existing data shapes, request/response contracts, or money math.** The "model" here is the set of
*display concepts* the UI renders, the design-token vocabulary, and the i18n key inventory. All
underlying data types (`investment.models`, `provider.models`, `payment.models`, `auth.models`)
remain exactly as they are.

## Display concepts (no schema change)

### Investment option ("instrument card")
- **Source**: existing search result item (from `investment.models`).
- **Rendered fields (display only)**: provider/instrument identity; localized category label
  (`category.<CATEGORY>`); localized risk label (`risk.<RISK>`) with `[attr.data-risk]` keeping the
  **raw enum** to drive color; expected-return figure (`min`-`max` % + `common.perYear`); key facts
  `<dl>` (return, currency via `igCurrency`, min amount via unchanged `formatMinorUnits`, liquidity
  with `common.dash` fallback); optional rationale; official-source link (`target="_blank"
  rel="noopener noreferrer"`); always-on disclaimer + optional currency-risk disclaimer.
- **Invariant**: numeric values and `[attr.data-risk]` raw enum are unchanged; only labels/layout
  change. The shared `ig-results` renderer must render identically on Search and History-detail.

### Token pack
- **Source**: existing `TokenPack` (no `featured` field; unchanged).
- **Display-only treatment**: in a dark "pack wall"; when `packs().length === 3`, the middle pack
  (`$index === 1`) is highlighted as recommended via a gold ribbon + gold CTA. Derived in the
  template only - **no model field, no logic change**.
- **Invariant**: `price()`/`perToken()` minor-unit math and `buy()` behavior unchanged.

### History entry / status chip
- **Source**: existing history record + `statusKey()`.
- **Display-only treatment**: a `.ig-chip [attr.data-status]` mapping completed -> green
  (`--risk-low`), pending -> amber (`--risk-mod`), failed -> red (`--risk-high`), with a neutral
  fallback for unknown status; the chip always carries translated text.
- **Invariant**: `statusKey()`, `PAGE_SIZE`, `go()`, `[disabled]` paging math, and
  `amount/100 | number:'1.2-2'` formatting unchanged.

### Currency label
- **Source**: existing currency code (`SearchCurrency | string`).
- **Display-only treatment**: `igCurrency` maps UAH -> `грн`(uk)/`UAH`(en); USD -> `USD`. Carries no
  amount and touches no math; impure so it re-renders on runtime language switch.

## Design-token vocabulary (canonical)

Defined once in `:root` of `frontend/src/styles.css`. Full values and the alias table are in
[contracts/design-tokens.contract.md](contracts/design-tokens.contract.md). Categories:

- **Brand/identity**: `--navy-900..700`, `--blue-600/500/300`, `--gold-700/600/500/300/100`.
- **Neutrals (warm paper)**: `--paper`, `--surface`, `--surface-2`, `--ink`, `--muted`, `--line`,
  `--line-2`.
- **Status/risk pairs**: `--risk-low/mod/high-bg|fg`, `--success-bg|fg`, `--danger-bg|fg`,
  `--info-bg|fg`.
- **Typography**: `--font-display`, `--font-ui`, `--font-mono`.
- **Shape/depth/motion**: `--radius`, `--radius-sm`, `--shadow-sm/md/lg`, `--maxw` (1120px),
  `--ease`.
- **Alias layer**: every legacy `--ig-*` re-points onto the new ramp (back-compat; canonical names
  preferred in new code).

## Global CSS class vocabulary

The shared, reusable classes consumed by every component. Contract in
[contracts/global-classes.contract.md](contracts/global-classes.contract.md). Families: `.ig-card`,
`.ig-form`/`.ig-field*`, `.ig-input`/`.ig-select`/`.ig-textarea`, `.ig-btn` (+`--primary`/`--ghost`/
`--gold`/`--lg`/`--nav`), `.ig-error`/`.ig-muted`/`.ig-hint`, `.ig-kicker`/`.ig-eyebrow`/
`.ig-display`, `.ig-alert*`, `.ig-badge*`, `.ig-chip`, `.ig-sr-only`, `.ig-page-head`, `.ig-empty*`,
`.ig-options`/`.ig-opt*`/`.ig-fact*`/`.ig-disclaimer*`, motion `.reveal`/`.d1..d5`/`.onscroll`.

## i18n key inventory

Additive keys for both `uk.json` and `en.json`, kept identical, plus the single `notFound.title`
copy change. Full enumeration with both locales in
[contracts/i18n-keys.contract.md](contracts/i18n-keys.contract.md).

## Explicitly unchanged

`money.util` (`toMinorUnits`, `formatMinorUnits`), all validators (`amount` min 0.01, `goals`
maxlength 280, `PASSWORD_PATTERN`), services, guards, the auth interceptor, polling/backoff, error
mapping, routing tables, `routerLink`/`routerLinkActive` targets, `LanguageService`, `PluralPipe`,
and all existing translation keys (aside from the one listed copy change).
