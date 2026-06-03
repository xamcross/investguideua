# UI Contract: Design Tokens (`:root` in `frontend/src/styles.css`)

This contract fixes the canonical token vocabulary every component must consume. Authoritative
source: Section 3.2 of [docs/UI-REDESIGN-SPEC.md](../../../docs/UI-REDESIGN-SPEC.md).

## Canonical tokens (must all be present)

| Group | Tokens | Notes |
|---|---|---|
| Navy | `--navy-900 #081427`, `--navy-800 #0d1f3c`, `--navy-700 #13294b` | dark sections, hovers |
| Blue | `--blue-600 #0057b7`, `--blue-500 #1f6fd4`, `--blue-300 #7fb0e8` | `--blue-600` = primary interactive |
| Gold | `--gold-700 #7a5a0c`, `--gold-600 #b8860b`, `--gold-500 #d9a823`, `--gold-300 #f0cf6b`, `--gold-100 #f7ecc9` | `--gold-700` = AA small text; `500/600` fills/large only |
| Neutrals | `--paper #f6f3ec`, `--surface #fff`, `--surface-2 #fbf9f4`, `--ink #101b30`, `--muted #5c6678`, `--line #e6dfd1`, `--line-2 #d8cfbb` | warm document paper |
| Risk/status | `--risk-low-bg/fg`, `--risk-mod-bg/fg`, `--risk-high-bg/fg`, `--success-bg/fg`, `--danger-bg/fg`, `--info-bg/fg` | bg+fg pairs |
| Type | `--font-display` (Playfair), `--font-ui` (Manrope), `--font-mono` (JetBrains Mono) | each with full fallback stack |
| Shape/depth/motion | `--radius 14px`, `--radius-sm 9px`, `--shadow-sm/md/lg`, `--maxw 1120px`, `--ease cubic-bezier(.22,.61,.36,1)` | |

## Back-compat alias layer (must remain until migration complete)

Every legacy `--ig-*` re-points onto the new ramp so existing component CSS resolves unchanged:
`--ig-blue->--blue-600`, `--ig-blue-dark->--navy-700`, `--ig-yellow->--gold-500`, `--ig-ink->--ink`,
`--ig-muted->--muted`, `--ig-bg->--paper`, `--ig-surface->--surface`, `--ig-border->--line`,
`--ig-danger->--danger-fg`, `--ig-danger-bg->--danger-bg`, `--ig-success->--success-fg`,
`--ig-success-bg->--success-bg`, `--ig-radius->--radius`.

## Rules (contract obligations)

1. New CSS MUST use canonical bare-ramp names; `--ig-*` is legacy alias only.
2. Small gold text/icons MUST use `--gold-700`; `--gold-500/600` are for fills, rules, icons, and
   large (>=24px/700) display figures only. Gold button text is `--navy-900` (never white-on-gold).
3. Canonical page width is `--maxw` (1120px) **everywhere**, including `.ig-topbar__inner` (no 920px
   left anywhere).
4. The full `:root` block lives only in global `styles.css`; components MUST NOT redeclare tokens.

## Acceptance

- [ ] `:root` contains all groups above including `--gold-700`, the three `--font-*`, and `--maxw`.
- [ ] Every legacy `--ig-*` still resolves via the alias layer with the new palette.
- [ ] Build parses; no token referenced by a component is undefined.
