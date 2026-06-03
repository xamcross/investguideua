# Quickstart: InvestGuideUA UI/UX Redesign

How to build, verify, and phase the redesign. Authoritative implementation reference:
[docs/UI-REDESIGN-SPEC.md](../../docs/UI-REDESIGN-SPEC.md).

## Prerequisites

- Node 24 / npm 11 (verified available in this environment).
- Working directory: `frontend/`.
- No backend, DB, or Docker needed - this is a frontend-only restyle.

## Build & verify (the authoritative gate)

```powershell
cd frontend
npm ci          # if dependencies not yet installed
npm run build   # production build = parse + anyComponentStyle budget gate
```

Pass criteria:
- Build succeeds with **no `anyComponentStyle` warning (>4 kb) or error (>8 kb)** for any component.
- The stylesheet parses (no CSS error).
- Report explicitly whether the build was actually run or verification was static-only (never imply
  a build passed when it was only read).

Manual checks after build:
- Toggle UK <-> EN at runtime; confirm every restyled surface resolves in both locales.
- Keyboard-tab the app; confirm a visible `:focus-visible` ring on every interactive element.
- Enable OS "reduce motion"; confirm animations suppressed and no content stuck invisible.
- Narrow to phone width; confirm hamburger + UA/EN visible and the token balance reachable (never
  `display:none`).
- Spot-check contrast on body text, status pairs, and small gold text (`--gold-700`).

## Build phases (independently buildable; later phases depend only on Phase A)

- **Phase A - Foundation (blocking).** `index.html` fonts; full `:root` tokens + alias layer; global
  atmosphere + `.ig-container` width; headings; all global `.ig-*` component classes; motion +
  reduced-motion. Add the new `igCurrency` pipe file. Verify: every existing component still
  compiles/renders via the alias layer with **no component edits**; build under budget.
- **Phase B - App shell / nav / notifications.** Sticky topbar, brand, nav, balance pill, lang
  toggle, the responsive mobile menu (defect fix), toast restyle; reconcile `.ig-topbar__inner` to
  `--maxw`; add `nav.menuOpen`/`nav.menuClose`.
- **Phase C - Landing + Search + Results.** Editorial hero + trust ledger; instrument-card form +
  results; `igCurrency` wired; category/risk/currency localization; `results.component.ts` `styles[]`
  emptied. Add Phase-C i18n keys.
- **Phase D - Tokens / Payments / Auth.** Dark token section + recommended ribbon; payment-result
  states + spinner; centered auth credential cards. Add Phase-D i18n keys.
- **Phase E - History / Account / Providers / Not-found / Placeholder.** Ledger history + chips +
  pager + empty state + detail chrome; account dossier; provider instrument cards; on-brand empty
  states. Add Phase-E i18n keys; apply the `notFound.title` copy change. `results.component.ts` is
  not modified in this phase.

## After each phase (Definition of Done)

1. Run the production build; confirm no `anyComponentStyle` warning/error and the stylesheet parses.
2. Toggle UK/EN to confirm new copy resolves in both locales (identical key sets).
3. Accessibility spot-check (focus, contrast, reduced motion, mobile nav reachability).
4. Run the **mandatory two-role sub-agent review** (FE lead + QA/accessibility) including an actual
   build/parse; apply or report findings before closing the phase.

## Encoding note (Constitution V)

Only `.html`/`.css`/`.ts`/`.json` are edited - all UTF-8, Cyrillic allowed. **No `.ps1`/`.cmd`/
`.bat` are touched**, so the ASCII-only Windows-script scan has nothing to flag. Do not add a BOM to
`index.html`. New i18n strings use ASCII punctuation (`-`, `...`).
