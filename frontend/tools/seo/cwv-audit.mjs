/**
 * Lab Core Web Vitals audit (feature 010-seo-aeo-optimization, T024 / FR-013, SC-005).
 *
 * Serves the prerendered build output with a tiny static server, drives Chrome (via the existing
 * puppeteer dependency) and runs Lighthouse with a simulated mobile profile against three templates
 * (home, article index, one article detail). Reports the MEDIAN of 3 runs per URL and FAILS (exit 1)
 * if LCP > 2.5s, CLS > 0.1, or Total Blocking Time (the lab proxy for INP) > 200ms.
 *
 * ADVISORY: this is NOT part of the blocking `seo:all` chain (lab numbers jitter on shared runners);
 * run on demand or as a CI-reported job. Plain Node ESM. Not a Windows-executed script (Constitution V).
 *
 * Usage: npm run build   (prerender first), then   npm run seo:cwv
 */
import { createServer } from 'node:http';
import { readFileSync, existsSync, statSync } from 'node:fs';
import { join, dirname, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const FRONTEND_ROOT = join(__dirname, '..', '..');
const OUTPUT_DIR = join(FRONTEND_ROOT, 'dist', 'investguide-frontend', 'browser');

const ROUTES = ['/', '/articles', '/articles/ovdp-war-bonds'];
const THRESHOLDS = { lcp: 2500, cls: 0.1, tbt: 200 }; // ms, unitless, ms
const RUNS = 3;

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.mjs': 'text/javascript',
  '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png',
  '.jpg': 'image/jpeg', '.webp': 'image/webp', '.ico': 'image/x-icon', '.txt': 'text/plain',
  '.woff': 'font/woff', '.woff2': 'font/woff2', '.map': 'application/json',
};

/** Resolve a request path to a file under OUTPUT_DIR; extensionless/dir paths -> index.html. */
function resolveFile(urlPath) {
  let rel = decodeURIComponent(urlPath.split('?')[0]).replace(/^\/+/, '');
  let p = join(OUTPUT_DIR, rel);
  if (rel === '' || (existsSync(p) && statSync(p).isDirectory())) p = join(p, 'index.html');
  else if (!extname(p)) p = join(OUTPUT_DIR, rel, 'index.html');
  return p;
}

function startServer() {
  const server = createServer((req, res) => {
    const file = resolveFile(req.url);
    if (!existsSync(file) || statSync(file).isDirectory()) {
      // SPA-style fallback to home shell for unknown paths (mirrors Cloudflare behavior loosely).
      const fallback = join(OUTPUT_DIR, 'index.html');
      res.writeHead(existsSync(fallback) ? 200 : 404, { 'content-type': 'text/html; charset=utf-8' });
      res.end(existsSync(fallback) ? readFileSync(fallback) : 'Not found');
      return;
    }
    res.writeHead(200, { 'content-type': MIME[extname(file)] || 'application/octet-stream' });
    res.end(readFileSync(file));
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

const median = (xs) => {
  const s = [...xs].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

async function main() {
  if (!existsSync(join(OUTPUT_DIR, 'index.html'))) {
    console.error(`[cwv] Build output not found at ${OUTPUT_DIR}. Run "npm run build" first.`);
    process.exit(1);
  }
  const puppeteer = (await import('puppeteer')).default;
  const lighthouse = (await import('lighthouse')).default;

  const server = await startServer();
  const { port: serverPort } = server.address();
  const base = `http://127.0.0.1:${serverPort}`;

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  });
  const wsPort = Number(new URL(browser.wsEndpoint()).port);

  let failures = 0;
  try {
    for (const route of ROUTES) {
      const lcp = [], cls = [], tbt = [];
      for (let i = 0; i < RUNS; i++) {
        const result = await lighthouse(
          `${base}${route}`,
          { port: wsPort, output: 'json', logLevel: 'error', onlyCategories: ['performance'] },
          { extends: 'lighthouse:default', settings: { formFactor: 'mobile', screenEmulation: { mobile: true } } },
        );
        const a = result.lhr.audits;
        lcp.push(a['largest-contentful-paint'].numericValue);
        cls.push(a['cumulative-layout-shift'].numericValue);
        tbt.push(a['total-blocking-time'].numericValue);
      }
      const mLcp = median(lcp), mCls = median(cls), mTbt = median(tbt);
      const ok = mLcp <= THRESHOLDS.lcp && mCls <= THRESHOLDS.cls && mTbt <= THRESHOLDS.tbt;
      if (!ok) failures++;
      console.log(
        `[cwv] ${ok ? 'PASS' : 'FAIL'} ${route}  LCP=${Math.round(mLcp)}ms (<=${THRESHOLDS.lcp}) ` +
        `CLS=${mCls.toFixed(3)} (<=${THRESHOLDS.cls}) TBT=${Math.round(mTbt)}ms (<=${THRESHOLDS.tbt})`,
      );
    }
  } finally {
    await browser.close();
    server.close();
  }

  if (failures > 0) {
    console.error(`[cwv] FAILED on ${failures} route(s).`);
    process.exit(1);
  }
  console.log('[cwv] PASS - all templates meet lab Core Web Vitals thresholds.');
}

main().catch((err) => {
  console.error('[cwv] Unexpected error:', err);
  process.exit(1);
});
