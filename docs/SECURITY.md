# Security model and boundaries

## Threat model

The application assumes untrusted browsers, malformed API requests, repeated requests after timeouts, concurrent transfers and replayed callbacks. The database, deployment host and secret store are trusted administrative boundaries. Compromise of the host or database administrator is outside what application-level JWTs and row locks can prevent.

This document describes controls implemented by the application and deployment configuration. [VERIFICATION.md](VERIFICATION.md) identifies which controls have actually been exercised by tests. A configured control is not a substitute for its test evidence.

## Authentication and browser sessions

- Passwords are BCrypt-hashed; plaintext passwords and tokens must not be logged.
- JWTs are short-lived, validated by signature and expiration, and backed by a revocable server-side session.
- Session cookies are HttpOnly. Production enables Secure cookies and requires HTTPS origins.
- Unsafe browser requests require a CSRF token. CORS permits only explicitly configured origins, with credentials; it does not allow a wildcard origin.
- Logout revokes the session. Password changes invalidate sessions. The interface must clear its user state after either operation.
- Authentication throttling/brute-force protection limits cheap repeated attacks. If its limiter is process-local, multiple API instances need a shared limiter or an upstream enforcement layer before scaling.
- CSRF protection does not prevent XSS. The deployed UI adds a content security policy, restricts framing and avoids storing JWTs in JavaScript-accessible persistent storage.

## Money movement and integrity

Every money-changing operation uses authenticated actor identity, positive bounded integer minor units, database transactions and database constraints. Transfers lock wallets in a consistent order. Insufficient balances, overflow and self-transfers are rejected. The transaction and affected balances commit or roll back together.

The optional `DEMO_FUNDING_ENABLED` feature permits an authenticated account to claim a single fixed simulated ₹100 credit. The browser supplies no amount or target account. The persistent claim state, wallet lock, idempotency key, transaction and audit record protect against concurrent or repeated claims. The limit is per account; it is not a verified-person identity control. Registration remains zero-balance until the user claims or receives a legitimate sandbox transfer. This feature is off by default in deployment configuration and explicitly enabled by the native local helper.

An idempotency key prevents a retry from creating a second successful operation. The server also verifies the request fingerprint so the same key cannot silently change the amount or recipient. Callback authentication is separate from browser authentication; HMAC checks alone do not replace intent ownership and amount checks.

## Callback protection

The callback simulator is **off by default**. When enabled, it requires a separate secret, a valid HMAC over timestamp plus the exact raw body, a five-minute freshness window, and a matching pending intent. Persistent event uniqueness and transactional processing prevent repeated credit. Replays must remain safe even when different workers receive duplicates at the same time.

This is not a real provider integration. A shared-secret simulator is not presented as bank verification. The normal browser funding operation is the separately configured, authenticated, fixed one-time demo grant; it cannot select an arbitrary balance increase or complete a deposit intent. Only a valid signed callback can complete the separate sandbox deposit path.

## Data exposure

Transaction lists, exports and receipts are scoped to the authenticated user. Search and pagination limits reduce resource exhaustion. CSV cells containing user text are escaped against formula injection. API errors expose stable safe descriptions and request IDs, not SQL, stack traces or private entity fields. Audit records should contain operation identity, outcome and timestamp without secrets or full sensitive payloads.

## Secrets and deployment

`.env`, `.secrets/`, private keys, generated logs and backups are ignored by Git. Container builds exclude them as well. Runtime secrets are mounted as files and loaded by the backend entrypoint; container administrators can still inspect process state and must be trusted. For AWS, use a restricted instance role and an appropriate secret store instead of permanent AWS access keys.

Never open ports 5432 or 8080 to the internet. The optional EC2 gateway publishes only 80/443; database traffic stays on an internal Docker network. The database is not automatically TLS-encrypted inside that single-host network. A move to RDS must use hostname-verified TLS (`sslmode=verify-full`) with the correct AWS CA bundle and a private security-group path.

The default database-container account owns its schema for Flyway and is intended for the isolated single-host sandbox. A more serious deployment should split migration ownership from the runtime role and grant the latter only the required table/sequence operations. The supplied stack does not pretend a superuser database role is least-privilege production access.

## Financial boundaries

There is no real-money movement, provider settlement, reconciliation engine, KYC/AML workflow, fraud decisioning or regulatory approval. Atomic wallet balances and transaction rows are not a complete double-entry accounting ledger. A real financial system would require independently reviewed accounting semantics, protected audit retention, reconciliation against provider statements, transaction limits, anomaly controls, recovery exercises and verified legal obligations.

The bank-account area is marked **Under development** and its fields are disabled. It is a display placeholder, not an account-linking integration or a collection form. Do not enter, request or store real bank account numbers, routing information or payment credentials in this sandbox.

## Reporting a problem

Report vulnerabilities privately to the repository owner. Do not publish account data, passwords, tokens or exploitable details in public issues. Rotate any exposed secret immediately and revoke affected sessions. Add a regression test for every fixed security issue before changing the verification record.
