# Update an existing ZIP-based deployment

This guide applies to the existing Ubuntu EC2 installation of this edition, including **https://digitalwalletpay.duckdns.org**. The owner uploads a clean source update ZIP; Git and a new AWS instance are not required. No command below provisions AWS resources or changes DNS. The update's claim feature uses simulated money only.

## Preserve the running installation

Open a terminal on the existing host through your authorized access method. Change into the **existing project directory**, then confirm it contains `infra/compose.yaml`, `infra/compose.ec2.yaml`, and the current environment file. Use the same Compose project name and environment file as the running deployment. An installation that uses `server.env` must continue using that file; do not replace it with `.env.example`.

Preserve **`server.env` or `.env`, `.secrets/`, the database volume, and Caddy's certificate volumes**. The source update archive is generated from tracked source only and must not contain live credentials, `.local/`, backups or database data. Inspect its listing before extraction:

```sh
pwd
test -f infra/compose.yaml
test -f infra/compose.ec2.yaml
test -d .secrets
ENV_FILE=server.env
test -f "$ENV_FILE"
docker compose --env-file "$ENV_FILE" -f infra/compose.yaml -f infra/compose.ec2.yaml ps
unzip -l /home/ubuntu/WalletPay-UPDATE.zip
```

Replace the ZIP path with the uploaded filename. If the installation actually uses `.env`, set `ENV_FILE=.env` instead. Stop if the archive contains live configuration, unexpected absolute paths, `..` path components or files outside the expected source tree. This archive should contain source directories such as `backend/`, `frontend/`, `infra/`, `docs/`, `tools/` and `tests/`, plus repository configuration and documentation.

## Back up, apply source and enable the one-time claim

Before changing source, back up the current database and verify that the command succeeds. Keep the backup private and off the host as part of the normal recovery process:

```sh
umask 077
mkdir -p backups
docker compose --env-file "$ENV_FILE" -f infra/compose.yaml -f infra/compose.ec2.yaml \
  exec -T database sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > "backups/pre-demo-credit-$(date -u +%Y%m%dT%H%M%SZ).dump"
```

Extract the reviewed flat source archive **over the existing project directory**, replacing its known source files only:

```sh
unzip -o /home/ubuntu/WalletPay-UPDATE.zip
```

Edit the **existing** environment file and add or change just this non-secret setting:

```dotenv
DEMO_FUNDING_ENABLED=true
```

Keep its other settings unchanged, especially `DOMAIN`, `COOKIE_SECURE`, `ALLOWED_ORIGINS`, database identity, Compose project name and secret locations. The template and default remain `false`; the public demo must explicitly enable the flag. No secret or password is needed in browser code.

Validate the Compose model and build the two application images. Separate build commands avoid building both simultaneously on a small host:

```sh
docker compose --env-file "$ENV_FILE" -f infra/compose.yaml -f infra/compose.ec2.yaml config --quiet
docker compose --env-file "$ENV_FILE" -f infra/compose.yaml -f infra/compose.ec2.yaml build backend
docker compose --env-file "$ENV_FILE" -f infra/compose.yaml -f infra/compose.ec2.yaml build frontend
docker compose --env-file "$ENV_FILE" -f infra/compose.yaml -f infra/compose.ec2.yaml \
  up -d --no-build --no-deps backend frontend
docker compose --env-file "$ENV_FILE" -f infra/compose.yaml -f infra/compose.ec2.yaml ps
```

Do not run `docker compose down -v`, remove the database volume, recreate secrets, or initialize a new database. If build or migration fails, stop and inspect the specific error rather than changing credentials or deleting data.

## What V3 changes

The additive V3 Flyway migration records whether each wallet has claimed its welcome grant and permits `DEMO_CREDIT` transaction records. It does not delete users or transactions, reset balances, automatically credit registration, or complete existing pending deposit intents. Both existing and new accounts can manually claim **one fixed ₹100 grant**. Signing out, changing idempotency keys and restarting containers do not reset the persisted claim.

This is a forward application/schema change. An old binary may not understand `DEMO_CREDIT` rows after a user claims. Review compatibility before rollback; do not delete Flyway history or rewrite a migration checksum to force old code to start. Keep a tested backup and prefer a reviewed forward fix.

## Verify the update

1. Check backend health and logs; confirm Flyway completed V3 successfully.
2. Sign in to an existing account and claim the ₹100 demo credit if not already claimed. Confirm the balance, completed transaction, receipt and analytics.
3. Sign out and back in. The same account must not receive another grant.
4. Register a new account: its balance starts at zero until it manually claims the ₹100.
5. Transfer some available demo funds to another account and verify both balances.
6. Confirm old pending deposit intents remain pending and the bank-account section says **Under development**, with disabled fields and no bank-data submission.

These are deployment acceptance checks, not a statement that this source update has already been installed or tested on the public host. The current local test evidence is recorded separately in `VERIFICATION.md`.
