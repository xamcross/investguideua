# UI Contract: i18n Keys (`frontend/public/i18n/{uk,en}.json`)

Both files MUST stay key-for-key identical. All keys below are **additive** except the one
`notFound.title` value change. New strings use ASCII punctuation (`-`, `...`). Authoritative source:
Section 5.2 of [docs/UI-REDESIGN-SPEC.md](../../../docs/UI-REDESIGN-SPEC.md).

## New keys (both locales)

| Key | uk | en |
|---|---|---|
| `a11y.skipToContent` *(only if skip link added)* | Перейти до вмісту | Skip to content |
| `nav.menuOpen` | Відкрити меню | Open menu |
| `nav.menuClose` | Закрити меню | Close menu |
| `landing.eyebrow` | Каталог-орієнтований радник | Catalog-grounded advisor |
| `landing.titleLead` | Інвестуйте в Україну, | Invest in Ukraine, |
| `landing.titleAccent` | для українців | for Ukrainians |
| `landing.howKicker` | Як це працює | How it works |
| `landing.howTitle` | Три кроки до впевненого рішення | Three steps to a confident decision |
| `landing.ledgerLabel` | Підібрано для запиту | Matched for your request |
| `landing.ledgerCurrency` | грн | UAH |
| `landing.ledgerDeposit` | Банківський депозит | Bank deposit |
| `landing.ledgerBond` | Військові облігації | War bonds |
| `landing.ledgerFund` | Фонд грошового ринку | Money-market fund |
| `landing.ledgerFoot` | Кожен варіант з посиланням на офіційне джерело | Every option links to an official source |
| `search.kicker` | Каталог-орієнтований радник | Catalog-grounded advisor |
| `currency.UAH` | грн | UAH |
| `currency.USD` | USD | USD |
| `category.BANK_DEPOSIT` | Депозит | Deposit |
| `category.GOV_BOND` | Облігація | Bond |
| `category.BROKER` | Брокер | Broker |
| `category.FUND` | Фонд | Fund |
| `category.OTHER` | Інше | Other |
| `risk.LOW` | Низький | Low |
| `risk.MODERATE` | Помірний | Moderate |
| `risk.HIGH` | Високий | High |
| `tokens.kicker` | Прозорі ціни | Transparent pricing |
| `tokens.recommended` | Рекомендовано | Recommended |
| `tokens.recommendedSr` *(optional SR-only)* | Рекомендований пакет | Recommended pack |
| `paymentResult.kicker` | Платіж | Payment |
| `login.eyebrow` | Безпечний вхід | Secure sign-in |
| `register.eyebrow` | Новий акаунт | New account |
| `register.eyebrowSent` | Майже готово | Almost there |
| `verify.eyebrow` | Підтвердження | Verification |
| `history.kicker` | Ваш архів | Your archive |
| `history.paginationLabel` | Гортання сторінок історії | History pagination |
| `account.eyebrow` | Профіль | Profile |
| `providers.typicalReturn` | Типова дохідність | Typical return |
| `common.currencyUahShort` | грн | UAH |
| `placeholder.badge` | В розробці | In progress |

## Copy change (non-additive, intentional)

| Key | Old | New uk | New en |
|---|---|---|---|
| `notFound.title` | "404 - ..." prefix | `Сторінку не знайдено` | `Page not found` |

`notFound.body` is unchanged.

## Rules (contract obligations)

1. `uk.json` and `en.json` MUST have **identical key sets**; both MUST parse.
2. Keys are nested under their parent objects (add to existing objects; do not duplicate parents).
3. No existing key removed or renamed (only `notFound.title` value changes).
4. `risk.*` (raw-enum keyed) is intentionally distinct from existing `providers.risk*`/`search.risk*`.
5. Optional keys (`a11y.skipToContent`, `tokens.recommendedSr`) included only if their feature is
   added; if so, in BOTH locales.

## Acceptance

- [ ] Every new key present in both files; identical key sets; both parse.
- [ ] `notFound.title` updated in both; `notFound.body` unchanged.
- [ ] Runtime UK<->EN toggle resolves every new key on its surface; no hardcoded copy.
