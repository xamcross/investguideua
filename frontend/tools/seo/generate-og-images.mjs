/**
 * Build-time per-article Open Graph image generator (feature 013-seo-improvements).
 *
 * Before 013 every page shared the single generic /og-default.png, so article links pasted into
 * social/chat apps all looked identical. This script renders a branded 1200x630 PNG per published
 * article (title + brand mark on the site's paper/ink/gold palette) into public/seo/og/<slug>.png,
 * which the angular.json assets rule ships at /og/<slug>.png. build-articles.mjs stamps the same
 * path as each article's default ogImage, and the SEO audit (A29) asserts the asset exists.
 *
 * Runs as `npm run seo:og` AFTER seo:articles (reads seo-manifest.json) and BEFORE `ng build`
 * (so the assets copy picks the images up). Rendering is @resvg/resvg-js (prebuilt native, no
 * browser needed) with the committed OFL TTFs in tools/seo/fonts/ - fully deterministic across
 * Windows dev machines and the Cloudflare Pages Linux builder (no system-font dependence).
 *
 * Plain Node ESM (not a Windows .ps1/.cmd/.bat script) -> UTF-8/LF is fine (Constitution V).
 */
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Resvg } from '@resvg/resvg-js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const FRONTEND_ROOT = join(__dirname, '..', '..');
const MANIFEST = join(FRONTEND_ROOT, 'seo-manifest.json');
const OUT_DIR = join(FRONTEND_ROOT, 'public', 'seo', 'og');
const FONT_DIR = join(__dirname, 'fonts');

const WIDTH = 1200;
const HEIGHT = 630;
// Site palette (mirror src/styles.css tokens).
const PAPER = '#f6f3ec';
const INK = '#101b30';
const MUTED = '#5c6678';
const BLUE = '#0057b7';
const GOLD = '#d9a823';

function esc(s) {
  return s
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

/**
 * Greedy word-wrap using a rough average-glyph-width estimate (Playfair Display ~0.56em for
 * mixed Ukrainian text). Overlong single words are kept whole - the estimate leaves margin.
 */
function wrap(text, fontSize, maxWidth) {
  const maxChars = Math.max(8, Math.floor(maxWidth / (fontSize * 0.56)));
  const lines = [];
  let line = '';
  for (const word of text.split(/\s+/)) {
    const candidate = line ? line + ' ' + word : word;
    if (candidate.length > maxChars && line) {
      lines.push(line);
      line = word;
    } else {
      line = candidate;
    }
  }
  if (line) lines.push(line);
  return lines;
}

function titleSvg(title) {
  const boxWidth = WIDTH - 160;
  let fontSize = 62;
  let lines = wrap(title, fontSize, boxWidth);
  if (lines.length > 2) {
    fontSize = 52;
    lines = wrap(title, fontSize, boxWidth);
  }
  if (lines.length > 3) {
    lines = lines.slice(0, 3);
    lines[2] = lines[2].replace(/\s*\S*$/, '') + '...';
  }
  const lineHeight = Math.round(fontSize * 1.22);
  // Vertically center the title block between the brand row and the footer rule.
  const blockTop = 170 + Math.round((330 - lines.length * lineHeight) / 2);
  return lines
    .map(
      (l, i) =>
        `<text x="80" y="${blockTop + (i + 1) * lineHeight}" font-family="Playfair Display" ` +
        `font-weight="700" font-size="${fontSize}" fill="${INK}">${esc(l)}</text>`,
    )
    .join('\n  ');
}

function buildSvg(title) {
  return `<svg width="${WIDTH}" height="${HEIGHT}" viewBox="0 0 ${WIDTH} ${HEIGHT}" xmlns="http://www.w3.org/2000/svg">
  <rect width="${WIDTH}" height="${HEIGHT}" fill="${PAPER}"/>
  <rect x="0" y="0" width="12" height="${HEIGHT}" fill="${BLUE}"/>
  <rect x="12" y="0" width="6" height="${HEIGHT}" fill="${GOLD}"/>
  <rect x="80" y="76" width="34" height="12" fill="${BLUE}"/>
  <rect x="80" y="88" width="34" height="12" fill="${GOLD}"/>
  <text x="132" y="101" font-family="Manrope" font-weight="700" font-size="34" fill="${INK}">InvestGuideUA</text>
  ${titleSvg(title)}
  <rect x="80" y="536" width="72" height="4" fill="${GOLD}"/>
  <text x="80" y="582" font-family="Manrope" font-weight="400" font-size="26" fill="${MUTED}">investguideua.com</text>
</svg>`;
}

function render(svg) {
  const resvg = new Resvg(svg, {
    fitTo: { mode: 'width', value: WIDTH },
    font: {
      loadSystemFonts: false,
      fontFiles: [
        join(FONT_DIR, 'PlayfairDisplay-700.ttf'),
        join(FONT_DIR, 'Manrope-400.ttf'),
        join(FONT_DIR, 'Manrope-700.ttf'),
      ],
      defaultFontFamily: 'Manrope',
    },
  });
  return resvg.render().asPng();
}

function main() {
  if (!existsSync(MANIFEST)) {
    throw new Error(`seo-manifest.json not found at ${MANIFEST}. Run "npm run seo:articles" first.`);
  }
  const entries = JSON.parse(readFileSync(MANIFEST, 'utf-8')).filter((e) => e.kind === 'article');
  // Clear stale images first: renamed/unpublished article PNGs would otherwise linger locally and
  // ship as orphan /og/*.png via the public/seo assets glob.
  rmSync(OUT_DIR, { recursive: true, force: true });
  mkdirSync(OUT_DIR, { recursive: true });
  for (const e of entries) {
    const slug = e.path.replace(/^\/articles\//, '');
    writeFileSync(join(OUT_DIR, `${slug}.png`), render(buildSvg(e.title)));
  }
  console.log(`[seo:og] Rendered ${entries.length} per-article OG image(s) to ${OUT_DIR}`);
}

main();
