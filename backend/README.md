# WalletPay API

Java 21 and Spring Boot 4.1.3. PostgreSQL is the only supported application database. Flyway owns the schema; Hibernate validates it rather than changing it automatically.

## Run locally

From this directory, point the process to a **new WalletPay database**, then run Maven. Windows Command Prompt example:

```bat
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/walletpay"
set "DATABASE_USERNAME=walletpay"
set "DATABASE_PASSWORD=your-local-database-password"
mvn spring-boot:run
```

The development profile creates an ephemeral JWT signing key if `JWT_SECRET` is omitted. Sessions are revoked on application restart in that mode. Production requires a configured signing secret, secure cookies, and explicit HTTPS origins. Never use the integration-test keys in deployment.

## Verify

```bat
mvn verify
```

This runs formatting checks and isolated unit/Mockito tests. To include real PostgreSQL persistence, API, security, rollback, schema and concurrent-transfer tests:

```bat
set "TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/walletpay_test"
set "TEST_DATABASE_USERNAME=walletpay_test"
set "TEST_DATABASE_PASSWORD=your-disposable-test-password"
mvn -Pintegration verify
```

Integration tests deliberately reset their tables between cases. Use a disposable database ending in `_test`; the test guard rejects other database names. The integration profile fails when no integration tests are discovered. It does not require Docker or AWS.

Format Java consistently with `mvn spotless:apply`. CI checks the same Google Java Format AOSP configuration. Mockito is configured as a startup Java agent so tests do not depend on dynamic self-attachment.

## Boundaries and safety

- This is an INR sandbox. Amounts are integer paise; each movement is limited to 100,000,000 paise and balances to 10,000,000,000 paise.
- Browser authentication uses a short-lived HttpOnly JWT cookie, with a database session record for logout/password-change revocation. Unsafe browser requests require CSRF protection.
- Every password uses BCrypt. New passwords must have at least 12 characters and at most 72 UTF-8 bytes.
- Transfers lock wallets in ascending user-ID order. Request keys are sender-scoped, checked under the lock, and constrained in PostgreSQL.
- A deposit intent never credits a balance. The opt-in signed sandbox callback verifies the exact body and timestamp, validates the intent, and records a unique event before committing. This is **not a banking integration**.
- The body reader bounds requests even without Content-Length. Authenticated API rate limits are per user. Anonymous/gateway guards use the actual socket address and ignore spoofable forwarded headers. Behind a reverse proxy, that gateway guard is aggregate; phone-specific login limits still apply. Multi-instance deployments need shared rate limiting or an appropriately configured edge control.
- Transaction and audit evidence is application-managed, not a regulated double-entry ledger. There is no real-money capability, KYC/AML, provider reconciliation or verified ownership of phone numbers.

See the repository's `docs/` directory for the API contract, architecture, executed verification evidence and deployment guide.
