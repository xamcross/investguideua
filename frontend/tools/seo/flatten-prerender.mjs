/**
 * Post-build prerender flattener (feature 013-seo-improvements).
 *
 * Angular's prerenderer writes each route as <route>/index.html. Cloudflare Pages serves that
 * directory layout only at the trailing-slash URL and 308-redirects the canonical non-slash URL
 * (/articles/x -> /articles/x/). Every canonical, hreflang, og:url, sitemap and llms.txt URL the
 * app advertises is the NON-slash form, so before this fix 13 of 14 advertised URLs answered with
 * a redirect ("Page with redirect" in Search Console; a redirect hop on every crawled link).
 *
 * Converting to flat files makes Cloudflare Pages serve the canonical extension-less URL with 200
 * (and 308 the slash variant BACK to canonical - the correct direction):
 *   articles/ovdp-war-bonds/index.html -> articles/ovdp-war-bonds.html
 *   terms/index.html                   -> terms.html
 * The root index.html stays: it is the prerendered "/" and the _redirects SPA-fallback target.
 *
 * Idempotent: a second run (deploy scripts call seo:generate again after `npm run build` already
 * ran it) finds no nested index.html and exits 0 without touching anything.
 *
 * Plain Node ESM (no transpiler). Not a Windows-executed script (.ps1/.cmd/.bat) -> UTF-8/LF is
 * fine (Constitution V).
 */
import { existsSync, readdirSync, renameSync, rmSync, rmdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const FRONTEND_ROOT = join(__dirname, '..', '..');
const OUTPUT_DIR = join(FRONTEND_ROOT, 'dist', 'investguide-frontend', 'browser');

/** Collect every nested <dir>/index.html under root (the root's own index.html excluded). */
function findNestedIndexFiles(dir, isRoot = true) {
  const found = [];
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry);
    if (statSync(p).isDirectory()) {
      found.push(...findNestedIndexFiles(p, false));
    } else if (entry === 'index.html' && !isRoot) {
      found.push(p);
    }
  }
  return found;
}

function main() {
  if (!existsSync(OUTPUT_DIR)) {
    throw new Error(`Build output not found at ${OUTPUT_DIR}. Run "npm run build" first.`);
  }
  const nested = findNestedIndexFiles(OUTPUT_DIR);
  if (nested.length === 0) {
    console.log('[seo:flatten] No nested index.html files - output already flat. Nothing to do.');
    return;
  }
  let flattened = 0;
  for (const file of nested) {
    const routeDir = dirname(file);
    const target = routeDir + '.html';
    if (existsSync(target)) rmSync(target);
    renameSync(file, target);
    // Remove the route directory when it held only index.html. /articles keeps its directory: it
    // still contains the flattened per-slug .html files.
    if (readdirSync(routeDir).length === 0) rmdirSync(routeDir);
    flattened++;
  }
  console.log(`[seo:flatten] Flattened ${flattened} prerendered route(s) to <route>.html (canonical non-slash URLs now serve 200 on Cloudflare Pages).`);
}

main();
