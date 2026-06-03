# Contract: Verification Email Message

The message produced in SMTP mode. Content is illustrative (final UA copy set in
`VerificationEmailContent`); the **structural** guarantees are the contract.

## Link contract (binding)

The verification link MUST be byte-for-byte the link the existing `/verify` flow accepts:

```
<app.frontend-base-url>/verify?token=<rawToken>
```

- Built with `UriComponentsBuilder` + UTF-8 encoding, exactly as `LoggingVerificationNotifier`
  builds it today (so opening it hits the SPA `/verify` route, which calls
  `POST /api/v1/auth/verify` with the raw token).
- `rawToken` is the one-time secret whose SHA-256 hash was persisted synchronously at registration.
- Opening the link before `app.verification.token-ttl-ms` elapses verifies the account and grants
  the free tokens (existing behavior, unchanged).

## Message structure (binding)

| Property | Requirement |
|----------|-------------|
| From | `mail.from` |
| To | the registrant's email |
| Subject | Non-empty, UA, identifies product + purpose |
| MIME | `multipart/alternative` with BOTH `text/plain` and `text/html` parts, UTF-8 |
| Body must contain | product identity ("InvestGuideUA"), the purpose, the link as a prominent CTA, an explicit expiry note (FR-009) |
| Body must NOT contain | the user's password; any SMTP credential |
| External assets | none (no remote images/CSS) — deliverability + Constitution I |

## Illustrative content (UA)

```
Subject: Pidtverdte svoyu adresu - InvestGuideUA

(text/plain)
Vitayemo v InvestGuideUA!
Shchob aktyvuvaty akaunt, pidtverdte svoyu elektronnu adresu za posylannyam:
<link>
Posylannya diye 24 hodyny. Yakshcho vy ne reyestruvalysya - proignoruyte tsey lyst.

(text/html)
<h1>Vitayemo v InvestGuideUA!</h1>
<p>Shchob aktyvuvaty akaunt, natysnit knopku nyzhche:</p>
<p><a href="<link>">Pidtverdyty adresu</a></p>
<p>Posylannya diye 24 hodyny.</p>
```

> Note: the snippet above is documentation only. The real strings live in a `.java` file
> (Maven-pinned UTF-8) and may contain Ukrainian Cyrillic. They MUST NOT be pasted into any
> Windows-executed script (`.ps1/.cmd/.bat`) per Constitution V.

## Failure contract

On a send failure (SMTP unreachable, auth rejected, timeout), the dispatcher logs an ERROR with:
- the recipient address,
- the failure class/short reason,
- **never** the raw token, the password, or any SMTP credential,

and the registration outcome is unchanged (already returned). No exception propagates to the
caller thread (FR-006/FR-007).
