# Bond price scraper (feature 009)

Headless-browser scraper that collects Ukrainian military bond (military OVDP) prices from
PrivatBank's public Privat24 bonds endpoint and POSTs them to the InvestGuideUA backend.

It loads `https://next.privat24.ua/bonds` in real headless Chromium, lets PrivatBank's own SPA
complete its cross-domain anti-bot (`xref`) handshake, and **intercepts** the
`POST /api/p24/pub/bonds` XHR response JSON directly. We never reconstruct the `xref` ourselves.

This tool runs **only** on GitHub Actions (scheduled) and on a developer machine. It must **not** run
on the Fly backend (512 MB; Chromium would OOM) nor as an in-process job (the backend scales to zero).

## Layout

- `scrape-bonds.mjs` - entry point: launch -> intercept -> convert -> validate -> warmup -> POST.
- `lib/convert.mjs` - exact decimal -> integer minor-unit conversion (BigInt string math, no float).
- `lib/validate.mjs` - client-side batch validation (non-empty, minimum-record floor, per-record shape).
- `lib/post.mjs` - warm up the scaled-to-zero backend via `GET /api/v1/ping`, then POST once.
- `test/convert.test.mjs` - unit tests for the conversion (`node --test`).

## Environment variables

| Var | Default | Purpose |
|-----|---------|---------|
| `BACKEND_BASE_URL` | `http://localhost:8080` | Backend base URL (prod: `https://api.investguideua.com`). |
| `BOND_INGEST_SECRET` | (none) | Shared secret sent as `X-Bond-Ingest-Secret`. Required to POST; never logged. |
| `BONDS_MIN_RECORDS` | `5` | Minimum records before a result is accepted (below = treated as truncated -> fail, no POST). |
| `BONDS_DRY_RUN` | `false` | `true` to scrape + validate + print a sample without POSTing. |

## Run locally

```bash
npm ci
npx playwright install --with-deps chromium   # first time only
BACKEND_BASE_URL=http://localhost:8080 BOND_INGEST_SECRET=local-dev-bond-secret npm run scrape
```

On Windows, prefer the repo helper `scripts/refresh-bond-prices.ps1`, which reads the secret from the
environment and invokes this scraper.

## Run tests

```bash
npm test   # node --test over test/*.test.mjs
```

## Failure behavior

The scraper FAILS LOUD and POSTs nothing on any of: navigation/XHR timeout, empty or truncated
result (below the minimum-record floor), per-record validation failure, or a non-2xx ingest response.
This guarantees a bad run can never blank the stored prices. Because ingest is upsert-by-ISIN with no
deletes, a successful partial run only updates the instruments present and leaves the rest intact
(their older `fetchedAt` reveals staleness).

## Scheduling

`.github/workflows/refresh-bond-prices.yml` runs this on cron `0 7 * * 1-5` (10:00 Kyiv in summer /
09:00 in winter; the one-hour DST drift is accepted) and on manual `workflow_dispatch`. Note GitHub
disables scheduled workflows after ~60 days of repo inactivity and cron firing is best-effort; the
read API exposes per-row `fetchedAt` so an admin can spot a missed run.
