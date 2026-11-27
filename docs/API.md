# API guide

Base path: `/api/v1`. Requests and normal responses use JSON. Successful responses are DTOs directly. Error responses use `application/problem+json` with stable `code` and safe human-readable `detail`. A request ID supports correlation without exposing private data. See [API_CONTRACT.md](API_CONTRACT.md) for the detailed implementation contract.

## Session and CSRF flow

1. `GET /auth/csrf` returns `{ "token": "...", "headerName": "X-XSRF-TOKEN" }` and any required cookie.
2. Send this header with unsafe browser requests. Include cookies. The frontend client performs these steps.
3. Register or log in. The response contains a user DTO, and the JWT travels only in an HttpOnly cookie.
4. After authentication, obtain a current CSRF token before an unsafe action. Logout revokes the session and clears the cookie. Password changes invalidate sessions as well.

Example registration body:

```json
{
  "fullName": "Asha Rao",
  "phone": "9876543210",
  "password": "a-unique-long-password"
}
```

This is an example input, not a seeded account. There is no password bypass or shared demo login.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/auth/csrf` | CSRF token for browser requests |
| POST | `/auth/register` | Create a user and zero-balance wallet |
| POST | `/auth/login` | Start a short-lived session |
| GET | `/auth/me` | Resolve the active session |
| POST | `/auth/logout` | Revoke the active session |
| GET | `/wallet` | Balance in minor units and currency |
| POST | `/transfers` | Transfer sandbox funds to another wallet |
| POST | `/deposits` | Create a pending sandbox deposit intent |
| GET | `/demo-funds` | Authenticated account's one-time demo-grant availability |
| POST | `/demo-funds/welcome` | Claim the fixed simulated ₹100 once; no request body |
| GET | `/transactions` | Filtered, paginated history |
| GET | `/transactions/export` | Bounded CSV export of matching history |
| GET | `/transactions/{id}/receipt` | Receipt visible only to a participant |
| GET | `/analytics?days=30` | Completed-activity summary and daily series |
| GET / PUT | `/profile` | Read or update the current profile |
| PUT | `/profile/password` | Change password and invalidate sessions |
| POST | `/webhooks/sandbox` | Authenticated simulator callback; disabled by default |

`/actuator/health` is a minimal health response. The deployed gateway does not expose the Actuator surface publicly.

## Transfer requests and retries

```http
POST /api/v1/transfers
Content-Type: application/json
Idempotency-Key: 55c9a92c-7b6b-4fb8-ac80-e17fc6bbd55d
X-XSRF-TOKEN: <current token>

{
  "recipientPhone": "9876543211",
  "amountMinor": 12550,
  "note": "Shared lunch"
}
```

`12550` means ₹125.50. A timeout does not prove a payment failed. Retry the same payload with the **same** idempotency key to discover the original result. Reusing that key with a different amount or recipient returns a conflict. Do not generate a new key automatically after a network timeout.

Recipients must already have a WalletPay account. Transfer lookup first uses the exact registered phone number. When no exact account exists, an Indian mobile number can be matched across its 10-digit, `+91` and `91` forms only if exactly one alternative account exists. Ambiguous alternatives require the exact registered form. Other number formats must match registration exactly. Existing phone identities and login rules are unchanged. Missing recipients return `RECIPIENT_NOT_FOUND` with guidance to check registration and the country code. Self-transfers are rejected even when the number is entered in another supported form; failed requests do not debit the balance.

## One-time demo funds

When `DEMO_FUNDING_ENABLED=true`, both existing and newly registered accounts can manually claim **one ₹100.00 grant**. Registration itself adds no money. `GET /demo-funds` returns:

```json
{
  "enabled": true,
  "welcomeAvailable": true,
  "welcomeCreditMinor": 10000
}
```

The authenticated claim is `POST /demo-funds/welcome`, with the current CSRF header, cookies and a UUID `Idempotency-Key`, and **no request body**. The server chooses the fixed amount, records the persistent claim flag and a completed `DEMO_CREDIT` transaction, and updates that account's balance atomically. It accepts no caller-selected account or amount. Retrying an uncertain request uses the same key. A new key, a new session or a restart cannot provide a second grant for that account.

The feature is disabled by default in deployment configuration; the native development helper enables it explicitly. This grant is simulated wallet credit, not a confirmed payment or a bank deposit. Existing `PENDING` deposit intents remain separate and unchanged. There is no normal user-facing arbitrary or repeat free top-up endpoint.

## History and export

```text
/transactions?page=0&size=20&type=TRANSFER&direction=DEBIT&status=COMPLETED
/transactions?search=lunch&from=2026-11-01&to=2026-11-30
/transactions/export?type=TRANSFER&direction=DEBIT
```

Filters are optional. `ALL` means no restriction. Dates are inclusive ISO dates, interpreted by the backend in UTC. Search is bounded, pages have a maximum size of 100, and exports are capped at 10,000 rows. Rows are newest first. The CSV uses safe cell escaping, including protection against spreadsheet formula interpretation for user-controlled text.

Transaction types include `TRANSFER`, `DEPOSIT` and `DEMO_CREDIT`. A completed demo grant appears as credit activity in history, receipts, CSV export and the corresponding analytics period.

Paginated response:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

## Sandbox callback authentication

The exact raw JSON body is signed. The signature is the lowercase hexadecimal HMAC-SHA256 of:

```text
timestamp + "." + exactRawRequestBody
```

Headers are `X-Webhook-Timestamp` (Unix seconds) and `X-Webhook-Signature`. The server accepts only a five-minute freshness window. Body:

```json
{
  "eventId": "6402682f-3039-4567-8e37-a8df69af07fd",
  "depositId": "6bbef9d2-aa99-4217-986b-c2fcf07221de",
  "amountMinor": 12550,
  "currency": "INR",
  "status": "COMPLETED"
}
```

A valid signature is necessary but not sufficient: the intent, amount and currency must also match. No endpoint should accept an arbitrary user ID and credit an unassociated wallet. The simulator secret is server-side only and must never be shipped to the frontend.

## Error handling

| Status | Meaning |
| --- | --- |
| 400 | Invalid shape, field values, filters or amount |
| 401 | Invalid credentials, expired session or missing authentication |
| 403 | Missing/invalid CSRF token or forbidden operation |
| 404 | Resource unavailable to the current user, or disabled feature |
| 409 | Duplicate identity, incompatible idempotency retry, or conflicting state |
| 429 | Request throttled; respect any `Retry-After` header |
| 500 | Unexpected server failure; use request ID for investigation |

The precise domain error code and HTTP mapping are implemented by the backend exception handler. Consumers should use `code` for program behavior and `detail` for the user-facing message rather than parsing exception text.
