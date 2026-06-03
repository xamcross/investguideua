# UI Contract: Global Component Classes (`frontend/src/styles.css`)

Shared, reusable classes that MUST be defined once globally and consumed by components. Components
MUST NOT redeclare these (Constitution-adjacent budget rule; source Sections 3.3-3.5, 4.1).

## Class families (must exist globally)

| Family | Classes | Purpose |
|---|---|---|
| Card | `.ig-card` | elevated surface container |
| Forms | `.ig-form`, `.ig-field` (+ `input/select/textarea`) | labeled control rows |
| Input atoms | `.ig-input`, `.ig-select`, `.ig-textarea`, `.ig-input--figure` | reusable controls (Search) |
| Buttons | `.ig-btn`, `.ig-btn--primary`, `.ig-btn--ghost`, `.ig-btn--gold`, `.ig-btn--lg` | actions |
| Inline text | `.ig-error`, `.ig-muted`, `.ig-hint` | form/help text |
| Editorial | `.ig-kicker`, `.ig-eyebrow`, `.ig-display` (+`--sm`) | mono eyebrow + serif title |
| Alerts | `.ig-alert`, `.ig-alert--error/--success/--info` | status banners |
| Badges | `.ig-badge`, `--cat`, `--risk` (`[data-risk]`), `--ok`, `--warn` | category/risk/email status |
| Chips | `.ig-chip` (`[data-status]`) | history status pills |
| A11y | `.ig-sr-only` | screen-reader-only text |
| Page head | `.ig-page-head` | section header wrapper |
| Empty state | `.ig-empty`, `__mark`, `__code`, `__actions` | not-found/placeholder/empty |
| Results | `.ig-options`, `.ig-opt*`, `.ig-fact*`, `.ig-disclaimer*` | instrument cards |
| Motion | `.reveal`, `.d1..d5`, `.onscroll`, `.in`, `.s1..s3`, `@keyframes ig-rise` | entrance |
| Focus/atmosphere | `:focus-visible` ring, `body` paper mesh + grain, `::selection`, headings | global |

## Rules (contract obligations)

1. Shared rules live ONLY in global `styles.css`; component `styles[]` carry component-local
   structure only and MUST stay < 4 kb (warn) / 8 kb (error) per component.
2. Motion class names are canonical `.reveal` + `.d1..d5` (NOT `.ig-reveal--dN`). Single shared
   `ig-rise` keyframe; no per-section keyframes.
3. `@media (prefers-reduced-motion: reduce)` MUST neutralize animation/transition and force
   `.reveal`/`.onscroll` content visible.
4. Companion deletions: redundant local rules superseded by globals SHOULD be removed
   (`search.component.ts` textarea/select/`.ig-alert--info`; `results.component.ts` becomes empty
   `styles[]`; `account.component.ts` local `.ig-btn--ghost`/`.ig-badge`/`.ig-alert--info`;
   `providers.component.ts` local `.ig-chip`/`.ig-alert--info`).

## Acceptance

- [ ] All families above are present in global `styles.css`.
- [ ] `results.component.ts` `styles[]` is empty and history-detail renders identically to search.
- [ ] No component redeclares a globally-owned class; each component build is under budget.
- [ ] Reduced-motion block present and effective.
