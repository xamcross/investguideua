# Accessibility Contract (UI Contract)

**Feature**: 007-ui-ux-improvements | **Date**: 2026-06-04

This is the UI contract for the presentation feature: testable behavioral guarantees the
implementation MUST satisfy, mapped to functional requirements (FR) and success criteria (SC).
Each item is phrased so a unit spec, the axe scan, or the manual checklist can verify it.

---

## C-A11Y-1: Skip to content (FR-001, SC-002)

- **Given** any page, **the** first Tab stop is a "Skip to main content" link that is visible
  on focus and visually hidden otherwise.
- Activating it moves focus to `<main id="main-content" tabindex="-1">`.
- **Verify**: unit spec asserts the link exists, precedes the header in DOM, and targets the
  main landmark; manual keyboard pass confirms focus lands on main.

## C-A11Y-2: SPA route focus (FR-001, SC-002)

- **Given** a client-side navigation completes, focus moves to the main landmark (or its first
  heading), not left on the activated link.
- **Verify**: unit spec on the route-focus service (mock `NavigationEnd`); manual keyboard pass
  across routes. SSR: service is a no-op on the server (no `document` access).

## C-A11Y-3: Form error association (FR-002, FR-018, FR-019)

- For every form control: when invalid-and-touched, the control has `aria-invalid="true"` and
  `aria-describedby` referencing its error element's id; the error element shows a specific
  message.
- Required fields show a visible required indicator with an accessible name.
- **Login** specifically exposes per-field errors (currently absent); **register** links its
  existing inline errors. Search remains the reference standard.
- Format errors surface on blur; required errors surface on blur or submit, never only after a
  failed submit.
- **Verify**: unit specs per form (set invalid -> assert attributes + message); manual SR pass.

## C-A11Y-3b: In-flight form locking (FR-020)

- **Given** a form submit is in flight, the whole field group is disabled (not just the submit
  button), values cannot be edited or resubmitted, and the disabled controls leave the tab
  order; the form re-enables on success or error (never permanently locked).
- **Verify**: unit spec asserts controls are disabled while `submitting()` is true and re-enabled
  after; manual pass attempts to edit/resubmit during a slow request.

## C-A11Y-4: Status messages for dynamic content (FR-003)

- The populated search-results region is a polite live region
  (`role="status" aria-live="polite"`) that announces a result summary on load; it carries
  `aria-busy="true"` while fetching.
- Toasts continue to announce politely; async status changes use the same mechanism.
- **Verify**: unit spec asserts the region role/attributes and that the summary text updates;
  manual SR pass confirms announcement without focus hunting.

## C-A11Y-5: Visible, unclipped focus indicator (FR-005, SC-002)

- Every interactive element shows the global focus-visible ring; no container clips or obscures
  it (fix `overflow:hidden`/transform cases such as the history row).
- **Verify**: manual keyboard pass over each journey; visual check that the ring is fully shown.

## C-A11Y-6: Reduced motion honored (FR-004, SC-005)

- With OS reduced-motion on, no non-essential animation/transition plays anywhere; revealed
  content is visible without animation.
- **Verify**: DevTools "prefers-reduced-motion: reduce" emulation + manual pass; confirm the
  global rule covers every component transition (audit-and-confirm, D8).

## C-A11Y-7: Contrast (FR-007, SC-008)

- All text and meaningful UI elements meet WCAG 2.2 AA (>=4.5:1 normal, >=3:1 large/UI),
  including opacity-composited and gradient cases (disabled buttons, gold-on-light, MODERATE
  badge, footer blue-on-navy, pricing fine print).
- **Verify**: axe scan for detectable cases + manual contrast-tool measurement of the
  opacity/gradient pairs; fixes applied at the token level.

## C-A11Y-8: Heading/landmark structure (FR-008)

- Each page has exactly one `h1`, a logical non-skipping heading order, and correct landmarks
  (`header`/`nav`/`main`/`footer`); the landing page's heading nesting is corrected.
- **Verify**: axe scan (heading-order, landmark rules) + manual outline review.

## C-A11Y-9: Touch targets (FR-009, SC-003)

- Every interactive control (buttons, control-links, inputs, selects, language toggle, menu
  icon, toast dismiss/ref) has >= 44x44 CSS px tappable area.
- **Verify**: axe target-size rule where applicable + manual measurement at mobile widths.

## C-A11Y-10: Mobile nav dismissal (FR-006, FR-012)

- Opening a nav link closes the menu and navigates; when focus leaves the open menu it does not
  trap focus or obscure content; collapsed links are not focusable.
- **Verify**: unit spec on menu-open state + manual keyboard pass.

---

## Pass criteria (acceptance)

- **Automated**: `tools/a11y/axe-audit.mjs` reports zero violations across the **public,
  unauthenticated** routes that render without a backend session - landing, search form, login,
  register, articles (index + sample) - SC-001 automated layer. Authenticated/data-backed
  screens (account, history, populated search results) are intentionally excluded from the
  headless scan to avoid false passes on redirect/error states and are covered by the manual
  pass instead.
- **Manual (authoritative)**: the keyboard + screen-reader checklist in quickstart.md passes
  with no A/AA blocker across all 8 journeys (including the authed/data screens) - SC-001 manual
  bar, SC-002.
- **Responsive**: no horizontal scroll / clipping 320-1440px - SC-004.
- **Regression**: currency/money rendering and all translated strings unchanged; full UK/EN
  switch shows no layout breakage - SC-009, FR-023.
