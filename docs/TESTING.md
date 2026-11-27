# Testing

Tests are divided by what they can establish. The current machine's executed results, failures and environmental blocks belong in [VERIFICATION.md](VERIFICATION.md). CI configuration alone is not evidence that a workflow has passed on GitHub.

## Backend

From `backend/`:

```sh
mvn -B -ntp verify
```

Unit tests should cover service decisions and cryptographic/validation behavior without a database. API/security tests exercise routing, status codes, authentication and CSRF boundaries. Tests involving persistence, constraints, row locks, concurrent transfers or callback races must run against real PostgreSQL.

For the disposable database integration profile:

```text
TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:55439/walletpay_test
TEST_DATABASE_USERNAME=walletpay_test
TEST_DATABASE_PASSWORD=<your disposable database password>
```

```sh
mvn -B -ntp -Pintegration verify
```

Use a new test database only. Tests may clean their own fixtures; never point this profile at existing development or production data. The GitHub CI service starts PostgreSQL 18 on a disposable runner and does not require Docker on the laptop.

## Frontend

From `frontend/`:

```sh
npm ci
npm run test -- --run
npm run build
```

Component/unit tests should cover money parsing, validation, loading/error states and critical controls. The production build checks bundling; it does not prove authentication, transfer correctness or responsive accessibility in a running browser.

## End-to-end

The root `tests/e2e/` package exercises a running application. The acceptance flow includes registration, login, dashboard, the manual one-time ₹100 demo claim, a transfer between two users, transaction search, export, receipt authorization, profile changes and logout. Operator/API regression tests also retain the separate pending deposit intent and signed callback path. Tests should cover invalid inputs, insufficient funds, missing authentication and CSRF failure. See the current verification record for which cases were actually executed.

Browser tests use disposable accounts and sandbox funds. The HMAC secret belongs in the test runner environment, never in browser JavaScript. Run the documented test script from `tests/e2e/package.json` after preparing the local processes. Desktop and narrow-screen screenshots are test evidence, not source assets; generated browser reports are ignored.

## Containers and deployment

CI builds backend, frontend and HTTPS-edge images after source tests pass and validates both Compose models. A successful Docker build is not a successful rollout. Runtime Compose checks, public DNS, ACME issuance, HTTPS redirects, real security groups, restore drills, AWS log delivery and ECR/IAM permissions still need a controlled deployment environment.

Do not claim latency, load capacity or an availability target without measurements. Concurrency tests establish specific race behavior; they do not constitute a load test or prove unlimited scalability.

## Recommended regression matrix

| Area | Required invariant |
| --- | --- |
| Authentication | No demo bypass; invalid credentials rejected; expired/revoked sessions fail |
| Authorization | Another user's receipt and transactions are inaccessible |
| Transfers | Positive integer amount, no self-transfer, no negative balances, overflow rejected |
| Atomicity | Failed operation changes neither wallet nor transaction history |
| Idempotency | Same key/payload returns original; changed payload conflicts |
| Concurrency | Ordered locks prevent lost updates; simultaneous debits cannot overspend |
| Webhooks | Bad signature, stale timestamp and mismatched intent fail; duplicate cannot credit twice |
| Demo funds | One fixed ₹100 claim per account, including concurrent attempts; no automatic signup credit; disabled mode rejects claims |
| Demo history | Completed `DEMO_CREDIT` appears in receipts, filters, export and analytics; existing pending deposits remain pending |
| Bank placeholder | Marked Under development, controls disabled, no bank-data submission/storage |
| Queries/export | Filters and page limits hold; CSV cells cannot become spreadsheet formulas |
| UI | Errors recover; controls accessible; responsive layout; sessions clear on logout |
| Migrations | Fresh PostgreSQL schema migrates and Hibernate validation succeeds |
