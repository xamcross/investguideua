/**
 * Build-time sitemap.xml + robots.txt generator (feature 006-seo-optimization, T018).
 *
 * Single source of truth = the prerender allow-list (`prerender-routes.txt`): the sitemap lists
 * exactly the URLs that were prerendered to static HTML, so it can never reference a URL that 404s.
 * hreflang alternates (uk / en / x-default) are emitted only between language variants that are
 * BOTH in the prerender list. robots.txt allows public paths, disallows the private/utility
 * prefixes, and references the sitemap.
 *
 * Plain Node ESM (no transpiler dependency). Run via `npm run seo:generate` AFTER the build.
 * Output is written into the build output root (dist/investguide-frontend/browser/).
 *
 * NOTE: not a Windows-executed script (.ps1/.cmd/.bat), so UTF-8/LF is fine (Constitution V).
 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const FRONTEND_ROOT = join(__dirname, '..', '..');

const SITE_ORIGIN = (process.env.SEO_SITE_ORIGIN || 'https://investguideua.com').replace(/\/+$/, '');
const OUTPUT_DIR = join(FRONTEND_ROOT, 'dist', 'investguide-frontend', 'browser');
const PRERENDER_LIST = join(FRONTEND_ROOT, 'prerender-routes.txt');

/** Private/utility path prefixes - must mirror seo-routes.config.ts PRIVATE_PREFIXES. */
const PRIVATE_PREFIXES = [
  '/login', '/register', '/verify', '/search', '/history',
  '/tokens', '/payments', '/account', '/providers',
];

/** Read the prerendered public paths (one per line). */
function readPrerenderedPaths() {
  if (!existsSync(PRERENDER_LIST)) {
    throw new Error(`prerender-routes.txt not found at ${PRERENDER_LIST}`);
  }
  return readFileSync(PRERENDER_LIST, 'utf-8')
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#'));
}

/** Strip a leading /en prefix to get the canonical (uk) path. */
function toCanonical(path) {
  if (path === '/en' || path === '/en/') return '/';
  return path.startsWith('/en/') ? path.slice(3) : path;
}

function isPrivate(path) {
  const c = toCanonical(path);
  return PRIVATE_PREFIXES.some((p) => c === p || c.startsWith(p + '/'));
}

function abs(path) {
  return SITE_ORIGIN + (path === '/' ? '/' : path);
}

function buildSitemap(paths) {
  const set = new Set(paths);
  // Group by canonical path so each appears once with its language alternates.
  const canonicals = [...new Set(paths.map(toCanonical))].sort();
  const today = process.env.SEO_BUILD_DATE || new Date().toISOString().slice(0, 10);

  const urls = canonicals.map((canon) => {
    const ukPath = canon;
    const enPath = canon === '/' ? '/en' : '/en' + canon;
    const hasUk = set.has(ukPath);
    const hasEn = set.has(enPath);
    // loc = the uk (canonical) URL if prerendered, else the en URL.
    const loc = hasUk ? abs(ukPath) : abs(enPath);
    const alts = [];
    if (hasUk) alts.push(['uk', abs(ukPath)]);
    if (hasEn) alts.push(['en', abs(enPath)]);
    if (hasUk) alts.push(['x-default', abs(ukPath)]);
    const altXml = alts
      .map(([hl, href]) => `    <xhtml:link rel="alternate" hreflang="${hl}" href="${href}"/>`)
      .join('\n');
    return `  <url>\n    <loc>${loc}</loc>\n    <lastmod>${today}</lastmod>\n${altXml}\n  </url>`;
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
  const lines = ['User-agent: *', 'Allow: /'];
  for (const p of PRIVATE_PREFIXES) {
    lines.push(`Disallow: ${p}`);
    lines.push(`Disallow: /en${p}`);
  }
  lines.push('', `Sitemap: ${SITE_ORIGIN}/sitemap.xml`, '');
  return lines.join('\n');
}

function main() {
  if (!existsSync(OUTPUT_DIR)) {
    throw new Error(`Build output not found at ${OUTPUT_DIR}. Run "npm run build" first.`);
  }
  const all = readPrerenderedPaths();
  const publicPaths = all.filter((p) => !isPrivate(p));
  const dropped = all.filter(isPrivate);
  if (dropped.length) {
    console.warn(`[seo:generate] WARNING: ${dropped.length} private path(s) in prerender list were excluded from sitemap: ${dropped.join(', ')}`);
  }

  writeFileSync(join(OUTPUT_DIR, 'sitemap.xml'), buildSitemap(publicPaths), 'utf-8');
  writeFileSync(join(OUTPUT_DIR, 'robots.txt'), buildRobots(), 'utf-8');
  console.log(`[seo:generate] Wrote sitemap.xml (${publicPaths.length} public URL group(s)) and robots.txt to ${OUTPUT_DIR}`);
  console.log(`[seo:generate] Site origin: ${SITE_ORIGIN}`);
}

main();
