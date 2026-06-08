# Quickstart: Precious Metals Price Refresh

How to run and verify feature 011 locally and in production. Mirrors the 009 bond workflow.

## Prerequisites

- Backend running (local dev `:8080`, or prod at `BACKEND_BASE_URL`).
- `METAL_INGEST_SECRET` set identically in: backend env (`.env` / Fly secret) and the scraper env /
  GitHub Actions secret. Blank secret => ingest fails closed (401).
- Node >= 20 in `scraper/`.

## Run the collector

```powershell
# from scraper/
$env:BACKEND_BASE_URL = "http://localhost:8080"
$env:METAL_INGEST_SECRET = "<dev secret>"
$env:METALS_DRY_RUN = "true"     # print sample, do not POST
npm run scrape:metals
```

Drop `METALS_DRY_RUN` to actually warm up the backend (`GET /api/v1/ping`) and POST the batch once.
The scraper fetches `GET https://privatbank.ua/pb/ajax/bank-metall-courses` (plain fetch, no browser),
flattens gold+silver x groups x weights into `IngestMetalRequest[]`, converts `"6 780.00"` ->
`678000`, validates (both metals present, min-records floor), then ingests.

## Verify storage (admin read)

```powershell
# obtain an ADMIN access token, then:
curl -H "Authorization: Bearer <admin token>" http://localhost:8080/api/v1/metal-prices
# expect 200 with one record per metal+rateGroup+weight; non-admin -> 403; anonymous -> 401
```

## Verify user-facing grounding

1. Run an investment search (`POST /api/v1/investments/search`) with a request whose results include a
   precious-metals option.
2. Confirm any option with `category=PRECIOUS_METALS` carries `metal` and `metalPricePerGramMinor`
   equal to the stored sale rate (per gram, primary group, smallest tier) - not a model estimate.
3. With the `metalPrices` collection empty, confirm metals options are dropped (not shown with a blank
   or estimated price) and the rest of the search still returns.

## Schedule

GitHub Actions `refresh-metal-prices.yml`, cron `0 7 * * *` (daily ~10:00 Kyiv), plus
`workflow_dispatch` for manual off-schedule runs. Secret: `METAL_INGEST_SECRET`.

## Verification gates (Constitution V/VI) before "done"

- Non-ASCII scan of any executed Windows script (`.ps1`/`.cmd`/`.bat`) returns clean. (The scraper
  `.mjs`, the workflow `.yml`, and Java sources are not Windows-executed; they stay UTF-8/LF.)
- `mvn -q test` (backend) and `npm test` (scraper) pass, or static-only is declared explicitly.
- Two role sub-agents review, including the scan/parse step.
