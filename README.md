# InvestGuideUA

A web app that helps users discover ways to invest a chosen amount of money **in Ukraine, for
Ukrainians**. The user enters an amount (and optional preferences); the backend queries an LLM
constrained to a curated catalog of Ukrainian providers and returns concrete investment options.
Each search costs one token; every verified user gets 5 free tokens, after which they buy packs.

See [`SPECIFICATION.md`](SPECIFICATION.md) for the full spec and [`TASKS.md`](TASKS.md) for the
implementation tickets.

## Stack

| Layer | Technology |
|---|---|
| Frontend | Angular 17+ (standalone) — *FE-CORE1 onward; X1 ships a static placeholder* |
| Backend | Java 21 + Spring Boot 3.4 (single deployable JAR) |
| Database | MongoDB 7 |
| LLM | Anthropic Claude Haiku (`claude-haiku-4-5-20251001`), server-side only |
| Payments | monobank "Plata by mono" acquiring (gateway-swappable abstraction) |
| Auth | JWT (access + refresh), BCrypt |

## Implemented so far

**Epic X (cross-cutting):** X1 scaffold + Docker Compose · X2 config/secrets · X3 error
envelope, requestId & global exception handling · X4 security baseline (JWT, BCrypt, CORS).
Feature epics (BE-A/C/T/S/P, FE-*) follow per `TASKS.md`.

> **Platform:** development and local deployment target **Windows** (PowerShell + Docker
> Desktop). Commands below are PowerShell. The Docker images themselves are Linux containers —
> Docker Desktop runs them via the WSL2 backend, so no changes are needed for Windows hosts.
> Helper scripts live in `scripts\` (run them from the repo root in PowerShell).

### Prerequisites (Windows)

```powershell
# Docker Desktop (Linux containers, WSL2 backend)
winget install Docker.DockerDesktop

# Only needed to build/run the backend WITHOUT Docker:
winget install EclipseAdoptium.Temurin.21.JDK
winget install Apache.Maven
```

> If PowerShell blocks the helper scripts, allow them for the current user once:
> `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

## Run locally with Docker Compose (≤10 steps)

1. Install Docker Desktop (see above) and make sure it is running.
2. `Copy-Item .env.example .env`  *(or run `.\scripts\setup-env.ps1`, which also generates `JWT_SECRET`)*
3. Open `.env` and set `ANTHROPIC_API_KEY`. `MONO_TOKEN` (the monobank "Plata by mono" merchant
   X-Token) is optional at startup — leave it blank to run the app with payment creation disabled.
4. If you did not run `setup-env.ps1`, generate a `JWT_SECRET` and paste it into `.env`:
   ```powershell
   $b=[byte[]]::new(48);[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b);[Convert]::ToBase64String($b)
   ```
5. For a boot smoke-test you may use any non-empty placeholder values for the secrets; the app
   only requires them to be present at startup.
6. `docker compose up --build`  *(or `.\scripts\dev-up.ps1`)*
7. Wait for the `backend` container to report healthy.
8. Open the SPA at <http://localhost:8081> — it pings `GET /api/v1/ping`.
9. Check backend health: <http://localhost:8080/actuator/health> → `{"status":"UP"}`.
10. Stop with `docker compose down` (add `-v` to drop the Mongo volume).

## Run the backend without Docker (Windows)

The easiest path is the helper script, which loads `.env` into the session and starts the app
(needs a local MongoDB on `mongodb://localhost:27017`, or set `MONGODB_URI`):

```powershell
.\scripts\backend-run.ps1          # mvn spring-boot:run (dev)
.\scripts\backend-run.ps1 -Jar     # package, then java -jar target\...
```

Equivalent manual steps in PowerShell:

```powershell
cd backend
$env:ANTHROPIC_API_KEY="..."   # $env:MONO_TOKEN="..." is optional at startup
$b=[byte[]]::new(48);[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
$env:JWT_SECRET=[Convert]::ToBase64String($b)
mvn -q -DskipTests package
java -jar target\investguide-backend-1.0.0.jar
# → http://localhost:8080/actuator/health returns UP
```

Run the tests: `.\scripts\backend-test.ps1`  *(or `cd backend; mvn -q test`)* — the X1-X4 suite
needs no secrets or MongoDB.

## Configuration & secrets (X2)

All `[CONFIG]` values live in typed `@ConfigurationProperties` beans under
`com.investguide.config` and are overridable via environment without a rebuild (see
`application.yml` and `.env.example`). Four secrets — `ANTHROPIC_API_KEY`, `JWT_SECRET`,
`MONGODB_URI`, and `MONO_TOKEN` (monobank merchant X-Token) — are read from the environment only.
The app **fails fast at startup** with a clear message if a required secret (`ANTHROPIC_API_KEY`,
`JWT_SECRET`, or `MONGODB_URI`) is missing; `MONO_TOKEN` is intentionally optional at startup
(payment creation returns `502` until it is set, while the rest of the app runs). No secret is
ever logged or shipped to the client.

## Error envelope (X3)

Every error response uses:

```json
{ "error": { "code": "INSUFFICIENT_TOKENS", "message": "...", "requestId": "..." } }
```

A `requestId` is assigned per request (returned as the `X-Request-Id` header and included in
logs). Validation failures aggregate field errors under `error.details`. Stack traces are never
exposed to clients.

## Security (X4)

Stateless JWT auth: short-lived access token (≤15 min) + rotating refresh token, BCrypt
password hashing, CORS locked to the configured app origin. Public routes: `/auth/register`,
`/auth/login`, `/auth/refresh`, `/payments/mono/callback`, `/actuator/health`, `/api/v1/ping`.
Everything else requires a valid access token (else `401 UNAUTHORIZED`). Run behind a
TLS-terminating proxy in production.
