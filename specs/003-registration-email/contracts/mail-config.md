# Contract: Mail Delivery Configuration

The configuration surface operators use to enable/disable verification email. This is the external
contract of the feature (there is **no HTTP API change** — `/api/v1/auth/register` and
`/api/v1/auth/verify` request/response shapes are unchanged).

## `application.yml` block (new `mail:` namespace)

```yaml
mail:
  # Master switch. Default OFF: app runs and registers users with email delivery disabled
  # (verification link is available via logs only when mail.log-link=true).
  enabled: ${MAIL_ENABLED:false}
  host: ${MAIL_HOST:}            # SMTP host; required when enabled=true. Blank == absent.
  # port bound as String then normalized in MailProperties (see below) so that a blank
  # MAIL_PORT='' does NOT crash int binding at startup (FR-004). Default 587.
  port: ${MAIL_PORT:587}
  username: ${MAIL_USERNAME:}    # SECRET (env only); required by most providers
  password: ${MAIL_PASSWORD:}    # SECRET (env only)
  from: ${MAIL_FROM:}            # sender address; required when enabled=true. Blank == absent.
  start-tls: ${MAIL_START_TLS:true}
  connect-timeout-ms: 7000
  read-timeout-ms: 7000
  # DEV ONLY: when true, the disabled/fallback notifier logs the raw verification link for local
  # testing. MUST remain false in production (SC-005). Default false.
  log-link: ${MAIL_LOG_LINK:false}

# Soft-dependency guard (review must-fix): spring-boot-starter-mail on the classpath can
# auto-activate a MailHealthIndicator that pings SMTP and flips /actuator/health to DOWN when mail
# is misconfigured/unreachable, which would FAIL the docker-compose healthcheck and break deploy.
# Disable it so mail never gates liveness (the soft-dependency goal, FR-004).
management:
  health:
    mail:
      enabled: false
```

> **Startup-crash guards (review must-fixes, FR-004):**
> 1. `MAIL_PORT=''` (operator blanks the var) binds an empty string to an `int` and crashes
>    startup. Bind `port` as `String`/`Integer` in `MailProperties` and normalize a blank to the
>    `587` default (mirror the null/blank handling already in `PaymentProperties.Mono`).
> 2. Keep `management.health.mail.enabled: false` (above) so an unreachable SMTP never turns the
>    actuator health endpoint DOWN and fails the compose healthcheck.
> 3. Ensure no `spring.mail.*` / `SPRING_MAIL_*` is ever set in env/compose — that would
>    re-activate Spring's `MailSenderAutoConfiguration` and create a second `JavaMailSender` bean.

## Environment variables (`.env.example` additions, all OPTIONAL)

```bash
# ----- Email / SMTP (OPTIONAL — app builds, starts, and registers without these) -----
# Leave MAIL_ENABLED=false (default) to run with verification email disabled: registration still
# works; a descriptive line is logged at startup. Set MAIL_ENABLED=true AND fill host/from/creds
# to actually send. Incomplete config (enabled but host/from blank) degrades to disabled + a WARN.
MAIL_ENABLED=false
MAIL_HOST=
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=
MAIL_START_TLS=true
# DEV ONLY: print the verification link to logs when email is disabled. NEVER true in prod.
MAIL_LOG_LINK=false
```

## Behavioral contract (startup x registration)

Startup log uses the project's snake_case event-key style (cf. `user_registered`,
`verification_email_stub`), so it indexes cleanly in the ECS JSON log pipeline:

| Config state | Build | Startup | Startup log (event key) | A registration | Email |
|--------------|-------|---------|-------------------------|----------------|-------|
| `enabled=false` (default) | succeeds | succeeds | INFO once: `mail_delivery_disabled` (+ how to enable) | succeeds | none (link in logs iff `log-link=true`) |
| `enabled=true`, host+from set | succeeds | succeeds | INFO once: `mail_delivery_enabled host={} port={} from={}` | succeeds | dispatched off-thread |
| `enabled=true`, host/from blank | succeeds | succeeds | WARN once: `mail_config_incomplete missing=host,from` | succeeds | none (degraded to logging) |
| `enabled=true`, creds wrong | succeeds | succeeds | INFO once: `mail_delivery_enabled ...` | succeeds | send fails -> `verification_email_send_failed email={} reason={exceptionClass}` (no secret), registration unaffected |

> Failure logs record the exception **class + a fixed reason**, never the raw
> `Exception.getMessage()` (some Jakarta Mail auth errors echo the username) and never
> `mail.debug`/protocol output (FR-003/SC-005).

**Invariants**:
- Missing/partial mail config MUST NEVER fail build or startup (FR-004).
- `MAIL_USERNAME` / `MAIL_PASSWORD` MUST NEVER appear in any log line (FR-003/SC-005).
- The raw verification token MUST NEVER appear in logs unless `mail.log-link=true` (dev override).
- The registration HTTP response MUST be identical whether email succeeds, fails, or is disabled
  (FR-006/FR-011).
