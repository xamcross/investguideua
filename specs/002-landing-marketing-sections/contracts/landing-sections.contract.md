# UI Contract: Landing marketing sections

Two sections added to the existing `landing.component.ts`, after "how it works" and before the
existing disclaimer. Both are signed-out-facing, static, and reuse the `001-ui-redesign` system.

## A. Sample-results ("Examples") section

- Header: mono kicker (`landing.sampleKicker` = "Sample result") + serif `<h2>` (`landing.sampleTitle`).
- Three illustrative instrument cards using the GLOBAL `.ig-opt` classes (identical look to real
  results): provider/instrument, localized category (`category.*`) + risk (`risk.*`, `data-risk`
  raw enum for color) badges, a serif return figure + mono `% / yr`, a 2-col facts grid, a one-line
  rationale, and an "official source" affordance.
- A disclaimer (`landing.sampleDisclaimer`) stating the figures are indicative / an example, not
  advice.

Rules: hard-coded display constants; NO advisor/API call; NO token cost; NO sign-in. `data-risk`
keeps the raw enum; only labels are localized. Decorative SVGs `aria-hidden`.

## B. Transparent-pricing preview section

- Wrapped in the global `.ig-section--dark` navy section.
- Header: mono gold kicker (`landing.pricingKicker` = "Transparent pricing") + serif `<h2>`
  (`landing.pricingTitle`) + note (`landing.pricingNote`).
- Three pack cards (10 / 30 / 100 tokens via `igPlural`), each with an example price, a per-token
  value, and an `landing.pricingExample` ("example") marker; the MIDDLE pack is highlighted as best
  value with an emphasized (gold) CTA, others standard.
- Every CTA routes to `/register` (`landing.pricingCta`). Fineprint (`landing.pricingFine`).

Rules: illustrative figures only, clearly marked; no float math; no purchase from the public page;
no API call. Gold small text uses `--gold-700`/`--gold-300` per the dark/light context.

## Cross-cutting rules

1. All copy via `translate`; both locales identical; no hardcoded user-facing strings (decorative
   numeric figures are allowed literals, as on the existing landing ledger).
2. Reuse the redesign design system (tokens, `.ig-opt`, `.ig-section--dark`, motion `.reveal`,
   focus). No new dependency.
3. AA contrast; visible focus; reduced-motion honored; responsive single-column collapse at small
   widths; nothing clipped.
4. The existing landing hero, "how it works", `ngOnInit` redirect, and disclaimer are unchanged;
   sections are additive. Landing component `styles[]` stays under budget (shared CSS global).

## Acceptance

- [ ] Sample section: 3 `.ig-opt` cards with localized category/risk (color from `data-risk`),
      return figure, facts, rationale, source affordance, and an example disclaimer; no token/API.
- [ ] Pricing section: dark section, 3 packs, middle highlighted, example markers, CTAs to `/register`,
      fineprint; illustrative figures only.
- [ ] Both locales resolve; AA + focus + reduced-motion; responsive; build under budget.
