# Quickstart: Send Registration Verification Email

How to build, configure, run, and verify this feature locally (Windows / Docker per project
conventions). Commands assume the repo root.

## 1. Build (mail is a SOFT dependency)

```powershell
cd backend
mvn -q clean test        # compiles + runs unit tests; no SMTP needed
```

The build MUST succeed with **no mail configuration present** (FR-004). If `mvn`/JDK 21 is not
installed in your environment, state that verification was static-only (Constitution V).

## 2. Run with email DISABLED (default — dev/CI)

No `MAIL_*` set (or `MAIL_ENABLED=false`). Start the stack:

```powershell
docker compose up --build
```

Expect at startup, **once**:

```
INFO  ... mail_delivery_disabled (mail.enabled=false). Set MAIL_ENABLED=true with
      MAIL_HOST/MAIL_FROM/MAIL_USERNAME/MAIL_PASSWORD to send verification emails.
```

Register a user and confirm it still succeeds:

```powershell
curl -X POST http://localhost:8080/api/v1/auth/register `
  -H "Content-Type: application/json" `
  -d '{"email":"you@example.com","password":"Password1"}'
```

- Response: normal `201`/created (account created, `emailVerified=false`).
- To grab the verify link locally, set `MAIL_LOG_LINK=true` (dev only) and read the log line.

## 3. Run with email ENABLED (real send)

Fill `.env` (copy from `.env.example`), e.g. with a real SMTP provider or a local test server:

```bash
MAIL_ENABLED=true
MAIL_HOST=smtp.yourprovider.com
MAIL_PORT=587
MAIL_USERNAME=apikey-or-user
MAIL_PASSWORD=secret
MAIL_FROM=no-reply@investguide.ua
MAIL_START_TLS=true
```

Restart. Expect once at startup:

```
INFO  ... mail_delivery_enabled host=smtp.yourprovider.com port=587 from=no-reply@investguide.ua
```

(An incomplete config — `MAIL_ENABLED=true` with a blank `MAIL_HOST`/`MAIL_FROM` — logs
`WARN ... mail_config_incomplete missing=host,from` instead and still starts.)

Register with a mailbox you control -> a UA verification email arrives -> click the link -> account
becomes verified and 5 free tokens are granted (existing `/verify` behavior).

## 4. Verify the acceptance criteria

| Spec item | How to check |
|-----------|--------------|
| US1 / FR-001/002 | Register (enabled) -> email received -> link verifies + grants tokens |
| US2 AS-1 / FR-004/005 | Start with no mail config -> startup OK + one descriptive INFO line |
| US2 AS-2 / FR-006 | Register while disabled -> still `201`; disabled log entry present |
| US2 AS-3 | `MAIL_ENABLED=true` but blank `MAIL_HOST` -> startup OK + WARN "incomplete" |
| US3 / FR-007 | Point `MAIL_HOST` at an unreachable host -> register still `201`; ERROR logged, no token in log |
| FR-003 / SC-005 | grep logs: no raw token (unless `MAIL_LOG_LINK=true`), no `MAIL_PASSWORD` |
| FR-011 / SC-006 | Register against a slow/unreachable SMTP -> response returns promptly |

## 5. Run the targeted tests

```powershell
cd backend
mvn -q -Dtest=MailDeliveryModeTest,SmtpVerificationNotifierTest,LoggingVerificationNotifierTest,AuthServiceTest test
```

All four classes green; `AuthServiceTest` must remain unchanged and passing (regression guard).

## Log-secret safety check (do not skip)

```bash
# After exercising the flows, scan captured logs:
grep -iE "MAIL_PASSWORD|password=" app.log        # expect: no credential values
# raw token only allowed when MAIL_LOG_LINK=true (dev)
```
