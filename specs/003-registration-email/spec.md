# Feature Specification: Send Registration Verification Email

**Feature Branch**: `003-registration-email`  
**Created**: 2026-06-03  
**Status**: Draft  
**Input**: User description: "Currently the registration flow doesn't actually send a letter to user's email. Implement this feature. If mailing server is not configured the build and deployment should not fail but rather write some descriptive error to the logs"

## Overview

When a new user registers, the system issues an email-verification token and is expected to
deliver a verification link to the user's inbox. Today the link is only written to the server
logs (a development stub), so a real user never receives anything and cannot verify their
account — which in turn blocks them from receiving their welcome token grant.

This feature makes registration deliver an **actual verification email** to the user's address.
It must do so **without making the mail server a hard dependency**: if no mail server is
configured (for example in local development or a misconfigured environment), the application
must still build, start, and accept registrations, while writing a clear, descriptive message
to the logs explaining that email delivery is disabled and how to enable it. The existing
verification-token logic, the verification endpoint, and the welcome-token grant are unchanged;
this feature only changes *how the verification link reaches the user*.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - New user receives a verification email (Priority: P1)

A person signs up with their email and password. Within moments they receive an email at that
address containing a link to verify their account. They click the link, their account becomes
verified, and they receive their welcome token grant.

**Why this priority**: This is the core of the feature and the only way an unverified real user
can complete onboarding. Without it, registration produces accounts that can never be verified
through normal means.

**Independent Test**: With a working mail server configured, register a new account using a
mailbox you control, confirm an email arrives containing a working verification link, click it,
and confirm the account transitions to verified and the welcome tokens are granted.

**Acceptance Scenarios**:

1. **Given** a configured mail server and a new, unused email address, **When** the user submits
   registration, **Then** the account is created as unverified **and** a verification email is
   sent to that address containing a single-use verification link.
2. **Given** the verification email has been received, **When** the user opens the link before it
   expires, **Then** their account becomes verified and the welcome tokens are granted (existing
   behavior).
3. **Given** a configured mail server, **When** registration succeeds, **Then** the raw
   verification token appears only in the email and is never written to the application logs.

---

### User Story 2 - Application tolerates a missing/unconfigured mail server (Priority: P1)

An operator deploys or runs the application in an environment where the mail server has not been
configured (no credentials, no host, or intentionally disabled). The application still builds,
starts successfully, and continues to accept registrations. Instead of crashing or failing the
deployment, it records a clear, descriptive log message stating that verification emails are
disabled and what must be configured to enable them.

**Why this priority**: Explicitly required by the request. Mail configuration is environment-
specific and frequently absent in local/dev/CI; a hard dependency would break builds and
deployments. This safety behavior is as important as delivery itself.

**Independent Test**: Start the application with no mail server configured, register a new
account, and confirm: (a) the build/startup does not fail, (b) registration still returns
success, and (c) a descriptive warning/error appears in the logs identifying that email delivery
is disabled and naming the configuration needed to enable it.

**Acceptance Scenarios**:

1. **Given** no mail server configuration is present, **When** the application starts, **Then**
   startup completes successfully and at least one descriptive log message is emitted (once, at
   startup) explaining that verification emails are disabled and how to enable them.
2. **Given** no mail server configuration is present, **When** a user registers, **Then** the
   account is still created and the API returns the normal success response, while a descriptive
   log entry records that the verification email for that registration could not be sent because
   delivery is disabled.
3. **Given** a partially/incompletely configured mail server (e.g., host present but credentials
   missing), **When** the application starts, **Then** it does not crash, it degrades to the
   disabled-with-logging behavior rather than wiring a half-configured sender, and the log clearly
   indicates the configuration is incomplete rather than failing silently.

---

### User Story 3 - Registration survives a transient mail delivery failure (Priority: P3)

A mail server is configured, but a send attempt fails transiently (the mail server is briefly
unreachable, rejects the message, or times out). The user's registration must still complete
and the failure must be recorded in the logs so operators can diagnose it, rather than the
registration request erroring out.

**Why this priority**: Improves resilience and operability but is secondary to the core delivery
and the explicitly required graceful-degradation behavior. A first release can ship with the
basic send path and this hardening can follow.

**Independent Test**: Configure a mail server pointing at an unreachable/invalid endpoint,
register an account, and confirm registration still returns success while a descriptive error
(including the affected email, without exposing the raw token) is logged.

**Acceptance Scenarios**:

1. **Given** a configured mail server that is temporarily unreachable, **When** a user registers,
   **Then** the account is created and the registration response is successful, and a descriptive
   error is logged identifying that the verification email failed to send.
2. **Given** a send failure occurred, **When** an operator inspects the logs, **Then** the log
   entry contains enough detail to diagnose the cause (recipient address and failure reason)
   without leaking the raw verification token or the user's password.

---

### Edge Cases

- **Mail server configured but credentials are wrong / rejected**: startup must not fail;
  registration still succeeds; the authentication failure is logged descriptively on send.
- **User registers with a malformed or undeliverable address that passed validation**: the send
  failure is logged; registration outcome is unaffected (the address-format check at registration
  is unchanged by this feature).
- **Verification link is opened after expiry**: existing verification behavior applies (token
  rejected); this feature does not change token lifetime.
- **Duplicate / repeated registration attempts**: governed by existing duplicate-email and
  rate-limiting rules; no additional emails are sent for a rejected duplicate registration.
- **Sensitive data in logs**: under no failure mode may the raw verification token or password
  appear in logs (only the non-sensitive verification *event* and recipient may be logged).
- **Mail delivery latency**: a slow mail server must not block or noticeably delay the
  registration API response to the user.
- **Dispatched but never delivered** (provider drop, spam filtering, greylisting): the system's
  responsibility ends at successful hand-off to the mail server; guaranteed inbox delivery and
  on-demand resend are out of scope (a lost email leaves the user to wait until token expiry — a
  known follow-up, not a defect of this feature).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: On successful registration, the system MUST send a verification email to the
  registrant's email address containing a working, single-use verification link.
- **FR-002**: The verification link delivered by email MUST be the same token-bearing link the
  existing verification flow expects, such that opening it verifies the account and triggers the
  existing welcome-token grant.
- **FR-003**: When email delivery is enabled, the raw verification token MUST appear only in the
  email and MUST NOT be written to application logs. Mail server credentials MUST NEVER be written
  to logs in any mode (including connection-error/stack-trace output).
- **FR-004**: If the mail server is not configured, the application build and startup MUST still
  succeed (mail configuration MUST NOT be a hard dependency for building or deploying).
- **FR-005**: If the mail server is not configured (or is incompletely configured), the system
  MUST emit at least one clear, descriptive log message **once at startup** stating that
  verification emails are disabled and identifying what configuration is required to enable them.
  (Per-registration disabled-mode log entries per FR-006 are additive to, not a substitute for,
  this startup message.)
- **FR-006**: If the mail server is not configured, registration MUST still succeed and return the
  normal success response; the inability to send the email MUST NOT cause the registration request
  to fail.
- **FR-007**: When a send attempt fails at runtime despite configuration being present, the
  registration request MUST still succeed and the failure MUST be logged with enough detail
  (recipient and reason) to diagnose it, without exposing the raw token, password, or mail
  credentials. If sending is performed off the request thread, such failures MUST still be
  captured in the logs out-of-band (a decoupled send failure MUST NOT be silently swallowed).
- **FR-008**: Mail server connection settings (such as host, port, credentials, sender address,
  and enable/disable state) MUST be configurable via environment/configuration without code
  changes, consistent with how other external integrations in this project are configured.
- **FR-009**: The email MUST clearly identify the sender/product, state the purpose (verify your
  account), present the verification link prominently, and indicate that the link expires.
- **FR-010**: The system MUST NOT send a verification email for a registration attempt that is
  rejected (e.g., duplicate email or rate-limited request).
- **FR-011**: The registration API response MUST be returned to the user without waiting for mail
  delivery to complete; the success/failure of the email send MUST NOT change the registration
  response, and a slow or unresponsive mail server MUST NOT delay that response.

### Key Entities *(include if feature involves data)*

- **Verification Email**: The message delivered to a registrant. Conveys product identity, the
  purpose (account verification), the single-use verification link, and an expiry indication.
  Carries no persisted state of its own beyond what the existing verification token already holds.
- **Mail Delivery Configuration**: The set of settings that determine whether and how emails are
  sent (enabled/disabled state, server host/port, credentials, sender identity). Determines which
  delivery behavior (real send vs. disabled-with-logging) is active.
- **Verification Token** (existing, unchanged): The single-use, time-limited credential whose link
  is now delivered by email instead of only logged.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With a working mail server, 100% of successful registrations result in a
  verification email being dispatched (handed off to the configured mail server for delivery) to
  the registrant's address. Final inbox delivery (spam filtering, greylisting, provider drops) is
  outside the system's control and out of scope.
- **SC-002**: A newly registered user can go from submitting registration to a verified account
  using only the emailed link, without any operator/developer reading server logs.
- **SC-003**: With no mail server configured, the application builds and starts successfully in
  100% of attempts (no build/deploy failure attributable to missing mail configuration).
- **SC-004**: With no mail server configured, every registration still returns success, and each
  such run produces a descriptive log entry that a reviewer can use to identify the missing
  configuration without consulting source code.
- **SC-005**: In no scenario (success, disabled, or failure) do the raw verification token, the
  user's password, or the mail server credentials appear in the application logs, except behind an
  explicit, clearly-named developer-only override (see Assumptions) that is off by default and
  never enabled in production.
- **SC-006**: The registration API response is returned without waiting for the email send to
  complete: measured registration response time is unaffected by mail-server latency (a slow or
  unreachable mail server produces no measurable increase in registration response time versus the
  same flow with delivery disabled).

## Assumptions

- The existing registration, verification-token issuance, verification endpoint, and welcome-token
  grant behaviors are correct and remain unchanged; this feature only replaces the log-only
  delivery stub with real email delivery plus a safe disabled fallback.
- "Send a letter to the user's email" means delivering the existing account-verification link by
  email; no new email types (password reset, marketing, etc.) are in scope.
- The verification email content is primarily in Ukrainian (the product's primary language),
  consistent with the rest of the application's user-facing copy.
- When the mail server is unconfigured, the absence of a configured server is surfaced as a
  descriptive warning/error in the logs (not a silent success), and by default the raw
  verification link/token is NOT written to the logs. A developer-only override (off by default,
  clearly named, never enabled in production) MAY print the verification link to the logs for
  local testing convenience; SC-005 carves out exactly this explicit override.
- Mail server credentials and connection details are provided through the project's existing
  environment/secret configuration mechanism and are treated as secrets (never committed,
  never logged).
- Resending verification emails on demand, email open/click tracking, templated branding beyond a
  clear functional message, and internationalized multi-language email bodies are out of scope for
  this feature.
- Standard outbound email (e.g., SMTP) is the delivery mechanism; integrating a third-party
  transactional-email API is out of scope unless later chosen as the configured server.
