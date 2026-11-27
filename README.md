# WalletPay — Digital Wallet

A full-stack wallet sandbox built with **Java 21, Spring Boot, PostgreSQL, React and Vite**. It demonstrates secure session handling, concurrent money movement, retry-safe APIs, auditable state changes, and a practical AWS deployment path.

**Hosted demo:** [digitalwalletpay.duckdns.org](https://digitalwalletpay.duckdns.org/)

This repository contains the application code used by the hosted WalletPay demo. **All balances are simulated INR funds. This project has no real banking or payment-provider integration.**

## What the application does

- Register and sign in with hashed passwords and a short-lived, HttpOnly JWT session.
- View a wallet dashboard, spending summary, activity chart and recent transactions.
- Review and confirm wallet-to-wallet transfers with required idempotency keys and support for Indian phone-number formats.
- Switch between light, dark and system themes, with the selected appearance remembered in the browser.
- Open profile and sign-out actions from the bottom avatar; use the WalletPay logo to return home.
- Search and filter paginated transaction history, export CSV, and open receipts.
- Update a profile and change a password with session invalidation.
- Claim **one free simulated ₹100 credit per account** when the optional demo-funding feature is enabled. Existing and new accounts claim it manually; registration does not automatically add funds.
- Keep the separate signed-callback deposit mechanism for operator testing. Ordinary demo users cannot choose a credit amount or request repeat free top-ups.
- Show the bank-account area as **Under development**. Its disabled placeholder fields do not collect or store real bank details.
- Record sensitive business operations in an audit trail.

Amounts are integer **minor units**, so `12550` means **₹125.50**. The application is deliberately single-currency. It never uses floating-point values to store balances.

## Start here

| Goal | Read |
| --- | --- |
| Run without Docker or AWS | [Local development](docs/LOCAL_DEVELOPMENT.md) |
| Understand the design | [Architecture](docs/ARCHITECTURE.md) |
| Integrate with the API | [API guide](docs/API.md) |
| Review security and known limits | [Security model](docs/SECURITY.md) |
| Run tests and inspect actual results | [Testing](docs/TESTING.md) and [verification record](docs/VERIFICATION.md) |
| Deploy on AWS | [AWS deployment](docs/AWS_DEPLOYMENT.md) |
| Update the deployed ZIP-based installation | [Deployment update](docs/DEPLOYMENT_UPDATE.md) |
| Operate, back up and recover | [Operations](docs/OPERATIONS.md) |
| Make a focused contribution | [Contributing](CONTRIBUTING.md) |

## Repository layout

```text
backend/                 Java application, migrations and backend tests
frontend/                React interface, API client and component tests
infra/
  docker/                Multi-stage images and secret-file entrypoint
  nginx/                 Same-origin SPA/API gateway and security headers
  caddy/                 HTTPS edge configuration
  scripts/               Local secret generation
  compose.yaml           Local container stack, bound only to loopback
  compose.ec2.yaml       Optional public HTTPS edge override
docs/                    Design, API, security, testing and deployment guides
tools/                   Native development helpers (no Docker required)
tests/e2e/               Browser acceptance tests
.github/workflows/       CI and manually approved image publication
```

The backend separates HTTP handling, business rules, persistence, security and configuration. The frontend separates application state, API calls, pages and shared UI. Deployment files live outside either application so the same source remains usable locally or in containers.

## Optional container quick start

Docker is optional for development and is not assumed to be installed on your laptop. On a machine with Docker Engine and Compose v2:

```sh
node infra/scripts/setup-env.mjs
docker compose --env-file .env -f infra/compose.yaml up --build -d
```

Open **http://localhost:8088**. This local stack uses HTTP with non-secure cookies; the EC2 override enables HTTPS and the strict production profile. The database and backend have no published host ports. Generated secrets remain in the ignored `.secrets/` directory. Existing configuration is preserved by the setup script.

The deployment default is `DEMO_FUNDING_ENABLED=false`. Set it to `true` in the existing environment file and recreate the backend to show the one-time ₹100 claim. The native development helper enables this feature explicitly. Claim status is stored in PostgreSQL, so signing out, restarting containers or using a new idempotency key does not create another grant for the same account. This is simulated demo money, not a bank deposit.

## Native development status

Run `python tools/local.py status` from the repository root to check the local database port, API health and frontend endpoint without starting services or changing data. See [local development](docs/LOCAL_DEVELOPMENT.md#check-local-services) for the checks and exit codes.

## Engineering choices

**Consistency first.** Transfers lock both wallets in a deterministic order and update balances and transaction records within one database transaction. Duplicate requests resolve through the same operation key rather than creating a second payment.

**Browser sessions stay out of JavaScript storage.** A JWT is delivered in an HttpOnly cookie, backed by server-side revocable sessions. Unsafe browser requests also require a CSRF token. The UI does not store tokens in localStorage.

**Database behavior is tested on PostgreSQL.** Unit tests do not substitute H2 behavior for PostgreSQL locking and constraints. Integration tests and the CI database service use a disposable PostgreSQL database.

**AWS hosting with explicit deployment control.** The hosted demo uses EC2, Docker Compose and a Caddy HTTPS edge. CI checks source and builds containers. ECR publication requires an explicit manual action and configured AWS role. No workflow provisions AWS resources or deploys automatically. See the verification record for checks actually executed in the current environment.

## Scope

This application is a financial sandbox, not a regulated payment system. Real payment acceptance would need a verified provider integration, reconciliation, fraud controls, compliance review and operational controls. The current balance-and-transaction model is not presented as a complete double-entry accounting ledger. See [security boundaries](docs/SECURITY.md#financial-boundaries).
