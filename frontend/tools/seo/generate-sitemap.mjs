/**
 * Build-time sitemap.xml + robots.txt + llms.txt generator
 * (feature 006-seo-optimization, T018; extended by 010-seo-aeo-optimization).
 *
 * Single source of truth = seo-manifest.json (emitted by build-articles.mjs). It carries each public
 * route's true lastmod (article dateModified, not the build date - fixes the 006 lastmod bug),
 * indexability, kind, title and description. This generator:
 *   - sitemap.xml : one <url> per indexable manifest entry, with the entry's true <lastmod> and
 *                   hreflang alternates (uk / x-default; en only when EN_ENABLED and an en variant
 *                   exists - currently never, so no /en alternate is advertised: regression guard).
 *   - robots.txt  : Allow public, Disallow the private/utility prefixes, PLUS explicit allow stanzas
 *                   for the major AI crawlers (single AI_CRAWLERS source for one-line reversal).
 *   - llms.txt    : an LLM-readable index of the indexable pages (title + absolute URL + description).
 *
 * Staleness guard: throws if the manifest is missing OR if its article set does not match the
 * prerendered output (so a stale manifest can never silently emit wrong lastmod / wrong URLs).
 *
 * Plain Node ESM (no transpiler). Run via `npm run seo:generate` AFTER the build. Output is written
 * into the build output root (dist/investguide-frontend/browser/).
 *
 * NOTE: not a Windows-executed script (.ps1/.cmd/.bat), so UTF-8/LF is fine (Constitution V).
 */
import { readFileSync, writeFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const FRONTEND_ROOT = join(__dirname, '..', '..');

const SITE_ORIGIN = (process.env.SEO_SITE_ORIGIN || 'https://investguideua.com').replace(/\/+$/, '');
const OUTPUT_DIR = join(FRONTEND_ROOT, 'dist', 'investguide-frontend', 'browser');
const MANIFEST = join(FRONTEND_ROOT, 'seo-manifest.json');

/**
 * Whether the English (/en) mirror is published. Mirror of EN_ENABLED in seo-routes.config.ts -
 * keep in sync. While false, no en hreflang alternate or /en sitemap entry is emitted (010 FR-018).
 */
const EN_ENABLED = false;

/** Private/utility path prefixes - must mirror seo-routes.config.ts PRIVATE_PREFIXES. */
const PRIVATE_PREFIXES = [
  '/login', '/register', '/verify', '/search', '/history',
  '/tokens', '/payments', '/account', '/providers',
];

/** Major AI crawlers explicitly welcomed (010 FR-006). Single source - edit here to reverse a policy. */
const AI_CRAWLERS = ['GPTBot', 'Google-Extended', 'PerplexityBot', 'ClaudeBot'];

/** One-line site summary for llms.txt. */
const LLMS_SUMMARY =
  'Curated, catalog-grounded investment guidance for Ukrainians. Independent, editor-reviewed ' +
  'explainers on bank deposits, war bonds (OVDP), currency savings, and avoiding scams.';

function loadManifest() {
  if (!existsSync(MANIFEST)) {
    throw new Error(
      `seo-manifest.json not found at ${MANIFEST}. Run "npm run seo:articles" (the build script ` +
      `does this first) before "npm run seo:generate".`,
    );
  }
  const entries = JSON.parse(readFileSync(MANIFEST, 'utf-8'));
  if (!Array.isArray(entries) || entries.length === 0) {
    throw new Error('seo-manifest.json is empty or malformed.');
  }
  return entries;
}

/** Slugs of articles actually prerendered to dist/.../articles/<slug>/index.html. */
function prerenderedArticleSlugs() {
  const dir = join(OUTPUT_DIR, 'articles');
  if (!existsSync(dir)) return new Set();
  const slugs = new Set();
  for (const e of readdirSync(dir)) {
    const p = join(dir, e);
    if (statSync(p).isDirectory() && existsSync(join(p, 'index.html'))) slugs.add(e);
  }
  return slugs;
}

/** Throw if the manifest's article set does not match the prerendered output (stale manifest guard). */
function assertManifestFresh(entries) {
  const manifestSlugs = new Set(
    entries
      .filter((e) => e.kind === 'article')
      .map((e) => e.path.replace(/^\/articles\//, '')),
  );
  const builtSlugs = prerenderedArticleSlugs();
  const missing = [...manifestSlugs].filter((s) => !builtSlugs.has(s));
  const extra = [...builtSlugs].filter((s) => !manifestSlugs.has(s));
  if (missing.length || extra.length) {
    throw new Error(
      `seo-manifest.json is stale: it does not match the prerendered output. ` +
      `In manifest but not built: [${missing.join(', ')}]. Built but not in manifest: [${extra.join(', ')}]. ` +
      `Re-run "npm run build" so the manifest and prerender are from the same build.`,
    );
  }
}

function isPrivate(path) {
  return PRIVATE_PREFIXES.some((p) => path === p || path.startsWith(p + '/'));
}

function abs(path) {
  return SITE_ORIGIN + (path === '/' ? '/' : path);
}

function buildSitemap(entries) {
  const urls = entries.map((e) => {
    const ukPath = e.path;
    const enPath = ukPath === '/' ? '/en' : '/en' + ukPath;
    const alts = [['uk', abs(ukPath)]];
    if (EN_ENABLED) alts.push(['en', abs(enPath)]);
    alts.push(['x-default', abs(ukPath)]);
    const altXml = alts
      .map(([hl, href]) => `    <xhtml:link rel="alternate" hreflang="${hl}" href="${href}"/>`)
      .join('\n');
    return `  <url>\n    <loc>${abs(ukPath)}</loc>\n    <lastmod>${e.lastmod}</lastmod>\n${altXml}\n  </url>`;
  });

  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"\n' +
    '        xmlns:xhtml="http://www.w3.org/1999/xhtml">\n' +
    urls.join('\n') +
    '\n</urlset>\n'
  );
}

function buildRobots() {
  const disallows = [];
  for (const p of PRIVATE_PREFIXES) {
    disallows.push(`Disallow: ${p}`);
    disallows.push(`Disallow: /en${p}`);
  }
  const blocks = [];
  // Default block for all agents.
  blocks.push(['User-agent: *', 'Allow: /', ...disallows].join('\n'));
  // Explicit allow blocks for the major AI crawlers (inherit the same private Disallows).
  for (const ua of AI_CRAWLERS) {
    blocks.push([`User-agent: ${ua}`, 'Allow: /', ...disallows].join('\n'));
  }
  return blocks.join('\n\n') + `\n\nSitemap: ${SITE_ORIGIN}/sitemap.xml\n`;
}

/** Strip the trailing " - InvestGuideUA" brand suffix from a manifest title for clean llms link text. */
function cleanTitle(title) {
  return title.replace(/\s*-\s*InvestGuideUA\s*$/, '').trim();
}

function buildLlms(entries) {
  const guides = entries.filter((e) => e.kind === 'article');
  const pages = entries.filter((e) => e.kind === 'static');
  const lines = [`# InvestGuideUA`, '', `> ${LLMS_SUMMARY}`, ''];
  if (guides.length) {
    lines.push('## Guides', '');
    for (const e of guides) {
      lines.push(`- [${cleanTitle(e.title)}](${abs(e.path)}): ${e.description}`);
    }
    lines.push('');
  }
  if (pages.length) {
    lines.push('## About', '');
    for (const e of pages) {
      lines.push(`- [${cleanTitle(e.title)}](${abs(e.path)}): ${e.description}`);
    }
    lines.push('');
  }
  return lines.join('\n');
}

function main() {
  if (!existsSync(OUTPUT_DIR)) {
    throw new Error(`Build output not found at ${OUTPUT_DIR}. Run "npm run build" first.`);
  }
  const manifest = loadManifest();
  assertManifestFresh(manifest);

  const publicEntries = manifest.filter((e) => e.indexable && !isPrivate(e.path));
  const dropped = manifest.filter((e) => isPrivate(e.path));
  if (dropped.length) {
    console.warn(`[seo:generate] WARNING: ${dropped.length} private path(s) in manifest were excluded: ${dropped.map((e) => e.path).join(', ')}`);
  }

  writeFileSync(join(OUTPUT_DIR, 'sitemap.xml'), buildSitemap(publicEntries), 'utf-8');
  writeFileSync(join(OUTPUT_DIR, 'robots.txt'), buildRobots(), 'utf-8');
  writeFileSync(join(OUTPUT_DIR, 'llms.txt'), buildLlms(publicEntries), 'utf-8');
  console.log(`[seo:generate] Wrote sitemap.xml (${publicEntries.length} URL(s)), robots.txt (+${AI_CRAWLERS.length} AI crawlers), and llms.txt to ${OUTPUT_DIR}`);
  console.log(`[seo:generate] Site origin: ${SITE_ORIGIN}`);
}

main();
