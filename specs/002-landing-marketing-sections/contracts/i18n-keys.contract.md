# UI Contract: i18n Keys (`frontend/public/i18n/{uk,en}.json`)

Both files MUST stay key-for-key identical. All keys are **additive**. New strings use ASCII
punctuation (`-`, `...`). Copy is grounded in `design/redesign-concept.html` (`ft.*`, `res.*`, `pk.*`).
Final exact wording is confirmed at implementation; the table is the intended set + meaning.

## Footer (`footer.*`)

| Key | uk (intent) | en (intent) |
|---|---|---|
| `footer.about` | Інвестуйте в Україну, для українців. Прозорий, каталог-орієнтований радник. | Invest in Ukraine, for Ukrainians. A transparent, catalog-grounded advisor. |
| `footer.product` | Продукт | Product |
| `footer.tokens` | Токени | Tokens |
| `footer.legal` | Правова інформація | Legal |
| `footer.terms` | Умови | Terms |
| `footer.privacy` | Приватність | Privacy |
| `footer.made` | Зроблено в Україні | Made in Ukraine |
| `footer.rights` | (c) 2026 InvestGuideUA | (c) 2026 InvestGuideUA |

Reuse existing `nav.search`, `nav.providers` for the Product group links.

## Sample-results section (`landing.sample*`)

| Key | uk (intent) | en (intent) |
|---|---|---|
| `landing.sampleKicker` | Зразок результату | Sample result |
| `landing.sampleTitle` | Варіанти для 25 000 грн | Options for 25,000 UAH |
| `landing.sampleDisclaimer` | Цифри орієнтовні. Це приклад, а не фінансова порада. | Figures are indicative. This is an example, not financial advice. |
| `landing.sampleProvider1/2/3` | (provider names) | (provider names) |
| `landing.sampleInstrument1/2/3` | (instrument names) | (instrument names) |
| `landing.sampleRationale1/2/3` | (one-line rationale each) | (one-line rationale each) |

Reuse existing `category.*`, `risk.*`, `currency.*`, `common.perYear`, `results.expectedReturn`,
`common.officialSource` for the card chrome. Numeric figures (e.g. `14.5-16.0`, `25 000`) are
decorative literals like the existing landing ledger.

## Pricing-preview section (`landing.pricing*`)

| Key | uk (intent) | en (intent) |
|---|---|---|
| `landing.pricingKicker` | Прозорі ціни | Transparent pricing |
| `landing.pricingTitle` | Один пошук - один токен | One search, one token |
| `landing.pricingNote` | Перші 5 токенів - безкоштовно після підтвердження пошти. | Your first 5 tokens are free after you verify your email. |
| `landing.pricingCta` | Зареєструватися | Register |
| `landing.pricingFine` | Платежі безпечно обробляє monobank (Plata by mono). | Payments are securely handled by monobank (Plata by mono). |
| `landing.pricingExample` | приклад | example |

Reuse `units.token` (via `igPlural`) for token counts; per-token value is a decorative literal.

## Rules

1. `uk.json` and `en.json` MUST have **identical key sets**; both MUST parse.
2. Keys nest under their parent objects (add to existing `landing`/new `footer`; do not duplicate).
3. No existing key removed or renamed.
4. Reuse existing keys where the meaning is identical (`nav.*`, `risk.*`, `category.*`, `currency.*`,
   `common.officialSource`, `common.perYear`) rather than duplicating.

## Acceptance

- [ ] Every new key present in both files; identical key sets; both parse.
- [ ] Runtime UK<->EN toggle resolves all new copy on the landing + footer; no hardcoded strings.
