/**
 * Build-time article compiler (feature 006-seo-optimization, T027; extended by 010-seo-aeo-optimization).
 *
 * Reads in-repo Markdown+frontmatter article files (src/content/articles/<slug>.<lang>.md),
 * validates frontmatter against the article schema, renders the body to sanitized HTML, and emits:
 *   1. src/app/features/articles/articles.data.ts  - a typed, published-only index the app imports.
 *   2. prerender-routes.txt                         - base public routes + every published article
 *                                                     URL, so prerender + sitemap stay in sync (FR-003).
 *   3. seo-manifest.json (010)                      - single source of truth for the sitemap / robots /
 *                                                     llms.txt generators: one entry per public route
 *                                                     with path, lastmod (true dateModified for
 *                                                     articles), indexable, kind, title, description.
 *
 * Fails the build (exit 1) on any invalid PUBLISHED article. Plain Node ESM, no transpiler.
 * Not a Windows-executed script (.ps1/.cmd/.bat) -> UTF-8/LF is fine (Constitution V).
 */
import { readdirSync, readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import matter from 'gray-matter';
import MarkdownIt from 'markdown-it';

const __dirname = dirname(fileURLToPath(import.meta.url));
const FRONTEND_ROOT = join(__dirname, '..', '..');
const CONTENT_DIR = join(FRONTEND_ROOT, 'src', 'content', 'articles');
const OUT_TS = join(FRONTEND_ROOT, 'src', 'app', 'features', 'articles', 'articles.data.ts');
const PRERENDER_LIST = join(FRONTEND_ROOT, 'prerender-routes.txt');
const MANIFEST = join(FRONTEND_ROOT, 'seo-manifest.json');
const UK_I18N = join(FRONTEND_ROOT, 'public', 'i18n', 'uk.json');

/**
 * Static public, indexable pages (uk). Mirror of PUBLIC_PAGES in
 * src/app/core/seo/seo-routes.config.ts - keep in sync. titleKey/descKey resolve from uk.json so
 * the manifest carries real titles/descriptions for llms.txt. `/articles` only ships when at least
 * one uk article is published.
 */
const STATIC_PAGES = [
  { path: '/', titleKey: 'seo.home.title', descKey: 'seo.home.desc' },
  { path: '/articles', titleKey: 'seo.articles.title', descKey: 'seo.articles.desc', articlesIndex: true },
  { path: '/terms', titleKey: 'seo.terms.title', descKey: 'seo.terms.desc' },
  { path: '/privacy', titleKey: 'seo.privacy.title', descKey: 'seo.privacy.desc' },
  { path: '/editorial-policy', titleKey: 'seo.editorial.title', descKey: 'seo.editorial.desc' },
  { path: '/contact', titleKey: 'seo.contact.title', descKey: 'seo.contact.desc' },
];

/** Minimum inbound peer cross-links a published article needs to not be an orphan (FR-023, clarified = 2). */
const MIN_INBOUND_LINKS = 2;

/** Curated provider catalog ids/names allowed in article content (FR-025). Keep in sync with backend seed. */
const CATALOG = new Set([
  'privatbank', 'oschadbank', 'otp', 'otpbank', 'universalbank', 'monobank', 'mono',
  'minfin', 'ukrgasbank', 'pumb', 'raiffeisen', 'sensebank',
]);

const md = new MarkdownIt({ html: false, linkify: true, typographer: true });

function fail(msg) {
  console.error(`[seo:articles] ERROR: ${msg}`);
  process.exitCode = 1;
  throw new Error(msg);
}

/** Resolve a dotted key (e.g. "seo.home.title") from a parsed JSON object. */
function resolveKey(obj, dotted) {
  return dotted.split('.').reduce((o, k) => (o && typeof o === 'object' ? o[k] : undefined), obj);
}

function validate(fm, file, content) {
  const req = ['slug', 'lang', 'title', 'description', 'summary', 'primaryTopic',
    'datePublished', 'dateModified', 'status'];
  for (const k of req) {
    if (fm[k] === undefined || fm[k] === null || fm[k] === '') fail(`${file}: missing frontmatter "${k}"`);
  }
  if (!['uk', 'en'].includes(fm.lang)) fail(`${file}: lang must be uk|en`);
  if (!/^[a-z0-9-]+$/.test(fm.slug)) fail(`${file}: slug must be kebab-case`);
  if (fm.status === 'published') {
    if (String(fm.title).length > 60) fail(`${file}: title > 60 chars (${String(fm.title).length})`);
    const dl = String(fm.description).length;
    if (dl < 110 || dl > 160) fail(`${file}: description out of 110-160 (${dl})`);
    if (!fm.reviewedBy || !fm.reviewedOn) fail(`${file}: published article needs reviewedBy + reviewedOn (FR-024)`);
    const dp = new Date(fm.datePublished), dm = new Date(fm.dateModified);
    if (dm < dp) fail(`${file}: dateModified < datePublished`);
    for (const p of fm.providersReferenced || []) {
      if (!CATALOG.has(String(p).toLowerCase())) fail(`${file}: provider "${p}" not in curated catalog (FR-025)`);
    }
    // 010 FR-022 (answer-first): a required, length-bounded direct-answer lead (the extractable answer).
    if (!fm.answer || String(fm.answer).trim() === '') fail(`${file}: published article needs an "answer" frontmatter lead (FR-022)`);
    const al = String(fm.answer).trim().length;
    if (al < 40 || al > 300) fail(`${file}: answer out of 40-300 chars (${al}) (FR-022)`);
    // 010 FR-022 (answer-first structure): at least one question-style "## ...?" heading so the
    // article exposes an extractable Q&A for AI Overviews / featured snippets (US7 AC#1).
    const hasQuestionHeading = content
      .split(/\r?\n/)
      .some((line) => /^##\s+.*\?\s*$/.test(line.trim()));
    if (!hasQuestionHeading) fail(`${file}: published article needs >= 1 question-style "## ...?" heading (FR-022)`);
  }
}

function loadArticles() {
  if (!existsSync(CONTENT_DIR)) return [];
  const files = readdirSync(CONTENT_DIR).filter((f) => f.endsWith('.md'));
  const articles = [];
  for (const file of files) {
    const m = file.match(/^(.+)\.(uk|en)\.md$/);
    // Non-article docs (e.g. REVIEW-CHECKLIST.md) legitimately live here; skip quietly via stdout
    // (console.log, not console.warn) so the notice never lands on stderr.
    if (!m) { console.log(`[seo:articles] skipping non-article file ${file}`); continue; }
    const raw = readFileSync(join(CONTENT_DIR, file), 'utf-8');
    const { data: fm, content } = matter(raw);
    fm.slug = fm.slug ?? m[1];
    fm.lang = fm.lang ?? m[2];
    validate(fm, file, content);
    if (fm.status !== 'published') continue;
    articles.push({
      slug: fm.slug,
      lang: fm.lang,
      title: fm.title,
      description: fm.description,
      summary: fm.summary,
      answer: String(fm.answer),
      primaryTopic: fm.primaryTopic,
      keywords: fm.keywords || [],
      datePublished: String(fm.datePublished),
      dateModified: String(fm.dateModified),
      ogImage: fm.ogImage || null,
      relatedSlugs: fm.relatedSlugs || [],
      reviewedBy: fm.reviewedBy,
      reviewedOn: String(fm.reviewedOn),
      providersReferenced: fm.providersReferenced || [],
      faq: fm.faq || [],
      bodyHtml: md.render(content),
    });
  }
  // relatedSlugs must resolve within the same language.
  for (const a of articles) {
    for (const rs of a.relatedSlugs) {
      if (!articles.some((x) => x.slug === rs && x.lang === a.lang)) {
        fail(`${a.slug}.${a.lang}: relatedSlug "${rs}" does not resolve to a published ${a.lang} article`);
      }
    }
  }
  // 010 FR-023 (no orphan): every published article must be referenced by >= MIN_INBOUND_LINKS peers
  // in the same language (the auto-generated index listing does not count).
  for (const a of articles) {
    const inbound = articles.filter(
      (x) => x.lang === a.lang && x.slug !== a.slug && (x.relatedSlugs || []).includes(a.slug),
    ).length;
    if (inbound < MIN_INBOUND_LINKS) {
      fail(`${a.slug}.${a.lang}: only ${inbound} inbound cross-link(s); needs >= ${MIN_INBOUND_LINKS} (FR-023)`);
    }
  }
  return articles;
}

function emitTs(articles) {
  mkdirSync(dirname(OUT_TS), { recursive: true });
  const header =
    '// AUTO-GENERATED by tools/seo/build-articles.mjs - DO NOT EDIT BY HAND.\n' +
    '// Source: src/content/articles/<slug>.<lang>.md. Regenerate via `npm run seo:articles`.\n\n' +
    'export interface ArticleFaq { readonly q: string; readonly a: string; }\n' +
    'export interface Article {\n' +
    '  readonly slug: string;\n  readonly lang: \'uk\' | \'en\';\n  readonly title: string;\n' +
    '  readonly description: string;\n  readonly summary: string;\n  readonly answer: string;\n' +
    '  readonly primaryTopic: string;\n' +
    '  readonly keywords: readonly string[];\n  readonly datePublished: string;\n  readonly dateModified: string;\n' +
    '  readonly ogImage: string | null;\n  readonly relatedSlugs: readonly string[];\n' +
    '  readonly reviewedBy: string;\n  readonly reviewedOn: string;\n' +
    '  readonly providersReferenced: readonly string[];\n  readonly faq: readonly ArticleFaq[];\n' +
    '  readonly bodyHtml: string;\n}\n\n' +
    'export const ARTICLES: readonly Article[] = ';
  writeFileSync(OUT_TS, header + JSON.stringify(articles, null, 2) + ';\n', 'utf-8');
}

function emitPrerenderList(articles) {
  const ukPublished = articles.filter((a) => a.lang === 'uk');
  const hasArticles = ukPublished.length > 0;
  const routes = STATIC_PAGES
    .filter((p) => !p.articlesIndex || hasArticles)
    .map((p) => p.path);
  if (hasArticles) {
    for (const a of ukPublished) routes.push(`/articles/${a.slug}`);
  }
  // NOTE: Angular's prerender routesFile does NOT support comments - every non-empty line is
  // treated as a route. Write paths only (no header), one per line.
  writeFileSync(PRERENDER_LIST, routes.join('\n') + '\n', 'utf-8');
  return routes;
}

/**
 * 010: emit seo-manifest.json - the single source consumed by generate-sitemap.mjs (sitemap +
 * robots) and the llms.txt generator. Static-page titles/descriptions resolve from uk.json.
 */
function emitManifest(articles) {
  const buildDate = process.env.SEO_BUILD_DATE || new Date().toISOString().slice(0, 10);
  const i18n = existsSync(UK_I18N) ? JSON.parse(readFileSync(UK_I18N, 'utf-8')) : {};
  const ukPublished = articles
    .filter((a) => a.lang === 'uk')
    .sort((a, b) => b.datePublished.localeCompare(a.datePublished));
  const hasArticles = ukPublished.length > 0;

  const entries = [];
  for (const p of STATIC_PAGES) {
    if (p.articlesIndex && !hasArticles) continue;
    const title = resolveKey(i18n, p.titleKey);
    const description = resolveKey(i18n, p.descKey);
    if (!title || !description) {
      fail(`seo-manifest: missing i18n ${p.titleKey}/${p.descKey} for ${p.path} (add to public/i18n/uk.json)`);
    }
    entries.push({
      path: p.path,
      lastmod: buildDate,
      indexable: true,
      kind: 'static',
      title: String(title),
      description: String(description),
    });
  }
  for (const a of ukPublished) {
    entries.push({
      path: `/articles/${a.slug}`,
      lastmod: a.dateModified,
      indexable: true,
      kind: 'article',
      title: a.title,
      description: a.description,
    });
  }
  entries.sort((x, y) => x.path.localeCompare(y.path));
  writeFileSync(MANIFEST, JSON.stringify(entries, null, 2) + '\n', 'utf-8');
  return entries;
}

const articles = loadArticles();
emitTs(articles);
const routes = emitPrerenderList(articles);
const manifest = emitManifest(articles);
console.log(
  `[seo:articles] ${articles.length} published article(s); ${routes.length} prerender route(s); ` +
  `${manifest.length} manifest entr(ies).`,
);
