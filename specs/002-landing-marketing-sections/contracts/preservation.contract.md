# UI Contract: Behavior Preservation (what MUST NOT change)

This feature is additive marketing presentation. The following are frozen; any diff touching them is
a contract violation. Source: spec FR-015, Constitution principles I/III/IV.

## Frozen

1. **Backend / security**: no backend change; `/tokens/packs` stays authenticated (NOT exposed to
   anonymous users). No security config, controller, or guard change.
2. **Money & tokens**: `money.util`, the token ledger, real pack data, and payment flows are
   untouched. The pricing preview shows illustrative, clearly-labelled figures only — no float math,
   no authoritative quote, no purchase, no token consumed.
3. **Advisor / LLM**: the sample-results section uses hard-coded data; no advisor/LLM call, no
   catalog-grounding or token-budget surface touched.
4. **Existing screens & logic**: no change to validators, services, guards, interceptor, polling,
   error mapping, or any existing component's logic. The landing `ngOnInit` redirect, hero, "how it
   works", and existing disclaimer are unchanged (sections are appended).
5. **Routing**: no existing route, guard, or `routerLink` target changes. Only `/terms` and
   `/privacy` are ADDED (to `PlaceholderComponent`).
6. **i18n mechanism**: ngx-translate, `LanguageService`, `PluralPipe` unchanged; no existing key
   removed/renamed; `uk.json`/`en.json` stay identical; no `[innerHTML]` for translated copy.
7. **Component shape**: new + edited components stay standalone, `OnPush`, `ig-`-prefixed; no new npm
   dependency; Angular version unchanged.

## Permitted changes (the only ones)

- New `core/layout/footer.component.ts` (`ig-footer`), rendered in `app.component`.
- Additive sample-results + pricing-preview sections in `landing.component.ts` (+ display consts).
- `app.routes.ts`: ADD `/terms` + `/privacy` -> `PlaceholderComponent` (additive).
- `styles.css`: global `.ig-section--dark` + shared footer/pack-preview rules.
- Additive i18n keys in both locales.

## Acceptance

- [ ] Diff touches only the permitted surfaces; no backend/security/money/LLM/guard change.
- [ ] `/tokens/packs` auth unchanged; no public packs read added.
- [ ] Pricing/sample figures are illustrative + clearly labelled; no real money math added.
- [ ] Existing routes unchanged; only `/terms` + `/privacy` added; OnPush + `ig-` retained.
