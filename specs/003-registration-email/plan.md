# Implementation Plan: Send Registration Verification Email

**Branch**: `003-registration-email` | **Date**: 2026-06-03 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/003-registration-email/spec.md`

## Summary

Registration already issues a verification token and calls a `VerificationNotifier` abstraction;
today the only implementation is `LoggingVerificationNotifier`, which writes the link to the logs
instead of emailing it. This feature adds a real SMTP-backed `VerificationNotifier` that emails
the existing verification link, while keeping mail a **soft dependency**: if SMTP is unconfigured
or only partially configured, the app still builds, starts, and registers users — it degrades to
the logging fallback and emits one descriptive startup log explaining that email is disabled and
how to enable it (mirroring the existing optional-secret pattern used for `MONO_TOKEN`). Email
sending is offloaded to a small bounded executor so a slow/failed SMTP server never blocks or
fails the registration response. No HTTP contract, token-ledger, or verification logic changes.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.4.1 (existing); add `spring-boot-starter-mail` (Jakarta
Mail, version managed by the Boot BOM — current, non-deprecated)
**Storage**: MongoDB 7 (existing `verificationTokens` collection — unchanged; no new collection)
**Testing**: JUnit 5 + Mockito + AssertJ + Spring Boot Test (existing stack); mock `JavaMailSender`
for notifier unit tests; a context-loads slice for bean-selection. No new test dependency required.
**Target Platform**: Single Spring Boot JAR in Docker (Linux container), Mongo + backend + SPA via
`docker-compose.yml` (unchanged topology)
**Project Type**: Web application — backend change only (`backend/`); no frontend change
**Performance Goals**: Registration API response unaffected by mail latency (send is off-thread);
SMTP send governed by a connect/read timeout so a hung server cannot pin a worker indefinitely
**Constraints**: Mail credentials are secrets (env only, never logged); raw verification token never
logged by default; build/startup must not fail when mail is unconfigured; single backend instance
(no queue/broker) per Constitution Principle I
**Scale/Scope**: MVP signup volume (per-instance rate limit `signup-per-hour-per-ip: 10`); a bounded
in-process thread pool is sufficient — no external mail queue

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verified against `.specify/memory/constitution.md` (v1.0.0):

- [x] **I. MVP Discipline**: No new service/instance, queue, or broker. Delivery uses a small,
      bounded in-process `ThreadPoolTaskExecutor` (non-blocking send), not a message queue or extra
      deployable. Single backend + single MongoDB + managed providers preserved. Scope limited to
      replacing the log-only stub with real delivery + safe fallback. (Thread-pool rationale noted;
      not a Principle-I violation — see Complexity Tracking: none required.)
- [x] **II. Fixed Stack**: Angular/Java 21/Spring Boot 3.x/MongoDB unchanged. The only new
      dependency is `spring-boot-starter-mail`, whose version is managed by the existing Spring Boot
      3.4.1 parent BOM (no pinned/forked version, current release line, not deprecated/EOL). Delivery
      stays behind the existing `VerificationNotifier` abstraction (provider swappable).
- [x] **III. LLM Guardrails**: Not applicable — this feature makes no LLM calls and touches no
      catalog/prompt path.
- [x] **IV. Financial Integrity**: Not applicable to delivery; the token-grant path
      (`verify` -> `TokenLedgerService.grantFreeTokens`, idempotent single-doc guarded update) is
      untouched. Money/token invariants unaffected.
- [x] **V. Encoding & Verification**: No Windows-executed script (`.ps1/.cmd/.bat`) is modified.
      Ukrainian email copy lives in Java source, which Maven pins to UTF-8 (`project.build`
      `.sourceEncoding=UTF-8`) — allowed by the encoding rule. `.env.example` stays LF / no BOM.
      Verification gate = `mvn -q test` (parse/compile + tests) plus a non-ASCII scan of scripts
      (none changed); if no JDK/Maven runtime is available it will be declared static-only.
- [x] **VI. Multi-Role Review**: At least two role sub-agents (BE lead + QA, plus DevOps for the
      config/deploy surface) will review, including an actual compile/parse step, before done.

**Result**: PASS. No violations; Complexity Tracking left empty.

### Post-design review (Principle VI)

Ran the mandatory multi-role review on the design (BE lead + DevOps). Both returned **PASS WITH
NITS**; findings folded into `research.md` (D3, D7), `data-model.md`, and `contracts/mail-config.md`
before `/speckit.tasks`. Two are startup/deploy must-fixes that `tasks.md` MUST carry:

1. Bind `mail.port` blank-safe so a blank `MAIL_PORT=''` cannot crash `int` binding (FR-004).
2. Set `management.health.mail.enabled: false` so an unreachable SMTP cannot flip
   `/actuator/health` DOWN and fail the compose healthcheck (FR-004).

Plus: drop-and-log executor rejection policy (not `CallerRunsPolicy`) to keep FR-011 absolute;
no `spring.mail.*` (avoid auto-config double-wiring); snake_case log event keys; log exception
class + fixed reason (never raw `getMessage()`). The post-design re-check introduced no new
Constitution violations.

> Note: this is a planning/design review — no `.java`/`.ps1` were written, so the
> compile/parse gate (Principle V) applies at implementation time (`mvn -q test`), not here. No
> Windows-executed script was modified; the agent-context script's stray BOM on `CLAUDE.md` was
> stripped (verified byte-level: file starts `23 20 43` = `# C`, no BOM).

## Project Structure

### Documentation (this feature)

```text
specs/003-registration-email/
|-- plan.md              # This file (/speckit.plan output)
|-- research.md          # Phase 0 output
|-- data-model.md        # Phase 1 output
|-- quickstart.md        # Phase 1 output
|-- contracts/           # Phase 1 output (config + email-message contracts)
|   |-- mail-config.md
|   `-- verification-email.md
|-- checklists/
|   `-- requirements.md   # from /speckit.specify
`-- tasks.md             # /speckit.tasks output (NOT created here)
```

### Source Code (repository root)

```text
backend/
|-- pom.xml                                    # + spring-boot-starter-mail
`-- src/
    |-- main/
    |   |-- java/com/investguide/
    |   |   |-- InvestGuideApplication.java     # + MailProperties in @EnableConfigurationProperties
    |   |   |-- config/
    |   |   |   `-- MailProperties.java          # NEW: @ConfigurationProperties(prefix="mail")
    |   |   `-- auth/
    |   |       |-- VerificationNotifier.java        # unchanged interface (the seam)
    |   |       |-- LoggingVerificationNotifier.java  # MODIFIED: plain class, dev-only link logging
    |   |       |-- SmtpVerificationNotifier.java     # NEW: real SMTP delivery (off-thread, isolated)
    |   |       |-- VerificationEmailContent.java     # NEW: builds UA subject + HTML/plain body
    |   |       |-- MailDeliveryConfig.java           # NEW: @Configuration; selects notifier,
    |   |       |                                     #      builds JavaMailSender + bounded executor,
    |   |       |                                     #      logs the once-at-startup mode banner
    |   |       `-- AuthService.java                  # unchanged (still calls notifier.sendVerification)
    |   `-- resources/
    |       `-- application.yml                  # + `mail:` block (env-bound, disabled by default)
    |                                            #   + management.health.mail.enabled: false (soft-dep guard)
    `-- test/java/com/investguide/auth/
        |-- MailDeliveryModeTest.java            # NEW: selection logic (disabled/incomplete/complete)
        |-- SmtpVerificationNotifierTest.java    # NEW: mock JavaMailSender; build + failure isolation
        |-- LoggingVerificationNotifierTest.java # NEW: default no-link; dev override logs link
        `-- AuthServiceTest.java                 # unchanged (notifier mocked) - must stay green

.env.example                                     # + MAIL_* (optional, commented)
```

**Structure Decision**: Web application, backend-only change. All new code lives in the existing
`com.investguide.auth` package (delivery logic) and `com.investguide.config` package (the
`MailProperties` record), matching where `VerificationNotifier`/`AppProperties` already live. The
`VerificationNotifier` interface is the unchanged extension seam the codebase was designed around.

## Complexity Tracking

> No Constitution violations to justify. The bounded in-process executor is a standard
> non-blocking primitive within the single backend instance (not a queue/broker/extra service),
> so Principle I is satisfied without an exception.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| (none)    | -          | -                                    |
