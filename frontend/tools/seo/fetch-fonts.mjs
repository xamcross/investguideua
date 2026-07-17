/**
 * One-time font self-hosting fetcher (feature 013-seo-improvements). NOT part of the build.
 *
 * Downloads the woff2 subsets (latin + cyrillic) for the site's three families from the Google
 * Fonts CSS API and emits:
 *   - public/fonts/<family>[-italic]-<subset>.woff2  (committed)
 *   - src/fonts.css  (committed; listed in angular.json "styles" ahead of styles.css)
 *
 * Why: loading fonts from fonts.googleapis.com/fonts.gstatic.com puts a third-party DNS+TLS
 * round-trip on the critical rendering path (LCP risk on slow mobile connections) and adds an
 * availability dependency. Self-hosting serves the same OFL-licensed files from our own origin
 * with immutable caching (see public/seo/_headers).
 *
 * Dedup: the css2 API answers modern Chrome with the SAME variable-font file for every requested
 * discrete weight of a family/style/subset. Downloading is therefore grouped by
 * (family, style, subset) and byte-identical responses collapse into ONE file declared with a
 * `font-weight: <min> <max>` range - otherwise a page using N weights would fetch the same bytes
 * N times on first visit. If Google ever returns genuinely different files per weight, the
 * fetcher falls back to per-weight files for that group automatically.
 *
 * Re-run only when the family/weight set changes: `node tools/seo/fetch-fonts.mjs`, then commit
 * the refreshed outputs (and update the preload links in src/index.html if filenames changed).
 * Requires Node 18+ (global fetch).
 */
import { createHash } from 'node:crypto';
import { mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const FRONTEND_ROOT = join(__dirname, '..', '..');
const FONT_DIR = join(FRONTEND_ROOT, 'public', 'fonts');
const CSS_OUT = join(FRONTEND_ROOT, 'src', 'fonts.css');

/** Must mirror the family/weight set the UI uses (see src/styles.css --font-* tokens). */
const CSS2_URL =
  'https://fonts.googleapis.com/css2' +
  '?family=Playfair+Display:ital,wght@0,500;0,700;0,800;0,900;1,500' +
  '&family=Manrope:wght@400;500;600;700;800' +
  '&family=JetBrains+Mono:wght@500;700' +
  '&display=swap';

/** Ukrainian text needs latin + cyrillic (Google's cyrillic range includes U+0490-0491). */
const SUBSETS = new Set(['latin', 'cyrillic']);

/** A modern-Chrome UA so the API answers with subset woff2 @font-face blocks. */
const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) ' +
  'Chrome/126.0.0.0 Safari/537.36';

function slugify(family) {
  return family.toLowerCase().replace(/\s+/g, '-');
}

function faceCss({ family, style, weightMin, weightMax, file, unicodeRange, subset }) {
  const weight = weightMin === weightMax ? String(weightMin) : `${weightMin} ${weightMax}`;
  return (
    `/* ${subset} */\n@font-face {\n` +
    `  font-family: '${family}';\n` +
    `  font-style: ${style};\n` +
    `  font-weight: ${weight};\n` +
    `  font-display: swap;\n` +
    `  src: url(/fonts/${file}) format('woff2');\n` +
    `  unicode-range: ${unicodeRange};\n` +
    `}`
  );
}

async function download(url) {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`${url} answered ${r.status}`);
  return Buffer.from(await r.arrayBuffer());
}

async function main() {
  const res = await fetch(CSS2_URL, { headers: { 'User-Agent': UA } });
  if (!res.ok) throw new Error(`Google Fonts CSS API answered ${res.status}`);
  const css = await res.text();

  // Blocks look like: /* cyrillic */\n@font-face { ... url(https://...woff2) ... unicode-range: ...; }
  const blockRe = /\/\*\s*([a-z-]+)\s*\*\/\s*(@font-face\s*\{[^}]*\})/g;
  const entries = [];
  for (const m of css.matchAll(blockRe)) {
    const subset = m[1];
    const face = m[2];
    if (!SUBSETS.has(subset)) continue;
    const family = (face.match(/font-family:\s*'([^']+)'/) || [])[1];
    const weight = Number((face.match(/font-weight:\s*(\d+)/) || [])[1]);
    const italic = /font-style:\s*italic/.test(face);
    const remote = (face.match(/url\((https:[^)]+\.woff2)\)/) || [])[1];
    const unicodeRange = (face.match(/unicode-range:\s*([^;]+);/) || [])[1];
    if (!family || !weight || !remote || !unicodeRange) {
      throw new Error(`Unparseable @font-face block:\n${face}`);
    }
    entries.push({ family, weight, italic, subset, remote, unicodeRange });
  }
  if (entries.length === 0) throw new Error('No @font-face blocks matched - API markup changed?');

  // Group by family/style/subset and collapse byte-identical weight files.
  const groups = new Map();
  for (const e of entries) {
    const key = `${e.family}|${e.italic ? 'italic' : 'normal'}|${e.subset}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(e);
  }

  rmSync(FONT_DIR, { recursive: true, force: true });
  mkdirSync(FONT_DIR, { recursive: true });
  const faces = [];
  let filesWritten = 0;
  for (const group of groups.values()) {
    group.sort((a, b) => a.weight - b.weight);
    const { family, italic, subset, unicodeRange } = group[0];
    const buffers = new Map(); // remote url -> {buffer, hash}
    for (const e of group) {
      if (!buffers.has(e.remote)) {
        const buffer = await download(e.remote);
        buffers.set(e.remote, { buffer, hash: createHash('sha256').update(buffer).digest('hex') });
      }
    }
    const hashes = new Set([...buffers.values()].map((b) => b.hash));
    const style = italic ? 'italic' : 'normal';
    const baseName = `${slugify(family)}${italic ? '-italic' : ''}-${subset}`;
    if (hashes.size === 1) {
      const file = `${baseName}.woff2`;
      writeFileSync(join(FONT_DIR, file), [...buffers.values()][0].buffer);
      filesWritten++;
      faces.push(faceCss({
        family, style, subset, unicodeRange, file,
        weightMin: group[0].weight, weightMax: group[group.length - 1].weight,
      }));
    } else {
      // Distinct static files per weight - keep one file + face per weight.
      for (const e of group) {
        const file = `${slugify(family)}-${e.weight}${italic ? '-italic' : ''}-${subset}.woff2`;
        writeFileSync(join(FONT_DIR, file), buffers.get(e.remote).buffer);
        filesWritten++;
        faces.push(faceCss({
          family, style, subset, unicodeRange: e.unicodeRange, file,
          weightMin: e.weight, weightMax: e.weight,
        }));
      }
    }
  }

  const header =
    '/* AUTO-GENERATED by tools/seo/fetch-fonts.mjs (feature 013) - self-hosted Google Fonts\n' +
    ' * (latin + cyrillic woff2 subsets, OFL-1.1). Do not edit by hand; re-run the fetcher when\n' +
    ' * the family/weight set changes. Served from /fonts/ with immutable caching (_headers). */\n\n';
  writeFileSync(CSS_OUT, header + faces.join('\n') + '\n', 'utf-8');
  console.log(`[fonts] Wrote ${filesWritten} woff2 file(s) to ${FONT_DIR} (${readdirSync(FONT_DIR).length} total)`);
  console.log(`[fonts] Wrote ${CSS_OUT} (${faces.length} @font-face rules)`);
}

main();
