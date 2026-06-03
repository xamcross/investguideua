# UI & Content Contract: Legal Document Screens

**Feature**: 004-terms-privacy-content | **Date**: 2026-06-03

This feature exposes no network/API contract (no backend involved). The relevant contracts are the
**route contract**, the **component input contract**, and the **i18n content schema** that the
component and translators must agree on.

## 1. Route contract

Both routes stay public (no guard) and are reachable from the existing footer links (FR-006, FR-007).

| Path       | Title key       | Component               | Route `data`            |
|------------|-----------------|-------------------------|-------------------------|
| `/terms`   | `title.terms`   | `LegalDocumentComponent`| `{ doc: 'terms' }`      |
| `/privacy` | `title.privacy` | `LegalDocumentComponent`| `{ doc: 'privacy' }`    |

- Lazy-loaded via `loadComponent` (consistent with all other routes).
- `data.doc` is bound to the component's `@Input() doc` through the app-wide
  `withComponentInputBinding()`.

## 2. Component input contract (`LegalDocumentComponent`, selector `ig-legal`)

```text
@Input() doc: 'terms' | 'privacy'   // required; selects the legal.<doc> content namespace
```

Behavior contract:
- **Injects `TranslateService`** and reads the *structured* document object in TypeScript - the
  `translate` pipe MUST NOT be used to render `sections` (it returns `[object Object]`). The flat
  label keys (`legal.effectiveLabel`, `legal.seeAlso*`, `common.backToHome`) may use the pipe.
- **First paint uses `TranslateService.get('legal.' + doc)` (Observable), NOT `instant()`.** Because
  these routes are lazy-loaded and directly URL-addressable, a cold deep-link can construct the
  component before the dictionary has loaded; `instant()` would return the raw key. Subscribe to
  `get(...)` for the initial value and to `onLangChange` for live switches (re-read on each).
- **Runtime defense (VR-5)**: if the resolved value is not an object with a non-empty `sections`
  array, render a safe fallback (loading/empty), never a raw key string.
- Resolves `legal.<doc>` and renders `title` as the single `<h1>`, `effectiveDate`, optional `intro`,
  then each `sections[]` entry as a sequential `<h2>` (no skipped heading levels) + paragraph(s) +
  optional bullet list.
- Renders a cross-link to the sibling document (`/privacy` from terms, `/terms` from privacy) and a
  "back to home" action as real `<a routerLink>` elements (focusable), reusing existing
  `ig-empty`/`ig-btn` style conventions where appropriate.
- Content column uses `overflow-wrap: break-word` and a readable max-width so long strings/URLs do
  not cause horizontal scroll at 320px (SC-004) and prose stays legible.
- Standalone, `ChangeDetectionStrategy.OnPush`, imports `RouterLink` + `TranslateModule`, injects
  `TranslateService`.
- No hardcoded visible strings - every label resolves through i18n.

## 3. i18n content schema (added to `uk.json` and `en.json`)

```jsonc
{
  "title": {
    // ...existing keys...
    "terms": "Умови використання - InvestGuideUA",     // en: "Terms of Use - InvestGuideUA"
    "privacy": "Політика приватності - InvestGuideUA"   // en: "Privacy Policy - InvestGuideUA"
  },
  "legal": {
    "backToHome": "На головну",            // may reuse common.backToHome instead
    "seeAlsoTerms": "Див. також: Умови",   // cross-link label (privacy -> terms)
    "seeAlsoPrivacy": "Див. також: Приватність", // cross-link label (terms -> privacy)
    "effectiveLabel": "Чинні з",           // label preceding effectiveDate (e.g. "Чинні з 3 червня 2026")
    "terms": {
      "title": "Умови використання",
      "effectiveDate": "3 червня 2026 р.",
      "intro": "...",                       // optional
      "sections": [
        { "heading": "...", "body": ["...", "..."], "bullets": ["...", "..."] }
        // ...one object per required section (see data-model.md)
      ]
    },
    "privacy": {
      "title": "Політика приватності",
      "effectiveDate": "3 червня 2026 р.",
      "intro": "...",
      "sections": [
        { "heading": "...", "body": ["..."] }
        // ...one object per required section (see data-model.md)
      ]
    }
  }
}
```

### Schema rules (must hold in BOTH languages)

- `legal.terms` and `legal.privacy` are objects with `title` (string), `effectiveDate` (string),
  optional `intro` (string), and `sections` (non-empty array).
- Each `sections[]` item: `heading` (non-empty string), `body` (string array), optional `bullets`
  (string array). At least one of `body`/`bullets` is non-empty.
- Key structure is **identical** across `uk.json` and `en.json`; only the string values differ
  (FR-008 / SC-002). Section count and ordering match across languages.
- All required topics from `data-model.md` are present in each language (FR-002, FR-004).

## 4. Acceptance / verification checks (contract tests)

These are the observable checks the implementation must satisfy (mapped to spec SC/FR):

1. Visiting `/terms` and `/privacy` (signed out, and via direct URL) renders the document content,
   **not** the `placeholder.*` strings. (SC-001, SC-006, FR-006)
2. Both screens show a visible effective date matching the content. (SC-005, FR-005)
3. Switching language (uk <-> en) on either screen swaps the content language in place with no blank
   or raw-key render. (FR-008, edge case)
4. Every required topic (data-model tables) is present in both languages. (SC-002, FR-002, FR-004)
5. No visible raw translation key (e.g. text beginning `legal.`) appears in either language. (VR-5)
6. Footer "Terms"/"Privacy" links navigate to the populated screens. (FR-007)
7. A cross-link reaches the sibling document. (FR-012)
8. Both `uk.json` and `en.json` parse as valid JSON. (VR-6)
9. Content renders without horizontal overflow/clipping from 320px to large desktop, including long
   unbreakable strings/URLs (`overflow-wrap: break-word`). (SC-004, FR-010)
10. Exactly one `<h1>` per page; section headings are sequential `<h2>` with no skipped levels;
    cross-link and back-home are focusable `<a routerLink>`. (a11y)
11. `effectiveDate` is the same calendar date in `uk.json` and `en.json`. (VR-7)
12. Privacy content names the AI provider (Anthropic Claude), monobank, and the email-delivery
    processor; includes a cookies/localStorage section; and the retention copy says "until deletion
    on request, no fixed TTL" (matches the system, FR-011 / VR-8).

> Title-key note (intentional divergence): footer link labels keep `footer.terms`/`footer.privacy`
> (short nav words); the browser/page titles use new `title.terms`/`title.privacy` keys, consistent
> with the app-wide `title.*` strategy. Both key sets are intended to coexist - not a duplicate to
> remove.
