# Quickstart: Military Bond Price Refresh

How to run the bond price refresh locally and understand the production path. Audience: a developer
on Windows with Docker Desktop, or anyone reading the CI workflow.

## Prerequisites

- Local Docker Compose stack running (`./scripts/dev-up.ps1`) so the backend is on
  `http://localhost:8080`.
- Node.js 20+ on PATH (for the scraper). Playwright installs Chromium on first run.
- A shared ingest secret known to both the backend and the scraper.

## 1. Configure the ingest secret (local)

The backend reads `BOND_INGEST_SECRET` (env). Add it to your local `.env` (written without a BOM);
`docker-compose.yml` already passes `.env` to the backend via `env_file`, so you do **not** need to
set it in two places. A commented placeholder lives in `.env.example`.

```
BOND_INGEST_SECRET=local-dev-bond-secret
```

If the secret is blank/unset, the backend rejects all ingest (fail-closed) but the rest of the app
still starts normally. Restart the backend after setting it.

## 2. Run the scraper locally

A pure-ASCII PowerShell helper drives the same scraper used in CI. It reads the secret from
`$env:BOND_INGEST_SECRET` (defaulted from `.env`) so it does not land in shell history; `-Secret` is
accepted only as an explicit override:

```powershell
.\scripts\refresh-bond-prices.ps1 -BackendBaseUrl "http://localhost:8080"
```

This runs `node scraper/scrape-bonds.mjs`, which:
1. Launches headless Chromium and opens `https://next.privat24.ua/bonds`.
2. Waits for PrivatBank's SPA to complete its `xref` handshake and fire `POST /api/p24/pub/bonds`
   (bounded `waitForResponse` timeout).
3. Intercepts that XHR's JSON response.
4. Validates the result: non-empty, meets the minimum-record floor, every record schema-valid. If
   not, it logs the reason, exits non-zero, and POSTs nothing (existing prices preserved).
5. Converts each record to the ingest shape (prices -> integer minor units via string math).
6. Warms up the backend with `GET /api/v1/ping` (backoff, to wake a scaled-to-zero Fly machine),
   then POSTs the batch once to `POST /api/v1/admin/bond-prices` with the secret header.

On success it prints the `{ accepted, rejected }` tally.

## 3. Verify stored prices (as ADMIN)

Log in as an ADMIN user and call the read endpoint:

```powershell
# obtain an ADMIN access token via /api/v1/auth/login, then:
curl.exe -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>" http://localhost:8080/api/v1/bond-prices
```

Expect a JSON array of bonds with integer minor-unit prices, yields, currency, dates, and `fetchedAt`.
A non-admin token returns `403`; no token returns `401`. An empty collection returns `[]`.

## 4. Production path (reference)

- `.github/workflows/refresh-bond-prices.yml` runs on cron `0 7 * * 1-5` (10:00 Kyiv summer / 09:00
  winter) and on manual `workflow_dispatch`.
- It runs the same `scraper/scrape-bonds.mjs` with `BACKEND_BASE_URL=https://api.investguideua.com`
  and `BOND_INGEST_SECRET` from GitHub Actions secrets.
- The POST helper retries with backoff to wake the scaled-to-zero Fly backend before failing the job.
- A failed scrape (source down, empty/truncated data) fails the job WITHOUT POSTing, so existing
  stored prices are preserved.

## 5. Tests / verification

- Backend: `cd backend; mvn -B test` (ingest secret accept/reject, validation, upsert-by-ISIN,
  empty-batch guard, ADMIN read gating).
- Scraper conversion: `cd scraper; node --test` (decimal -> minor-unit conversion, e.g. `1076.58`
  -> `107658`, rounding/edge cases).
- Windows script: non-ASCII scan + PowerShell parser on `scripts/refresh-bond-prices.ps1`
  (Constitution V).

## Environment variables summary

| Var | Where | Purpose |
|-----|-------|---------|
| `BOND_INGEST_SECRET` | backend + scraper | Shared secret for the ingest endpoint. |
| `BACKEND_BASE_URL` | scraper | `http://localhost:8080` (local) / `https://api.investguideua.com` (prod). |
