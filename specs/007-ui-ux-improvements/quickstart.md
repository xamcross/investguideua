# Quickstart: Verifying the UI/UX Improvements

**Feature**: 007-ui-ux-improvements | **Date**: 2026-06-04

How to build, run the automated accessibility scan, and perform the authoritative manual
verification for this presentation-layer feature. All commands run from `frontend/` on Windows
(PowerShell). Money/token behavior and translated copy must be unchanged - regression steps
included.

---

## 1. Prerequisites

```powershell
cd frontend
npm install            # installs deps incl. dev-only @axe-core/puppeteer (once added)
```

## 2. Build & serve

```powershell
npm start              # ng serve -> http://localhost:4200 (dev)
# or a production-like SSR build:
npm run build
```

## 3. Automated accessibility scan (SC-001 automated layer)

Start the dev server in one terminal, then run the scan in another:

```powershell
npm start                     # terminal 1: serves http://localhost:4200 (CSR DOM)
npm run a11y:audit            # terminal 2: == node tools/a11y/axe-audit.mjs
```

- The script launches headless Chrome (reusing puppeteer's bundled binary), polls the URL for
  readiness, navigates to each **public** journey, runs axe-core WCAG 2.1/2.2 A+AA rules, prints
  violations, and **exits non-zero on any** (so CI can gate on it, like `seo:audit`).
- **Scope**: public, no-backend routes only - landing, search form, login, register, articles
  (index + a sample). Authenticated/data-backed screens (account, history, populated search
  results) are NOT scanned headlessly (they would hit a redirect/error state and falsely pass);
  verify those in the manual pass (section 5).
- `npm start` serves the client-rendered DOM, which is correct for axe's DOM-level rules. SSR-
  specific focus behavior is covered by unit specs + the manual pass, not this scan.
- **Pass**: zero violations reported.
- Reminder: axe catches only part of WCAG; section 5 (manual) is the authoritative bar.

## 4. Unit tests (component a11y attributes/behavior)

```powershell
npm run test:ci        # ng test --watch=false --browsers=ChromeHeadlessCI
```

- **Pass**: all specs green, including the new specs for the skip link, route-focus service,
  search results live region, login/register field error association, and the shared
  empty/loading/error components.

## 5. Manual keyboard + screen-reader pass (SC-001 authoritative, SC-002)

Walk each of the 8 journeys:

- [ ] First Tab reveals a visible "Skip to main content" link; activating it focuses main.
- [ ] After every in-app navigation, focus is on the main landmark/heading (not the old link).
- [ ] Every interactive control shows a clearly visible, unclipped focus ring.
- [ ] In a form, submitting/leaving an invalid field announces a specific error; the field is
      marked invalid. Login and register behave like search.
- [ ] Required fields are indicated before submit.
- [ ] During a slow submit, the form's fields are locked (cannot edit or resubmit) and re-enable
      after the request resolves or fails.
- [ ] After a search, results are announced (screen reader) without hunting; the region is busy
      while loading.
- [ ] The mobile menu opens, a link closes it, and focus is not trapped when leaving it.
- [ ] No keyboard trap anywhere; tab order is logical.

Screen readers: NVDA or Narrator (Windows); VoiceOver (macOS) if available.

## 6. Reduced motion (SC-005, FR-004)

- [ ] Enable OS "reduce motion" (or DevTools Rendering -> "Emulate prefers-reduced-motion:
      reduce").
- [ ] Reload each page: no reveal/scale/slide animation plays; all content is visible.

## 7. Responsive / touch (SC-003, SC-004)

Test at 320, 375, 768, 1024, 1280, 1440 px (DevTools device toolbar):

- [ ] No horizontal scrolling or clipped text/figures at any width (incl. long provider names
      and large money figures at 320px).
- [ ] Every interactive control is >= 44x44 px (measure the language toggle, nav links, toast
      dismiss/ref, form controls, buttons).
- [ ] Multi-column forms/grids reflow without cramping in the 560-900px range.

## 8. Contrast (SC-008, FR-007)

- [ ] Measure the flagged pairs with a contrast tool: disabled button (opacity .55),
      gold-on-light text, MODERATE risk badge, footer blue-on-navy, pricing fine print.
- [ ] All meet >=4.5:1 (normal) / >=3:1 (large/UI). Fixes are at the token level.

## 9. Consistency (FR-013/014/015/016/017)

- [ ] Every data-driven screen (history, providers, tokens, articles, search results, account)
      shows empty/loading/error via the shared components - no leftover ad-hoc `.ig-alert--info`
      empty states or unroled error alerts.
- [ ] Spacing/type/radius use the named scale; no stray hardcoded `6px`/`16px` radii remain.

## 10. Regression: money + bilingual (SC-009, FR-023)

- [ ] Switch UK <-> EN on every screen: no layout breakage from text-length shifts; all strings
      present in both languages.
- [ ] Currency/money rendering (igCurrency pipe, return figures, token packs) is identical to
      before - confirm against `git diff` that no money/format logic changed and the existing
      search/results specs still pass.

## 11. Constitution verification gates (Principle V/VI)

- [ ] Non-ASCII scan of any executed Windows script returns clean (none added by this feature;
      the audit helper is `.mjs`). Confirm no `.ps1`/`.cmd`/`.bat` was introduced.
- [ ] `npm run build` and `npm run test:ci` pass (parse/compile, not reading).
- [ ] At least two role sub-agents (front-end lead + QA/accessibility, DevOps for the script)
      have reviewed, including a build/scan step; findings applied or reported.

**Definition of done**: sections 3-10 pass, the constitution gates in 11 are satisfied, and the
acceptance criteria in `contracts/accessibility-contract.md` and
`contracts/component-state-contract.md` are met.
