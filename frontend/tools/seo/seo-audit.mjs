/**
 * SEO acceptance audit (feature 006-seo-optimization, T049 / contracts/seo-acceptance.md).
 *
 * Runs against the prerendered build output and FAILS (exit 1) on any violation. This is the
 * executable definition of done (FR-032 / SC-012). Verification is by parsing build bytes, not by
 * reading rendered text (Constitution Principle V). Plain Node ESM, no transpiler.
 *
 * Scope note: the `/en` mirror is a later increment (US4). The audit therefore requires uk +
 * x-default hreflang and treats the `en` alternate as optional until enabled.
 */
import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..', '..');
const OUT = join(ROOT, 'dist', 'investguide-frontend', 'browser');
const MIN_ARTICLES = 8;

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

group(`A1-A9: per-page checks (${pages.length} prerendered pages)`);
for (const rel of pages) {
  const url = '/' + rel.replace(/index\.html$/, '').replace(/\/$/, '');
  const path = url === '' ? '/' : url;
  const html = read(rel);
  const isArticle = path.startsWith('/articles/');

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

  // A6: OG + Twitter + image
  check(/property="og:title"/.test(html), `${path}: missing og:title`);
  check(/property="og:image"/.test(html), `${path}: missing og:image`);
  check(/name="twitter:card"/.test(html), `${path}: missing twitter:card`);

  // A7: Organization + WebSite JSON-LD
  check(/"@type":"Organization"/.test(html), `${path}: missing Organization JSON-LD`);
  check(/"@type":"WebSite"/.test(html), `${path}: missing WebSite JSON-LD`);

  // robots index for public pages
  check(/name="robots" content="index,follow"/.test(html), `${path}: not marked index,follow`);

  // A8/A9: article-specific JSON-LD + disclaimer + dates
  if (isArticle) {
    check(/"@type":"Article"/.test(html), `${path}: missing Article JSON-LD`);
    check(/"@type":"BreadcrumbList"/.test(html), `${path}: missing BreadcrumbList JSON-LD`);
    check(/"datePublished":"\d{4}-\d{2}-\d{2}"/.test(html), `${path}: missing datePublished`);
    check(/"dateModified":"\d{4}-\d{2}-\d{2}"/.test(html), `${path}: missing dateModified`);
    check(/інформаційний характер/.test(html), `${path}: missing financial disclaimer`);
  }
}

group('A10: robots.txt');
const robots = read('robots.txt');
check(robots != null, 'robots.txt missing');
if (robots) {
  check(/Sitemap:\s*https?:\/\/\S+\/sitemap\.xml/.test(robots), 'robots.txt does not reference sitemap.xml');
  for (const p of ['/login', '/search', '/account', '/tokens', '/payments', '/history', '/providers']) {
    check(robots.includes(`Disallow: ${p}`), `robots.txt missing Disallow ${p}`);
  }
}

group('A11/A12: sitemap.xml');
const sitemap = read('sitemap.xml');
check(sitemap != null, 'sitemap.xml missing');
if (sitemap) {
  check(/<urlset[^>]*sitemaps\.org\/schemas\/sitemap\/0\.9/.test(sitemap), 'sitemap not schema 0.9');
  const locs = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]);
  check(locs.length >= MIN_ARTICLES + 3, `sitemap has only ${locs.length} URLs`);
  const priv = ['/login', '/search', '/account', '/tokens', '/payments', '/history', '/providers', '/register', '/verify'];
  for (const loc of locs) {
    const path = loc.replace(/^https?:\/\/[^/]+/, '') || '/';
    check(!priv.some((p) => path === p || path.startsWith(p + '/')), `sitemap contains private URL ${loc}`);
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

group('A16: search-console verification file');
const verify = readdirSync(OUT).some((f) => /^google.*\.html$/.test(f));
check(verify, 'no google-site-verification file at output root');

console.log(`\n[audit] ${checks - failures}/${checks} checks passed.`);
if (failures > 0) {
  console.error(`[audit] FAILED with ${failures} violation(s).`);
  process.exit(1);
}
console.log('[audit] PASS - all SEO acceptance checks passed.');
