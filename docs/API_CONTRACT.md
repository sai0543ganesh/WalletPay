# Implementation contract

This sandbox uses Java 21 / Spring Boot / PostgreSQL and React / Vite. No real payment provider. All monetary amounts are integer INR minor units (paise). The original project is read-only at `../outputs/walletpay_original_source`.

API base: `/api/v1`. Success responses are the DTO directly; errors use RFC 9457 ProblemDetail with `status`, `title`, `detail`, stable `code`, optional `fieldErrors`, and request ID. Never expose stack traces, SQL, passwords, JWTs, or secrets.

## Authentication
- GET `/auth/csrf`: `{token,headerName}`. Obtain before any unsafe cookie-authenticated request. Client sends returned header (X-XSRF-TOKEN).
- POST `/auth/register`: `{fullName,phone,password}` -> 201 user `{id,fullName,phone}` and HttpOnly JWT cookie.
- POST `/auth/login`: `{phone,password}` -> 200 user and HttpOnly JWT cookie.
- GET `/auth/me`: user or 401.
- POST `/auth/logout`: 204, revoke session and clear cookie.
- JWT stays in HttpOnly cookie, never localStorage. CSRF protection and explicit allowed CORS origins. Production requires secure cookies and configured secrets.
- User IDs are Long. Transaction identifiers are UUID strings.

## Wallet and transactions
- GET `/wallet`: `{balanceMinor,currency:"INR",updatedAt}`.
- POST `/transfers`: `{recipientPhone,amountMinor,note}` plus required UUID `Idempotency-Key` -> 201 transaction. Same key / same payload returns same transaction; changed payload returns 409.
- POST `/deposits`: `{amountMinor,provider:"SANDBOX"}` plus UUID Idempotency-Key -> 201 pending transaction. This creates a sandbox deposit intent; it never credits funds directly.
- GET `/demo-funds` (authenticated): `{enabled,welcomeAvailable,welcomeCreditMinor:10000}`. Availability is scoped to the current account. This does not change its balance.
- POST `/demo-funds/welcome`: no body, required UUID `Idempotency-Key`, valid session and CSRF header -> completed `DEMO_CREDIT` transaction. Exactly one manually claimed ₹100 grant per account when `DEMO_FUNDING_ENABLED=true`; existing and new accounts are eligible until they claim. No automatic registration credit and no arbitrary-amount or repeat free top-ups.
- Transaction DTO: `{id,reference,type:"TRANSFER"|"DEPOSIT"|"DEMO_CREDIT",direction:"DEBIT"|"CREDIT",status:"COMPLETED"|"PENDING",amountMinor,currency:"INR",note,counterpartyName,counterpartyPhone,createdAt}`. Nullable counterparty fields for deposit/demo credit. reference is human readable unique identifier. Transaction filtering accepts `DEMO_CREDIT` as well.
- GET `/transactions`: filters page=0, size=20 (max 100), type/direction/status (omit or ALL for all), search (max 80), from/to inclusive ISO local dates. Sort newest first. Response `{items,page,size,totalItems,totalPages}`.
- GET `/transactions/export`: same filters except pagination, CSV, bounded export (max 10000), formula-safe cells.
- GET `/transactions/{id}/receipt`: authorized transaction DTO (no other user's receipt).
- GET `/analytics?days=30`: `{periodDays,incomeMinor,spendingMinor,transferCount,daily:[{date,incomeMinor,spendingMinor}]}`. Only completed entries count.
- GET `/profile`: user DTO.
- PUT `/profile`: `{fullName}` -> user DTO.
- PUT `/profile/password`: `{currentPassword,newPassword}` -> 204, invalidate sessions and clear cookie.

## Signed sandbox webhook
POST `/webhooks/sandbox` raw JSON `{eventId,depositId,amountMinor,currency:"INR",status:"COMPLETED"}` with X-Webhook-Timestamp (epoch seconds), X-Webhook-Signature = hex HMAC-SHA256(secret, timestamp + "." + exact raw body). Enforce five-minute time window, constant-time signature verification, persistent unique event ID, locked deposit and wallet. Disabled unless explicitly configured. This endpoint is exempt from browser CSRF but requires its own signature. Return `{received:true}` after commit, including safe replay.

## Concurrency and schema
Use Flyway, Hibernate validate, FK/check/unique/index constraints. Monetary values use long with checked arithmetic, nonnegative balances and bounded positive amounts. Transfers lock both wallets in a consistent user-ID order, then recheck sender-scoped idempotency. Each successful balance movement has immutable transaction/audit evidence. Financial sandbox, not a claim of a regulated double-entry ledger.

The V3 demo-funding migration is additive. It stores persistent per-wallet welcome-claim state, initially unclaimed, and supports the `DEMO_CREDIT` transaction type. Existing pending deposit intents remain pending and are not converted into demo grants. The schema and transaction behavior are forward-only changes; do not roll back the application without reviewing compatibility with `DEMO_CREDIT` rows.

## Local verification
Windows Java: C:/Program Files/Java/jdk-21/bin/java.exe. Maven: C:/Users/chath/Downloads/apache-maven-3.9.16-bin/apache-maven-3.9.16/bin/mvn.cmd. PostgreSQL 18 is installed at C:/Program Files/PostgreSQL/18/bin and may be used for a new disposable cluster. Never touch existing databases. Execute shell tools with cmd.exe, login:false; no PowerShell. Root agent owns runtime/integration/GitHub verification. Agent file ownership is coordinated separately.
