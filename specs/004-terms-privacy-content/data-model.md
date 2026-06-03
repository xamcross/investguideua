# Phase 1 Data Model: Terms & Conditions and Privacy Statement Content

**Feature**: 004-terms-privacy-content | **Date**: 2026-06-03

This feature has **no persisted/database entities**. The "data" is static localized content shipped
in the i18n dictionaries and a small in-memory view model the component binds to. The structures
below define the shape of that content and the required sections.

## Content structure (i18n `legal.<doc>` object)

Stored once per language in `public/i18n/uk.json` and `public/i18n/en.json`, under
`legal.terms` and `legal.privacy`.

### `LegalDocument`

| Field           | Type                | Required | Notes |
|-----------------|---------------------|----------|-------|
| `title`         | string              | yes      | Document heading shown as the page `<h1>`. |
| `effectiveDate` | string              | yes      | Human-readable effective/last-updated date (FR-005), localized per language. Must match the published content. |
| `intro`         | string              | no       | Optional lead paragraph shown above the first section. |
| `sections`      | `LegalSection[]`    | yes      | Ordered list; rendered in array order. Must be non-empty. |
| `updatedNote`   | string              | no       | Optional short note on how/when terms change (Terms) - may instead be a section. |

### `LegalSection`

| Field      | Type       | Required | Notes |
|------------|------------|----------|-------|
| `heading`  | string     | yes      | Section heading (`<h2>`), used for scan-ability (SC-003, FR-009). |
| `body`     | string[]   | yes      | One or more paragraphs. Rendered as separate `<p>` elements. May be empty only if `bullets` present. |
| `bullets`  | string[]   | no       | Optional unordered list rendered after the paragraphs. |

### View model (component-internal)

The `LegalDocumentComponent` resolves the active `doc` input to the corresponding `legal.<doc>`
object via `TranslateService`, exposing a single `document` reference (the `LegalDocument` above)
plus the sibling link target (`'privacy'` when `doc==='terms'`, and vice versa) for the
cross-reference (FR-012). No additional persisted state.

## Required section coverage

The content is free to organize sections as long as every required topic is covered (validated in
the QA review). Mapping of spec requirements to expected sections:

### Terms & Conditions (`legal.terms`) - satisfies FR-002

| # | Section (suggested heading) | Required topic(s) |
|---|-----------------------------|-------------------|
| 1 | Who we are / Operator | Identification of the service operator + contact (placeholder allowed). |
| 2 | What InvestGuideUA is | Description of the service: information/discovery only, never moves user funds (FR-011). |
| 3 | Not financial advice | Explicit disclaimer: general information, NOT individualized financial, investment, tax, or legal advice (Principle IV consistency). |
| 4 | Accounts | Registration, email verification, user responsibility for credentials. |
| 5 | Tokens & payments | Token-based usage model (1 search = 1 token), free starter grant, paid packs via monobank. |
| 6 | Acceptable use | Permitted/prohibited use (no abuse, scraping, attempts to manipulate the service/LLM). |
| 7 | Intellectual property | Ownership of the service, catalog, and content. |
| 8 | Disclaimers & limitation of liability | Warranty disclaimer + liability limits. |
| 9 | Changes to these terms | How and when terms may be updated. |
| 10 | Contact | Contact point for questions (placeholder allowed). |

### Privacy Statement (`legal.privacy`) - satisfies FR-004

| # | Section (suggested heading) | Required topic(s) |
|---|-----------------------------|-------------------|
| 1 | Who we are / Controller | Operator/controller identity + privacy contact (placeholder allowed). |
| 2 | Data we collect | Account/email + auth data; usage & search history; payment-related data. |
| 3 | Why we process it (purposes) | Purposes for each category. |
| 4 | Legal basis | Justification/legal basis for processing. |
| 5 | Cookies & local storage | The HttpOnly/Secure refresh-token cookie used for sessions (strictly necessary) and the `ig.lang` language preference stored in browser localStorage. (Confirmed in code: `auth.service.ts` refresh cookie; `language.service.ts` `ig.lang`.) |
| 6 | Sharing & third parties | Payment provider (monobank) for purchases; the AI provider (Anthropic Claude) used to generate recommendations; the email-delivery processor used for verification emails (name it, or state self-hosted SMTP if applicable); hosting/infrastructure processor. Each named, not a vague "other processors". |
| 7 | International transfers | **Required (not conditional)**: cross-border transfer of search text to the AI provider (US) and any other non-UA processor; describe the transfer and safeguard posture. |
| 8 | Retention | **Accuracy-bound**: copy MUST reflect reality - account/email and search history are retained until the user requests account/data deletion; there is **no fixed automatic expiry / TTL** on search history (per `SPECIFICATION.md`). Do not invent a retention period that contradicts the system. |
| 9 | Your rights | Access, correction, deletion (account/data deletion on request), how to exercise. |
| 10 | Contact | Privacy contact point (placeholder allowed). |

## Validation rules

- **VR-1**: Both `legal.terms` and `legal.privacy` MUST exist in **both** `uk.json` and `en.json`
  with identical key structure and equivalent topic coverage (FR-008, SC-002).
- **VR-2**: Each document MUST have a non-empty `sections` array; each section MUST have a non-empty
  `heading` and at least one of `body`/`bullets` non-empty (FR-001/FR-003, no empty render).
- **VR-3**: `effectiveDate` MUST be present and reflect the shipped content (FR-005, SC-005).
- **VR-4**: Every required topic in the two tables above MUST be covered in each language (FR-002,
  FR-004) - verified in the QA/BA review.
- **VR-5**: No required-topic key may resolve to a raw key string at runtime (i.e. no missing
  translation surfaced as `legal.terms...`) in either language. The component MUST defend at runtime:
  if the resolved `legal.<doc>` value is not an object with a non-empty `sections` array, it renders
  a safe fallback (e.g. nothing/loading) rather than a raw key string (FR-008 edge case).
- **VR-6**: Both JSON files MUST remain valid JSON (parse-validated) after edits.
- **VR-7**: `legal.terms.effectiveDate` and `legal.privacy.effectiveDate` MUST represent the **same
  calendar date** across `uk.json` and `en.json` (no silent drift between languages) (SC-005).
- **VR-8**: The retention copy MUST be accurate per the system (VR/Privacy section 8): retained until
  deletion on request, no fixed TTL on search history. Verified explicitly in the QA review (FR-011).
