# UI Contract: Behavior Preservation (what MUST NOT change)

This redesign is presentation-only. The following are frozen and any diff touching them is a
contract violation. Source: Sections 1, 8 of
[docs/UI-REDESIGN-SPEC.md](../../../docs/UI-REDESIGN-SPEC.md); Constitution principles II & IV.

## Frozen logic & contracts

1. **TypeScript logic**: signals, reactive forms, validators (`amount` min 0.01, `goals` maxlength
   280, `PASSWORD_PATTERN`), services, guards, the auth interceptor, polling/backoff, error mapping
   (`mapError` and its five branches), `submit()`, `showError()`, `statusKey()`, `price()`/
   `perToken()`, `buy()`, `finishSuccess()`, `deletionMailto()`, lifecycle hooks. Only `template:`
   and `styles:` strings (and the listed signal additions for the mobile menu) change.
2. **Routing**: route tables, `routerLink`/`routerLinkActive` targets, `@Input()` route bindings
   (`token`, `id`, `paymentId`, `heading`), redirects (e.g. landing -> `/search` when authed).
3. **Money**: `money.util` (`toMinorUnits`, `formatMinorUnits`) is read-only; amounts stay integer
   minor units; `amount/100 | number:'1.2-2'` and `formatMinorUnits` outputs unchanged; the
   `igCurrency` label is display-only.
4. **i18n mechanism**: ngx-translate, `LanguageService`, `PluralPipe` behavior unchanged; no
   `[innerHTML]` for translated copy; no existing key removed/renamed (only `notFound.title` value).
5. **Risk/status raw values**: `[attr.data-risk]` and `[attr.data-status]` keep the **raw enum**
   driving color; only the visible *label* is localized.
6. **Shared renderer**: `ig-results` must render identically on Search results and History-detail;
   `results.component.ts` is owned by Phase C only and not edited elsewhere.
7. **Links**: external/official-source links keep `target="_blank" rel="noopener noreferrer"`.
8. **Component shape**: every component stays standalone, `ChangeDetectionStrategy.OnPush`, and
   `ig-`-prefixed; no new npm dependency; Angular version unchanged.

## Permitted changes (the only ones)

- `index.html`: +4 font/preconnect lines (no BOM).
- `styles.css`: tokens, atmosphere, headings, global `.ig-*` classes, motion, focus.
- Component `template:`/`styles:` strings (lean, under budget).
- One new file `core/i18n/currency-label.pipe.ts` (display-only `igCurrency`).
- Additive i18n keys + the single `notFound.title` copy change.
- App-shell: `menuOpen` signal + `menuOpen.set(false)` in `logout()` for the mobile-menu defect fix.

## Acceptance

- [ ] Diff touches only the permitted surfaces above.
- [ ] No validator/service/guard/interceptor/route/money-util change; build + behavior unchanged.
- [ ] `data-risk`/`data-status` raw values preserved; only labels localized.
- [ ] `ig-results` renders identically in both consumers; OnPush + `ig-` selectors retained.
