// Automated WCAG 2.1/2.2 A+AA accessibility scan (feature 007-ui-ux-improvements, task T002).
//
// Unlike tools/seo/seo-audit.mjs (which statically reads prerendered HTML off disk and launches no
// browser), this script DRIVES a real headless Chrome against a RUNNING, hydrated app and runs
// axe-core over each PUBLIC route. Authenticated/data-backed routes (account, history, populated
// search results) are intentionally NOT scanned here - headless they only reach a redirect/error
// state and would falsely pass; they are covered by the manual keyboard/screen-reader pass
// (see specs/007-ui-ux-improvements/quickstart.md section 5).
//
// Usage:
//   1. Serve the app in another terminal:  npm start   (http://localhost:4200)
//   2. Run the scan:                        npm run a11y:audit
//   Optional env: A11Y_BASE_URL (default http://localhost:4200)
//
// Exit code: 0 when zero violations across all routes; 1 on any violation or setup failure (so CI
// can gate on it, mirroring seo:audit). This file is intentionally pure ASCII (Constitution V).

const BASE_URL = (process.env.A11Y_BASE_URL || 'http://localhost:4200').replace(/\/$/, '');

// Public, no-backend routes that render fully without an authenticated session.
const ROUTES = [
  '/',
  '/search',
  '/login',
  '/register',
  '/articles',
  '/articles/ovdp-war-bonds',
];

// axe rule set: WCAG 2.0/2.1/2.2 levels A and AA.
const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

async function loadDeps() {
  try {
    const [{ AxePuppeteer }, puppeteer] = await Promise.all([
      import('@axe-core/puppeteer'),
      import('puppeteer'),
    ]);
    return { AxePuppeteer, puppeteer: puppeteer.default || puppeteer };
  } catch {
    console.error(
      '[a11y] Missing dev dependency. Install it once, then re-run:\n' +
        '       npm install --save-dev @axe-core/puppeteer\n' +
        '       (puppeteer itself is already a devDependency via karma.conf.js)',
    );
    process.exit(1);
  }
}

async function waitForServer(url, attempts = 40, delayMs = 1000) {
  for (let i = 0; i < attempts; i++) {
    try {
      const res = await fetch(url, { method: 'GET' });
      if (res.ok) return true;
    } catch {
      // server not up yet
    }
    await new Promise((r) => setTimeout(r, delayMs));
  }
  return false;
}

async function main() {
  const { AxePuppeteer, puppeteer } = await loadDeps();

  console.log(`[a11y] Waiting for ${BASE_URL} ...`);
  if (!(await waitForServer(BASE_URL))) {
    console.error(`[a11y] Server at ${BASE_URL} did not become ready. Start it with "npm start".`);
    process.exit(1);
  }

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  });

  let totalViolations = 0;
  try {
    for (const route of ROUTES) {
      const page = await browser.newPage();
      try {
        await page.goto(`${BASE_URL}${route}`, { waitUntil: 'networkidle0', timeout: 30000 });
        const results = await new AxePuppeteer(page).withTags(AXE_TAGS).analyze();
        const violations = results.violations || [];
        if (violations.length === 0) {
          console.log(`[a11y] PASS ${route}`);
        } else {
          totalViolations += violations.length;
          console.error(`[a11y] FAIL ${route} - ${violations.length} violation(s):`);
          for (const v of violations) {
            const nodes = v.nodes.map((n) => n.target.join(' ')).join('; ');
            console.error(`        [${v.impact || 'n/a'}] ${v.id}: ${v.help}`);
            console.error(`          ${v.helpUrl}`);
            console.error(`          nodes: ${nodes}`);
          }
        }
      } finally {
        await page.close();
      }
    }
  } finally {
    await browser.close();
  }

  if (totalViolations > 0) {
    console.error(`[a11y] DONE - ${totalViolations} total violation(s). FAILING.`);
    process.exit(1);
  }
  console.log('[a11y] DONE - zero violations across all public routes.');
}

main().catch((err) => {
  console.error('[a11y] Unexpected error:', err);
  process.exit(1);
});
