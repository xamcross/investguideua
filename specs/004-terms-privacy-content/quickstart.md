# Quickstart: Terms & Conditions and Privacy Statement Content

**Feature**: 004-terms-privacy-content | **Date**: 2026-06-03

This is a frontend-only change. It replaces the placeholder at `/terms` and `/privacy` with real,
bilingual legal content.

## What you are changing

| File | Change |
|------|--------|
| `frontend/src/app/features/legal/legal-document.component.ts` | **New** standalone OnPush component (`ig-legal`) that renders a `legal.<doc>` document by language. |
| `frontend/src/app/app.routes.ts` | Point `/terms` and `/privacy` at the new component; pass `data: { doc: 'terms' }` / `{ doc: 'privacy' }`; switch `title` to `title.terms` / `title.privacy`. |
| `frontend/public/i18n/uk.json` | Add `legal.terms.*`, `legal.privacy.*`, `legal.*` labels, and `title.terms`/`title.privacy` (canonical Ukrainian). |
| `frontend/public/i18n/en.json` | Add the same keys with English values (parity). |

The existing `PlaceholderComponent` stays - other not-yet-built routes still use it.

## Build / run (Windows, frontend)

```powershell
cd C:\Users\xamcr\InvestGuideUA\frontend
npm ci          # if dependencies are not installed
npm start       # ng serve - dev server
```

Then open:
- `http://localhost:4200/terms`
- `http://localhost:4200/privacy`

Toggle the language switch (top-right) to verify uk <-> en content swaps live.

## Manual verification checklist (maps to the contract)

- [ ] `/terms` and `/privacy` show full content, not the "coming soon" placeholder (signed out too).
- [ ] Each screen shows an effective date.
- [ ] Language toggle swaps content in place; no blank screen, no raw `legal.` keys visible.
- [ ] Every required topic (see `data-model.md`) appears in both Ukrainian and English.
- [ ] Footer "Umovy"/"Pryvatnist" (Terms/Privacy) links reach the populated screens.
- [ ] Cross-link reaches the sibling document; "back to home" works.
- [ ] No horizontal scrollbar / clipped text at 320px width through desktop.

## Static / build verification (required before "done")

```powershell
# JSON validity (both dictionaries must parse)
node -e "require('./public/i18n/uk.json'); require('./public/i18n/en.json'); console.log('JSON OK')"

# Compile / build the frontend if a runtime is available
npm run build   # ng build ; if no runtime available, state verification was static-only

# Confirm NO Windows-executed script was introduced (Constitution Principle V).
# This feature touches only .ts and .json; a non-ASCII scan applies only to .ps1/.cmd/.bat.
```

> Note: `.ts` and `.json` are consumed by the Node/Angular build as UTF-8, so Ukrainian text is
> correct there. The ASCII-only rule is specifically for `.ps1`/`.cmd`/`.bat`, none of which this
> feature creates or edits.

## Mandatory review before closing (Constitution Principle VI)

Two role sub-agents must review, including an actual JSON parse + build (not just reading):
1. **Frontend lead** - Angular standalone/OnPush correctness, route + input binding, live language
   switch, a11y (heading order, landmarks), responsiveness, no hardcoded strings.
2. **QA / Business Analyst** - content completeness vs. FR-002/FR-004 required topics, uk/en parity,
   disclaimer presence and platform accuracy (FR-011), effective date.

## Out of scope (per spec Assumptions)

- Registration-time "I accept the Terms" checkbox or stored per-user consent.
- Backend storage/versioning of legal documents.
- Finalized legal wording / regulatory sign-off (draft pending qualified legal review).
