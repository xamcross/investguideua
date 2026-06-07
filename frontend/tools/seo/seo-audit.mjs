/**
 * SEO acceptance audit (feature 006-seo-optimization, T049; extended by 010-seo-aeo-optimization).
 *
 * Runs against the prerendered build output and FAILS (exit 1) on any violation. This is the
 * executable definition of done. Verification is by parsing build bytes, not by reading rendered
 * text (Constitution Principle V). Plain Node ESM, no transpiler.
 *
 * 006 checks (A1-A16) are retained. 010 adds: A16' (real verification token), A17 (AI-crawler
 * allow stanzas), A18 (llms.txt coverage), A19 (editorial trust block), A20 (editorial/contact
 * routes), A21 (true sitemap lastmod), A22 (no /en alternate while EN disabled), A23 (img alt),
 * A24 (Organization @id graph), A25 (analytics beacon).
 *
 * Scope: the /en mirror is a later increment; the audit requires uk + x-default hreflang and FAILS
 * if an en alternate appears while EN_ENABLED is false (A22).
 */
import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..', '..');
const OUT = join(ROOT, 'dist', 'investguide-frontend', 'browser');
const MANIFEST = join(ROOT, 'seo-manifest.json');
const MIN_ARTICLES = 8;

/** Major AI crawlers that must be explicitly allowed (mirror generate-sitemap.mjs AI_CRAWLERS). */
const AI_CRAWLERS = ['GPTBot', 'Google-Extended', 'PerplexityBot', 'ClaudeBot'];
/** Private prefixes that must never leak into llms.txt / sitemap (mirror generate-sitemap.mjs). */
const PRIVATE_PREFIXES = [
  '/login', '/register', '/verify', '/search', '/history',
  '/tokens', '/payments', '/account', '/providers',
];

let failures = 0;
let checks = 0;
function check(cond, msg) {
  checks++;
  if (!cond) { failures++; console.error(`  FAIL: ${msg}`); }
}
function group(name) { console.log(`\n[audit] ${name}`); }

function read(rel) {
  const f = join(OUT, rel);
  return existsSync(f) ? readFileSync(f, 'utf-8') : null;
}
function attr(html, re) {
  const m = html.match(re);
  return m ? m[1] : null;
}

// Collect prerendered public HTML pages (index.html files under the output, excluding the static
// 404.html which is not a routed page).
function findPages(dir, base = '') {
  const out = [];
  for (const e of readdirSync(dir)) {
    const p = join(dir, e);
    const rel = base ? `${base}/${e}` : e;
    if (statSync(p).isDirectory()) out.push(...findPages(p, rel));
    else if (e === 'index.html') out.push(rel);
  }
  return out;
}

if (!existsSync(OUT)) {
  console.error(`[audit] Build output not found at ${OUT}. Run "npm run build" first.`);
  process.exit(1);
}

const pages = findPages(OUT);
const titles = new Map();
const descs = new Map();
let beaconPages = 0;

group(`A1-A9: per-page checks (${pages.length} prerendered pages)`);
for (const rel of pages) {
  const url = '/' + rel.replace(/index\.html$/, '').replace(/\/$/, '');
  const path = url === '' ? '/' : url;
  const html = read(rel);
  const isArticle = path.startsWith('/articles/') && path !== '/articles';

  // A1: primary content present (non-trivial body)
  check(html.length > 4000, `${path}: prerendered HTML too small (${html.length}b) - content missing`);

  // A2/A3: unique title (<=60) + description (110-160)
  const title = (attr(html, /<title>([^<]*)<\/title>/) || '').trim();
  check(title.length > 0 && title.length <= 60, `${path}: title length ${title.length} (must be 1-60): "${title}"`);
  check(!titles.has(title), `${path}: duplicate title "${title}" (also ${titles.get(title)})`);
  titles.set(title, path);

  const desc = attr(html, /<meta name="description" content="([^"]*)"/);
  check(desc != null && desc.length >= 110 && desc.length <= 160, `${path}: description length ${desc ? desc.length : 'MISSING'} (must be 110-160)`);
  if (desc) { check(!descs.has(desc), `${path}: duplicate description (also ${descs.get(desc)})`); descs.set(desc, path); }

  // A4: canonical self
  const canon = attr(html, /<link rel="canonical" href="([^"]*)"/);
  check(canon != null && canon.endsWith(path === '/' ? '/' : path), `${path}: canonical missing/incorrect (${canon})`);

  // A5: hreflang uk + x-default
  check(/hreflang="uk"/.test(html), `${path}: missing hreflang="uk"`);
  check(/hreflang="x-default"/.test(html), `${path}: missing hreflang="x-default"`);

  // A22 (010): no en alternate while EN_ENABLED is false (regression guard).
  check(!/hreflang="en"/.test(html), `${path}: emits hreflang="en" while the /en mirror is unpublished`);

  // A6: OG + Twitter + image
  check(/property="og:title"/.test(html), `${path}: missing og:title`);
  check(/property="og:image"/.test(html), `${path}: missing og:image`);
  check(/name="twitter:card"/.test(html), `${path}: missing twitter:card`);

  // A7: Organization + WebSite JSON-LD
  check(/"@type":"Organization"/.test(html), `${path}: missing Organization JSON-LD`);
  check(/"@type":"WebSite"/.test(html), `${path}: missing WebSite JSON-LD`);

  // A24 (010): Organization entity has a stable @id (graph consolidation).
  check(/"@type":"Organization","@id":"[^"]+#organization"/.test(html), `${path}: Organization JSON-LD missing stable @id`);

  // robots index for public pages
  check(/name="robots" content="index,follow"/.test(html), `${path}: not marked index,follow`);

  // A23 (010): every <img> has non-empty alt + explicit width/height (no CLS, accessible).
  const imgs = html.match(/<img\b[^>]*>/g) || [];
  for (const tag of imgs) {
    check(/\balt="[^"]+"/.test(tag), `${path}: <img> missing non-empty alt: ${tag.slice(0, 80)}`);
    check(/\bwidth="\d+"/.test(tag) && /\bheight="\d+"/.test(tag), `${path}: <img> missing width/height: ${tag.slice(0, 80)}`);
  }

  // A25 (010): count Cloudflare Web Analytics beacon presence (asserted after the loop).
  if (/data-cf-beacon/.test(html)) beaconPages++;

  // A8/A9: article-specific JSON-LD + disclaimer + dates
  if (isArticle) {
    check(/"@type":"Article"/.test(html), `${path}: missing Article JSON-LD`);
    check(/"@type":"BreadcrumbList"/.test(html), `${path}: missing BreadcrumbList JSON-LD`);
    check(/"datePublished":"\d{4}-\d{2}-\d{2}"/.test(html), `${path}: missing datePublished`);
    check(/"dateModified":"\d{4}-\d{2}-\d{2}"/.test(html), `${path}: missing dateModified`);
    check(/інформаційний характер/.test(html), `${path}: missing financial disclaimer`);
    // A24 (010): Article publisher references the Organization by @id.
    check(/"publisher":\{"@id":"[^"]+#organization"\}/.test(html), `${path}: Article publisher not linked by @id`);
    // A19 (010): editorial trust block (marker + review date + editorial-policy link).
    check(/data-trust-block/.test(html), `${path}: missing editorial trust block (data-trust-block)`);
    check(/editorial-policy/.test(html), `${path}: trust block missing link to /editorial-policy`);
  }
}

group('A20: editorial-policy + contact routes prerendered & indexable');
for (const p of ['editorial-policy/index.html', 'contact/index.html']) {
  const html = read(p);
  check(html != null, `missing prerendered route /${p.replace('/index.html', '')}`);
  if (html) check(/name="robots" content="index,follow"/.test(html), `/${p.replace('/index.html', '')}: not index,follow`);
}

group('A25: analytics beacon');
if (beaconPages === 0) {
  console.warn(`  WARN: Cloudflare Web Analytics beacon not present on any page (cloudflareAnalyticsToken unset in this build). Set the token to enable measurement.`);
} else {
  check(beaconPages === pages.length, `analytics beacon present on only ${beaconPages}/${pages.length} pages (must be all)`);
}

group('A10: robots.txt');
const robots = read('robots.txt');
check(robots != null, 'robots.txt missing');
if (robots) {
  check(/Sitemap:\s*https?:\/\/\S+\/sitemap\.xml/.test(robots), 'robots.txt does not reference sitemap.xml');
  for (const p of ['/login', '/search', '/account', '/tokens', '/payments', '/history', '/providers']) {
    check(robots.includes(`Disallow: ${p}`), `robots.txt missing Disallow ${p}`);
  }
  // A17 (010): explicit AI-crawler allow stanzas.
  for (const ua of AI_CRAWLERS) {
    check(new RegExp(`User-agent:\\s*${ua}\\b`).test(robots), `robots.txt missing AI-crawler stanza for ${ua}`);
  }
}

group('A11/A12: sitemap.xml');
const sitemap = read('sitemap.xml');
check(sitemap != null, 'sitemap.xml missing');
let manifest = null;
if (existsSync(MANIFEST)) manifest = JSON.parse(readFileSync(MANIFEST, 'utf-8'));
check(manifest != null, 'seo-manifest.json missing (run npm run seo:articles)');
if (sitemap) {
  check(/<urlset[^>]*sitemaps\.org\/schemas\/sitemap\/0\.9/.test(sitemap), 'sitemap not schema 0.9');
  const locs = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]);
  check(locs.length >= MIN_ARTICLES + 3, `sitemap has only ${locs.length} URLs`);
  const priv = [...PRIVATE_PREFIXES, '/en'];
  for (const loc of locs) {
    const path = loc.replace(/^https?:\/\/[^/]+/, '') || '/';
    check(!priv.some((p) => path === p || path.startsWith(p + '/')), `sitemap contains private/en URL ${loc}`);
  }

  // A21 (010): each article URL's <lastmod> equals the manifest's dateModified (not a uniform build
  // date). Cross-check every <url> block against the manifest by path.
  if (manifest) {
    const lastmodByPath = new Map(manifest.map((e) => [e.path, e.lastmod]));
    const urlBlocks = [...sitemap.matchAll(/<url>([\s\S]*?)<\/url>/g)].map((m) => m[1]);
    for (const block of urlBlocks) {
      const loc = (block.match(/<loc>([^<]+)<\/loc>/) || [])[1];
      const lastmod = (block.match(/<lastmod>([^<]+)<\/lastmod>/) || [])[1];
      if (!loc) continue;
      const path = loc.replace(/^https?:\/\/[^/]+/, '') || '/';
      if (path.startsWith('/articles/') && path !== '/articles') {
        const expected = lastmodByPath.get(path);
        check(expected != null && lastmod === expected, `sitemap lastmod for ${path} is "${lastmod}", expected dateModified "${expected}"`);
      }
    }
  }
}

group('A18: llms.txt');
const llms = read('llms.txt');
check(llms != null, 'llms.txt missing');
if (llms && manifest) {
  const indexable = manifest.filter((e) => e.indexable);
  for (const e of indexable) {
    const absUrl = `https://investguideua.com${e.path === '/' ? '/' : e.path}`;
    check(llms.includes(absUrl), `llms.txt missing indexable URL ${absUrl}`);
  }
  for (const p of PRIVATE_PREFIXES) {
    check(!llms.includes(`investguideua.com${p}`), `llms.txt leaks private route ${p}`);
  }
}

group('A14: true 404 asset + no SPA catch-all');
check(read('404.html') != null, '404.html missing (needed for real 404 status)');
const redirects = read('_redirects');
check(redirects != null, '_redirects missing');
if (redirects) {
  const hasCatchAll = /^\s*\/\*\s+\/index\.html\s+200/m.test(redirects);
  check(!hasCatchAll, '_redirects contains a catch-all /* 200 rule (reintroduces soft-404 trap)');
  check(/^\/search\s+\/index\.html\s+200/m.test(redirects), '_redirects missing /search SPA fallback');
}

group('A15: article count + OG image asset');
const articlePages = pages.filter((p) => p.startsWith('articles/') && p !== 'articles/index.html');
check(articlePages.length >= MIN_ARTICLES, `only ${articlePages.length} articles prerendered (need >= ${MIN_ARTICLES})`);
check(existsSync(join(OUT, 'og-default.png')), 'og-default.png missing');

group("A16': search-console verification (DNS TXT or non-placeholder file)");
// Verification uses the Search Console "Domain" property (a DNS TXT record, off-repo), so an HTML
// verification file is NOT required in the deployed output. If a google*.html file IS shipped, it
// must not be a placeholder (guards against a stray REPLACE_WITH file being deployed).
const googleFiles = readdirSync(OUT).filter((f) => /^google.*\.html$/.test(f));
for (const f of googleFiles) {
  const content = read(f) || '';
  check(!/REPLACE_WITH/i.test(f) && !/REPLACE_WITH/i.test(content), `${f}: placeholder verification token (REPLACE_WITH) - replace with the real file or remove it`);
}
if (googleFiles.length === 0) {
  console.log('  note: no google*.html file present (verification via DNS TXT / Domain property - off-repo).');
}

console.log(`\n[audit] ${checks - failures}/${checks} checks passed.`);
if (failures > 0) {
  console.error(`[audit] FAILED with ${failures} violation(s).`);
  process.exit(1);
}
console.log('[audit] PASS - all SEO acceptance checks passed.');
