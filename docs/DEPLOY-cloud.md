# InvestGuideUA — Cloud Deployment Runbook (minimal cost)

**Target topology (chosen for lowest cost + Cloudflare-first):**

```
            Browser
               |
   https://investguideua.com            https://api.investguideua.com
   (Angular SPA, Cloudflare Pages)        (Spring Boot, Fly.io machine)
        free, global CDN                  ~$2-5/mo, auto-stop to zero
                                                   |
                                          MongoDB Atlas M0 (free, 512 MB)
```

| Layer | Service | Cost |
|---|---|---|
| Frontend (Angular static SPA) | **Cloudflare Pages** | $0 (500 builds/mo, ~unlimited bandwidth) |
| DNS / TLS / CDN / WAF | **Cloudflare** (zone) | $0 |
| Backend (Java 21 / Spring Boot JAR) | **Fly.io** machine, `shared-cpu-1x`, 512 MB, auto-stop | ~$2-5/mo (no free tier; $5 trial credit) |
| Database | **MongoDB Atlas M0** | $0 (512 MB, 100 ops/s, 500 conns) |
| Domain | any registrar, NS to Cloudflare | ~$10/yr |
| LLM | Anthropic Claude Haiku (pay-per-use) | ~$0.0065 / search (covered by token price) |

**Estimated running cost: ~$2-5 / month + ~$10 / year domain.** Everything except Fly.io and the domain is free-tier.

> **Why not "all on Cloudflare"?** Cloudflare Workers/Pages run JavaScript/WASM, not a JVM. The Spring Boot backend is a JAR and needs a container host. Cloudflare still does the heavy lifting (static hosting, DNS, TLS, CDN, WAF); Fly.io only runs the one backend container, deployed straight from the existing `backend/Dockerfile`.

---

## Naming used in this runbook (replace with your own)

- Root domain: `investguideua.com`
- SPA origin: `https://investguideua.com` (and `www`)
- API origin: `https://api.investguideua.com`
- Fly app name: `investguideua-api`
- Fly region: `ams` (Amsterdam — low latency to Ukraine; `fra` Frankfurt is a fine alternative). Note: the `waw` (Warsaw) region is deprecated on Fly.io — do not use it.
- Atlas region: AWS `eu-central-1` (Frankfurt) — keep DB near the Fly region

> **Keep the API on a *subdomain of the same root domain* as the SPA.** `investguideua.com` and `api.investguideua.com` share a registrable domain, so the refresh-token cookie counts as **same-site** — it survives Safari ITP and Chrome third-party-cookie restrictions. A different root domain for the API would break the refresh flow.

---

## Phase 0 — Pre-flight (local, ~15 min)

You need accounts: **Cloudflare**, **Fly.io** (`flyctl`), **MongoDB Atlas**, **Anthropic**, **monobank** merchant ("Plata by mono", web.monobank.ua). Install the CLIs:

```powershell
# Fly CLI (Windows PowerShell)
pwsh -Command "iwr https://fly.io/install.ps1 -useb | iex"
flyctl version
flyctl auth signup   # or: flyctl auth login

# Cloudflare Wrangler (for Pages) via npx — no global install needed
npx wrangler --version
```

**Generate the JWT secret** (>= 32 bytes, base64) and keep it for Phase 3:

```powershell
$b=[byte[]]::new(48); [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Sanity-check the build locally (optional but recommended):

```powershell
cd backend; .\..\scripts\backend-test.ps1   # mvn test
cd ..\frontend; npm ci; npm run build        # outputs dist/investguide-frontend/browser
```

---

## Phase 1 — Domain + Cloudflare zone (~20 min, mostly waiting)

1. **Register a domain.** Cheapest: register directly at **Cloudflare Registrar** (at-cost pricing, free WHOIS privacy) once the zone exists — or use any registrar (Namecheap, Porkbun) and point nameservers to Cloudflare.
2. In the Cloudflare dashboard: **Add a site** -> enter `investguideua.com` -> choose the **Free** plan.
3. Cloudflare shows two nameservers. At your registrar, replace the nameservers with those two. (If you registered *at* Cloudflare, this is already done.)
4. Wait for the zone to go **Active** (minutes to a few hours).
5. In **SSL/TLS -> Overview**, set encryption mode to **Full (strict)**.
6. In **SSL/TLS -> Edge Certificates**, enable **Always Use HTTPS** and **Automatic HTTPS Rewrites**.

DNS records get added in later phases (Pages auto-creates the SPA records; you add the API record in Phase 4).

---

## Phase 2 — MongoDB Atlas M0 (free) (~15 min)

1. Create a project, then **Build a Database -> M0 (Free)**. Provider **AWS**, region **eu-central-1 (Frankfurt)**.
2. **Database Access -> Add New Database User.** Username `investguide_app`, **Autogenerate a strong password** (save it). The user needs **`readWrite`** (the app creates indexes + seeds at startup — a read-only role fails with *"user is not allowed to do action [createIndex]"*). Either:
   - **Built-in Role "Read and write to any database"** (simplest), or
   - **Specific Privileges**: role `readWrite`, database **`investguide`** — least privilege, but the DB name is **case-sensitive** and must be exactly `investguide` (not the cluster name, not `investGuide`), or index creation is denied.
   This credential is reachable from the whole internet under the M0 allowlist below, so prefer the scoped option and **rotate the password periodically** (`flyctl secrets set MONGODB_URI=...` re-deploys).
3. **Network Access -> Add IP Address.**
   - **MVP pragmatic option:** `0.0.0.0/0` (allow from anywhere). Fly machines have rotating egress IPs, and M0 does **not** support VPC peering / PrivateLink. Security relies on the strong SCRAM password + enforced TLS. Acceptable for the MVP; see the Security note below to tighten later.
   - **Tighter option (v1.1 hardening, not MVP):** allocate a dedicated Fly egress IP and allowlist just that. **Cost note:** a dedicated IPv4 on Fly bills **+$2/mo**, which roughly doubles the backend floor cost — skip it for the MVP and keep `0.0.0.0/0` + a strong scoped credential.
4. **Connect -> Drivers** -> copy the **SRV connection string**, e.g.:
   ```
   mongodb+srv://investguide_app:<PASSWORD>@cluster0.xxxxx.mongodb.net/investguide?retryWrites=true&w=majority&appName=investguideua
   ```
   Put the real password in, and **insert the database name in the path**. Atlas hands you a string
   ending in `.../?retryWrites=...` (a bare `/?` with **no** DB name) — you must change `/?` to
   `/investguide?` so the path segment is **`/investguide`** (the app's DB name). An empty path makes
   the backend fail at startup with *"Database name must not be empty"*. Save this as `MONGODB_URI`
   for Phase 3.

> **URL-encode the password (common startup-failure gotcha).** If the Atlas-generated password
> contains any of `@ : / ? # % [ ]`, it MUST be percent-encoded in the connection string, or the
> Mongo driver aborts at startup with *"The connection string contains invalid user information..."*.
> Encode just the password: PowerShell `[uri]::EscapeDataString("<raw-password>")` (`@`→`%40`,
> `:`→`%3A`, `/`→`%2F`, `#`→`%23`, `%`→`%25`). Simplest alternative: regenerate the Atlas password
> until it is alphanumeric-only, then no encoding is needed.

> Atlas M0 enforces TLS automatically; the Mongo Java driver uses it with no extra config.

---

## Phase 3 — Backend on Fly.io (~30 min)

The backend deploys straight from `backend/Dockerfile` (multi-stage: Maven build -> `eclipse-temurin:21-jre`, runs on `:8080`, healthcheck `/actuator/health`).

### 3.1 Create the Fly app (do **not** deploy yet)

```powershell
cd backend
flyctl launch --no-deploy --ha=false --name investguideua-api --region ams
```

When prompted: **no** Postgres, **no** Redis, **no** to tweaking settings. This generates `fly.toml`. **`--ha=false` is important for cost** — without it Fly provisions 2 machines by default, doubling the bill and defeating the single-machine goal.

### 3.2 Replace `fly.toml` with a cost-minimized config

Edit `backend/fly.toml` to match this (the key parts are the health check, the 512 MB VM, and **auto-stop to zero**):

```toml
app = "investguideua-api"
primary_region = "ams"

[build]
  dockerfile = "Dockerfile"

[env]
  SERVER_PORT = "8080"
  # JVM: cap heap to fit a 512 MB machine
  JAVA_TOOL_OPTIONS = "-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"

[http_service]
  internal_port = 8080
  force_https = true
  auto_stop_machines = "stop"     # scale to zero when idle -> minimal cost
  auto_start_machines = true      # wake on the next request (incl. monobank callback)
  min_machines_running = 0        # 0 = cheapest (cold start ~10-20s). Set 1 to avoid cold starts (~$3-5/mo).

  [[http_service.checks]]
    grace_period = "60s"            # JVM cold start on shared-cpu-1x can take 20-35s
    interval = "15s"
    timeout = "5s"
    method = "GET"
    path = "/actuator/health"

[[vm]]
  size = "shared-cpu-1x"
  memory = "512mb"
```

> **Cold-start tradeoff.** `min_machines_running = 0` is the cheapest: the machine sleeps when idle and wakes on the next HTTP request (~10-20 s JVM start). monobank retries webhooks up to 3×, so payments still settle. If you prefer no cold starts, set `min_machines_running = 1` — costs roughly $3-5/mo instead of ~$2.

### 3.3 Set secrets (encrypted at rest; never in `fly.toml` or git)

> **`MONO_TOKEN` is deliberately omitted for the first deploy.** monobank issues the production
> merchant token only after you show them a working, deployed app — so the backend boots fine
> without it (payment *creation* returns 502 until it's set; auth/search/history all work). Add it
> later with `flyctl secrets set MONO_TOKEN="..." -a investguideua-api` once you have it.

```powershell
flyctl secrets set `
  ANTHROPIC_API_KEY="sk-ant-..." `
  JWT_SECRET="<base64 from Phase 0>" `
  MONGODB_URI="mongodb+srv://investguide_app:<PASSWORD>@cluster0.xxxxx.mongodb.net/investguide?retryWrites=true&w=majority&appName=investguideua" `
  APP_CORS_ALLOWED_ORIGIN="https://investguideua.com" `
  APP_FRONTEND_BASE_URL="https://investguideua.com" `
  PAYMENT_RESULT_URL="https://investguideua.com/payments/result" `
  PAYMENT_CALLBACK_URL="https://api.investguideua.com/api/v1/payments/mono/callback" `
  APP_REFRESH_COOKIE_SECURE="true" `
  APP_REFRESH_COOKIE_SAMESITE="None" `
  -a investguideua-api
```

Notes:
- `APP_CORS_ALLOWED_ORIGIN` must be the **exact** SPA origin (scheme + host, no trailing slash). The backend allows a single origin with credentials.
- Refresh cookie stays `Secure` + `SameSite=None` (the production default) — correct for the SPA and API being on different subdomains over TLS.
- The app **fails fast at startup** if `ANTHROPIC_API_KEY`, `JWT_SECRET`, or `MONGODB_URI` is missing. `MONO_TOKEN` is the exception — it is **optional at startup** (see the note above) so you can deploy first and onboard with the bank second.
- These values are passed as plaintext command arguments, so they land in your PowerShell history and process list. After setting them, clear history (`Clear-History` and delete `(Get-PSReadlineOption).HistorySavePath`) — or pipe each secret in via `flyctl secrets import` from a file you then delete. Fly stores them encrypted at rest; the exposure is only on your local machine.

### 3.4 Deploy

```powershell
flyctl deploy -a investguideua-api
flyctl logs -a investguideua-api          # watch for "Started InvestGuideApplication"
flyctl status -a investguideua-api
```

Verify health over Fly's own hostname first:

```powershell
curl https://investguideua-api.fly.dev/api/v1/ping
curl https://investguideua-api.fly.dev/actuator/health   # expect {"status":"UP"}
```

---

## Phase 4 — Point `api.investguideua.com` at Fly + TLS (~15 min)

1. Tell Fly to manage a cert for the custom hostname:
   ```powershell
   flyctl certs add api.investguideua.com -a investguideua-api
   flyctl certs show api.investguideua.com -a investguideua-api   # shows the DNS target + status
   ```
2. In **Cloudflare -> DNS**, add a record:
   - Type **CNAME**, Name `api`, Target `investguideua-api.fly.dev`.
   - **Proxy status: DNS only (grey cloud)** for the API.

   > **Why grey-cloud the API?** It lets Fly complete its ACME (Let's Encrypt) challenge and terminate TLS directly, which is the least-friction, reliably-working setup. The SPA still gets full Cloudflare proxy/CDN/WAF via Pages. Proxying the API through Cloudflare (orange cloud) is possible but requires Full (strict) + careful cert handling with Fly and is **not** recommended for the MVP. The backend has its own JWT auth + per-IP/per-user rate limiting, and `forward-headers-strategy=framework` already trusts `X-Forwarded-*` for correct client IPs.
3. Wait until `flyctl certs show` reports the cert **Issued**, then verify:
   ```powershell
   curl https://api.investguideua.com/actuator/health   # {"status":"UP"}
   ```

---

## Phase 5 — Frontend on Cloudflare Pages (~20 min)

The SPA must call the API cross-origin, so set the absolute API base URL before building.

### 5.0 Create the Git repo (first time only)

Cloudflare Pages' Git integration (5.2) builds from a **GitHub/GitLab** repo. This project ships
`.gitignore` (excludes `.env`, `backend/target/`, `frontend/node_modules/`, `frontend/dist/`,
`frontend/.angular/`) and `.gitattributes` (pins line endings per CLAUDE.md), but it is not yet a Git
repo. Initialize and push it once, from the repo root (`C:\Users\xamcr\InvestGuideUA`):

```powershell
cd C:\Users\xamcr\InvestGuideUA
git init -b main
# DETERMINISTIC secret gate — run BEFORE staging. Each must echo the path back (= ignored):
git check-ignore .env docs\secrets.txt          # both paths must print; if either is silent, STOP
git add .
git ls-files | Select-String -Pattern "secrets.txt|^\.env$"   # must return NOTHING
git commit -m "Initial commit: InvestGuideUA MVP"
```

Then create the remote and push — pick one:

- **GitHub CLI** (easiest): `winget install GitHub.cli`, then
  ```powershell
  gh auth login
  gh repo create investguideua --private --source=. --remote=origin --push
  ```
- **Manual:** create an **empty private** repo at github.com (no README/.gitignore/license), then
  ```powershell
  git remote add origin https://github.com/<your-username>/investguideua.git
  git push -u origin main
  ```

> **Secret hygiene (do this — secrets were stored in plaintext).** `.gitignore` now blocks `.env`
> and `docs/secrets.txt` from being committed, but `docs/secrets.txt` already wrote live credentials
> to disk, so:
> 1. **Rotate the exposed secrets** (they're considered burned): revoke the **Anthropic API key** at
>    console.anthropic.com and issue a new one; regenerate the **Atlas DB password** (Database Access);
>    regenerate **`JWT_SECRET`** (48 random bytes). Push each new value with
>    `flyctl secrets set KEY="..." -a investguideua-api` (rotating `JWT_SECRET` just forces re-login).
> 2. **Delete `docs/secrets.txt`** after rotating — Fly Secrets (`flyctl secrets list`) is the
>    encrypted source of truth; don't keep prod creds in the working tree even when gitignored. Keep
>    any local copy in a password manager, outside the repo.
> 3. **Enable GitHub Secret Scanning + Push Protection** on the repo (Settings -> Code security) so a
>    future accidental secret push is blocked. Optionally run `gitleaks detect --no-git` once before
>    the first push as a belt-and-braces scan.
> If `git ls-files` ever shows `.env` or `secrets.txt`, stop and run `git rm --cached <file>`.

### 5.1 Point the SPA at the API origin

Edit `frontend/src/environments/environment.ts`:

```ts
export const environment = {
  production: true,
  apiBaseUrl: 'https://api.investguideua.com/api/v1',
};
```

(Local dev `environment.development.ts` keeps `/api/v1` — the dev proxy handles it.) Commit the change.

> The SPA already sends `withCredentials: true` on `/auth/*` calls, so the cross-origin refresh cookie works once CORS (Phase 3.3) and same-root-domain (Phase 1) are in place. No other code change is needed.

### 5.2 Deploy via Git integration (recommended)

In **Cloudflare -> Workers & Pages -> Create -> Pages -> Connect to Git**, pick the repo, then set:

- **Production branch:** `main`
- **Build command:** `cd frontend && npm ci && npm run build && npm run seo:generate`
- **Build output directory:** `frontend/dist/investguide-frontend/browser`
- **Root directory:** `/` (repo root)
- **Environment variable (recommended):** `SEO_SITE_ORIGIN=https://investguideua.com` (used by `seo:generate` for canonical/sitemap/robots URLs).

> **Routing & true 404 (feature 006-seo-optimization).** Do **NOT** write a catch-all
> `/*  /index.html  200` rule - that produces SPA *soft-404s* (every unknown URL returns 200),
> which harms SEO. Instead, `frontend/public/seo/_redirects` maps only the **private/utility**
> routes to the SPA shell (200) and lets unknown paths fall through to the prerendered `404.html`
> with a real HTTP 404. Public routes are prerendered static files served directly. These SEO
> assets (`_redirects`, `404.html`, `og-default.png`, the search-console verification file) DO
> reach the output: `angular.json` now has an explicit `assets` glob copying `public/seo/**` to the
> output root (the Angular 17.3 `application` builder does not auto-copy `public/`, so the glob is
> required). `npm run seo:generate` then writes `robots.txt` + `sitemap.xml` into the same output
> root. **In the Cloudflare Pages project, do not enable the "Single Page Application" framework
> preset** - it injects the catch-all 200 rewrite that this design deliberately avoids.

### 5.2-alt Deploy by direct upload (no Git)

```powershell
cd frontend
npm ci
npm run build
npm run seo:generate
npx wrangler pages deploy dist/investguide-frontend/browser --project-name investguideua
```

> The private-only `_redirects` and `404.html` ship from `public/seo/` via the `angular.json`
> assets glob; `seo:generate` writes `robots.txt` + `sitemap.xml`. No catch-all 200 rule (feature
> 006-seo-optimization).

### 5.3 Custom domain

In the Pages project -> **Custom domains** -> add `investguideua.com`. Cloudflare creates the proxied DNS records automatically. Verify:

```powershell
curl -I https://investguideua.com   # 200, served by Cloudflare
```

> **`www` must redirect, not serve.** The backend allows exactly **one** CORS origin (`APP_CORS_ALLOWED_ORIGIN`, set to `https://investguideua.com` — exact match, no wildcard). If you also serve the SPA on `https://www.investguideua.com`, API calls from `www` are blocked by CORS and the refresh cookie won't apply. So **redirect `www` -> apex** instead of adding it as a Pages custom domain: in **Cloudflare -> Rules -> Redirect Rules**, create a 301 from `www.investguideua.com/*` to `https://investguideua.com/$1`. (Or pick `www` as the single canonical origin and set every URL/CORS value to match — but redirecting to the apex is simpler.)

---

## Phase 6 — Wire monobank "Plata by mono" + first run (~15 min)

1. In the **monobank merchant cabinet** (web.monobank.ua), obtain the merchant **X-Token** and set it as `MONO_TOKEN` (Phase 3.3). The `redirectUrl` and `webHookUrl` are sent by the backend on each `invoice/create` call from `PAYMENT_RESULT_URL` / `PAYMENT_CALLBACK_URL` — confirm those env values point at `https://investguideua.com/payments/result` and `https://api.investguideua.com/api/v1/payments/mono/callback`.
2. The callback endpoint is public (monobank is server-to-server) but is protected by **ECDSA signature verification**: the `x-sign` header is verified with `SHA256withECDSA` against monobank's public key fetched (and cached) from `GET /api/merchant/pubkey` — forged callbacks credit nothing. The backend reads the **raw body bytes** to verify, so the grey-clouded API (Phase 4) must not transform the request body (Cloudflare DNS-only on `api.*` leaves it untouched).
3. **(Launch checklist, optional for first run)** To issue fiscal receipts, bind ПРРО in the monobank cabinet (register ПРРО + cashier with ДПС). Until bound, payments still work; receipts just aren't fiscalized.
4. Seeders run automatically on first boot: the 4-bank provider catalog and the 3 token packs (5/10/25 @ 99/169/379 UAH). Seed-time pricing validation (now using the 1.3% monobank fee) aborts startup if a pack is mispriced.

---

## Phase 7 — End-to-end smoke test (the acceptance path)

```
1. Open https://investguideua.com -> Register a new account.
2. Check backend logs for the verification link (the MVP notifier logs it):
     flyctl logs -a investguideua-api | Select-String "verify"
   Open the link -> account verified -> balance shows 5 tokens.
3. Run a search (amount + currency) -> options returned, balance -> 4,
   disclaimer present, every providerId is one of the 4 seeded banks.
4. Open History -> the search appears.
5. Buy Tokens -> pick a pack -> redirect to the monobank checkout (pay.mbnk.biz) ->
   complete a test payment -> after the signed webhook, balance increases by the pack's tokens.
6. Spend down to 0 tokens -> next search returns 402 INSUFFICIENT_TOKENS.
```

Confirm secrets never reached the client bundle:

```powershell
.\scripts\scan-frontend-secrets.ps1     # expect: clean
```

---

## Day-2 operations

- **Logs:** `flyctl logs -a investguideua-api` (structured ECS JSON).
- **Redeploy backend:** push code, then `flyctl deploy -a investguideua-api`.
- **Redeploy frontend:** push to `main` (Pages auto-builds) or re-run `wrangler pages deploy`.
- **Rotate a secret:** `flyctl secrets set KEY=newval -a investguideua-api` (triggers a rolling restart).
- **Atlas backups:** M0 has no automated backups — for the MVP, periodically `mongodump` from your machine, or upgrade to M10 later for continuous backups.
- **Scale up later:** raise `memory`/`vm size` in `fly.toml`, or set `min_machines_running = 1` to remove cold starts. Atlas M0 -> M10 when you outgrow 512 MB / 100 ops/s.
- **Known MVP limitation — no server-side logout revocation:** the SPA's logout clears local state, but there is no `/auth/logout` endpoint, so an already-issued refresh cookie stays valid until its 14-day TTL. A stolen refresh token can't be revoked early in the MVP. Acceptable for launch; add a logout/revoke endpoint before handling higher-value accounts.

## Cost controls / guardrails

- The app already bounds LLM spend: per-request token budget (3000 in / 700 out), per-user rate limit (5 searches/min), and the 5-free-token cap per verified account.
- Fly: keep `auto_stop_machines = "stop"` and one machine (`--ha=false`). Set a **spending alert** in the Fly dashboard. Note that **auto-stop only saves money if the machine is actually idle** — an external uptime monitor, status pinger, or bot traffic hitting `api.investguideua.com` keeps it awake and erodes the savings. Don't add an aggressive external health pinger; Fly's own health checks don't keep it up. Egress is ~$0.02/GB (negligible for a JSON API).
- Atlas M0 and Cloudflare Pages are free; the only metered external cost is the Anthropic API (~$0.0065/search), which token pricing covers ~50-70x.

## Rollback

- **Backend:** `flyctl releases -a investguideua-api` then `flyctl deploy --image <previous-image-ref>` or `flyctl releases rollback`.
- **Frontend:** in the Pages project, **Deployments -> ... -> Rollback** to a previous build.
