# Shared State-Component Contract (UI Contract)

**Feature**: 007-ui-ux-improvements | **Date**: 2026-06-04

Contracts for the three shared presentational components that unify empty/loading/error states
(FR-013/014/015) and the screens that MUST adopt them. All are standalone Angular components,
SSR-safe, bilingual via content projection / `@Input`, and carry no business logic.

---

## `<ig-empty-state>` (FR-013)

- **Inputs**: `code?` (small mono label), `heading` (string), `message?` (string);
  **content projection**: optional CTA actions slot.
- **Renders**: the existing `.ig-empty` structure (mark + heading + message + `.ig-empty__actions`).
- **A11y**: lives inside page `main`; heading is a real heading element; no `role` needed beyond
  semantics. Reduced-motion safe (the mark is static).
- **Adopted by**: history (empty), providers (empty), tokens (empty), articles-index (empty),
  search results (no-options branch).

## `<ig-loading-state>` (FR-014)

- **Inputs**: `label` (string, e.g. translated "Loading...").
- **Renders**: one consistent indicator (spinner or skeleton) + visible label.
- **A11y**: `role="status"` with the label as accessible text; spinner animation is suppressed
  under `prefers-reduced-motion` (falls back to static label).
- **Adopted by**: history, providers, tokens, account (profile fetch). Search uses `aria-busy`
  on the results region rather than swapping in this component (per D3).

## `<ig-error-state>` (FR-015)

- **Inputs**: `message` (string); optional `retry` output/event for a recoverable action.
- **Renders**: the `.ig-alert--error` visual with an optional retry control (>=44px).
- **A11y**: `role="alert"` so the error is announced; retry control is keyboard-focusable with a
  visible ring.
- **Adopted by**: history, providers, tokens, account (adds the currently-missing `role="alert"`),
  search results (request failure).

---

## State-presentation invariants (acceptance)

1. No data-driven screen renders an empty, loading, or error state with bespoke ad-hoc markup;
   each uses the corresponding shared component (verified by code review + grep for legacy
   `.ig-alert--info` empty-state usage returning none).
2. Each component preserves the existing visual language (FR-022) - it wraps current classes,
   it does not restyle them.
3. All text is supplied by the caller via translated strings (FR-023); the components hardcode
   no user-facing copy.
4. Reduced-motion: the loading indicator shows no animation when reduced motion is requested
   (FR-004).
5. Touch: any interactive element inside these components (e.g., empty-state CTA, error retry)
   meets the 44px target (FR-009).

## Verification hooks

- **Unit (Karma/Jasmine)**: each component spec asserts its role/attributes and that projected
  content / inputs render; an adoption spec per refactored screen asserts the shared component
  is present in the relevant state.
- **Scan**: `tools/a11y/axe-audit.mjs` exercises the empty/loading/error paths where reachable.
- **Manual**: quickstart checklist walks each screen through no-data, slow-network, and
  failed-request states.
