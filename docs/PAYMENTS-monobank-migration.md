# Roadmap: migrate payments from LiqPay → monobank "Plata by mono"

**Status:** proposed (provider not yet final — "most probably mono"). This is the adjusted roadmap, reviewed by Architect / Security / Backend-QA sub-agents (see end).

**Goal:** swap the payment provider with **zero change to the money-critical token ledger** (`TokenLedgerService`, spec §7). The spec already defines a `PaymentGateway` abstraction (§9.4) precisely for this; the ledger, the `Payment` snapshot model, and the idempotent status-guarded crediting/reversal are provider-agnostic and stay as-is.

---

## 1. Why this is mostly a "gateway swap", and what genuinely changes

The existing design isolates the provider behind `PaymentGateway` + `PaymentService`. The orchestration (create pending → redirect → verified callback → idempotent credit/reverse) is unchanged in shape. What changes is **how a checkout is created**, **how a callback is authenticated**, and **the wire contract** — because mono is fundamentally different from LiqPay:

| Aspect | LiqPay (current) | Plata by mono (target) | Impact |
|---|---|---|---|
| Auth to gateway | `public_key` + `private_key` | single secret **`X-Token`** header | config/secrets |
| Create checkout | build base64 `data` + HMAC-ish `signature`, client POSTs a form to `liqpay.ua/api/3/checkout` | server `POST https://api.monobank.ua/api/merchant/invoice/create` → returns `{invoiceId, pageUrl}`; client **redirects** to `pageUrl` | gateway + DTOs + frontend |
| Callback auth | **symmetric**: `base64(sha1(privKey+data+privKey))`, recomputed & compared | **asymmetric ECDSA**: `x-sign` header = `SHA256withECDSA` over the **raw JSON body**, verified with mono's **public key** from `GET /api/merchant/pubkey` | gateway verify, controller raw-body, key cache |
| Order identity | our `order_id` is the gateway key | mono owns `invoiceId`; our id travels in `merchantPaymInfo.reference` | `Payment` gains `providerInvoiceId` |
| Callback body | form-urlencoded `data`+`signature` | `application/json`, body == "invoice status" object; **no delivery-order guarantee**, use `modifiedDate`; **`expired` is NOT delivered** (must poll) | controller + status logic |
| Statuses | `success` / `reversed` / `failure` (+`sandbox`) | `created`/`processing`/`hold`/`success`/`failure`/`reversed`/`expired` | status mapping |
| Acquiring fee | 2.75% | **1.3%** UA cards / 2% foreign (actual fee returned in `paymentInfo.fee`) | pricing config (margins improve) |
| Fiscal receipts | LiqPay ПРРО via `rro_info.delivery_emails` | monobank built-in ПРРО (bind in web cabinet; `merchantPaymInfo` basket required when active; fiscal-checks endpoint) | §9.5 note |

**Net effect on the spec's correctness guarantees:** none weaken. Crediting is still gated on a verified callback + amount/currency match + status guard; replays are still idempotent per order. The fee drop *improves* the §9.1 unit economics (validation still passes with more headroom).

---

## 2. Backend change set (Java)

### 2.1 Generalize `PaymentGateway` (breaking, but the intended extension point)
The current interface leaks LiqPay's shape (`createCheckout` returns `data`+`signature`; `verifyCallback(data, signature)`; `parseResult(data)`). Make it provider-neutral:

```java
public interface PaymentGateway {
    String slug();                                            // "monobank"
    CheckoutData createCheckout(Payment payment, String deliveryEmail);
    boolean verifyCallback(byte[] rawBody, String signatureHeader);   // ECDSA over raw bytes
    CallbackResult parseResult(byte[] rawBody);
}
```

- **`CheckoutData`** → `{ String pageUrl, String providerInvoiceId }` (drop `data`/`signature`/`checkoutUrl`).
- **`CallbackResult`** → add `String providerInvoiceId` and `Instant modifiedDate`; `orderId` now sourced from `reference`. Keep `status`, `amountMinorUnits`, `currency`.
- Verify takes **raw bytes** (not a decoded string) because ECDSA is over the exact transmitted body.
- **Also rewrite `CreatePaymentResponse.from(payment, checkout)`** — it currently reads `checkout.data()/signature()/checkoutUrl()`; those getters disappear, so the mapper body must change to `{ paymentId, orderId, providerInvoiceId, pageUrl }`. (Compile-break if missed.)

> LiqPay can remain as a second `PaymentGateway` impl if you want a fallback, but its `createCheckout` no longer fits a single return shape cleanly. **Recommendation:** retire `LiqPayGateway` (and `payment.liqpay.*` config) to keep one honest implementation for the MVP; the abstraction stays, so re-adding a provider later is still a localized change.

### 2.2 New `MonoAcquiringGateway implements PaymentGateway`
- Use the JDK 21 `HttpClient` (same approach as `AnthropicInvestmentAdvisorService` — no new dependency).
- `createCheckout`: POST `/api/merchant/invoice/create` with header `X-Token`, body `{ amount (kopiykas), ccy: 980, merchantPaymInfo:{ reference: orderId, destination:"InvestGuideUA tokens: <packId>" }, redirectUrl: resultUrl, webHookUrl: callbackUrl, validity: <e.g. 3600> }`. Parse `{invoiceId, pageUrl}`.
- **Currency mapping:** store/compare `"UAH"` app-side; map to/from ISO-4217 numeric `980` at the gateway boundary (helper `ccyToNumeric`/`numericToCcy`). **`parseResult` must map `980 → "UAH"` before constructing `CallbackResult`**, so the reused amount/currency snapshot check (`PaymentService.creditOrFail`, `equalsIgnoreCase`) keeps passing — otherwise every success mismatches.
- `verifyCallback`: load mono public key (PEM from `GET /api/merchant/pubkey`, parsed via `KeyFactory.getInstance("EC")` + `X509EncodedKeySpec` — the PEM is an X.509 `SubjectPublicKeyInfo`), `Signature.getInstance("SHA256withECDSA")`, `verify(Base64.decode(xSign))`. **Pure JCA, no BouncyCastle** (SunEC handles `secp256r1`).
  - **Signature encoding (critical):** JCA `SHA256withECDSA` expects the signature in **ASN.1 DER** (`SEQUENCE{r,s}`), which is exactly what mono sends — feed the base64-decoded `x-sign` bytes straight to `verify()`. **Do NOT** convert to a raw `r||s` concatenation (a common cross-language mistake that throws `SignatureException` on valid callbacks). Add a doc-vector test asserting DER, not raw.
  - **Key cache + bounded refresh:** cache the key (`pubkeyCacheTtlMs`). On a verify failure, refresh **at most once per cooldown window** (e.g. token-bucket / min-interval) — NOT once per failed call. Otherwise a flood of forged callbacks with bad signatures each trigger an outbound `GET /pubkey`, amplifying into a DoS / mono rate-limit against `api.monobank.ua` that breaks real callbacks.
  - **TLS trust:** fetch the pubkey only over HTTPS to `api.monobank.ua` with default JDK cert validation; forbid `trustAll` and any `apiBaseUrl` override that downgrades the scheme. (Cert pinning optional for MVP.)
- `parseResult`: parse the JSON body (the "invoice status" object) → `reference`, `invoiceId`, `status`, `amount`, `ccy`, `modifiedDate`.

### 2.3 `Payment` entity + indexes (data-model care needed)
- Add a `providerInvoiceId` field (mono `invoiceId`) so webhooks can be matched by it as well as by `reference`/`orderId`. `orderId` stays the idempotency key the ledger guards on. `gateway = "monobank"`.
- **Index must be PARTIAL/SPARSE, not a plain unique index.** `PaymentService.create` inserts the `Payment` **before** `createCheckout` returns the `invoiceId`, so every row is briefly `providerInvoiceId = null` (and any retired LiqPay rows are permanently null). A plain `@Indexed(unique=true)` treats null as a value → the second null collides with `DuplicateKeyException`. Declare it in `PaymentIndexConfig` as a **partial unique index** (`partialFilterExpression: { providerInvoiceId: { $exists: true } }`) — mirror the existing `orderId` `ensureIndex` pattern there; do **not** rely on the annotation alone.
- **Patch `providerInvoiceId` after invoice creation:** in `create()`, set it on the saved payment once `createCheckout` returns. (The `DuplicateKeyException` reuse leg also calls `createCheckout` — for mono that is now a live HTTP POST; see §2.8.)
- Add `PaymentRepository.findByProviderInvoiceId(...)` for the webhook fallback match.

### 2.4 `PaymentService.handleCallback(byte[] rawBody, String xSign)`
- Verify signature first (reject → `PAYMENT_ERROR`, mutate nothing — preserves AC #7).
- Parse, persist raw payload for audit, match payment by `reference` (fallback `providerInvoiceId`).
- **Act only on terminal statuses:** `success` (+ amount/ccy match) → `tokenLedger.creditFromPayment` (idempotent, AC #6); `reversed` → `tokenLedger.reversePayment`; `failure` → `markFailed` (status-guarded). **Ignore** `created`/`processing`/`hold` (acknowledge 200, no state change).
- **Out-of-order safety:** the existing status guards are the load-bearing protection — crediting guards on `status:"pending"` and `markFailed` guards on `status:"pending"`, so a late `processing`/`failure` after a `success` already cannot regress it. The `modifiedDate` low-water-mark is **defensive only**; if added, the compare-and-store must itself be a **guarded single-doc update** (`{orderId, modifiedDate < incoming}`), never a read-modify-write — otherwise it reintroduces the race the ledger deliberately avoids.
- **No new persisted status value.** Stored status stays `pending|success|failed|reversed` (mono's `created`/`processing`/`hold` → ignore; `expired` → handled by reconciliation, §2.5; `failure` → `failed`). This preserves the `PaymentStatus` "persisted value never drifts from the ledger's literal guards" invariant.
- **`reversed` while still `pending`:** `reversePayment` guards on `status:"success"`, so a reversal for an uncredited payment is a safe no-op (nothing was credited). State this explicitly.
- Update `SUCCESS_STATUSES = {"success"}` (drop `sandbox`); `REVERSAL_STATUSES = {"reversed"}` (mono has no `refund`/`chargeback` strings).

### 2.5 `expired` reconciliation (no webhook for it)
mono does **not** send a webhook for `expired`. Add a lightweight reconciliation: when `GET /payments/{id}` is polled and the payment is still `pending` past its validity, call mono `GET /api/merchant/invoice/status` (authenticated with `X-Token`) and reconcile (mark `failed` on `expired`/`failure`). Keep it **best-effort and non-blocking**: the reconciliation call must not throw into the owner-scoped 404/read path — on a network error, just return the current cached status. Optional: a scheduled sweep of stale pendings. MVP-minimal: reconcile on read.

### 2.6 Controller
- Replace `POST /payments/liqpay/callback` (form-urlencoded) with **`POST /payments/mono/callback`** consuming `application/json`, reading the **raw body** as `byte[]` (`@RequestBody byte[] body`) and the header `@RequestHeader("x-sign") String xSign`. Returning 200 acknowledges; non-200 triggers mono's up-to-3 retries.
- `CreatePaymentResponse` → `{ paymentId, orderId, providerInvoiceId, pageUrl }` (drop `data`/`signature`/`checkoutUrl`).

### 2.7 Config / secrets / pricing
- `PaymentProperties`: replace the `LiqPay` record with `Mono { String token; String apiBaseUrl = "https://api.monobank.ua"; long pubkeyCacheTtlMs; ... }` (token NOT `@NotBlank` — see below). Keep `resultUrl`, `callbackUrl`.
- `application.yml`: `payment.mono.token=${MONO_TOKEN}`, `api-base-url`, drop `payment.liqpay.*`; `payment.callback-url` path → `/api/v1/payments/mono/callback`; `app.pricing.gateway-percent-fee: 0.0275 → 0.013`.
- `.env` / `.env.example`: remove `LIQPAY_PUBLIC_KEY`/`LIQPAY_PRIVATE_KEY`; add `MONO_TOKEN` (secret). Update `PAYMENT_CALLBACK_URL`.
- `PricingValidator` / `TokenPackSeeder`: no price change (packs stay 99/169/379 UAH); re-run the seed-time check with 1.3% — it passes with more margin. Keep `safety-multiple=10`.
- `docker-compose.yml`: thread `MONO_TOKEN` through the backend service env (the LiqPay keys were supplied via `.env` `env_file`; ensure the new secret is plumbed the same way and the old ones removed).
- `PaymentProperties.Mono` keeps `@Validated`, but the `token` is **intentionally NOT `@NotBlank`** and `application.yml` uses an empty default (`${MONO_TOKEN:}`). The app must boot without a merchant token (a working deployed app is shown to the bank to obtain the production token); until set, payment creation returns 502 while the rest of the app runs. (Unlike the LiqPay keys, which were fail-fast.)

### 2.8 Error & retry semantics (distinguish fault types)
- **Create-invoice HTTP failure** (mono down / non-2xx): leave the payment **`pending`** (do **not** `markFailed`), surface **502** to the user so they can retry; reconciliation/poll resolves it later. **No token is spent** — payments never touch the ledger on create (only a verified `success` callback credits), so a failed create costs nothing. Note: `ErrorCode.PAYMENT_ERROR` currently maps to **400** and there is no 502 path today — add a gateway-fault mapping (502) for transient provider errors, keeping 400 for client/validation errors.
- **Callback signature mismatch / malformed body:** reject, mutate nothing, return the rejection (no retry wanted) — preserves AC #7.
- **Callback transient fault** (e.g. pubkey fetch fails at verify time): return **non-200 (5xx)** so mono **retries** (up to 3×), rather than swallowing as a permanent reject. Distinguish "can't verify right now" (retryable) from "signature is bad" (reject).

---

## 3. Frontend change set (Angular)
- `payment.models.ts`: `CreatePaymentResponse` → `{ paymentId, orderId, providerInvoiceId, pageUrl }`.
- `tokens.component.ts`: replace the hidden-form `submitCheckout(data, signature, checkoutUrl)` with a single top-level redirect `window.location.href = res.pageUrl`; delete the `hidden()` form helper. Still stash `paymentId` in `sessionStorage` for the result page.
- `payment-result.component.ts`: unchanged — still polls `GET /payments/{id}` after mono redirects back to `redirectUrl` (`/payments/result`).
- Update LiqPay-referencing comments.

---

## 4. Tests
- **Replace** `LiqPayGatewayTest` with `MonoAcquiringGatewayTest`: prefer an **in-test generated EC keypair** (sign a known body with the private key, verify with the public) over the official doc vector — the doc vector pins to mono's real *rotating* key and is brittle. Assert: valid signature verifies; one-byte body mutation rejected; wrong key rejected; **DER-encoded** sig accepted while a raw `r||s` blob is rejected; pubkey cache hit; refresh-on-failure-then-success (and that refresh is rate-bounded).
- **`AcceptanceCriteriaTest` AC #10 is a compile-break the roadmap must own:** AC #10 currently instantiates the **real `LiqPayGateway`** (building `PaymentProperties.LiqPay(...)`) and asserts the private key is absent from `data`/`signature`. Retiring `LiqPayGateway`/`PaymentProperties.LiqPay` breaks it. Rewrite AC #10 to mint a mono invoice and assert the `X-Token` / merchant secret never appears in `CheckoutData` (`{pageUrl, providerInvoiceId}`) or `CreatePaymentResponse` — the secret-leakage guarantee (AC #10 / spec §13.10) is preserved, the mechanics change.
- **`FakePaymentGateway`** (used by AC #6/#7 and fixtures) must move to the new interface: `verifyCallback(byte[], String)`, `parseResult(byte[])`, `createCheckout → {pageUrl, providerInvoiceId}`, and the widened `CallbackResult`.
- **`AcceptanceCriteriaTest`** AC #6 (credit only on verified success; replay no-op) and AC #7 (forged `x-sign` rejected, credits nothing) — call sites change from `handleCallback("data","sig")` to `handleCallback(byte[], xSign)`; AC #7's `verify(...never()...findByOrderId)` becomes the new finder. **Behavioral** assertions stay 1:1 with spec §13.
- **`PaymentServiceTest`**: new callback signature `(rawBody, xSign)`; matching by `reference` (fallback `providerInvoiceId`); terminal-only status handling; out-of-order `modifiedDate` ignored; amount/ccy (980↔UAH) mismatch → `failed`; create-invoice HTTP failure leaves `pending` + 502 + no token spent; pubkey-fetch failure at callback → retryable 5xx.
- New: `expired`-via-status reconciliation test.

---

## 5. Spec & docs to update (`SPECIFICATION.md`, `DEPLOY-cloud.md`, `TASKS.md`)
- **§2** stack table: payments → "monobank Plata by mono acquiring (primary); `PaymentGateway` abstraction keeps it swappable".
- **§5.1**: callback path `/payments/liqpay/callback` → `/payments/mono/callback`.
- **§9** rewrite: 9.2 invoice-create + redirect flow; 9.3 ECDSA `x-sign` verification + pubkey fetch/rotation + raw-body rule + out-of-order/`modifiedDate` + no-`expired`-webhook; 9.4 unchanged (abstraction); 9.5 monobank ПРРО; 9.1 fee `0.0275 → 0.013`.
- **§14**: replace LiqPay tariff (2.75%) with mono (1.3% UA / 2% foreign); VAT/ФОП note unchanged; receipts via monobank ПРРО.
- **`DEPLOY-cloud.md` Phase 6**: get `X-Token` from `web.monobank.ua`; set `redirectUrl`/`webHookUrl` (or pass per-invoice); bind ПРРО; secrets `MONO_TOKEN` instead of `LIQPAY_*`; `PAYMENT_CALLBACK_URL=https://api.investguideua.com/api/v1/payments/mono/callback`. Smoke test step 5 updated. Backend now makes outbound calls to `api.monobank.ua` (egress is open on Fly — fine; grey-clouded API still receives the inbound webhook).
- **`TASKS.md`**: re-scope epic BE-P (P1/P3/P4) to mono; add tickets for pubkey cache/rotation and `expired` reconciliation.

---

## 6. Risks & decisions
1. **Breaking interface/DTO change** ripples to `PaymentService`, controller, both DTOs, frontend, and `FakePaymentGateway`/tests. Medium effort; contained to the `payments` package + 2 FE files.
2. **Raw-body integrity:** signature is over exact bytes — read `byte[]`, never a re-serialized object, and ensure no proxy rewrites the body (Cloudflare is grey-clouded on the API → no transformation; Fly passes through).
3. **Public-key rotation:** must cache and refresh-on-failure, or a key rotation silently breaks all callbacks.
4. **Out-of-order / missing `expired` webhooks:** handled by `modifiedDate` guard + status reconciliation; do not assume strict ordering.
5. **`destination` language / ПРРО basket:** if ПРРО is bound, `merchantPaymInfo` must carry basket items; confirm before enabling fiscalization.
6. **Pubkey-refresh amplification:** an unbounded refresh-on-failure lets forged callbacks drive outbound `GET /pubkey` floods — bound the refresh rate (§2.2).
7. **Provider still tentative** ("most probably"): this roadmap is reversible — keeping the `PaymentGateway` abstraction means LiqPay or Fondy can be re-slotted without touching the ledger.

**Effort estimate (sub-agent consensus): ~7.5 person-days** — gateway+DTOs+service ~2.0d; `MonoAcquiringGateway`+pubkey cache/rotation ~1.5d; controller raw-body + `Payment`/index ~0.5d; expired reconcile ~0.5d; config/secrets ~0.5d; frontend ~0.5d; tests incl. AC#10 rewrite ~1.5d; docs ~0.5d. Plus the mandatory closing `mvn test` + role sub-agent (QA/DevOps/BE) review.

## 7. Suggested execution order
1. Generalize `PaymentGateway` + DTOs (compile-break, fix call sites).  2. Implement `MonoAcquiringGateway` (+ pubkey cache).  3. `Payment.providerInvoiceId` + controller raw-body callback.  4. `PaymentService` status routing + `modifiedDate`.  5. Config/secrets/pricing.  6. Frontend redirect.  7. Tests (gateway, service, AC6/AC7, expired).  8. Update spec + deploy runbook + TASKS.  9. Mandatory sub-agent review of the implementation (QA + DevOps + BE lead) with an actual `mvn test` run before done.
