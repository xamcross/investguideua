<!--
SYNC IMPACT REPORT
==================
Version change: (unratified template) -> 1.0.0
Bump rationale: Initial ratification. The file was previously an unfilled template
  (all [PLACEHOLDER] tokens); this is the first concrete adoption, so it takes the
  baseline MAJOR.MINOR.PATCH of 1.0.0 rather than a delta bump.

Principles defined (6; template shipped 5 slots, one added per user mandate):
  I.   MVP Discipline - Minimal Cost & Scale
  II.  Fixed, Current Technology Stack (no deprecated dependencies)
  III. LLM Guardrails & Cost Control
  IV.  Financial Integrity & Token Accounting
  V.   Encoding & Verification Discipline (Windows-safe)
  VI.  Mandatory Multi-Role Sub-Agent Review   <- added beyond the 5 template slots

Sections:
  Added: "Technology, Security & Compliance Constraints" (was [SECTION_2_NAME])
  Added: "Development Workflow & Quality Gates"          (was [SECTION_3_NAME])
  Added: "Governance" (filled)

Templates / artifacts reviewed for consistency:
  - .specify/templates/plan-template.md   ... UPDATED (Constitution Check gates filled)
  - .specify/templates/spec-template.md   ... reviewed, no change required (structure compatible)
  - .specify/templates/tasks-template.md  ... reviewed, no change required (sample tasks are
                                              replaced per-feature; review/verify gates live in
                                              the constitution + plan gate)
  - .specify/templates/commands/*.md      ... N/A (directory does not exist in this project)
  - README.md / SPECIFICATION.md / CLAUDE.md ... source of truth; not modified

Deferred TODOs: none. RATIFICATION_DATE set to initial adoption date (today).
-->

# InvestGuideUA Constitution

## Core Principles

### I. MVP Discipline - Minimal Cost & Scale

Every feature MUST ship as a fully working end-to-end flow at the smallest viable
footprint. Concretely:

- The deployment topology MUST remain a single backend instance, a single MongoDB, and a
  managed LLM API. Microservices, Kubernetes, message queues, and multi-region scaling are
  PROHIBITED for the MVP.
- "Full functionality, small footprint": a flow is either implemented end-to-end or not
  started. Half-wired stubs presented as done are not acceptable.
- YAGNI is binding. Any capability not required by `SPECIFICATION.md` scope is out of scope;
  scaling work is explicitly deferred.

**Rationale**: The product's sole near-term goal is a public, working MVP delivered at
minimal cost and effort. Premature scale or extra moving parts directly contradict the
delivery mandate and burn the limited budget.

### II. Fixed, Current Technology Stack

The stack is fixed and MUST NOT be substituted without a constitution amendment: Angular 17+
(standalone components) frontend; Java 21 + Spring Boot 3.x backend (single deployable JAR);
MongoDB 7.x; Anthropic Claude Haiku (`claude-haiku-4-5-20251001`) for the LLM, called
server-side only; monobank
"Plata by mono" acquiring behind a swappable `PaymentGateway` abstraction.

- Deprecated, end-of-life, or otherwise outdated dependencies, libraries, or frameworks MUST
  NOT be introduced. New dependencies MUST be on a currently-supported release line.
- The LLM and payment providers MUST sit behind their abstractions
  (`InvestmentAdvisorService`, `PaymentGateway`) so a provider can be swapped without
  touching business logic or the token ledger.

**Rationale**: A pinned, current stack keeps the build reproducible, avoids security debt
from abandoned libraries, and preserves the ability to re-slot providers cheaply.

### III. LLM Guardrails & Cost Control

The LLM is a constrained, server-side tool, never an open-ended chatbot:

- It MUST be invoked only server-side; the Anthropic key MUST NOT reach the client.
- Recommendations MUST be drawn EXCLUSIVELY from the active provider catalog. The server MUST
  enforce this after the model responds by dropping any option whose `providerId` is not in
  the active catalog; if none remain, the output is treated as invalid.
- Hard per-request budgets MUST be enforced: bounded output `max_tokens`, an assembled input
  capped under the configured input-token limit, low temperature for determinism, and a
  per-user search rate limit independent of token balance.
- User free-text MUST be treated as data, not instructions (length-capped, escaped). The
  system prompt MUST refuse to reveal itself or to produce output outside the JSON schema.
- Output MUST be strict-JSON validated; on failure, retry at most once, then fail safe.

**Rationale**: Guardrails bound both legal/abuse risk (no off-catalog or jailbroken advice)
and spend (a hard token budget plus rate limits cap worst-case LLM cost per request).

### IV. Financial Integrity & Token Accounting

Money and tokens are correctness-critical and MUST follow exact rules:

- All monetary amounts MUST be stored and computed as integer minor units (kopiykas). Float
  or floating-point arithmetic for money is PROHIBITED.
- Token balance MUST never go negative. Every balance mutation MUST be a single-document,
  status-guarded conditional update routed through `TokenLedgerService`.
- One search consumes exactly one token, decremented before the LLM call; a failed or invalid
  LLM result MUST refund the token idempotently. Tokens MUST be credited only after a
  signature-verified successful payment callback, idempotent per `orderId`. Reversals and
  chargebacks MUST run at most once per `orderId`, debit at most the remaining balance, and
  never drive the balance negative.
- Token-pack seeds MUST pass the seed-time margin check (net-of-fee pack price must exceed
  `tokens * per-search LLM cost * safety multiple`); seeds that violate it MUST be rejected.
- New users start at 0 tokens; the free grant (default 5) is credited once on first email
  verification and MUST be idempotent.
- Every result set MUST carry the financial disclaimer (information, not individualized
  professional advice); a currency-risk disclaimer MUST be appended when an option's currency
  differs from the user's amount.

**Rationale**: Tokens are the sole revenue path; ledger errors are direct financial loss or
fraud exposure, and integer-only money avoids rounding corruption.

### V. Encoding & Verification Discipline (Windows-safe)

This principle is NON-NEGOTIABLE; a violation has already caused a real outage.

- All scripts executed directly by Windows (`.ps1`, `.cmd`, `.bat`) MUST be pure ASCII. No
  em-dash, en-dash, curly quotes, ellipsis, non-breaking space, or similar non-ASCII
  punctuation anywhere, including comments. If non-ASCII is unavoidable, the file MUST be
  saved as UTF-8 with BOM or UTF-16.
- `.env` files written by scripts MUST be written without a BOM.
- Line endings follow `.gitattributes`: Linux-consumed files (Dockerfile, `*.conf`, `*.yml`,
  `.env`) stay LF; `*.ps1`/`*.cmd`/`*.bat` stay CRLF.
- "Done" requires verification by inspecting bytes or running a parser/compiler, NOT by
  reading rendered text. Before any script or source change is marked complete: scan executed
  scripts for non-ASCII (expect zero matches) AND parse/compile when a runtime is available
  (`pwsh` parser for PowerShell, `mvn test` for Java). If no runtime is available, that MUST
  be stated explicitly and the change labeled static-only - never imply a script "works" when
  it was only read.

**Rationale**: Windows PowerShell 5.1 decodes BOM-less files as Windows-1252, mis-decoding
UTF-8 punctuation into string delimiters that break the whole script. Visual review cannot
catch byte-level encoding bugs; only a scan or parse can.

### VI. Mandatory Multi-Role Sub-Agent Review

Every non-trivial task MUST be reviewed by at least two role sub-agents with relevant roles
(QA, Business Analyst, DevOps, front-end lead, back-end lead, etc.) before it is considered
done.

- This review is mandatory and automatic: it runs as part of completing the task, without
  pausing to ask the user for permission first. It is a required step, not an optional
  follow-up.
- The review MUST include an actual scan/parse/compile (per Principle V) where an
  encoding or parse class of bug is possible. Human or sub-agent reading alone does NOT
  satisfy the verification gate.
- Findings MUST be applied or explicitly reported before the task is closed.

**Rationale**: Independent role perspectives catch product, quality, and operational defects
early and cheaply, which matters most on a lean MVP with no dedicated QA staff.

## Technology, Security & Compliance Constraints

- **Auth**: JWT access (short-lived, <=15 min) + rotating, revocable refresh tokens; BCrypt
  password hashing. Passwords MUST never be logged.
- **Transport & origin**: all endpoints over HTTPS (behind a TLS-terminating proxy in
  production); CORS restricted to the configured app origin.
- **Secrets**: the Anthropic key, monobank merchant token, and JWT secret live in the
  environment/secret store only - never in source, client bundles, or logs. The app MUST fail
  fast at startup if a required secret is missing.
- **Payment webhooks**: monobank callbacks MUST be ECDSA-signature verified against the
  merchant public key over the exact raw request bytes before any state change; forged or
  mismatched signatures mutate nothing. Public-key refresh on failure is rate-limited
  (anti-amplification).
- **Input & injection**: validate all input, reject unknown fields, and apply prompt-injection
  hygiene (Principle III). Per-IP and per-user rate limiting on `/auth/*` and
  `/investments/search`.
- **PII minimization**: store only email + auth data; support account/data deletion on
  request.
- **Fiscal/tax**: the business operates as a Private Entrepreneur (FOP) - no VAT on token
  sales; monobank PRRO fiscalization is an operational launch-checklist prerequisite, not code.

## Development Workflow & Quality Gates

- **Spec-driven**: `SPECIFICATION.md` defines frozen MVP scope; `TASKS.md` carries the
  implementation tickets. Acceptance Criteria in the spec are the definition of done for the
  corresponding flows.
- **Config not hard-coding**: values marked `[CONFIG]` MUST live in typed
  `@ConfigurationProperties` and be overridable via environment without a rebuild.
- **Quality gates before "done"** (all MUST pass):
  1. Non-ASCII scan of any executed Windows script returns clean (Principle V).
  2. Parse/compile or test run when a runtime is available; otherwise state static-only.
  3. At least two role sub-agents have reviewed, including the scan/parse step (Principle VI).
  4. The change satisfies the relevant SPECIFICATION acceptance criteria.
- **Faithful reporting**: failing tests, skipped steps, and unverified work MUST be reported
  plainly; never imply verification that did not occur.

## Governance

This constitution supersedes other development practices for InvestGuideUA. Where another
document conflicts with it, this constitution wins until amended.

- **Amendments**: proposed as a change to this file with rationale, reviewed under Principle VI
  (at least two role sub-agents), and version-bumped per the policy below. Amendments that
  change the fixed stack, the token-accounting rules, or the security model MUST document a
  migration impact.
- **Versioning policy** (semantic):
  - MAJOR: backward-incompatible governance or principle removal/redefinition.
  - MINOR: a new principle/section or materially expanded guidance.
  - PATCH: clarifications, wording, or non-semantic refinements.
- **Compliance review**: every task and PR MUST verify compliance with these principles as
  part of the mandatory sub-agent review; complexity that deviates from Principle I MUST be
  justified in the plan's Complexity Tracking table or it is rejected.
- **Runtime guidance**: `CLAUDE.md` holds the day-to-day engineering conventions (encoding,
  line endings, verification commands) and MUST stay consistent with this constitution.

**Version**: 1.0.0 | **Ratified**: 2026-06-03 | **Last Amended**: 2026-06-03
