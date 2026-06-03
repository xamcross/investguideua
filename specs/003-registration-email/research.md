# Phase 0 Research: Send Registration Verification Email

All Technical Context items were resolvable from the existing codebase conventions and Spring Boot
3.4 capabilities; no open `NEEDS CLARIFICATION` remained from the spec. Decisions below.

## D1. Mail library / dependency

- **Decision**: Add `spring-boot-starter-mail` (no version — managed by the Spring Boot 3.4.1
  parent BOM). Use `JavaMailSender` / `JavaMailSenderImpl` (Jakarta Mail).
- **Rationale**: Already in the Boot BOM the project pins, so it is current and non-deprecated
  (Constitution II). It is the idiomatic Spring SMTP client and needs no third-party account
  (Constitution I — no new managed service). Keeps delivery behind the existing
  `VerificationNotifier` seam.
- **Alternatives considered**:
  - Transactional-email HTTP API (SendGrid/Mailgun/SES): rejected for MVP — adds a vendor account,
    secret, and SDK; spec explicitly scopes this out (Assumptions). The `VerificationNotifier`
    abstraction lets such a provider drop in later without touching auth logic.
  - Hand-rolled raw SMTP over sockets: rejected — reinvents a solved problem, no upside.

## D2. How "configured vs not configured" is detected, and graceful degradation

- **Decision**: Introduce a typed `MailProperties` (`@ConfigurationProperties(prefix = "mail")`)
  with an explicit `enabled` flag plus `host`, `port`, `username`, `password`, `from`, `startTls`,
  and a dev-only `logLink` flag. A single `@Configuration` factory (`MailDeliveryConfig`) inspects
  these at startup and **chooses the bean in Java code** (not via annotation-only conditions):
  - `enabled=false` (default) or absent -> **disabled mode**: wire `LoggingVerificationNotifier`.
  - `enabled=true` AND required fields (`host`, `from`) present -> **SMTP mode**: build a
    `JavaMailSenderImpl` from `MailProperties` and wire `SmtpVerificationNotifier`.
  - `enabled=true` BUT required fields blank/missing -> **incomplete mode**: wire
    `LoggingVerificationNotifier` and log a descriptive WARN naming the missing keys (FR-005,
    spec AS-2.3 "degrade rather than half-wire"; never throw -> startup cannot fail, FR-004).
- **Rationale**: Java-side selection is the only way to satisfy *all three* of (a) no startup
  crash when incomplete, (b) a descriptive once-at-startup message, and (c) a binary
  fully-wired-or-not sender. Pure `@ConditionalOnProperty` can gate on the flag but cannot emit a
  tailored "incomplete config" warning or fall back without an exception. The decision predicate is
  extracted to a pure, unit-testable helper (`MailDeliveryMode.decide(props)`).
- **Spring auto-config interaction**: Boot's `MailSenderAutoConfiguration` activates only when
  `spring.mail.host` is set. We deliberately bind our own `mail.*` namespace and never set
  `spring.mail.*`, so the auto-configured `JavaMailSender` is **not** created and cannot half-wire
  a hostless sender. We construct `JavaMailSenderImpl` ourselves only in SMTP mode.
- **Precedent**: Mirrors the existing optional-secret pattern for `MONO_TOKEN`
  (`payment.mono.token: ${MONO_TOKEN:}` — optional at startup, feature degrades until set) rather
  than the fail-fast required secrets (`JWT_SECRET`, `ANTHROPIC_API_KEY`). The constitution
  explicitly blesses this "optional at startup, degrade" shape.
- **Alternatives considered**:
  - `SmtpVerificationNotifier` as `@Component @ConditionalOnProperty(name="mail.enabled")` +
    keeping `LoggingVerificationNotifier`'s `@ConditionalOnMissingBean(name="smtpVerificationNotifier")`:
    rejected because the incomplete-config case would either crash at construction or silently
    activate a broken sender — neither satisfies AS-2.3.
  - Fail-fast at startup when mail missing: rejected — directly violates FR-004 and the user's
    explicit "build and deployment should not fail" requirement.

## D3. Non-blocking send + failure isolation (FR-006, FR-007, FR-011)

- **Decision**: `SmtpVerificationNotifier.sendVerification(...)` submits the actual send to a
  small **bounded** `ThreadPoolTaskExecutor` (e.g. core 1 / max 2, small bounded queue), and the
  submitted task wraps the send in try/catch that logs a descriptive error on failure (recipient +
  exception **class + fixed reason**, **never** raw `getMessage()`/token/password/creds — some
  Jakarta Mail auth errors echo the username; `mail.debug`/protocol logging stays off).
  - **Rejection policy (review fix)**: do NOT use `CallerRunsPolicy` — on queue saturation it runs
    the send on the *calling registration thread*, reintroducing the SMTP-latency block FR-011
    forbids. Use a **drop-and-log** policy instead (`DiscardPolicy`, or `AbortPolicy` with the
    `RejectedExecutionException` caught and logged as `verification_email_send_dropped`), so a
    burst against a slow SMTP can never delay the registration response. The dropped-email
    trade-off is acceptable for MVP (resend is out of scope) and is logged, not silent.
  - **Lifecycle (review fix)**: use a Spring-managed `ThreadPoolTaskExecutor` bean with
    `setWaitForTasksToCompleteOnShutdown(true)` + a bounded `setAwaitTerminationSeconds(...)` so
    in-flight sends finish on container stop rather than being abruptly dropped.
  The token row is still persisted **synchronously** in `AuthService.issueAndSendVerification`
  (so the link is valid immediately); only the email hand-off is off-thread. `AuthService` is
  unchanged — it keeps calling `verificationNotifier.sendVerification(...)`, which now returns
  promptly.
- **Rationale**: Putting async+isolation inside the SMTP notifier means (a) registration never
  waits on SMTP and never fails because of it (FR-006/FR-011), (b) `LoggingVerificationNotifier`
  stays trivially synchronous (instant), and (c) `AuthService` and its existing unit tests need no
  change. The bounded pool + bounded queue + **drop-and-log** rejection (see above) prevents
  unbounded thread/memory growth under a burst and degrades gracefully (Constitution I) without
  ever spilling the send back onto the registration thread. A connect/read timeout on the mail
  session ensures a hung server cannot pin a worker forever.
- **Why not `@Async`**: `@Async` needs a Spring proxy; the notifier is constructed inside a
  `@Configuration` factory (not a scanned proxied bean), so an explicit executor is cleaner and
  avoids `@EnableAsync` global surface. The executor is a normal bean owned by `MailDeliveryConfig`.
- **Out-of-band failure capture (FR-007)**: because the catch/log lives *inside* the submitted
  task, a decoupled send failure is always logged and never silently swallowed.
- **Alternatives considered**: synchronous send in the request thread (rejected — FR-011 violation,
  slow SMTP would delay/possibly fail registration); a persistent outbox/retry queue (rejected for
  MVP — Constitution I; resend is explicitly out of scope).

## D4. Email content (FR-009)

- **Decision**: A `VerificationEmailContent` helper builds a Ukrainian-language message: clear
  product identity ("InvestGuideUA"), purpose ("Pidtverdte svoyu adresu" / verify your account),
  the verification link prominently as the primary call to action, and an explicit note that the
  link expires (derived from `app.verification.token-ttl-ms`, e.g. "24 hodyny"). Send a
  `multipart/alternative` MIME message with both a `text/plain` and a simple inline-styled
  `text/html` part (no external assets/images), UTF-8.
- **Rationale**: Plain+HTML alternative maximizes deliverability and renders in all clients without
  external image/template dependencies (Constitution I — minimal). Ukrainian matches the product's
  primary language (spec Assumption). The link is built exactly as the existing stub builds it
  (`<app.frontend-base-url>/verify?token=<rawToken>` via `UriComponentsBuilder`), guaranteeing the
  emailed link is identical to what the `/verify` endpoint expects (FR-002).
- **Encoding note**: Ukrainian literals live in `.java` (Maven `sourceEncoding=UTF-8`) — permitted;
  this is not a Windows-executed script, so Constitution V's ASCII rule does not apply here.
- **Alternatives considered**: Thymeleaf/FreeMarker templating (rejected — extra dependency and
  machinery for a single static email, Constitution I); plain-text only (rejected — FR-009 wants
  the link "prominent"; HTML gives a clearer CTA while the text part remains the fallback).

## D5. Secrets, logging hygiene, and configuration surface (FR-003, FR-008)

- **Decision**: `mail.username`/`mail.password` bind from `MAIL_USERNAME`/`MAIL_PASSWORD`
  environment variables (no defaults in `application.yml`); added to `.env.example` as optional,
  commented entries alongside the existing optional `MONO_TOKEN`. No mail secret is ever placed in
  a log statement; failure logs include only recipient + a sanitized reason. `toString()` on
  `MailProperties` is never logged; if a startup line echoes config it prints host/port/from/enabled
  only — never username/password.
- **Rationale**: Matches the constitution's secrets rule (env/secret store only, never logged) and
  the existing `.env` conventions (LF, no BOM). The `from` address and `host` are non-secret and may
  appear in the startup banner to aid operators (SC-004).
- **Alternatives considered**: logging the full effective mail config for debugging (rejected —
  credential-leak vector, FR-003/SC-005).

## D6. Testing strategy

- **Decision**:
  - Unit: `MailDeliveryModeTest` (pure decision: disabled / incomplete / complete);
    `SmtpVerificationNotifierTest` (mock `JavaMailSender`, **synchronous/inline executor** in test
    so assertions are deterministic — assert recipient/subject/link present; assert a thrown
    `MailException` is caught, registration-side unaffected, and the log contains no raw token);
    `LoggingVerificationNotifierTest` (default `logLink=false` -> link NOT logged; `logLink=true`
    -> link logged).
  - Slice: a `@SpringBootTest`/context test asserting bean selection — no `mail.*` -> the wired
    `VerificationNotifier` is the logging impl; `mail.enabled=true` + host/from -> the SMTP impl.
  - Regression: existing `AuthServiceTest` stays green unchanged (notifier is mocked there).
- **Rationale**: Deterministic, no live SMTP, no new test dependency. The executor is injected so
  tests can run it inline.
- **Alternatives considered**: GreenMail in-memory SMTP for a true end-to-end send test —
  reasonable but adds a test dependency; deferred as an optional follow-up, not required for the
  acceptance criteria (which are about dispatch + degradation, both coverable with a mock).

## D7. Multi-role review hardening (BE lead + DevOps)

The mandatory Principle-VI review (BE lead + DevOps) returned PASS WITH NITS; the following are
folded into the design above and MUST be reflected in `tasks.md`:

- **(must-fix) `MAIL_PORT=''` startup crash**: a blank env var binds an empty string to an `int`
  and fails startup, violating FR-004. Bind `port` blank-safe (normalize blank -> `587`), mirroring
  `PaymentProperties.Mono`. (D2 / data-model updated.)
- **(must-fix) actuator mail health indicator**: `spring-boot-starter-mail` can auto-activate a
  `MailHealthIndicator` that pings SMTP and flips `/actuator/health` DOWN when mail is
  unreachable/misconfigured, failing the docker-compose healthcheck and breaking deploy. Set
  `management.health.mail.enabled: false` so mail never gates liveness. (contracts/mail-config.md.)
- **(guard) no `spring.mail.*`**: never set `spring.mail.*`/`SPRING_MAIL_*` in env/compose, or
  Spring's `MailSenderAutoConfiguration` re-activates and creates a second `JavaMailSender` bean.
- **(consistency) log event keys**: startup + failure logs use snake_case event keys
  (`mail_delivery_disabled` / `mail_config_incomplete missing=...` / `mail_delivery_enabled ...` /
  `verification_email_send_failed ...`) to match the project's ECS-JSON logging convention.
- **(hygiene) failure logs**: log exception class + a fixed reason, never raw `getMessage()`.
- **(behavior-change note)** the dev-only `log-link` defaults to `false`, which is *stricter* than
  today's `LoggingVerificationNotifier` (which always logs the raw link). Local-dev users who read
  the link from logs must now set `MAIL_LOG_LINK=true`.

## Resolved unknowns summary

| Item | Resolution |
|------|------------|
| Mail mechanism | `spring-boot-starter-mail` / `JavaMailSender` (BOM-managed) |
| "Configured?" detection | Java-side factory over typed `MailProperties` (enabled + host + from) |
| Incomplete config | Degrade to logging + WARN; never crash (FR-004/AS-2.3) |
| Non-blocking/isolation | Bounded executor inside the SMTP notifier; token persisted sync |
| Email format | UA `multipart/alternative` (text + simple HTML), link == `/verify` link |
| Secrets | `MAIL_USERNAME`/`MAIL_PASSWORD` env only; never logged |
| Tests | Mock `JavaMailSender` + mode unit tests + context-loads slice; no new dep |
