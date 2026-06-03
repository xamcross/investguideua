# Quickstart: Landing marketing sections

Build, verify, and sequencing for the footer + landing pricing-preview + sample-results sections.

## Prerequisites

- Node 24 / npm 11 (available).
- Working directory: `frontend/`.
- **Depends on `001-ui-redesign`**: this feature reuses that design system (`.ig-opt` instrument
  cards, dark-section/footer tokens, motion `.reveal`, accessibility baseline). Implement on top of
  001 — merge 001 first, then rebase this branch onto it (or branch the implementation from 001).
  This spec branch was cut from `main` only to keep the spec diff independent.

## Build & verify (authoritative gate)

```powershell
cd frontend
npm ci          # if needed
npm run build   # production build = parse + anyComponentStyle budget gate
```

Pass criteria:
- Build succeeds with **no `anyComponentStyle` warning (>4 kb) or error (>8 kb)** for any component
  (footer + landing stay lean; shared CSS is global).
- Stylesheet parses. State explicitly whether the build was actually run (not static-only).

Manual checks:
- Landing (signed out): the sample-results and pricing-preview sections render in the redesign style;
  each carries its "example / not advice" framing; the footer shows on every page.
- Toggle UK <-> EN: all new footer/sample/pricing copy resolves in both locales.
- Keyboard-tab the footer + CTAs: visible focus ring on every link; AA contrast on the dark footer.
- Reduce motion: entrance animations suppressed, nothing hidden.
- Phone width: all three sections collapse to a single readable column; nothing clipped; footer does
  not overlap toasts.
- Confirm `/tokens/packs` is still authenticated (no public packs read added) and no token is spent
  by the marketing sections.

## Build phases (suggested)

- **Phase A - Footer.** New `ig-footer`, render in shell, add `/terms` + `/privacy` placeholder
  routes, footer i18n keys, global `.ig-section`/footer styles.
- **Phase B - Sample-results section.** Landing section reusing global `.ig-opt`; hard-coded sample
  consts; sample i18n keys; example disclaimer.
- **Phase C - Pricing-preview section.** Landing `.ig-section--dark` packs; middle highlighted; CTAs
  to `/register`; pricing i18n keys; example markers + fineprint.

After each phase: run the production build (no budget regression), toggle UK/EN, and run the
mandatory two-role sub-agent review (FE lead + QA/accessibility) including an actual build.

## Encoding note (Constitution V)

Only `.ts/.css/.json/.html` are edited (UTF-8, Cyrillic allowed). **No `.ps1/.cmd/.bat`** touched, so
the ASCII-only Windows-script scan has nothing to flag. New i18n strings use ASCII punctuation.
