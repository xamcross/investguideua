# Phase 1 Data Model: Frontend UI/UX Audit & Improvements

**Feature**: 007-ui-ux-improvements | **Date**: 2026-06-04

This feature introduces **no business/persisted entities** (FR-021, presentation-only). The
structured "model" here is the **design-system contract**: the token scales that consistency
requirements resolve against, and the screen x state inventory that drives the audit. These are
the artifacts implementation and review will check against.

---

## 1. Design-token scales (the consistency "schema")

Values are chosen to match what is already on screen (FR-022); this names a scale, it does not
restyle. All live in `:root` in `frontend/src/styles.css`; the existing `--ig-*` alias layer is
preserved.

### 1.1 Spacing scale

| Token | Value | Replaces ad-hoc values seen in audit |
|-------|-------|--------------------------------------|
| `--space-1` | 4px (.25rem) | `.35rem`-ish micro gaps |
| `--space-2` | 8px (.5rem) | `.5rem` gaps |
| `--space-3` | 12px (.75rem) | `.75rem` gaps |
| `--space-4` | 16px (1rem) | `1rem` gaps |
| `--space-5` | 24px (1.5rem) | `1.25rem`/`1.5rem` |
| `--space-6` | 32px (2rem) | `2rem` |
| `--space-7` | 48px (3rem) | section padding |
| `--space-8` | 64px (4rem) | large section padding |

**Rule (FR-016)**: new and refactored spacing MUST use a scale step; arbitrary `rem` gaps are
removed or mapped to the nearest step.

### 1.2 Type scale

| Token | Value | Use |
|-------|-------|-----|
| `--text-xs` | .75rem | hints, eyebrows, badges (mono) |
| `--text-sm` | .875rem | secondary/body-small, errors |
| `--text-base` | 1rem | body, control text |
| `--text-lg` | 1.125rem | lead text, h3-ish |
| heading clamps | existing `h1/h2/h3` clamps | unchanged display scale |

**Rule**: collapses the audit's `.82/.85/.88/.9/.92rem` sprawl onto `--text-sm`/`--text-base`.

### 1.3 Radius scale

| Token | Value | Use |
|-------|-------|-----|
| `--radius` | 14px (existing) | cards, large surfaces |
| `--radius-sm` | 9px (existing) | controls, alerts |
| `--radius-pill` | 999px | chips |

**Rule**: hardcoded `border-radius: 6px` (badges/chips) and `16px` (empty mark) are replaced by
the appropriate token; no raw radius literals remain.

### 1.4 Touch-target constant

| Token | Value | Use |
|-------|-------|-----|
| `--touch-min` | 44px | enforced min-height/width on every interactive control (FR-009) |

### 1.5 Validation rules for tokens

- Every interactive control's effective height >= `--touch-min` on touch viewports.
- Every color pair used for text/UI meets WCAG 2.2 AA (>=4.5:1 normal, >=3:1 large/UI),
  measured including opacity/gradient compositing (FR-007, D9).
- No raw spacing/radius/type literal that has a scale equivalent remains after refactor.

---

## 2. Screen x State inventory (the audit matrix)

For each data-driven screen, the state presentation MUST converge on the shared components
(FR-013/014/015). "Current" = audit finding; "Target" = shared contract.

| Screen | Empty (current -> target) | Loading (current -> target) | Error (current -> target) |
|--------|---------------------------|------------------------------|----------------------------|
| history | `.ig-empty` (rich) -> keep, via `<ig-empty-state>` | ad-hoc muted text -> `<ig-loading-state>` | `.ig-alert--error role=alert` -> `<ig-error-state>` |
| providers | `.ig-alert--info` -> `<ig-empty-state>` | ad-hoc muted text -> `<ig-loading-state>` | alert -> `<ig-error-state>` |
| tokens | `.ig-alert--info` -> `<ig-empty-state>` | ad-hoc muted text -> `<ig-loading-state>` | alert -> `<ig-error-state>` |
| articles-index | plain `<p>` -> `<ig-empty-state>` | n/a (static) | n/a |
| search results | `role=status` "no options" -> `<ig-empty-state>` | none -> `aria-busy` region | inline -> `<ig-error-state>` |
| account | n/a | "loading" text -> `<ig-loading-state>` | alert **missing role** -> `<ig-error-state>` (adds role=alert) |

---

## 3. Component / behavior inventory (new + modified)

### New (presentation-only, standalone)

| Artifact | Type | Responsibility | ARIA contract |
|----------|------|----------------|----------------|
| `features/shared/empty-state.component.ts` | standalone component | icon/mark + message + optional CTA (content-projected) | within page main; heading + descriptive text |
| `features/shared/loading-state.component.ts` | standalone component | consistent loading indicator | `role="status"`, reduced-motion-safe spinner |
| `features/shared/error-state.component.ts` | standalone component | recoverable error message + optional retry | `role="alert"` |
| `core/a11y/route-focus.service.ts` | injectable service | move focus to `#main-content` on `NavigationEnd` | SSR-guarded; no DOM on server |
| `tools/a11y/axe-audit.mjs` | Node ESM dev script | launch headless Chrome, run axe over the **public** journeys, exit non-zero on violations | n/a (tooling) |
| `package.json` | config | add `"a11y:audit": "node tools/a11y/axe-audit.mjs"` script + CI step (mirrors `seo:audit`) | n/a |

### Modified (attributes/markup/styles only; no logic change)

| Artifact | Change |
|----------|--------|
| `styles.css` | token scales, `--touch-min`, focus-clip fixes, shared-state utility styling, radius/spacing/type mapping, contrast token fixes |
| `app.component.ts` | skip link + `#main-content tabindex=-1`; nav/lang-toggle 44px; mobile-menu dismiss/focus behavior |
| `core/errors/notification-host.component.ts` | toast dismiss/ref controls to 44px |
| `auth/login.component.ts` | per-field validation + `aria-invalid`/`aria-describedby`; required indicators |
| `auth/register.component.ts` | link inline errors via `aria-describedby`; `aria-invalid` |
| `search/search.component.ts` | results live region + `aria-busy`; adopt shared states |
| `providers/`, `payments/tokens`, `articles/articles-index`, `history/`, `account/` | adopt shared empty/loading/error components; account error gets `role=alert`; history focus-ring clip fix |

---

## 4. Non-goals (explicit, from spec scope guardrails)

- No backend, API, data-model, money/token, or LLM changes (FR-021).
- No redesign or brand/color/type replacement - scales codify existing values (FR-022).
- No new pages or product flows; no changes to translated string *content* (FR-023).
- The unused `.onscroll` scroll-reveal mechanism is not adopted in this feature (D8).
