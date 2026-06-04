# InvestGuideUA - UI/UX Redesign Specification

**Version:** 1.0.0 (Draft)
**Date:** 2026-06-03
**Status:** Ready for implementation

This specification defines a complete visual and UX restyle of the InvestGuideUA Angular 17 application under a single design language — "institutional trust + Ukrainian identity." It is a presentation-layer redesign only: no business logic, API contracts, validators, signals, routing, guards, interceptors, money math, or ngx-translate behavior changes. The work is organized as one canonical design system (fonts, tokens, global component classes, motion, accessibility) plus per-component restyles that consume it, with all shared rules consolidated into the global `frontend/src/styles.css` to keep every component's scoped `styles[]` array under the production `anyComponentStyle` budget (8 kb error / 4 kb warn). The document is ordered so it can be fed to an AI implementation workflow in independently buildable phases, each ending in a verifiable build.

---

## 1. Purpose & scope

**Purpose.** Replace the app's legacy flat blue/lemon-yellow styling with a cohesive, editorial, trustworthy visual system: warm "document paper" surfaces, institutional navy ink, Ukraine-blue as the single interactive color, refined wheat-gold as accent, a serif/sans/mono type system, layered depth, and restrained motion.

**In scope:** `index.html` (font links only), the global stylesheet `frontend/src/styles.css`, the inline `template`/`styles[]` of feature components, additive ngx-translate keys (and two small, clearly-listed copy changes), and one tiny pure display pipe for the localized currency label.

**Explicitly out of scope (MUST NOT change):**
- Any TypeScript logic: signals, reactive forms, validators (`amount` min 0.01, `goals` maxlength 280, `PASSWORD_PATTERN`), services, guards, the auth interceptor, polling/backoff, error mapping.
- API calls, request/response shapes, routing tables, `routerLink` targets, `routerLinkActive`.
- Money math: all amounts stay integer minor units (kopiykas); `money.util` (`toMinorUnits`, `formatMinorUnits`) is untouched.
- Removal/renaming of existing ngx-translate keys, or `LanguageService`/`PluralPipe` behavior.
- Angular version, framework choices, or dependencies — **no new npm packages.** Components stay standalone, `OnPush`, `ig-`-prefixed.

The one functional addition permitted is a **real responsive mobile navigation** (Section 7.1), because the original concept hid the nav and token balance on narrow screens with no fallback — that is a defect, and the redesign fixes it rather than replicating it.

---

## 2. Design principles & aesthetic direction

**"Institutional trust + Ukrainian identity."** The product advises Ukrainians on regulated investments; the interface must feel like a credible financial institution while carrying a clear, dignified Ukrainian identity — not a flag-themed novelty.

- **Editorial, document-like.** Warm paper background (`#f6f3ec`), generous spacing, serif display headings, layered soft shadows. The page should read as a considered financial document, not a SaaS dashboard.
- **Ukrainian identity, refined.** Ukraine blue `#0057b7` is the interactive color. The flag yellow is deliberately retired in favor of a refined **wheat-gold** (`#d9a823`) accent — warmer and more institutional than lemon. Blue/gold flag rules appear as thin decorative accents, never as large fills.
- **Trust through restraint.** Color is informative (risk/status), never decorative noise. Motion is subtle and always optional. Numbers and figures are set in monospace for a precise, ledger-like feel.
- **Accessibility is a first-class principle, not a finishing pass.** AA contrast, visible keyboard focus, reduced-motion support, and never-color-alone signaling are mandatory throughout.

Type system: **Playfair Display** (display serif headings/figures), **Manrope** (UI/body), **JetBrains Mono** (figures, codes, eyebrow/label microtext).

---

## 3. Design system (canonical)

This section is canonical. Every later section references these token names, font stacks, and class contracts. All global edits land in two files: `frontend/src/index.html` (fonts) and `frontend/src/styles.css` (everything else). Shared rules MUST live in `styles.css` so component-scoped `styles[]` stay under the 8 kb budget.

### 3.1 Fonts (`frontend/src/index.html`)

Add these four lines inside `<head>`, immediately after the `<meta name="viewport">` line (preconnects first so they fire early). One combined `css2` request, Cyrillic served automatically via the returned `unicode-range`, `display=swap` to avoid invisible text. Do not add a BOM (`index.html` is read by the Angular/Linux build chain); `.html` is UTF-8, Cyrillic is fine; the ASCII-only rule applies only to `.ps1/.cmd/.bat`.

```html
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link
    href="https://fonts.googleapis.com/css2?family=Playfair+Display:ital,wght@0,500;0,700;0,800;0,900;1,500&family=Manrope:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;700&display=swap"
    rel="stylesheet" />
```

Font-family stacks (defined as tokens below, used everywhere):
- Display serif: `"Playfair Display", Georgia, "Times New Roman", serif`
- UI/body: `"Manrope", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif`
- Mono/figures: `"JetBrains Mono", ui-monospace, "Cascadia Mono", Consolas, monospace`

This is the only `index.html` change. Do not add any inline `<script>` or `data-t` machinery — i18n stays ngx-translate.

### 3.2 Design tokens + alias layer (`frontend/src/styles.css`)

Replace the existing `:root { ... }` block with the canonical block below. It contains (a) the full new ramps, (b) three font tokens, (c) shape/depth/motion tokens, and (d) an **alias layer** that re-points every existing `--ig-*` name onto the new ramp so all current component CSS keeps resolving with the new palette and **zero component edits are required for any component to compile/run**.

```css
:root {
  /* ===== Canonical design system (institutional trust + Ukrainian identity) ===== */

  /* Brand / identity */
  --navy-900: #081427;   /* deepest institutional navy (hero, footer, dark sections) */
  --navy-800: #0d1f3c;
  --navy-700: #13294b;
  --blue-600: #0057b7;   /* Ukraine flag blue - primary interactive */
  --blue-500: #1f6fd4;
  --blue-300: #7fb0e8;

  --gold-700: #7a5a0c;   /* dark gold - AA (>=4.5:1) for SMALL text on paper. Use for gold labels. */
  --gold-600: #b8860b;
  --gold-500: #d9a823;   /* refined wheat-gold accent (NOT lemon flag yellow) */
  --gold-300: #f0cf6b;
  --gold-100: #f7ecc9;

  /* Neutrals - warm "document paper", not clinical white */
  --paper:     #f6f3ec;  /* page background */
  --surface:   #ffffff;
  --surface-2: #fbf9f4;
  --ink:       #101b30;  /* near-navy text */
  --muted:     #5c6678;
  --line:      #e6dfd1;  /* warm hairline */
  --line-2:    #d8cfbb;

  /* Status / risk (kept distinct from gold so risk reads instantly) */
  --risk-low-bg:  #e7f3ea; --risk-low-fg:  #1e7a3c;
  --risk-mod-bg:  #fbf0d3; --risk-mod-fg:  #8a6310;
  --risk-high-bg: #fbe4e1; --risk-high-fg: #b3261e;
  --success-fg:   #1e7a3c; --success-bg:   #e7f3ea;
  --danger-fg:    #b3261e; --danger-bg:    #fbe4e1;
  --info-fg:      #0057b7; --info-bg:      rgba(0, 87, 183, .06);

  /* Typography */
  --font-display: "Playfair Display", Georgia, "Times New Roman", serif;
  --font-ui:      "Manrope", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  --font-mono:    "JetBrains Mono", ui-monospace, "Cascadia Mono", Consolas, monospace;

  /* Shape / depth / motion */
  --radius:    14px;
  --radius-sm: 9px;
  --shadow-sm: 0 1px 2px rgba(8,20,39,.06), 0 2px 8px rgba(8,20,39,.04);
  --shadow-md: 0 10px 30px -12px rgba(8,20,39,.25);
  --shadow-lg: 0 30px 60px -20px rgba(8,20,39,.35);
  --maxw: 1120px;
  --ease: cubic-bezier(.22, .61, .36, 1);

  /* ===== Back-compat aliases: existing --ig-* names -> new ramp =====
     Keep these so all current component styles resolve with the new palette
     without touching a single component file. Prefer the canonical names above
     in NEW code; treat --ig-* as legacy. */
  --ig-blue:       var(--blue-600);
  --ig-blue-dark:  var(--navy-700);   /* old hover navy; maps to deep navy */
  --ig-yellow:     var(--gold-500);   /* retire lemon #ffd700 -> wheat-gold */
  --ig-ink:        var(--ink);
  --ig-muted:      var(--muted);
  --ig-bg:         var(--paper);
  --ig-surface:    var(--surface);
  --ig-border:     var(--line);
  --ig-danger:     var(--danger-fg);
  --ig-danger-bg:  var(--danger-bg);
  --ig-success:    var(--success-fg);
  --ig-success-bg: var(--success-bg);
  --ig-radius:     var(--radius);
}
```

**Old `--ig-*` -> new canonical token alias / rename reference table.** Give this to every downstream section so references migrate intentionally.

| Old `--ig-*` | New canonical token | Old value -> new value | Notes |
|---|---|---|---|
| `--ig-blue` | `--blue-600` | `#0057b7` -> `#0057b7` | unchanged value; same Ukraine blue |
| `--ig-blue-dark` | `--navy-700` | `#00408a` -> `#13294b` | used as button hover; now deepens to navy |
| `--ig-yellow` | `--gold-500` | `#ffd700` -> `#d9a823` | lemon flag yellow retired for wheat-gold |
| `--ig-ink` | `--ink` | `#1a1a1a` -> `#101b30` | near-navy text |
| `--ig-muted` | `--muted` | `#5b6470` -> `#5c6678` | secondary text |
| `--ig-bg` | `--paper` | `#f7f9fc` -> `#f6f3ec` | warm paper page bg |
| `--ig-surface` | `--surface` | `#ffffff` -> `#ffffff` | unchanged |
| `--ig-border` | `--line` | `#e2e8f0` -> `#e6dfd1` | warm hairline |
| `--ig-danger` | `--danger-fg` | `#c0392b` -> `#b3261e` | error fg |
| `--ig-danger-bg` | `--danger-bg` | `#fdecea` -> `#fbe4e1` | error bg |
| `--ig-success` | `--success-fg` | `#1e7e34` -> `#1e7a3c` | success fg |
| `--ig-success-bg` | `--success-bg` | `#e8f6ec` -> `#e7f3ea` | success bg |
| `--ig-radius` | `--radius` | `10px` -> `14px` | softer corners |
| (new) | `--gold-700` | n/a -> `#7a5a0c` | REQUIRED for small gold text on light (AA); never use `--gold-500/600` for small text |

> **Naming note (contradiction resolved).** Some early drafts proposed renaming everything into a single `--ig-navy-900`/`--ig-gold-500` namespace. This spec rejects that: the **canonical names are the bare ramps** (`--navy-900`, `--blue-600`, `--gold-500`, …) and the `--ig-*` names exist **only as the back-compat alias layer above**. Any draft snippet that wrote `--ig-navy-900`, `--ig-gold-500`, `--ig-font-display`, `--ig-shadow-md`, `--ig-ease`, etc. must be read as the canonical equivalent (`--navy-900`, `--gold-500`, `--font-display`, `--shadow-md`, `--ease`).

**Downstream rule:** small gold text (labels, eyebrows, "official source" gold accents) MUST use `--gold-700`. `--gold-500/600` are for fills, rules, icons, and large display figures only — never for body-size text on paper.

### 3.3 Global atmosphere (`frontend/src/styles.css`)

Replace the existing `* { box-sizing }` + `html,body` + `a` block with the following. Warm-paper radial mesh (fixed), grain overlay (below the sticky topbar), `::selection`, global `:focus-visible` ring, smooth scroll.

```css
* { box-sizing: border-box; }

html { scroll-behavior: smooth; }

body {
  margin: 0;
  padding: 0;
  font-family: var(--font-ui);
  color: var(--ink);
  background-color: var(--paper);
  background-image:
    radial-gradient(120% 80% at 85% -10%, rgba(217,168,35,.10), transparent 55%),
    radial-gradient(90% 60% at -10% 0%, rgba(0,87,183,.07), transparent 50%);
  background-attachment: fixed;
  line-height: 1.55;
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
}

/* Optional film-grain overlay. z-index:1 keeps it ABOVE flat content but BELOW the
   sticky topbar (z-index:100) and toasts (z-index:1000). pointer-events:none. */
body::before {
  content: "";
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 1;
  opacity: .035;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
}

a { color: var(--blue-600); text-decoration: none; }
a:hover { text-decoration: underline; }

::selection { background: var(--gold-300); color: var(--navy-900); }

/* Global keyboard focus ring. Mouse clicks do not trigger :focus-visible. (a11y mandatory) */
:focus-visible {
  outline: 2.5px solid var(--blue-500);
  outline-offset: 3px;
  border-radius: 4px;
}
```

**Page wrapper.** Bump `.ig-container` to the concept width; keep the class name.

```css
.ig-container {
  max-width: var(--maxw);   /* 1120px, was 920px */
  margin: 0 auto;
  padding: 1.5rem 1rem 3rem;
}
```

> **Width contradiction resolved.** The canonical page width is **`--maxw` (1120px)**. The App-shell section MUST reconcile `.ig-topbar__inner`'s hard-coded `max-width:920px` to `var(--maxw)` so the bar aligns with the content column (see Section 7.1). Do not leave 920px anywhere.

**Stacking-order contract:** grain `body::before` = `z-index:1`; sticky topbar = `position:sticky; z-index:100`; toasts `.ig-toasts` = `z-index:1000`; skip link (if added) = `z-index:10000`.

### 3.4 Headings (`frontend/src/styles.css`)

```css
h1, h2, h3 {
  font-family: var(--font-display);
  color: var(--ink);
  letter-spacing: -.01em;
  line-height: 1.12;
}
h1 { font-weight: 800; font-size: clamp(1.9rem, 3.4vw, 2.7rem); margin: 0 0 .6rem; }
h2 { font-weight: 700; font-size: clamp(1.4rem, 2.4vw, 1.9rem); margin: 0 0 .5rem; }
h3 { font-weight: 700; font-size: 1.15rem; margin: 0 0 .4rem; }
```

### 3.5 Global component classes (`frontend/src/styles.css`)

Replace the existing component block (`.ig-card` … `.ig-hint`) with the following. This restyles every shared class against the new tokens, adds `.ig-field select/textarea` (so component inline overrides can be deleted), promotes `.ig-alert--info` and the `.ig-badge*` family to global, and adds `.ig-btn--gold`, `.ig-btn--primary`, `.ig-btn--lg`, `.ig-kicker`, `.ig-display`, `.ig-sr-only`, and the shared empty-state/page-head primitives.

```css
/* ---- Card ---- */
.ig-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 1.75rem;
  box-shadow: var(--shadow-md);
  margin-bottom: 1.25rem;
}

/* ---- Forms ---- */
.ig-form { display: flex; flex-direction: column; gap: 1rem; max-width: 420px; }
.ig-field { display: flex; flex-direction: column; gap: .35rem; }
.ig-field label { font-weight: 600; font-size: .9rem; color: var(--ink); }

/* Canonical control styling. Covers input/select/textarea so component-scoped
   select/textarea CSS (e.g. in search.component.ts) can be REMOVED. */
.ig-field input,
.ig-field select,
.ig-field textarea {
  width: 100%;
  box-sizing: border-box;
  font: inherit;
  color: var(--ink);
  padding: .6rem .7rem;
  border: 1px solid var(--line-2);
  border-radius: var(--radius-sm);
  background: var(--surface);
  transition: border-color .18s var(--ease), box-shadow .18s var(--ease);
}
.ig-field textarea { padding: .5rem .7rem; resize: vertical; }
.ig-field input:hover,
.ig-field select:hover,
.ig-field textarea:hover { border-color: var(--muted); }
.ig-field input:focus,
.ig-field select:focus,
.ig-field textarea:focus {
  outline: none;
  border-color: var(--blue-600);
  box-shadow: 0 0 0 3px rgba(0, 87, 183, .15);
}
.ig-field input::placeholder,
.ig-field textarea::placeholder { color: var(--muted); opacity: .8; }
.ig-field input[type="number"] { font-family: var(--font-mono); }

/* Reusable input atoms (used by Search; same look as .ig-field controls) */
.ig-input {
  width: 100%; box-sizing: border-box; font: inherit; color: var(--ink);
  padding: .65rem .75rem; border: 1px solid var(--line-2); border-radius: var(--radius-sm);
  background: var(--surface); transition: border-color .18s var(--ease), box-shadow .18s var(--ease);
}
.ig-input::placeholder { color: var(--muted); }
.ig-input:hover { border-color: var(--navy-700); }
.ig-input:focus-visible { outline: 2.5px solid var(--blue-500); outline-offset: 1px; border-color: var(--blue-500); }
.ig-select { appearance: none; padding-right: 2.2rem; background-repeat: no-repeat; background-position: right .8rem center;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='8' fill='none' stroke='%235c6678' stroke-width='2'%3E%3Cpath d='M1 1l5 5 5-5'/%3E%3C/svg%3E"); }
.ig-textarea { resize: vertical; min-height: 3.2rem; }
.ig-input--figure { font-family: var(--font-mono); font-weight: 700; }

/* ---- Buttons ---- */
.ig-btn {
  display: inline-flex; align-items: center; justify-content: center; gap: .5rem;
  padding: .6rem 1.2rem; border: 1px solid transparent; border-radius: var(--radius-sm);
  background: var(--blue-600); color: #fff;
  font-family: var(--font-ui); font-size: 1rem; font-weight: 700;
  cursor: pointer; text-decoration: none;
  box-shadow: 0 6px 18px -6px rgba(0, 87, 183, .55);
  transition: transform .18s var(--ease), box-shadow .25s var(--ease), background .2s var(--ease);
}
.ig-btn:hover:not(:disabled) {
  background: var(--blue-500);
  box-shadow: 0 10px 26px -8px rgba(0, 87, 183, .6);
  transform: translateY(-1px); text-decoration: none;
}
.ig-btn:active:not(:disabled) { transform: translateY(1px); }
.ig-btn:disabled { opacity: .55; cursor: not-allowed; box-shadow: none; }

/* .ig-btn--primary is an explicit alias of the default blue button (templates use it). */
.ig-btn--primary { background: var(--blue-600); color: #fff; box-shadow: 0 6px 18px -6px rgba(0,87,183,.55); }
.ig-btn--primary:hover:not(:disabled) { background: var(--blue-500); transform: translateY(-1px); box-shadow: 0 10px 26px -8px rgba(0,87,183,.6); }

.ig-btn--ghost {
  background: transparent; color: var(--navy-800);
  border-color: var(--line-2); box-shadow: none;
}
.ig-btn--ghost:hover:not(:disabled) {
  background: rgba(13, 31, 60, .04); border-color: var(--navy-800);
  box-shadow: none; transform: none;
}

.ig-btn--gold {
  background: linear-gradient(180deg, var(--gold-500), var(--gold-600));
  color: var(--navy-900);   /* dark text on gold => AA */
  box-shadow: 0 6px 18px -6px rgba(184, 134, 11, .6);
}
.ig-btn--gold:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 26px -8px rgba(184, 134, 11, .7);
}

.ig-btn--lg { padding: .8rem 1.6rem; font-size: 1rem; }

/* ---- Inline form text ---- */
.ig-error { color: var(--danger-fg); font-size: .85rem; }
.ig-muted { color: var(--muted); }
.ig-hint  { font-size: .82rem; color: var(--muted); font-family: var(--font-mono); }

/* ---- Editorial eyebrow / kicker + display title (shared) ---- */
.ig-kicker, .ig-eyebrow {
  font-family: var(--font-mono); font-size: .72rem; font-weight: 700;
  letter-spacing: .18em; text-transform: uppercase; margin: 0 0 .5rem;
}
.ig-kicker { color: var(--blue-600); }
.ig-eyebrow { display: inline-flex; align-items: center; gap: 10px; color: var(--gold-700); }
.ig-eyebrow::before { content: ""; width: 26px; height: 2px; background: var(--gold-500); }
.ig-display { font-family: var(--font-display); font-weight: 800; letter-spacing: -.01em; line-height: 1.1;
  color: var(--ink); margin: 0 0 .5rem; font-size: clamp(1.7rem, 3vw, 2.3rem); }
.ig-display--sm { font-size: clamp(1.35rem, 2.4vw, 1.8rem); }

/* ---- Alerts (error / success / info) ---- */
.ig-alert {
  padding: .75rem 1rem; border-radius: var(--radius-sm); font-size: .92rem;
  border: 1px solid transparent; border-left-width: 4px;
  display: flex; gap: 10px; align-items: flex-start;
}
.ig-alert--error   { background: var(--danger-bg);  color: var(--danger-fg);  border-left-color: var(--danger-fg); }
.ig-alert--success { background: var(--success-bg); color: var(--success-fg); border-left-color: var(--success-fg); }
/* Promoted from search/results components (delete the duplicates there). */
.ig-alert--info {
  background: var(--info-bg); color: var(--ink);
  border-color: var(--line); border-left-color: var(--blue-600); margin-bottom: 1rem;
}
.ig-alert a { font-weight: 700; }

/* ---- Badges (category + risk + status). Promoted to global so colors are consistent. ---- */
.ig-badge {
  display: inline-flex; align-items: center;
  font-family: var(--font-mono); font-size: .66rem; font-weight: 700;
  letter-spacing: .04em; text-transform: uppercase;
  padding: .2rem .5rem; border-radius: 6px; white-space: nowrap;
}
.ig-badge--cat  { background: rgba(0, 87, 183, .1); color: var(--blue-600); }
.ig-badge--risk { background: var(--line); color: var(--ink); }            /* fallback */
.ig-badge--risk[data-risk="LOW"]      { background: var(--risk-low-bg);  color: var(--risk-low-fg); }
.ig-badge--risk[data-risk="MODERATE"] { background: var(--risk-mod-bg);  color: var(--risk-mod-fg); }
.ig-badge--risk[data-risk="HIGH"]     { background: var(--risk-high-bg); color: var(--risk-high-fg); }
/* Account email-status badges */
.ig-badge--ok   { background: var(--risk-low-bg); color: var(--risk-low-fg); }
.ig-badge--warn { background: var(--risk-mod-bg); color: var(--risk-mod-fg); }

/* ---- Status chip (history) — shares the risk-badge language ---- */
.ig-chip {
  display: inline-flex; align-items: center; font-family: var(--font-mono);
  font-size: .64rem; font-weight: 700; letter-spacing: .06em; text-transform: uppercase;
  padding: .2rem .55rem; border-radius: 999px; white-space: nowrap; line-height: 1.4;
  background: var(--line); color: var(--muted);     /* neutral fallback */
}
.ig-chip[data-status="completed"] { background: var(--risk-low-bg);  color: var(--risk-low-fg); }
.ig-chip[data-status="pending"]   { background: var(--risk-mod-bg);  color: var(--risk-mod-fg); }
.ig-chip[data-status="failed"]    { background: var(--risk-high-bg); color: var(--risk-high-fg); }

/* ---- Screen-reader-only (shell + reusable) ---- */
.ig-sr-only {
  position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
  overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0;
}

/* ---- Page header (Account / Providers / History) ---- */
.ig-page-head { margin: 0 0 1.5rem; }

/* ---- Shared empty / utility state (Not-found, Placeholder, History empty) ---- */
.ig-empty { text-align: center; max-width: 44ch; margin: 0 auto; padding: 2.5rem 1rem 1.5rem; }
.ig-empty__mark {
  width: 64px; height: 64px; margin: 0 auto 1.25rem; border-radius: 16px; position: relative; overflow: hidden;
  background: linear-gradient(180deg, var(--blue-600) 0 50%, var(--gold-500) 50% 100%);
  box-shadow: inset 0 0 0 1px rgba(255,255,255,.25), var(--shadow-sm); transform: rotate(-6deg);
}
.ig-empty__code {
  font-family: var(--font-mono); font-weight: 700; font-size: .72rem; letter-spacing: .18em;
  text-transform: uppercase; color: var(--gold-700); display: block; margin-bottom: .5rem;
}
.ig-empty h1 { margin: 0 0 .5rem; }
.ig-empty p { color: var(--muted); margin: 0 0 1.5rem; }
.ig-empty__actions { display: flex; gap: .75rem; justify-content: center; flex-wrap: wrap; }
```

**Companion deletions in components** (allowed — they only remove now-redundant CSS this global block supersedes; no template/logic change, reduces per-component style size). The global rules are authoritative; duplicates SHOULD be removed by whoever owns each component so old lemon/`#eee` values don't linger:
- `search.component.ts` `styles[]`: delete `textarea {…}`, `select {…}`, `.ig-alert--info {…}` (keep `.ig-grid2`).
- `results.component.ts` `styles[]`: delete `.ig-badge*`, `.ig-alert--info` (and the rest become global — array ends empty; see Section 7.3).
- `account.component.ts`: delete locally-redefined `.ig-btn--ghost`, `.ig-badge`, `.ig-alert--info`.
- `providers.component.ts`: delete local `.ig-chip`, `.ig-alert--info`.

---

## 4. Motion system & accessibility baseline

### 4.1 Motion utilities (`frontend/src/styles.css`, append at end)

Canonical, CSS-only. Staggered **load reveal** (`.reveal` + `.d1..d5`), **scroll reveal** (`.onscroll` + `.in` + `.s1..s3`), and a hard reduced-motion off-switch. The keyframe is named `ig-rise` to avoid global collisions.

> **Naming normalization (contradiction resolved).** Some drafts used `.ig-reveal` / `.ig-reveal--d1..d5`. The canonical classes are **`.reveal` with delay modifiers `.d1`–`.d5`** (load reveal). All component templates that referenced `.ig-reveal--dN` must instead apply `class="reveal dN"`. The `ig-rise` keyframe is the single shared entrance animation; do not define per-section keyframes.

```css
@keyframes ig-rise {
  from { opacity: 0; transform: translateY(22px); }
  to   { opacity: 1; transform: none; }
}

/* Load reveal: element animates in on first paint. */
.reveal { opacity: 0; animation: ig-rise .8s var(--ease) forwards; }
.reveal.d1 { animation-delay: .08s; }
.reveal.d2 { animation-delay: .18s; }
.reveal.d3 { animation-delay: .30s; }
.reveal.d4 { animation-delay: .44s; }
.reveal.d5 { animation-delay: .58s; }

/* Scroll reveal: starts hidden; add class "in" (via IntersectionObserver) to play. */
.onscroll {
  opacity: 0; transform: translateY(26px);
  transition: opacity .7s var(--ease), transform .7s var(--ease);
}
.onscroll.in { opacity: 1; transform: none; }
.onscroll.s1 { transition-delay: .08s; }
.onscroll.s2 { transition-delay: .18s; }
.onscroll.s3 { transition-delay: .28s; }

/* Reduced motion: kill all animation/transition and ensure revealed content is visible
   even if an IntersectionObserver never runs. MANDATORY a11y. */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: .001ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: .001ms !important;
    scroll-behavior: auto !important;
  }
  .reveal, .onscroll { opacity: 1 !important; transform: none !important; }
}
```

**`.onscroll` contract.** Any component using `.onscroll` MUST ensure content is visible without JS: either its own `IntersectionObserver` adds `.in`, or it provides a visible/`<noscript>` fallback. The reduced-motion block already forces `.onscroll{opacity:1}`, so reduced-motion users are always safe; the JS-disabled case is the owner's responsibility. Use `.reveal` only for above-the-fold load animation — never gate critical content solely behind a scroll observer. **No new IntersectionObserver utility is required for the MVP scope below;** all sections use `.reveal` (load reveal), not `.onscroll`.

### 4.2 Accessibility baseline (applies app-wide)

- **Gold contrast.** Small gold text uses `--gold-700` (#7a5a0c) only — ~4.8:1 on `--paper`/`--surface` (AA). `--gold-500/600` FAIL on light and are restricted to fills, rules, icons, and large (>=24px/700) display figures. **Gold button text is navy** (`--navy-900` on the gold gradient) — AA. Never white-on-gold.
- **Body contrast.** `--ink #101b30` on `--paper` ~14:1; `--muted #5c6678` on paper ~5.4:1 (AA). On the dark navy token panel, body copy uses `rgba(255,255,255,.74)` (AA on `#081427`); `--gold-300`/`--blue-300` are used only for large/bold text on dark.
- **Visible focus.** Global `:focus-visible` ring (2.5px `--blue-500`, offset 3px). Form controls get an additional blue ring-shadow on `:focus`. Never remove outlines without a visible replacement.
- **Never color alone.** Risk/status are background+foreground pairs AND always carry a translated text label (LOW/MODERATE/HIGH, completed/pending/failed).
- **Reduced motion** fully honored (Section 4.1); revealed content forced visible.
- **Decorative elements.** Grain overlay and all decorative SVGs/marks are `aria-hidden="true"` (+ `focusable="false"` on SVGs) and `pointer-events:none` where overlaid.
- **Mobile nav reachability (mandatory).** The original concept hid primary nav and the token balance below 900/560px with no fallback. The app MUST NOT replicate that. A working responsive nav keeping all items — **including the token balance** — reachable is required (Section 7.1). Token balance must never be `display:none` at any width. Importing the concept's `.nav__links{display:none}` / `.balance{display:none}` rules is forbidden.
- **`--gold-700` is mandatory** for every small gold text/icon instance: eyebrows, kickers (gold variants), "official source"/ledger gold labels, disclaimer icons.

---

## 5. Internationalization & localization changes

ngx-translate stays the i18n mechanism. All visible copy goes through the `translate` pipe; no hardcoded user-facing strings; never `[innerHTML]` for translated copy. Both `frontend/public/i18n/uk.json` and `frontend/public/i18n/en.json` MUST stay **key-for-key identical** (same key sets). JSON files are UTF-8 — Cyrillic is fine; the ASCII-only rule applies only to `.ps1/.cmd/.bat`. Keep new strings using ASCII punctuation (`-`, `...`) consistent with the existing files.

### 5.1 Localization rules

- **Currency label.** A new pure pipe `igCurrency` maps the ISO code to a localized display word: **UAH -> `грн` (uk) / `UAH` (en); USD -> `USD` (both)**. It performs no math and never touches minor-unit formatting. Used for the standalone "Currency" fact, the search `optionsFor` heading, and the providers "Currencies" list (via `common.currencyUahShort`). The numeric amount from `money.util`/`DecimalPipe` is unchanged; `formatMinorUnits` still appends the ISO code on the min-amount cell (left as-is per "do not change math").
- **Per-year unit.** `common.perYear` is `/ рік` (uk) / `/ yr` (en) — confirmed present; reused verbatim for return figures (rendered as `%` + `common.perYear`).
- **Dash.** `common.dash` (`-`) reused for liquidity fallback instead of a hardcoded glyph.
- **New pipe file:** `frontend/src/app/core/i18n/currency-label.pipe.ts`

```ts
import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { SearchCurrency } from '../investment/investment.models';

/**
 * Localized currency *label* (display word only). UAH -> 'грн' (uk) / 'UAH' (en); USD -> 'USD'.
 * NEVER touches amounts or minor-unit math. Impure so it re-renders on runtime language switch.
 */
@Pipe({ name: 'igCurrency', standalone: true, pure: false })
export class CurrencyLabelPipe implements PipeTransform {
  private readonly translate = inject(TranslateService);
  transform(code: SearchCurrency | string | null | undefined): string {
    if (!code) { return ''; }
    return this.translate.instant('currency.' + code) || String(code);
  }
}
```

Add `CurrencyLabelPipe` to the `imports` of `SearchComponent` and `ResultsComponent`.

### 5.2 Consolidated new-keys table

Every key introduced by this redesign, with both locales. Keys are nested under the shown parent objects (add to existing objects; do not duplicate parent blocks). All existing keys are reused unchanged unless noted in Section 7.8 (the two `notFound.title` / non-additive changes).

| Key | uk | en |
|---|---|---|
| `a11y.skipToContent` *(only if a skip link is added)* | Перейти до вмісту | Skip to content |
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
| `category.MILITARY_BOND` | Військові ОВДП | War bonds |
| `category.GOV_BOND` | ОВДП | Gov bonds |
| `category.CASH_CURRENCY` | Валюта | Cash FX |
| `category.PRECIOUS_METALS` | Метали | Metals |
| `category.REAL_ESTATE` | Нерухомість | Real estate |
| `category.INDEX_ETF` | Індексні ETF | Index ETF |
| `category.FOREIGN_STOCKS` | Іноземні акції | Foreign stocks |
| `category.CRYPTO` | Криптовалюта | Crypto |
| `category.CORPORATE_BOND` | Корп. облігації | Corp. bonds |
| `category.CROWDLENDING` | Краудлендинг | Crowdlending |
| `category.PENSION_FUND` | НПФ | Pension fund |
| `category.LIFE_INSURANCE` | Страхування життя | Life insurance |
| `category.BUSINESS_EQUITY` | Стартапи | Startups |
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

> Notes: `risk.*` (raw-enum keyed) is intentionally distinct from the existing `providers.risk*`/`search.risk*` keys, which remain unchanged. `search.optionsFor` and `results.*` are reused. `providers.currencies`/`providers.minAmount`/`common.officialSource` already exist. The optional `a11y.skipToContent` is only required if a skip link is added to the Angular shell (it cannot live in static `index.html` because that cannot use the translate pipe).

**Copy change (non-additive, intentional):** `notFound.title` drops its leading `"404 - "` prefix (the new on-brand empty state renders the "404" as a separate badge). New value uk: `Сторінку не знайдено`; en: `Page not found`. `notFound.body` is unchanged. (Section 7.8.)

---

## 6. Document conventions for the component sections

Each subsection in Section 7 keeps the **Current / Target / Changes / Accessibility / Acceptance-criteria** structure. All shared rules referenced (`.ig-btn*`, `.ig-card`, `.ig-field*`, `.ig-alert*`, `.ig-badge*`, `.ig-chip`, `.ig-kicker`/`.ig-eyebrow`/`.ig-display`, `.ig-empty*`, `.ig-page-head`, motion, focus) are defined globally in Sections 3–4 and MUST NOT be re-declared per component. Component `styles[]` carry only genuinely component-local structure.

---

## 7. Component specifications

### 7.1 App shell, top navigation, language toggle, notifications

Files: `app.component.ts`, `core/errors/notification-host.component.ts`, `styles.css`, `index.html`, both i18n files.

**Current.** `ig-root` (standalone, OnPush): a non-sticky `<header class="ig-topbar">` with `.ig-topbar__inner` (max-width 920px), `.ig-brand` (blue/yellow half-split mark + "InvestGuide" with blue "UA"), `<nav class="ig-nav">` with auth-gated `@if (auth.isAuthenticated())` branches (links `/search`, `/history`, `/providers`, a `/tokens` `.ig-balance` pill rendering `auth.tokenBalance() | igPlural:'token'`, `/account`, a `.ig-linkbtn` sign-out; anonymous: `/login` + `/register` as `.ig-btn ig-btn--nav`), all `routerLinkActive="is-active"`; a `.ig-lang` toggle button with dynamic `aria-label` (`lang.toEnglish`/`lang.toUkrainian`) and `UA`/`/`/`EN` spans; a 4px `.ig-flag` bar; `<main class="ig-container">` + `<router-outlet/>`; an `.ig-sr-only aria-live="polite"` region bound to `lang.announcement()`; `<ig-notification-host/>`. At ≤560px the inner becomes a 2-row grid. `NotificationHostComponent` (`ig-notification-host`): fixed top-right `.ig-toasts` stack with severity-colored left borders, title/msg, optional action (routerLink) and copyable request-id, close button; `role="status"`, `aria-live="polite"`, `aria-atomic="true"`.

**Preserve verbatim.** `ngOnInit` (`lang.init()` then silent `auth.refresh()`), `logout()` (→ navigate `['/']`); `LanguageService` (`current()`, `toggle()`, `announcement()`, `<html lang>` sync, localStorage); `PluralPipe` (impure); the dynamic lang `aria-label` expression; all `@if` branches, `routerLink`/`routerLinkActive`, `igPlural` for the balance; the toast host's markup/logic, `role`/`aria-live`/`aria-atomic`, dismiss label, `&times;` glyph.

**Target.** Translucent, blurred, **sticky** paper topbar; Playfair brand; Manrope nav with gold underline on hover/active; mono wheat-gold balance pill; mono UA/EN pill with blue active; 5px blue/gold flag rule; warm toasts with `--shadow-md`, `--radius-sm`, mono request-id, gold (not lemon) warning border. **Functional addition:** a real mobile menu.

**Changes.**
- *Fonts:* per Section 3.1 (single `index.html` change).
- *Template:* restructure the header — add a mobile disclosure `<button class="ig-nav__toggle">` with `[attr.aria-expanded]="menuOpen()"`, `aria-controls="ig-primary-nav"`, translated `aria-label` (`nav.menuOpen`/`nav.menuClose`); give `<nav>` `id="ig-primary-nav"` and `[class.is-open]="menuOpen()"`; clicking a nav action sets `menuOpen.set(false)`. Keep every existing binding. On the lang button, **add** `[attr.aria-pressed]="lang.current() === 'en'"` and `lang="uk"`/`lang="en"` on the `UA`/`EN` spans (the `/` stays `aria-hidden`); the dynamic `aria-label` expression is unchanged. Brand becomes `InvestGuide<b>UA</b>`.
- *Component class:* add `protected readonly menuOpen = signal(false);`; in `logout()` also `this.menuOpen.set(false)`. Everything else unchanged.
- *Component styles (lean, structural only):* sticky bar `position:sticky; top:0; z-index:100; background:rgba(246,243,236,.82); backdrop-filter:saturate(140%) blur(12px)` (+ `-webkit-` fallback); **`.ig-topbar__inner { max-width: var(--maxw); }`** (reconciled from 920px per Section 3.3); brand mark 30px rounded, rotated, blue→`--gold-500` split; nav links `--muted` with animated `--gold-500` underline, `--ink` on hover/active; `.ig-balance` mono pill on light gold gradient with navy text and a gold dot (underline suppressed); `.ig-btn--nav` tightens padding (reuses global `.ig-btn`); `.ig-linkbtn` muted→danger on hover; `.ig-lang` mono pill, blue active span; `.ig-flag` 5px blue/gold split.
- *Responsive (the defect fix):* below **760px**, collapse action links into a dropdown panel anchored under the bar (`max-height` transition), but keep the **hamburger and UA/EN toggle always visible in the bar** and reveal **every** nav item — including the token balance — as full-width rows. Hamburger is a 44×44 touch target. Remove the old `≤560px` grid block (superseded). **Token balance must never be `display:none`.**
- *Notifications:* restyle `.ig-toasts`/`.ig-toast*` to warm surface, `--radius-sm`, `--shadow-md`, `--font-mono` request-id; warning border `--gold-600` (decorative); markup/logic/ARIA unchanged.

**Accessibility.** Global `:focus-visible` covers brand, links, balance pill, lang toggle, hamburger, CTA, toast controls. Lang button keeps dynamic `aria-label` + adds `aria-pressed` and `lang` attrs. `.ig-sr-only aria-live` region untouched. Hamburger is a real `<button>` with `aria-expanded`/`aria-controls`/translated label, ≥44×44. Nav links `--muted` (~5.9:1) → `--ink` on state. Reduced-motion neutralizes underline/panel/hover transitions. `backdrop-filter` degrades to the `.82` background.

**Acceptance criteria.**
- [ ] Sticky blurred paper topbar; serif `InvestGuideUA` (blue "UA"); gold-underline nav; mono balance pill; mono UA/EN pill; 5px blue/gold flag rule.
- [ ] All bindings preserved (`isAuthenticated()` branches, `routerLink`/`routerLinkActive`, `tokenBalance() | igPlural:'token'`, `toggle()`, dynamic `aria-label`, `announcement()` region, `logout()`).
- [ ] Lang button adds `aria-pressed` + `lang="uk"/"en"`; no `LanguageService`/`PluralPipe` change.
- [ ] At ≤760px: hamburger + UA/EN visible in the bar; toggling sets `aria-expanded` and reveals all items **including the balance**; selecting an item closes the menu; balance never `display:none`.
- [ ] `nav.menuOpen`/`nav.menuClose` in both locales; hamburger labeled via translate.
- [ ] Toasts warm surface, `--radius-sm`/`--shadow-md`, mono ref, gold warning border; ARIA/dismiss unchanged.
- [ ] `.ig-topbar__inner` width reconciled to `var(--maxw)`.
- [ ] `:focus-visible` and reduced-motion honored; component `styles[]` under budget.

### 7.2 Landing page

Files: `features/landing/landing.component.ts`, plus global/i18n.

**Current.** `ig-landing` (standalone, OnPush, `imports: [RouterLink, TranslateModule]`): `.ig-hero.ig-card` (centered `<h1>` `landing.title`, `.ig-lead`, `.ig-cta` with `/register` `.ig-btn` + `/login` `.ig-btn--ghost`, `.ig-hint`), `.ig-steps` (3 `.ig-card.ig-step` with circular `.ig-step__num`), `.ig-disclaimer.ig-muted`. `ngOnInit` redirects authenticated users to `/search`.

**Preserve.** Decorator metadata, `ngOnInit` redirect, all copy via `translate`, router links `/register`/`/login`, no API calls. `landing.title` stays in the bundle (may be used as a document title elsewhere — do NOT delete).

**Target.** Asymmetric editorial hero on warm paper: gold-rule mono eyebrow (`landing.eyebrow`, `--gold-700`), Playfair headline split into `landing.titleLead` + an italic blue `.ig-l-accent` span (`landing.titleAccent`) with a skewed gold underline highlight (no `[innerHTML]`), Manrope lead, gold primary CTA + ghost CTA, hint with check icon, and a dark navy "trust ledger" card (decorative sample figures — `25 000` + `14.5–16.0%` ranges; not money math, no `money.util`). Below: a thin blue/gold flag rule; a "How it works" section (mono kicker + serif `<h2>`) with an ordered list of 3 cards using italic serif `01/02/03` numerals and a blue→gold left accent bar that scales in on hover; a `--surface-2` disclaimer bar with a 4px gold left border + info icon.

**Changes.** Replace `template` and `styles`; keep decorator/class. Use `<ol>/<li>` for steps (real list semantics); decorative numerals and all SVGs `aria-hidden="true" focusable="false"` with `currentColor`; CTAs use global `.ig-btn--gold` and `.ig-btn--ghost`; entrance via `.reveal .d1..d4` (Section 4.1) — no IntersectionObserver. Component styles are lean (well under 4 kb): hero grid, eyebrow, title/accent, lead, ledger card (navy with radial mesh + gold corner rule + serif amount + mono labels), flag rule, how-section, step cards, disclaimer bar; `@media (max-width:900px)` collapses hero and steps to one column; `@media (prefers-reduced-motion)` disables step hover. New `landing.*` keys per Section 5.2.

**Accessibility.** One `<h1>`, section `<h2>`, step `<h3>`; sections use `aria-labelledby`; ledger `aria-label` = `landing.ledgerLabel`. Small gold (eyebrow, hint icon, disclaimer icon) uses `--gold-700`; on the dark ledger use `--gold-300`/`--blue-300`; blue interactive uses `--blue-600`. CTAs show the global focus ring. Reduced motion disables entrance + hover. At ≤900px nothing is hidden. Redirect keeps authed users off the page.

**Acceptance criteria.**
- [ ] Standalone/OnPush/imports unchanged; `ngOnInit` redirect byte-for-byte unchanged.
- [ ] Asymmetric hero: serif headline + italic blue accent + skewed gold underline, eyebrow, lead, gold + ghost CTAs, hint icon, navy trust-ledger card.
- [ ] Steps render as an `<ol>` of 3 cards with italic serif `01/02/03` and blue→gold hover accent under the "How it works" kicker + serif `<h2>`.
- [ ] `--surface-2` disclaimer bar with 4px gold left border + info icon.
- [ ] All text via `translate` (only the decorative ledger figures are literals); new keys in both locales, identical sets; no `[innerHTML]`.
- [ ] Gold small text/icons use `--gold-700`; focus ring on both CTAs; reduced-motion honored; ≤900px collapses with nothing hidden; build passes with no new style-budget warning.

### 7.3 Search form + Results renderer

Files: `features/search/search.component.ts`, `features/search/results.component.ts`, plus `core/i18n/currency-label.pipe.ts`, global/i18n, `money.util.ts` (read-only).

**Current.** `ig-search` (standalone, OnPush): `.ig-card` with `<h1>`, intro via `igPlural`, out-of-tokens `.ig-alert--info` with `/tokens` link, `.ig-form` (amount `<input type=number>` + currency `<select>` in `.ig-grid2`; horizon + risk selects in `.ig-grid2`, first option `[ngValue]="null"`=`search.any`; goals `<textarea maxlength=280>` + `n/280` hint), error `.ig-alert--error` with conditional Buy/Verify links, submit `.ig-btn` disabled at 0 tokens / while submitting; then a second `.ig-card` with `<h2>` `search.optionsFor` + `<ig-results>`. `ig-results` (standalone, OnPush, `@Input() result`): no-options alert or `<ul class="ig-options">` of `.ig-option` items (raw category/risk badges, `<dl>` facts: return `min–max% common.perYear`, raw currency, `formatMoney` min amount, liquidity fallback), optional rationale, official-source link (`target="_blank" rel="noopener noreferrer"`), `.ig-disclaimer` (always `result.disclaimer`, optional `result.currencyRiskDisclaimer`).

**Preserve.** All validators, `showError`, `submit()`, `mapError()` and its five branches, `toMinorUnits`/`formatMinorUnits`, the `language` field, `igPlural`, `@for` `track`, `[attr.data-risk]` (raw enum), the always-on/conditional disclaimers, `target="_blank" rel="noopener noreferrer"`. No selector/structure-semantic changes.

**Target.** Concept instrument cards. *Form:* paper inputs (`.ig-input`/`.ig-select`/`.ig-textarea`), mono amount, custom select chevron, Playfair `.ig-display` title + mono `.ig-kicker`, `.ig-btn--lg` submit. *Results:* `.ig-opt` cards — `--surface-2` header (provider + instrument + category/risk badges on a dashed rule), a big serif `min–max` return figure with a mono `% / yr` unit on the baseline and a right-aligned 2-line gold `--gold-700` label (`margin-left:auto; text-align:right; max-width:11ch; line-height:1.25` so it never breaks the baseline), a 2-col `<dl>` facts grid, muted rationale, a full-width `--surface-2` official-source footer, and the gold-left-border disclaimer bar.

**Changes.**
- *Search template:* restyle field shells; add `novalidate` (suppresses native bubbles; submit still gated by `form.invalid`), `aria-invalid`/`aria-describedby` + `role="alert"` on the amount error (additive a11y). `search.optionsFor` formats currency via `igCurrency` (numeric `amount/100 | number` unchanged; USD stays USD). Add `CurrencyLabelPipe` to imports. Delete the component's `textarea`/`select`/`.ig-alert--info` styles (now global; Section 3.5); keep `.ig-grid2`, `.ig-form--wide`, `.ig-search__head`, `.ig-results-wrap` (~5 lines).
- *Results template:* render `.ig-opt` cards; **localize** badge labels via `category.` + `opt.category` and `risk.` + `opt.riskLevel` while `[attr.data-risk]` keeps the raw enum driving color; split the return figure into `.ig-opt__fig` (serif, `&ndash;`), `.ig-opt__unit` (mono `% ` + `common.perYear`), `.ig-opt__lbl` (gold-700, `results.expectedReturn`); currency fact uses `igCurrency`; min-amount uses unchanged `formatMoney` (still ISO-suffixed); liquidity fallback uses `common.dash`; entrance via `class="reveal d{1..5}"` (Section 4.1, normalized from the draft's `ig-reveal--dN`). Add `CurrencyLabelPipe` to imports. **`results.component.ts` `styles[]` becomes empty** — every card/badge/disclaimer rule is global (Section 3.5 + the results block below), guaranteeing the history-detail page (which reuses `ig-results`) renders identically.
- *Global styles to add (Section 3.5 companion):* the `.ig-options`/`.ig-opt*`/`.ig-fact*`/`.ig-disclaimer*` instrument-card block (auto-fit grid, surface-2 header with dashed divider, serif figure, mono units/labels, gold-700 label and disclaimer emphasis, surface-2 source footer; `@media (max-width:560px)` single-column facts).

**Accessibility.** All gold text `--gold-700`; risk badge pairs AA; global focus ring on all controls/links; reduced-motion disables hover/reveal/lift; `novalidate` + `role="alert"`/`aria-invalid`/`aria-describedby`; `role="status"` on info/no-options, `role="note"` on disclaimer; decorative SVGs `aria-hidden`; source link keeps text; risk shows localized text (never color-only); `<dl>/<dt>/<dd>` preserved.

**Acceptance criteria.**
- [ ] No change to any validator/signal/`submit()`/`mapError()` branch/`routerLink`/`track`/`[attr.data-risk]` value/`formatMinorUnits`/`toMinorUnits`/`language`.
- [ ] Form: paper inputs, mono amount, custom chevron, Playfair title, mono kicker; submit disabled at 0 tokens/while submitting; amount error on touched/dirty; `n/280` counter; out-of-tokens + Buy link; all five `mapError` messages + Buy/Verify links.
- [ ] Each option = instrument card with surface-2 dashed header, big serif `min–max` figure with mono `% / рік`(`% / yr`) on the baseline and a 2-line gold label that does not break the baseline; 2-col facts; rationale only when present; full-width source footer with `rel="noopener noreferrer"`.
- [ ] Category/risk show localized words while `data-risk` still drives color; currency shows `грн`(uk)/`UAH`(en)/`USD`, numeric formatting unchanged.
- [ ] Disclaimer always shows `result.disclaimer`; currency-risk only when present (gold-700 + gold left border); no-options alert preserved; history-detail renders identically.
- [ ] New keys in both locales; `common.perYear` confirmed `/ рік`/`/ yr`; no hardcoded copy; `results.component.ts` styles empty; search keeps ~5 layout lines; build passes 8 kb budget.

### 7.4 Buy tokens + Payment result

Files: `features/payments/tokens.component.ts`, `features/payments/payment-result.component.ts`, plus global/i18n.

**Current.** `tokens.component.ts`: `.ig-card` with `<h1>`, intro (`igPlural`), `@if` chain `loading()`/`loadError()`/empty/loaded; loaded shows optional `buyError()` alert + `<ul class="ig-packs">` `@for (pack of packs(); track pack.id)` with tokens/price/per-token and a `.ig-btn--primary` `(click)="buy(pack)"` disabled when `buyingId() !== null`; then fineprint + `/search` back link. `TokenPack` has no `featured` field. `payment-result.component.ts`: centered `.ig-card.ig-result`, `<h1>`, `@switch (state())` over `polling`/`success`/`failed`/`processing`/`missing` with the existing alerts and router links.

**Preserve.** All `.ts` logic: signals, `ngOnInit`, `buy()` (stash `PENDING_PAYMENT_KEY`, `window.location.href = res.pageUrl`), polling/backoff, `price()`/`perToken()` (minor-unit math), `OnPush`, `igPlural`, all `tokens.*`/`paymentResult.*` keys; on the result page the `state`/`ResultState`, `@Input() paymentId`, `resolvedId`, `BACKOFF_MS` polling, `finishSuccess()` (`auth.loadMe()` → `balanceRefreshed` → `state.set('success')`), `ngOnDestroy` cleanup.

**Target.** *Tokens:* a **dark institutional section** (`.ig-section--dark` navy-900 with a faint gold radial) holding pack cards — tokens count in mono gold, price in Playfair, per-token mono muted-on-dark; the **middle pack of exactly three** carries a gold diagonal "Рекомендовано/Recommended" ribbon and a gold CTA; others blue. Derive the recommended pack in the template (`packs().length === 3 && $index === 1`) — display-only, no model/logic change. *Payment result:* keep the centered paper card (contrast to the dark pack wall); add a mono kicker + Playfair title, a per-state accent rail (`[attr.data-state]`), a reduced-motion-safe polling spinner, `.reveal` entrance, and `role="status"`/`role="alert"` live regions.

**Changes.** Tokens: wrap section in `.ig-section--dark`; convert `<ul>/<li>` to the pack grid; ribbon `aria-hidden`; CTA toggles `.ig-btn--gold`/`.ig-btn--primary` by recommended index; component styles carry the dark-section + pack visuals (~1.6 kb). Payment result: per-state markup wrappers; component styles carry `.ig-result` layout, kicker, accent rail by `data-state`, spinner + `ig-spin` keyframe with a reduced-motion fallback (~1.1 kb). Shared `.ig-btn--gold`/alerts/`.reveal`/focus are global (Sections 3–4). New keys `tokens.kicker`, `tokens.recommended` (+optional `recommendedSr`), `paymentResult.kicker` per Section 5.2.

**Accessibility.** Gold-on-light small text uses `--gold-700`; on the dark panel only large/bold `--gold-300` is used; back link bumps to `--gold-100` if needed. Global focus ring on all CTAs/links. Reduced-motion disables `.reveal` and the spinner. Live regions: polling/success/processing = `role="status"`; failed/error = `role="alert"`. Ribbon decorative (`aria-hidden`); recommendation also conveyed by the gold CTA (+ optional SR-only text). Page stacks to one column at ≤900px (mobile nav reachability is the shell's responsibility, Section 7.1).

**Acceptance criteria.**
- [ ] No `.ts` logic edits; only `template:`/`styles:` strings change.
- [ ] `buy()` still stashes `PENDING_PAYMENT_KEY` and sets `window.location.href`; polling/backoff/`loadMe` unchanged.
- [ ] All five result states render with correct router links (`/search`, `/tokens`, `/account`).
- [ ] `/tokens` is a dark section; mono/Playfair pack cards; middle-of-three pack shows the gold ribbon + gold CTA, others blue.
- [ ] New keys in both locales; existing keys untouched; no hardcoded copy.
- [ ] OnPush + `ig-` selectors retained; build passes 8 kb budget; AA gold via `--gold-700`; focus + reduced-motion honored; money formatting unchanged.

### 7.5 Auth pages (login, register, verify)

Files: `features/auth/login.component.ts`, `register.component.ts`, `verify.component.ts`, plus global/i18n.

**Current.** All three standalone, OnPush, `ig-`-prefixed, with the shared inline style `.ig-auth { max-width:480px; margin:0 auto } .ig-auth h1 { margin-top:0 }`, leaning on global `.ig-card`/`.ig-form`/`.ig-field`/`.ig-btn`/`.ig-alert`/`.ig-error`/`.ig-hint`/`.ig-muted`. Login: email/password, `serverError()` alert, disabled-while-`submitting()`, `/register` link; success → `/search`; 401/422 → generic `login.invalidCredentials` (no enumeration). Register: two-state via `registeredEmail()` (form state with `showError`-gated inline errors + `PASSWORD_PATTERN`; check-email state with `register.sentLink {email}` + token-grant `register.verifyToActivate`). Verify: `@switch (state())` over five branches incl. `verify.success {tokens}` via `igPlural`, `@Input() token`.

**Preserve verbatim.** Every `formControlName`/`autocomplete`/`[formGroup]`/`(ngSubmit)`/`[disabled]`, `routerLink`, `@if`/`@switch`/`@case`, all keys + interpolation params, `showError()`, `PASSWORD_PATTERN`, error mapping, `igPlural`, token-grant copy, `@Input() token`.

**Target.** A centered "credential document": warm paper page, a single elevated white `.ig-card.ig-auth` (`clamp(360px, 92vw, 460px)`) with a 3px blue/gold flag top-rule, an optional JetBrains-Mono `--gold-700` eyebrow, a Playfair `<h1>`, Manrope fields with a visible blue focus ring, a full-width blue primary button, left-accent green/red alerts (status never gold), and a single restrained `ig-rise` reveal. Verify states keep token-grant wording.

**Changes.** Wrap each card in `<div class="ig-auth--page">` (flex-centered, `min-height`) — routing untouched. Add `.ig-auth__eyebrow` `<p>` per state (login: `login.eyebrow`; register: `register.eyebrow` / `register.eyebrowSent`; verify: `verify.eyebrow`). Move all shared rules to global `styles.css` — add `.ig-auth`/`.ig-auth--page`/`.ig-auth::before` (flag rule)/`.ig-auth__eyebrow`/`.ig-auth h1` and refine `.ig-card`/`.ig-field input`/`.ig-btn`/`.ig-alert*` (already in Sections 3.5/4.1). Each component's `styles` array becomes empty (`styles: []`) or is removed. New eyebrow keys per Section 5.2 (if the team declines eyebrows, drop the `<p>` and keys; the top-rule alone carries identity).

**Accessibility.** Eyebrow/any small gold text uses `--gold-700` on white (AA); body `--ink`, hints `--muted`, alerts green/red (never gold). Global focus ring + input focus ring on inputs/submit/links; never remove outlines. Reduced-motion disables reveal + transitions. One `<h1>` per state; eyebrow is a `<p>` (not a heading); inline `<span class="ig-error">` stays adjacent and only renders when `showError()`; inputs keep `for`/`id`/`autocomplete`. `.ig-auth--page` uses `min-height`+flex so short viewports scroll; card `overflow:hidden` only clips the 3px top-rule. Card `clamp` prevents overflow.

**Acceptance criteria.**
- [ ] All three render in a centered white `.ig-card.ig-auth` with the 3px blue/gold top-rule on warm paper; Playfair heading, Manrope body.
- [ ] Each component's `styles` array is empty/removed; shared rules global; under budget.
- [ ] Login: fields + `serverError()` + disabled-while-`submitting()` + `/register` work; success → `/search`; 401/422 → generic `login.invalidCredentials`.
- [ ] Register: both states render; `showError` conditions + `PASSWORD_PATTERN` + `EMAIL_TAKEN`/`VALIDATION_ERROR`/fallback unchanged; `register.sentLink {email}`; `register.verifyToActivate` preserved.
- [ ] Verify: all five branches render; `verify.success {tokens: balance() | igPlural:'token'}`; `@Input() token` + `PluralPipe` intact; router links work.
- [ ] New eyebrow keys in both locales; no hardcoded copy; focus ring visible; gold uses `--gold-700`; alerts green/red; reduced-motion honored.

### 7.6 History + History detail

Files: `features/history/history.component.ts`, `features/history/history-detail.component.ts`, `features/search/results.component.ts` (NOT modified here — owned by Section 7.3), plus global/i18n.

**Current.** `ig-history` (standalone, OnPush; `imports: [RouterLink, DatePipe, DecimalPipe, TranslateModule, PluralPipe]`): `.ig-card` with `<h1>`, four states (loading/error/empty/list) on `loading()`/`error()`/`page()`. List rows are `<a [routerLink]="['/history', item.id]">` with amount (`item.input.amount/100 | number:'1.2-2'` + currency), date, a `.ig-hist__status [attr.data-status]` chip (`statusKey()` → translate), and option count (`igPlural`). Pager: ghost prev/next with `[disabled]` bounds + `history.pageOf {current,total}` (`Math.max(totalPages,1)`). `statusKey()`, `PAGE_SIZE=10`, `go(page)`. `ig-history-detail` (standalone, OnPush; no styles): `.ig-card` with loading/notFound/result/error; result shows a `←` back link, `<h1>` amount, and the shared `<ig-results>`. `@Input() id`; 404 mapping via `parseApiError`/`status===404`.

**Preserve.** All signals + order; `go()`/pagination math + `[disabled]`; `statusKey()`; `PAGE_SIZE`; subscriptions + error mapping; `@Input() id`; every `routerLink`; the `ig-results` reuse; all listed keys; `amount/100 | number:'1.2-2'` formatting.

**Target.** History list = an editorial "ledger": mono `.ig-kicker` (`history.kicker`) + Playfair title; each row a card with a blue→gold left flag-rule that grows on hover/focus, mono tabular amount, muted small-caps currency, a `.ig-chip` status pill reusing the risk-badge language, and a chevron affordance. Status chips map to the `--risk-*` pairs (completed=green, pending=amber `--risk-mod`, failed=red) — including a real `pending` rule the original lacked. Pager becomes centered ghost buttons flanking a mono page indicator with a hairline top divider, wrapped in `<nav aria-label="history.paginationLabel">`. Empty state becomes an editorial `.ig-empty` panel with a primary CTA to `/search` (not an inline link). Detail page mirrors the chrome: a mono back link with an animated chevron, a Playfair amount heading with mono currency, the unchanged `<ig-results>` below; not-found reuses `.ig-empty` with a back link.

**Changes.** History: add `.ig-page-head` (kicker + title); restructure rows with `class="ig-hist__row reveal d{1..5}"` (capped at d5), rename the chip class to `.ig-chip` (keeps `[attr.data-status]="item.status"`), drop the inline `·` separators (no copy lost), add a decorative chevron SVG; wrap the pager in `<nav>`; replace the empty alert with `.ig-empty`. Component styles: `.ig-hist*`, `.ig-chip` overrides (already global in Section 3.5 — only layout-specific extras stay local), `.ig-pager*`; `@media (max-width:560px)` stacks rows and hides the chevron (row stays one tap target). Detail: add a small `styles` array (`.ig-back*`, `.ig-detail__amount`/`__cur`, `.ig-empty__title`); replace the literal `←` with an inline SVG chevron (key `historyDetail.backToHistoryArrow` unchanged); reuse `.ig-empty` for not-found. New keys `history.kicker`, `history.paginationLabel` per Section 5.2. **`results.component.ts` is not touched here.**

**Accessibility.** `--risk-*` pairs AA; pending uses `--risk-mod-fg` (never `--gold-500/600`); chips carry translated text. Kicker uses `--blue-600`; the empty icon uses `--gold-700`. Global focus ring on rows/buttons/CTA/back link; rows also reveal the accent bar on `:focus-visible`. Reduced-motion forces `.reveal` visible and disables transitions. Decorative SVGs `aria-hidden`; pager `<nav>` labeled. Single `<h1>` per view. Row is one large `<a>` (≥44px); stacks but stays one target on mobile.

**Acceptance criteria.**
- [ ] List rows show mono amount, muted currency, date, status chip, option count; row routes to `['/history', item.id]`.
- [ ] Chips correct for completed/pending/failed with translated text; unknown status → neutral fallback (no crash).
- [ ] `statusKey()`/`PAGE_SIZE`/`go()`/`[disabled]`/`history.pageOf` behavior byte-for-byte unchanged.
- [ ] Empty state = editorial panel with working `/search` CTA; loading/error unchanged.
- [ ] Detail shows serif amount heading, mono back link to `/history`, unchanged `<ig-results>`; not-found/error preserved (404 mapping intact).
- [ ] `amount/100 | number:'1.2-2'` + currency unchanged.
- [ ] `history.kicker`/`history.paginationLabel` in both locales; files parse; identical key sets; no existing key removed.
- [ ] No hardcoded copy; OnPush + `ig-` selectors; under budget; gold uses `--gold-700`; chip pairs AA; focus rings visible; rows visible under reduced motion; `ig-results` file unmodified.

### 7.7 Account / Providers / Not-found / Placeholder

Files: `features/account/account.component.ts`, `features/providers/providers.component.ts`, `features/not-found/not-found.component.ts`, `features/placeholder/placeholder.component.ts`, plus global/i18n; `money.util.ts` and `results.component.ts` read-only.

**Account (`ig-account`).** *Current:* `.ig-card` with `<h1>`, loading/profile/error branches on `loading()`/`user()`; profile `<dl class="ig-profile">` (email, email status badge + conditional `/verify` link, token balance `igPlural` + `/tokens` link, roles), `.ig-actions` sign-out button, `.ig-danger-zone` with mailto `<a [href]>` + reveal button → `.ig-alert--info`. *Preserve:* `auth.user` source, `loading`/`deletionRequested` signals, `ngOnInit` `auth.loadMe()`, `deletionMailto()` (translate.instant of `account.mailtoSubject`/`mailtoBody`), `logout()` → `/`, all `routerLink`s, the `mailto:` href, `igPlural`, `<dl>/<dt>/<dd>` semantics, element choices (sign-out `<button>`, deletion `<a [href]>`). *Target/changes:* add `.ig-page-head` eyebrow (`account.eyebrow`) + Playfair `<h1>`; profile becomes a mono-labeled ledger with hairline rows, balance `<dd class="ig-balance">` in mono; danger zone becomes a quiet `--surface-2` gold-left-ruled panel (not alarming red); status badges use global `.ig-badge--ok`/`--warn` (delete local redefinitions of `.ig-btn--ghost`/`.ig-badge`/`.ig-alert--info`); optional `.reveal` on profile/actions/danger-zone. New key `account.eyebrow`. *A11y:* sign-out stays `<button>`, deletion stays `<a [href="mailto:">`; badges keep text; inline links use `--blue-600`; focus ring visible; reveal inert under reduced motion.

**Providers (`ig-providers`).** *Current:* `.ig-card` with `<h1>` + intro + loading/error/empty/list; `<ul class="ig-providers">` of `.ig-provider` cards with head (name + category chip), description, a 4-fact `<dl>` (typical return via `returnRange`, risk via `riskLabel`, currencies `join`, min amount via `minAmount`→`formatMinorUnits`), external source link; `/search` back link. *Preserve:* `providerService.list()` subscription, signals, `CATEGORY_KEYS`/`RISK_KEYS`, `categoryLabel`/`riskLabel`/`returnRange`/`minAmount`, the `formatMinorUnits` call, external-link `rel`/`target`, back link, `track p.id`. *Target/changes:* match the results instrument-card family — move the **risk** out of the `<dl>` up beside the category chip as `.ig-badge--risk [attr.data-risk]="p.riskLevel"` (text via `riskLabel`); add a signature return-figure block (Playfair `returnRange` + mono `common.perYear` + gold-700 `providers.typicalReturn` label); drop the now-duplicated typical-return/risk `<dl>` entries, leaving currencies + min amount; add a display-only `currencyList(p)` that maps `UAH`→`common.currencyUahShort` and joins (no math change; `minAmount()` unchanged, still ISO-suffixed). Component styles model the `.ig-opt` card (tinted dashed head, return block, 2-col mono fact grid, surface-2 source footer); delete local `.ig-chip`/`.ig-alert--info`. New keys `providers.typicalReturn`, `common.currencyUahShort`. *A11y:* risk keeps text label (AA pairs); category chip `--blue-600` on light blue (AA); gold `__lbl` uses `--gold-700`; external link keeps `target/rel` + visible focus on the surface-2 footer; `loadError()` still renders the translated string.

**Not-found (`ig-not-found`).** *Current:* `.ig-card` with `<h1>` `notFound.title` (currently "404 - …"), body, `/` link. *Preserve:* `routerLink="/"`, all keys, OnPush/standalone/`imports`. *Target/changes:* on-brand `.ig-empty` (brand mark tile + mono gold "404" `.ig-empty__code` + Playfair `<h1>` + muted body + `.ig-btn--primary` to `/`). Change `notFound.title` to drop the leading "404 - " (badge supplies the code) — uk `Сторінку не знайдено`, en `Page not found`; `notFound.body` unchanged. No component styles added. *A11y:* mark `aria-hidden`; "404" text remains readable (`--gold-700`); single `<h1>`; button focus ring; static (reduced-motion safe).

**Placeholder (`ig-placeholder`).** *Current:* `.ig-card` with `<h1>{{ heading || ('placeholder.comingSoon' | translate) }}`, body, `/` link; `@Input() heading?` from route data. *Preserve:* `@Input() heading` + the `heading || (...)` fallback, `routerLink="/"`, all keys. *Target/changes:* same `.ig-empty` shell as Not-found but with a "coming soon" eyebrow code (`placeholder.badge`) instead of a numeral; keep the dynamic `heading` `<h1>`. No component styles added. New key `placeholder.badge`. *A11y:* mark `aria-hidden`; single `<h1>` reflecting route `heading`; button focus ring; static.

**Acceptance criteria (combined).**
- [ ] Account: `auth.loadMe()` in `ngOnInit`; branches unchanged; balance via `igPlural`; `deletionMailto()` href + `deletionRequested` toggle + `logout()`→`/` unchanged; Playfair title, mono ledger labels, hairline rows, gold-ruled (not red) danger zone; `account.eyebrow` in both locales; no duplicated global primitives; under budget.
- [ ] Providers: subscription/signals/four mapping methods unchanged; `minAmount()`→`formatMinorUnits` no math change; risk now a `data-risk` color badge in the head; category chip retained; typical return as Playfair figure + mono `common.perYear`, not duplicated in the grid; currencies show `грн`(uk)/`UAH`(en) via `common.currencyUahShort`, `USD` unchanged, min-amount still ISO-suffixed; new keys in both files; cards match the results family; under budget.
- [ ] Not-found: `routerLink="/"` + `common.backToHome` preserved; "404" appears once (badge); centered brand empty-state; `notFound.title` updated in both files, `notFound.body` unchanged; no per-component CSS.
- [ ] Placeholder: `@Input() heading` + fallback unchanged; matches Not-found styling differing only by badge text; `placeholder.badge` in both files; no per-component CSS.
- [ ] All four: standalone + OnPush + `ig-` selectors; no new deps; AA gold via `--gold-700`; focus rings; reduced-motion respected.

---

## 8. Implementation constraints & conventions (consolidated HARD CONSTRAINTS)

1. **Restyle only.** No changes to TypeScript logic, signals, reactive forms, validators (`amount` min 0.01, `goals` maxlength 280, `PASSWORD_PATTERN`), services, guards, the auth interceptor, polling/backoff, or error mapping. Only `template:` and `styles:` strings, `index.html` font links, additive i18n keys (+ the two explicit copy changes), and the one new display pipe.
2. **No new dependencies.** Angular 17+ standalone, Java 21 / Spring Boot 3.x / MongoDB backend untouched. Components remain standalone, `ChangeDetectionStrategy.OnPush`, `ig-`-prefixed. No deprecated/outdated libraries introduced.
3. **Money is always integer minor units (kopiykas); never float.** `money.util` (`toMinorUnits`, `formatMinorUnits`) is read-only. The `igCurrency` pipe and all "localized currency" treatments are display-only and never touch amounts. Decorative ledger/sample figures on the landing page are not money math.
4. **All visible copy via ngx-translate.** No hardcoded user-facing strings; never `[innerHTML]` for translated copy. `uk.json` and `en.json` stay key-for-key identical. No existing key removed/renamed (the only value edits are the explicitly listed `notFound.title`).
5. **Single source for shared CSS.** Tokens, fonts wiring, `.ig-btn*`, `.ig-card`, `.ig-field*`/`.ig-input*`, `.ig-alert*`, `.ig-badge*`, `.ig-chip`, `.ig-kicker`/`.ig-eyebrow`/`.ig-display`, `.ig-empty*`, `.ig-page-head`, `.ig-sr-only`, motion utilities, and the focus ring live in global `frontend/src/styles.css`. Component `styles[]` carry only component-local structure and MUST stay under the production `anyComponentStyle` budget (8 kb error / 4 kb warn).
6. **Canonical names.** Use the bare ramp tokens (`--navy-*`, `--blue-*`, `--gold-*`, `--paper`, `--surface*`, `--ink`, `--muted`, `--line*`, `--risk-*`, `--font-*`, `--radius*`, `--shadow-*`, `--ease`, `--maxw`) in all new CSS; `--ig-*` exists only as the alias layer. Use `.reveal .d1..d5` (not `.ig-reveal--dN`). `--gold-700` for all small gold text.
7. **Accessibility is required, not optional** (Section 4.2): AA contrast, visible `:focus-visible`, reduced-motion, never-color-alone, working mobile nav with the token balance always reachable.
8. **Page width** is `var(--maxw)` (1120px) everywhere, including `.ig-topbar__inner`.
9. **Encoding/verification (CLAUDE.md).** Only `.html`/`.css`/`.ts`/`.json` are touched here — all UTF-8, Cyrillic allowed. **No `.ps1/.cmd/.bat` are modified**, so the ASCII-only Windows-script rule is N/A; the non-ASCII scan over executed scripts therefore has nothing to flag. Do not add a BOM to `index.html`. New i18n strings use ASCII punctuation (`-`, `...`) to match the existing files. Verification of this redesign is by **running the production build** (`ng build` / `npm run build`) and confirming no `anyComponentStyle` budget warning/error and that the stylesheet parses — reviewing rendered text in chat is not sufficient. State explicitly whether the build was actually run or whether verification was static-only; never imply a build passed when it was only read.
10. **Mandatory two-role sub-agent review.** Per project settings, each non-trivial phase is reviewed by at least two role sub-agents (suggested: FE lead + QA/accessibility), and that review MUST include an actual build/parse, not just a read. This review is automatic and required — run it as part of completing the work, do not offer it as an optional follow-up.

---

## 9. Suggested implementation phasing

Each phase is independently buildable and verifiable (`ng build` passes, no budget regression). Later phases depend only on Phase A.

- **Phase A — Foundation (blocking for all others).** `index.html` font links (Section 3.1); the full `:root` token block + alias layer (3.2); global atmosphere + `.ig-container` width (3.3); headings (3.4); all global component classes incl. `.ig-btn*`, `.ig-field*`/`.ig-input*`, `.ig-alert*`, `.ig-badge*`, `.ig-chip`, `.ig-kicker`/`.ig-eyebrow`/`.ig-display`, `.ig-empty*`, `.ig-page-head`, `.ig-sr-only` (3.5); motion utilities + reduced-motion (4.1). Verify: every existing component still compiles/renders via the alias layer with no component edits; build passes budgets. Add the `igCurrency` pipe file (Section 5.1) here so later phases can import it.
- **Phase B — App shell / nav / notifications (Section 7.1).** Sticky topbar, brand, nav, balance pill, lang toggle, the responsive mobile menu (defect fix), toast restyle; reconcile `.ig-topbar__inner` to `--maxw`; add `nav.menuOpen`/`nav.menuClose`.
- **Phase C — Landing + Search + Results (7.2, 7.3).** Editorial hero + trust ledger; instrument-card form + results; `igCurrency` wired; category/risk/currency localization; results `styles[]` emptied. Add the Phase-C i18n keys.
- **Phase D — Tokens / Payments / Auth (7.4, 7.5).** Dark token section + ribbon; payment-result states + spinner; centered auth document cards. Add the Phase-D i18n keys.
- **Phase E — History / Account / Providers / Not-found / Placeholder (7.6, 7.7).** Ledger history list + chips + pager + empty state + detail chrome; account dossier; provider instrument cards; on-brand empty states. Add the Phase-E i18n keys; apply the `notFound.title` copy change. `results.component.ts` is not modified in this phase.

After each phase: run the production build, confirm no `anyComponentStyle` warning, toggle UK/EN to confirm new copy resolves in both locales, and run the mandatory two-role sub-agent review (incl. an actual build).

---

## 10. Global Definition of Done / acceptance checklist

- [ ] **Builds within budget.** `ng build` / `npm run build` (production) succeeds with **no `anyComponentStyle` warning (>4 kb) or error (>8 kb)** for any component; global `styles.css` carries all shared rules; the stylesheet parses. Build was actually run (not static-only).
- [ ] **Fonts loaded** once via `index.html` (Playfair Display, Manrope, JetBrains Mono, Cyrillic + Latin, `display=swap`, both preconnects); no BOM added.
- [ ] **Tokens + aliases.** `:root` contains the full navy/blue/gold ramps (incl. `--gold-700`), warm-paper neutrals, risk/status tokens, the three `--font-*`, `--radius*`/`--shadow-*`/`--ease`/`--maxw`; every legacy `--ig-*` still resolves via the alias layer with the new palette.
- [ ] **All text via i18n in both locales.** No hardcoded user-facing strings; `uk.json` and `en.json` parse and have **identical key sets**; runtime UK↔EN toggle updates every restyled surface (eyebrows, titles, badges, currency labels, ledger/sample labels). Every new key from Section 5.2 present in both files; the `notFound.title` copy change applied in both.
- [ ] **AA contrast.** Body `--ink`/`--muted` on paper, all risk/status fg/bg pairs, gold buttons (navy-on-gold), and every small gold text instance (using `--gold-700`) meet WCAG AA; spot-checked.
- [ ] **Reduced motion.** `prefers-reduced-motion: reduce` disables all animation/transition and forces `.reveal`/`.onscroll` content visible; spinners fall back to a static state.
- [ ] **Focus.** Global `:focus-visible` ring visible on every interactive element (brand, nav, balance pill, lang toggle, hamburger, CTAs, form controls, source/router links, toast controls); no outline removed without a visible replacement.
- [ ] **Mobile nav.** At ≤760px the hamburger + UA/EN toggle stay visible and the menu reveals all items including the token balance; token balance is never `display:none`.
- [ ] **OnPush preserved.** All components remain standalone, `ChangeDetectionStrategy.OnPush`, `ig-`-prefixed; no new dependencies; Angular version unchanged.
- [ ] **No behavior regressions.** No change to logic, signals, validators, routing, guards, interceptor, polling, error mapping, or money math (minor-unit integers only); `igCurrency` and currency localization are display-only; `formatMinorUnits`/`toMinorUnits` outputs unchanged; the shared `ig-results` renderer renders identically on Search and History-detail.
- [ ] **Encoding/verification (CLAUDE.md).** No `.ps1/.cmd/.bat` touched (ASCII-only scan N/A / nothing to flag); edited files are UTF-8 with Cyrillic where applicable; verification by running the build, not by reading rendered text.
- [ ] **Mandatory review.** Each phase passed the two-role sub-agent review (FE lead + QA/accessibility) including an actual build/parse, with findings applied or reported.
