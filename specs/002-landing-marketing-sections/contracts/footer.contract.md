# UI Contract: Footer (`ig-footer`)

A new shell-level standalone component rendered once in `app.component` beneath the router outlet.

## Structure

- Dark navy footer (uses `--navy-900` + a blue/gold top rule) consistent with the concept.
- Brand block: brand mark + `InvestGuideUA` wordmark + tagline (`footer.about`).
- "Product" link group (`footer.product`): Search (`/search`), Providers (`/providers`),
  Tokens (`/tokens`) via `routerLink`.
- "Legal" link group (`footer.legal`): Terms (`/terms`), Privacy (`/privacy`) via `routerLink`.
- Base row: copyright (`footer.rights`) + "Made in Ukraine" (`footer.made`) trust mark.

## Rules (contract obligations)

1. Standalone, `ChangeDetectionStrategy.OnPush`, `ig-`-prefixed selector (`ig-footer`).
2. Rendered once in the shell; appears on every page; sits in normal flow below `<main>`; MUST NOT
   overlap the fixed toast layer (`z-index:1000`) or the sticky topbar.
3. All visible text via the `translate` pipe; no hardcoded strings; both locales identical.
4. Links are real `routerLink`s; no broken/`#` links. `/terms` + `/privacy` are additive routes to
   `PlaceholderComponent`.
5. Accessibility: a `<footer>` landmark; AA contrast on the dark background (link text light enough,
   gold hover only on large/non-critical text); visible `:focus-visible` ring on every link;
   decorative mark `aria-hidden`.
6. Component `styles[]` stays under the 4 kb warn / 8 kb error budget; shared dark-section rules may
   live in global `styles.css`.

## Acceptance

- [ ] `ig-footer` renders site-wide with brand+tagline, Product + Legal groups, copyright, made-in-UA.
- [ ] Product/Legal links navigate correctly; `/terms` + `/privacy` resolve to placeholder pages.
- [ ] Both locales resolve; keyboard focus visible; AA contrast on dark; under budget.
