# Phase 1 Data Model: Send Registration Verification Email

This feature adds **no new persisted collection** and changes **no existing document schema**. The
"entities" here are configuration and in-memory delivery constructs, plus the unchanged existing
documents they interact with.

## New: `MailProperties` (configuration record)

Typed `@ConfigurationProperties(prefix = "mail")`, `@Validated`, bound from `application.yml` /
environment. Registered in `InvestGuideApplication`'s `@EnableConfigurationProperties` list.

| Field | Type | Source / default | Notes |
|-------|------|------------------|-------|
| `enabled` | `boolean` | `mail.enabled` (default `false`) | Master switch. Off -> logging fallback. |
| `host` | `String` (nullable) | `MAIL_HOST` | SMTP host. Required for SMTP mode. Blank treated as absent. |
| `port` | `Integer`/`String`-normalized | `mail.port` (default `587`) | Bound blank-safe: a blank `MAIL_PORT=''` MUST normalize to `587`, NOT crash `int` binding (FR-004). Mirror `PaymentProperties.Mono` null/blank handling. |
| `username` | `String` (nullable) | `MAIL_USERNAME` | **Secret.** Never logged. |
| `password` | `String` (nullable) | `MAIL_PASSWORD` | **Secret.** Never logged. |
| `from` | `String` (nullable) | `MAIL_FROM` | Sender address shown to users. Required for SMTP mode. |
| `startTls` | `boolean` | `mail.start-tls` (default `true`) | STARTTLS on submission port. |
| `connectTimeoutMs` | `int` | `mail.connect-timeout-ms` (default `7000`) | Mirrors other clients' 7s. |
| `readTimeoutMs` | `int` | `mail.read-timeout-ms` (default `7000`) | Bounds a hung server. |
| `logLink` | `boolean` | `mail.log-link` (default `false`) | **Dev-only** override: when true, the disabled/logging fallback prints the raw verification link (SC-005 carve-out). MUST stay false in prod. |

**Validation rules**:
- No `@NotBlank` on `host`/`from`/`username`/`password` (they are legitimately absent when
  `enabled=false`) — completeness is enforced at selection time, not by bean validation, so an
  unconfigured app still starts (FR-004).
- `connectTimeoutMs`, `readTimeoutMs`: `@Min(1)`. `port`: bound blank-safe (see above) and `@Min(1)`
  applied only after normalization — never let a blank env var fail `int` binding at startup.
- `toString()` MUST NOT expose `username`/`password` (override on the record; never log the record).
- Register `MailProperties` in `InvestGuideApplication`'s `@EnableConfigurationProperties` list.

## New: `MailDeliveryMode` (pure decision)

A small enum + pure predicate `decide(MailProperties) -> MailDeliveryMode`, unit-testable in
isolation. No state. The predicate MUST use `isBlank()` (not `== null`) for `host`/`from`, since
`application.yml`'s `${MAIL_HOST:}` binds an empty string, never null (the codebase already hit
this null-vs-blank trap in `PaymentProperties.Mono`).

| Mode | Condition | Wired notifier | Startup log (event key) |
|------|-----------|----------------|-------------------------|
| `DISABLED` | `enabled == false` | `LoggingVerificationNotifier` | INFO `mail_delivery_disabled` (+ how to enable) |
| `INCOMPLETE` | `enabled == true` AND (`host` blank OR `from` blank) | `LoggingVerificationNotifier` | WARN `mail_config_incomplete missing=...` |
| `SMTP` | `enabled == true` AND `host` and `from` present | `SmtpVerificationNotifier` | INFO `mail_delivery_enabled host={} port={} from={}` |

State transition: chosen **once at startup** (bean wiring); not re-evaluated at runtime.

**Bean-wiring rule**: `MailDeliveryConfig` produces **exactly one** `VerificationNotifier` bean.
Neither `LoggingVerificationNotifier` nor `SmtpVerificationNotifier` may carry `@Component` (both
are plain classes constructed by the factory), so the old
`@ConditionalOnMissingBean(name="smtpVerificationNotifier")` seam is removed and no duplicate-bean
ambiguity can arise. `AuthService` injects by the `VerificationNotifier` interface type (unchanged).

## New: `VerificationEmailContent` (in-memory value)

Builds the outgoing message from `(email, rawToken)` + `app.frontend-base-url` +
`app.verification.token-ttl-ms`:

| Part | Content |
|------|---------|
| `subject` | UA, e.g. "Pidtverdte svoyu adresu - InvestGuideUA" |
| `link` | `<app.frontend-base-url>/verify?token=<rawToken>` (identical to existing stub/`/verify` contract) |
| `text` | UA plain-text body with the link and an expiry note derived from the TTL |
| `html` | UA `text/html` alternative, link as the prominent CTA, no external assets |

Carries no persisted state. The `rawToken` exists only in the email parts and the synchronously
saved token **hash** (existing behavior) — never in logs (FR-003).

## Unchanged existing entities (for reference)

- **`User`** (`users` collection): `email`, `passwordHash`, `emailVerified` (false at signup),
  `tokenBalance` (0 -> 5 on first verify). **No change.**
- **`VerificationToken`** (`verificationTokens` collection): `userId`, `tokenHash` (SHA-256),
  `expiresAt` (TTL index), `used`. Still saved synchronously during registration so the emailed
  link is valid immediately. **No change.**
- **Token grant** (`TokenLedgerService.grantFreeTokens`, invoked by `/verify`): single-document
  status-guarded, idempotent free grant. **No change.**

## Relationships / flow

```text
register(email, password)
  -> save User (unverified)                         [sync, existing]
  -> save VerificationToken(hash, expiresAt)        [sync, existing]
  -> verificationNotifier.sendVerification(email, rawToken)
        SMTP mode:   build VerificationEmailContent -> submit send to bounded mailExecutor
                     (off-thread; try/catch logs failure w/o secrets)        [async, NEW]
        DISABLED/INCOMPLETE mode: LoggingVerificationNotifier
                     (logs event; link only if mail.log-link=true)           [sync, MODIFIED]
  -> return RegisterResponse immediately            [unaffected by send outcome, FR-011]
```
