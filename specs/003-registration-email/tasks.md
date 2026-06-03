---
description: "Task list for Send Registration Verification Email"
---

# Tasks: Send Registration Verification Email

**Input**: Design documents from `/specs/003-registration-email/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/)

**Tests**: INCLUDED. The design (research.md D6) explicitly specifies a unit + context-slice test
strategy, and Constitution Principle VI requires verification. Test tasks are therefore part of
each story.

**Organization**: Grouped by user story. US1 (P1) is the MVP (real email delivery + the selection
factory). US2 (P1) layers/verifies the unconfigured-server safety behavior on the same factory.
US3 (P3) hardens runtime send-failure resilience.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3 (Setup & Foundational & Polish have no story label)
- All paths are repo-relative. Backend root: `backend/src/main/java/com/investguide/`,
  tests: `backend/src/test/java/com/investguide/`.

## Path Conventions

Web application, backend-only change. New code lives in `com.investguide.auth` (delivery) and
`com.investguide.config` (the `MailProperties` record), per [plan.md](plan.md) Structure Decision.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the only new dependency. No project scaffolding needed (existing Spring Boot app).

- [X] T001 Add `spring-boot-starter-mail` dependency (no version — managed by the Spring Boot
  3.4.1 parent BOM) to `backend/pom.xml`, alongside the other `spring-boot-starter-*` entries.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Typed config + the pure mode-selection logic that BOTH the SMTP path (US1) and the
fallback path (US2) depend on. None of these reference a notifier/factory class.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Create `MailProperties` `@Validated @ConfigurationProperties(prefix = "mail")` record
  in `backend/src/main/java/com/investguide/config/MailProperties.java` with fields `enabled`
  (boolean), `host` (String, nullable), `port`, `username`/`password` (String, nullable, secrets),
  `from` (String, nullable), `startTls` (boolean), `connectTimeoutMs`/`readTimeoutMs`
  (`@Min(1)`), `logLink` (boolean). **MUST-FIX (FR-004)**: make `port` blank-safe. NOTE: binding
  `MAIL_PORT=''` to an `int` fails **before** any compact-constructor normalization can run, so
  declare the bound field as `String port` (normalize blank -> `"587"` in the compact constructor
  and parse), OR `Integer port` with `@DefaultValue("587")` — do not use a bare `int`. (`Mono`'s
  compact-constructor pattern is the precedent for the normalization, but `Mono` only ever
  normalized a `String`, so copy the String-binding shape, not an `int` field.) Do **not** put
  `@NotBlank` on
  `host`/`from`/`username`/`password` (they are legitimately absent when disabled). Override
  `toString()` to NEVER expose `username`/`password`.
- [X] T003 Register `MailProperties` in the `@EnableConfigurationProperties({...})` list in
  `backend/src/main/java/com/investguide/InvestGuideApplication.java` (depends on T002).
- [X] T004 [P] Add the `mail:` block to `backend/src/main/resources/application.yml` (env-bound,
  `enabled: ${MAIL_ENABLED:false}` default OFF; `host/username/password/from` empty defaults;
  `port: ${MAIL_PORT:587}`; `start-tls`, timeouts, `log-link: ${MAIL_LOG_LINK:false}`) per
  [contracts/mail-config.md](contracts/mail-config.md). **MUST-FIX (FR-004)**: also add
  `management.health.mail.enabled: false` so an unreachable SMTP cannot flip `/actuator/health`
  DOWN and fail the compose healthcheck. Ensure NO `spring.mail.*` key is introduced.
- [X] T005 [P] Add the optional `MAIL_*` variables (`MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`,
  `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_START_TLS`, `MAIL_LOG_LINK`) to `.env.example`
  as a commented "OPTIONAL" block mirroring the `MONO_TOKEN` precedent. File MUST stay pure ASCII,
  LF, no BOM (Constitution V; `.gitattributes` already pins `.env.example` to LF).
- [X] T006 [P] Create `MailDeliveryMode` enum (`DISABLED`, `INCOMPLETE`, `SMTP`) + pure static
  `decide(MailProperties) -> MailDeliveryMode` in
  `backend/src/main/java/com/investguide/auth/MailDeliveryMode.java`. Use `isBlank()` (NOT
  `== null`) for `host`/`from`, since `${MAIL_HOST:}` binds an empty string. No state, no Spring.
- [X] T007 [P] Unit-test the selection logic in
  `backend/src/test/java/com/investguide/auth/MailDeliveryModeTest.java`: `enabled=false` ->
  DISABLED; `enabled=true` + blank host or blank from -> INCOMPLETE; `enabled=true` + host+from
  present -> SMTP; assert empty-string (not just null) is treated as absent (follows T006).

**Checkpoint**: Config binds without crashing even when fully unset; mode decision is proven.

---

## Phase 3: User Story 1 - New user receives a verification email (Priority: P1) 🎯 MVP

**Goal**: With SMTP configured (enabled + host + from), a registration dispatches a Ukrainian
verification email whose link is identical to the existing `/verify` link, so clicking it verifies
the account and grants the welcome tokens.

**Independent Test**: Set `MAIL_ENABLED=true` + host/from/creds, register with a mailbox you
control -> email arrives -> click link -> account verified + 5 tokens granted. (quickstart §3.)

### Tests for User Story 1 ⚠️ (write first; expect FAIL before implementation)

- [X] T008 [P] [US1] `SmtpVerificationNotifierTest` in
  `backend/src/test/java/com/investguide/auth/SmtpVerificationNotifierTest.java`: mock
  `JavaMailSender`, inject a **synchronous/inline** `Executor` so assertions are deterministic;
  assert the built message has correct `To`/`From`/non-empty UA `Subject`, is `multipart/alternative`
  (text + html), the body contains the link `<frontend-base-url>/verify?token=<rawToken>`, and the
  body indicates the link expires (FR-009 — an expiry note derived from `token-ttl-ms`); assert the
  raw token is NOT emitted to logs.
- [X] T009 [P] [US1] Context-slice test in
  `backend/src/test/java/com/investguide/auth/MailDeliverySmtpWiringTest.java`: with
  `mail.enabled=true` + `mail.host` + `mail.from` set, the injected `VerificationNotifier` bean is
  a `SmtpVerificationNotifier` and the context starts.

### Implementation for User Story 1

- [X] T010 [P] [US1] Create `VerificationEmailContent` in
  `backend/src/main/java/com/investguide/auth/VerificationEmailContent.java`: builds UA `subject`,
  `text/plain` and `text/html` bodies from `(email, rawToken)` + `app.frontend-base-url` +
  `app.verification.token-ttl-ms`; build the link with `UriComponentsBuilder` exactly as
  `LoggingVerificationNotifier` does today (FR-002); no external assets. Ukrainian literals live in
  this `.java` (Maven UTF-8) — never in a Windows script. Per
  [contracts/verification-email.md](contracts/verification-email.md).
- [X] T011 [US1] Refactor `backend/src/main/java/com/investguide/auth/LoggingVerificationNotifier.java`
  into a **plain class**: remove `@Component` and `@ConditionalOnMissingBean(name="smtpVerificationNotifier")`
  (the factory now owns wiring — prevents a duplicate `VerificationNotifier` bean), and gate the
  raw-link log line behind `mail.log-link` (default false: NO raw link logged; emit only a
  non-sensitive `verification_email_disabled` event). Constructor takes `AppProperties` + the
  `logLink` flag. **Land T011 together with T014 as one compilable unit**: once `@Component` is
  removed, the *only* `VerificationNotifier` bean is the factory's (T014) — committing T011 without
  T014 leaves the context with zero `VerificationNotifier` beans and `AuthService` fails to wire.
- [X] T012 [US1] In a new `backend/src/main/java/com/investguide/auth/MailDeliveryConfig.java`
  `@Configuration`, add a bounded mail `Executor` bean: `ThreadPoolTaskExecutor` (core 1 / max 2,
  small bounded queue), `setWaitForTasksToCompleteOnShutdown(true)` + a bounded
  `setAwaitTerminationSeconds(...)`. **Rejection policy = drop-and-log** (NOT `CallerRunsPolicy`,
  which would run the send on the registration thread and break FR-011) — see T021 for the dropped
  event. (Same file as T014; sequence T012 -> T013 -> T014.)
- [X] T013 [US1] Create `SmtpVerificationNotifier` in
  `backend/src/main/java/com/investguide/auth/SmtpVerificationNotifier.java` implementing
  `VerificationNotifier`: build the message from `VerificationEmailContent` using
  `javaMailSender.createMimeMessage()` + `new MimeMessageHelper(msg, true, "UTF-8")` (the `true`
  enables the multipart/alternative text+html assembly), and **submit the actual send to the T012
  executor** (off-thread) wrapped in try/catch (failure handling hardened in T020). NOT a
  `@Component` — constructed by the factory. Depends on T010, T012.
- [X] T014 [US1] In `MailDeliveryConfig`, build a `JavaMailSenderImpl` from `MailProperties`
  (host, normalized port, username, password, STARTTLS, connect/read timeouts) **only in SMTP
  mode** (never set `spring.mail.*`, so Boot's auto-config stays inactive), and expose a single
  `@Bean VerificationNotifier` that selects via `MailDeliveryMode.decide(props)`: SMTP ->
  `SmtpVerificationNotifier`, otherwise `LoggingVerificationNotifier`. Emit the once-at-startup
  `mail_delivery_enabled host={} port={} from={}` INFO line for SMTP mode (snake_case event key;
  never log username/password). Depends on T006, T011, T012, T013.

**Checkpoint**: With SMTP configured, registration sends the email end-to-end; `AuthService` and
`AuthServiceTest` are untouched (notifier is injected by interface).

---

## Phase 4: User Story 2 - Application tolerates a missing/unconfigured mail server (Priority: P1)

**Goal**: With no (or incomplete) mail config, the app still builds, starts, and registers users;
it logs one descriptive message explaining email is disabled / incomplete and how to enable it.

**Independent Test**: Start with no `MAIL_*` -> startup succeeds + one `mail_delivery_disabled`
line; register -> still `201`. Set `MAIL_ENABLED=true` with blank `MAIL_HOST` -> startup succeeds +
`mail_config_incomplete` WARN. (quickstart §2, §4.)

### Tests for User Story 2 ⚠️

- [X] T015 [P] [US2] Context-slice test in
  `backend/src/test/java/com/investguide/auth/MailDeliveryFallbackTest.java`: with NO `mail.*` set,
  the context starts and the injected `VerificationNotifier` is a `LoggingVerificationNotifier`.
- [X] T016 [P] [US2] Context-slice test (same file or
  `MailDeliveryIncompleteTest.java`): with `mail.enabled=true` and blank `mail.host`, the context
  starts (no crash) and the wired notifier is the logging fallback (mode INCOMPLETE).
- [X] T017 [P] [US2] `LoggingVerificationNotifierTest` in
  `backend/src/test/java/com/investguide/auth/LoggingVerificationNotifierTest.java`: with
  `logLink=false` the raw verification link is NOT logged; with `logLink=true` the link IS logged
  (SC-005 carve-out).

### Implementation for User Story 2

- [X] T018 [US2] In `MailDeliveryConfig` (the bean from T014), implement the DISABLED and
  INCOMPLETE branches' once-at-startup logging: `mail_delivery_disabled` (INFO, + how to enable)
  and `mail_config_incomplete missing=host,from` (WARN, naming the blank required keys). Confirm
  neither branch can throw (FR-004/AS-2.3). Same file as T014; depends on T014.

**Checkpoint**: App is safe to build/deploy without mail; the disabled/incomplete state is
observable in the ECS JSON logs and registration is unaffected.

---

## Phase 5: User Story 3 - Registration survives a transient mail delivery failure (Priority: P3)

**Goal**: With SMTP configured but a send failing (unreachable/timeout/auth-rejected), registration
still returns success and the failure is logged for diagnosis without leaking secrets.

**Independent Test**: Point `MAIL_HOST` at an unreachable host, register -> still `201`; an ERROR
is logged with recipient + reason, no raw token / password / credentials. (quickstart §4 / FR-007.)

### Tests for User Story 3 ⚠️

- [X] T019 [P] [US3] Failure test in
  `backend/src/test/java/com/investguide/auth/SmtpVerificationNotifierFailureTest.java`: stub
  `JavaMailSender.send(...)` to throw a `MailException` (run the executor inline); assert no
  exception propagates to the caller, and the captured log line contains the recipient + a reason
  but NOT the raw token, password, credentials, or raw `getMessage()` username.

### Implementation for User Story 3

- [X] T020 [US3] Harden the catch block in `SmtpVerificationNotifier` (T013): log
  `verification_email_send_failed email={} reason={exceptionClass}` using the exception **class +
  a fixed reason only** (never raw `getMessage()`), never the token/password/credentials, and never
  enable `mail.debug`/protocol logging. Ensure the off-thread failure is captured here and not
  silently swallowed (FR-007). Same file as T013; depends on T013.
- [X] T021 [US3] Confirm/implement the executor rejection path as **drop-and-log** in
  `MailDeliveryConfig` (T012): on `RejectedExecutionException` (bounded queue saturated), log
  `verification_email_send_dropped email={}` and return — the registration thread MUST NOT run the
  send (keeps FR-011 absolute under burst). Same file as T012/T014; depends on T012.

**Checkpoint**: A failing or saturated SMTP path never fails or slows registration, and is always
diagnosable from the logs.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verification gates (Constitution V & VI) and guardrails spanning the stories.

- [X] T022 [P] Guard against double-wiring: confirm no `spring.mail.*` / `SPRING_MAIL_*` appears in
  `application.yml`, `.env.example`, or `docker-compose.yml`, and that exactly one
  `VerificationNotifier` (and at most one `JavaMailSender`) bean exists (grep + the T009/T015
  context tests).
- [X] T023 [P] Cross-check `quickstart.md` commands/log strings against the implemented event keys
  (`mail_delivery_disabled` / `mail_config_incomplete` / `mail_delivery_enabled` /
  `verification_email_send_failed` / `verification_email_send_dropped`); fix any drift.
- [X] T024 Verification gate (Constitution V): non-ASCII scan of any Windows-executed scripts
  (none changed -> expect zero) and run `mvn -q test` from `backend/` — the full suite incl. the
  **unchanged** `AuthServiceTest` (regression guard) must pass. If no JDK/Maven runtime is
  available, state explicitly that verification was static-only. Note coverage of two FRs that have
  no dedicated affirmative task: **FR-010** (no email on a rejected/duplicate registration) is
  guarded by `AuthServiceTest.register_duplicateEmail_throwsEmailTaken` (notifier never reached);
  **SC-006** (registration latency unaffected by mail latency) is guaranteed by construction
  (off-thread executor + drop-and-log, T012/T013/T021) and is asserted structurally rather than by
  a wall-clock timing test (the failure test T019 runs the executor inline by design).
- [X] T025 Mandatory multi-role sub-agent review (Constitution VI): BE lead + QA + DevOps, INCLUDING
  the compile/parse step from T024. Apply or report findings before marking the feature done.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001)**: no dependencies.
- **Foundational (T002-T007)**: depends on Setup; BLOCKS all user stories.
- **US1 (T008-T014)**: depends on Foundational. The MVP — builds the factory + SMTP path + the
  fallback wiring the later stories refine.
- **US2 (T015-T018)**: depends on US1's `MailDeliveryConfig`/`LoggingVerificationNotifier`
  existing (T011, T014). Adds the disabled/incomplete startup logging + degradation tests.
- **US3 (T019-T021)**: depends on US1's `SmtpVerificationNotifier`/executor (T012, T013). Adds
  failure-isolation hardening + tests.
- **Polish (T022-T025)**: after all desired stories.

### Key blocking edges

- T002 -> T003 (register the record) and -> T006/T014 (decision + sender use the record).
- T010 + T012 -> T013 (notifier needs content + executor).
- T011 + T013 -> T014 (factory wires both notifiers).
- T013 -> T020 ; T012 -> T021 (US3 hardens classes US1 created — same files, so NOT parallel).
- T014 -> T018 (US2 adds branches to the US1 factory — same file, NOT parallel).

### Within each story

- Tests are written first and expected to FAIL before the implementation tasks.
- Models/values (VerificationEmailContent) before services (SmtpVerificationNotifier) before the
  wiring factory (MailDeliveryConfig).

---

## Parallel Opportunities

- **Foundational**: T002, T004, T005, T006 touch different files -> run in parallel; T007 follows
  T006; T003 follows T002.
- **US1 tests**: T008, T009 in parallel. **US1 impl**: T010 is `[P]`; T011 is `[P]` (different
  file); T012 -> T013 -> T014 are the same-file/dependency chain (sequential).
- **US2**: T015, T016, T017 (tests, different files) in parallel; T018 (edits the factory) is
  sequential after T014.
- **US3**: T019 (test) `[P]`; T020 and T021 edit existing files (sequential w.r.t. their targets).
- **Polish**: T022, T023 in parallel; T024 then T025 sequential (review needs the compile result).

### Parallel Example: Foundational

```text
# Together (different files):
Task T002: Create MailProperties in config/MailProperties.java
Task T004: Add mail: block + health-disable to application.yml
Task T005: Add MAIL_* to .env.example
Task T006: Create MailDeliveryMode.decide() in auth/MailDeliveryMode.java
```

### Parallel Example: User Story 1 tests

```text
Task T008: SmtpVerificationNotifierTest (mock JavaMailSender, inline executor)
Task T009: MailDeliverySmtpWiringTest (context slice: SMTP bean wired)
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup (T001).
2. Phase 2 Foundational (T002-T007) — CRITICAL, blocks everything.
3. Phase 3 US1 (T008-T014) — real email delivery + the selection factory.
4. **STOP and VALIDATE**: with SMTP configured, register -> email -> verify -> tokens granted.

### Incremental Delivery

1. Setup + Foundational -> config binds safely even when unset.
2. US1 -> real email dispatch (MVP). Demo with a real mailbox.
3. US2 -> prove/observe graceful degradation with no/incomplete mail config (build & deploy safe).
4. US3 -> prove resilience to transient SMTP failures.
5. Polish -> double-wiring guard + `mvn -q test` + mandatory multi-role review.

### Notes

- `[P]` = different files, no incomplete-task dependency.
- The two **MUST-FIX** items (blank-safe `MAIL_PORT` in T002; `management.health.mail.enabled:
  false` in T004) are the startup/deploy-crash guards surfaced by the plan review — do not drop them.
- `AuthService.java` requires NO change; keep it and `AuthServiceTest.java` green as a regression
  guard.
- Ukrainian email copy is allowed only in `.java` (Maven UTF-8); never in `.ps1/.cmd/.bat`
  (Constitution V).
