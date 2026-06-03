# Phase 1 Data Model: Landing marketing sections

This feature adds **presentation/marketing content only**. It introduces no persisted entities, no
API calls, and no change to existing data shapes or money math. The "model" is a set of hard-coded
display constants and the i18n key inventory.

## Display concepts (no persistence, no API)

### Sample option (illustrative)
- **Source**: a hard-coded constant array in the landing component (3 items).
- **Shape (display only)**: provider/instrument name, localized category label (`category.*`),
  localized risk label (`risk.*`) with a `data-risk` raw-enum value driving color, an expected-return
  range, a short list of key facts, a one-line rationale, and an "official source" affordance
  (decorative/non-functional or pointing at a real public source URL).
- **Invariant**: NOT sourced from the advisor; costs no token; clearly labelled "sample result";
  rendered with the global `.ig-opt` instrument-card classes so it looks identical to real results.

### Pricing-preview pack (illustrative)
- **Source**: a hard-coded constant array in the landing component (3 packs: 10 / 30 / 100 tokens).
- **Shape (display only)**: token count, example price, per-token value, and a "best value" flag on
  the middle pack. CTA routes to `/register`.
- **Invariant**: illustrative and clearly labelled; no float math; consumes no token; starts no
  purchase; consistent in spirit with — but explicitly not an authoritative quote from — `/tokens`.

### Footer link
- **Source**: static config in the footer component.
- **Shape**: a label (i18n key) + a destination (internal `routerLink` or external URL). Groups:
  Product (`/search`, `/providers`, `/tokens`), Legal (`/terms`, `/privacy`).

## Routing additions (additive only)

| Path | Component | Notes |
|------|-----------|-------|
| `/terms` | `PlaceholderComponent` | new, additive; `data.heading` = Terms; "coming soon" until real page |
| `/privacy` | `PlaceholderComponent` | new, additive; `data.heading` = Privacy |

No existing route, guard, or `routerLink` target changes.

## i18n key inventory

Additive keys for BOTH `uk.json` and `en.json`, kept identical. Full enumeration in
[contracts/i18n-keys.contract.md](contracts/i18n-keys.contract.md). Families: `footer.*`,
`landing.sample*`, `landing.pricing*`, plus reuse of existing `nav.*`, `common.officialSource`,
`risk.*`, `category.*`, `currency.*`, and the existing landing disclaimer.

## Explicitly unchanged

`money.util`, the token ledger, `/tokens/packs` and its auth, payment flows, the advisor/LLM path,
all validators/services/guards/interceptor, and every existing route and `routerLink` target.
