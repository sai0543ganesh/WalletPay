# Local development without Docker

The application can run entirely on a Windows laptop using Java 21, Maven, Node.js and a locally installed PostgreSQL server. No AWS account is required. The root-level `tools/local.py` helper creates an isolated development database and manages the local processes; consult its help before running it. The helper uses its own local data directory and must never point at an existing PostgreSQL data directory. See [the executed verification record](VERIFICATION.md) for the actual environment used to test this revision.

## Prerequisites

- Java 21 JDK, with `JAVA_HOME` pointing to that installation.
- Maven 3.9 or a newer supported version.
- Node.js 22.12 or newer on the Node 22 LTS line, and npm.
- PostgreSQL 18 client/server tools, including `initdb`, `pg_ctl`, `createdb` and `psql`.
- Python 3 for the optional native helper.

Verify tools in **Command Prompt**:

```bat
java -version
mvn --version
node --version
npm --version
psql --version
python tools\local.py --help
```

If another Java installation appears first on PATH, set `JAVA_HOME` to your JDK 21 directory and prepend `%JAVA_HOME%\bin` in that terminal. Do not change system-wide settings merely to run this project.

## Native helper quick start

From the repository root in Command Prompt:

```bat
python tools\local.py doctor
python tools\local.py db-start
cd frontend
npm ci
cd ..
```

Then run these in two separate terminals, also from the repository root:

```bat
python tools\local.py backend
```

```bat
python tools\local.py frontend
```

Open **http://localhost:5173**. The helper uses its own PostgreSQL data under `.local/`, binds it to loopback port **55432**, and generates local credentials. Stop the backend and frontend with Ctrl+C in their respective terminals, then run `python tools\local.py db-stop` to stop this helper's database. Existing independently managed PostgreSQL services are not the target of these commands.

## Default ports

| Service | Native development | Local Docker Compose |
| --- | --- | --- |
| Browser interface | `http://localhost:5173` | `http://localhost:8088` |
| Backend | `http://localhost:8080` | Internal only; accessed through `/api` on port `8088` |
| PostgreSQL | Loopback port `55432` | Internal only; no host port published |

The status helper checks the native setup. For Compose, inspect containers with `docker compose --env-file .env -f infra/compose.yaml ps` instead.

## Check local services

From the repository root, run:

```bat
python tools\local.py status
```

This read-only command checks the database's loopback port, the backend's health endpoint and the frontend's HTTP endpoint. It prints `READY` or `NOT READY` for each service, exits with code 0 when all three checks pass, and exits with code 1 otherwise. It does not start processes, generate credentials or change data. `LOCAL_DB_PORT` is respected for the database probe; the API and frontend checks use the native setup's ports 8080 and 5173.

The database check confirms only that a TCP connection is accepted, not that database credentials are valid. The frontend check confirms an HTTP response, not a complete browser journey. Use the [testing guide](TESTING.md) for full application verification.

## Environment

For a manually managed local database, create a new database and a role dedicated to WalletPay. Configure these variables only for your development terminal:

| Variable | Development example / purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` |
| `DATABASE_URL` | `jdbc:postgresql://127.0.0.1:55432/walletpay` |
| `DATABASE_USERNAME` | Dedicated local role |
| `DATABASE_PASSWORD` | That role's password |
| `JWT_SECRET` | Random value of at least 32 UTF-8 bytes |
| `COOKIE_SECURE` | `false` for localhost HTTP only |
| `ALLOWED_ORIGINS` | `http://localhost:5173` |
| `SANDBOX_WEBHOOK_ENABLED` | `false` normally |
| `DEMO_FUNDING_ENABLED` | `true` to enable the manual one-time ₹100 grant; deployment default is `false` |
| `WEBHOOK_SECRET` | Separate random value; only used by the server-side simulator |

Do not copy examples containing empty secrets directly into an internet-facing deployment. `node infra/scripts/setup-env.mjs` generates independent random values in `.secrets/` for the optional Compose stack; it does not display them or overwrite existing secrets.

## Run manually

Start PostgreSQL separately using your own new development database, then run these commands in two terminals opened at the repository root. Keep the database variables in the backend terminal only.

```bat
cd backend
mvn spring-boot:run
```

```bat
cd frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. Vite proxies `/api` to **http://127.0.0.1:8080**. Browser cookies therefore stay same-origin. Register two independent accounts to explore transfers. A new account starts with zero balance. There is no automatic credit and no special password bypass.

## Claim the one-time ₹100 demo credit

The native `tools/local.py backend` helper explicitly enables demo funding. Sign in and use the one-time **₹100 demo credit** claim in the interface. The amount becomes available for wallet-to-wallet transfers after the claim succeeds. Existing and new accounts must claim manually; registration does not automatically credit a wallet.

Each account has one persisted claim allowance. Signing out, restarting the application or submitting a new request key does not create another grant. The claim is fixed at ₹100 and accepts no amount field. It is simulated money, not a real payment. If you have already claimed it, receive a transfer from another sandbox account to continue exploring transfers; there is no repeat free top-up button.

For Compose, set `DEMO_FUNDING_ENABLED=true` in the existing environment file and recreate the backend. The deployment template leaves the feature disabled by default. The bank-account section is marked **Under development**, with disabled placeholder fields and no real bank-data storage.

## Separate operator-only callback testing

The existing deposit-intent and signed-callback mechanism is retained for operator/API regression testing. A pending intent remains pending until its matching valid callback arrives; claiming the demo credit does not complete or convert it. The native helper explicitly enables this **local** simulator as well and supplies a generated secret from `.local/runtime.json`. The ordinary funding interface uses only the fixed one-time demo grant. Deployment configuration keeps the callback simulator disabled by default.

To complete an explicitly created test deposit intent, replace `DEPOSIT_UUID` with that intent's UUID and use its exact amount in paise. For an operator-created ₹100.00 test intent:

```bat
python tools\confirm_deposit.py DEPOSIT_UUID 10000
```

The amount must exactly match the intent, in paise. The helper reads the local secret without printing it and only sends to loopback addresses. This is a privileged operator testing tool, not an additional free-funding option for users and not a remote banking integration. Never put its signing secret in a `VITE_` variable or browser code.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Status reports `NOT READY` immediately after startup | Wait for the backend startup message, then run `python tools\local.py status` again |
| Frontend fails because Vite is missing | Run `npm ci` in `frontend/`, then restart the frontend helper |
| Database port 55432 is already occupied | Set `LOCAL_DB_PORT` to an unused port in each native-helper terminal; leave the existing service running |
| Backend fails before serving requests | Database URL/credentials, migration errors, port 8080 availability and secret length |
| Browser receives 403 on a form | CSRF fetch/header, cookies, allowed origin and whether a stale token followed logout |
| Login succeeds but next request is 401 | Secure-cookie flag on HTTP, clock, cookie path, session expiry |
| Transfer has an uncertain result | Retry with the same idempotency key; do not blindly create a new payment |
| Database migration fails on an old database | Stop. Follow the existing-data guidance; do not enable automatic baseline or delete tables |
| Java/PostgreSQL timezone error | Use UTC for this application's JVM and timestamps |

The native application and optional container stack have separate data stores. Stopping a process should not erase its database. Remove only a disposable database after confirming its absolute path and that no data is needed.
