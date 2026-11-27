# Operations and recovery

## Health and logs

The backend exposes minimal `/actuator/health`, including configured readiness/liveness checks. Compose uses it internally before starting dependent services. Nginx `/healthz` reports that the static gateway can respond; it does not prove the database is healthy. Do not equate a gateway health check with a successful wallet transaction.

Application logs go to stdout/stderr. Docker logs are bounded to three 10 MB files per service in the supplied stack. Nginx's access format excludes query strings, cookies, authorization headers and bodies; Caddy access logging is left disabled so the edge does not duplicate sensitive request URLs. Caddy's runtime diagnostics remain available. Investigate by request ID and operation reference. Do not enable verbose HTTP-body logging in a deployed environment. Audit business events separately from temporary diagnostic logs.

For an authorized deployment, alert on:

- sustained 5xx failures, readiness failures and repeated container restarts;
- host memory pressure, disk space and inode exhaustion;
- database connection saturation, lock waits and failed migrations;
- unusual login throttling or signature failures;
- unexpected cost increases or CPU-credit charges.

EC2 memory/disk metrics require an agent or equivalent collection; they are not all available from default EC2 metrics. Configure CloudWatch Agent deliberately, with a restricted role and bounded retention. Keep any metric/log ingestion costs in the cost estimate.

## Backups

For the single-host database, create logical backups with `pg_dump -Fc`, encrypt them at rest, and retain copies off the host. A volume surviving a container restart is persistence, not a backup. A snapshot is useful, but a tested logical restore gives additional evidence that application data can be recovered.

On the deployment host, with the correct Compose project and database identity verified:

```sh
mkdir -p backups
docker compose --env-file .env -f infra/compose.yaml exec -T database \
  pg_dump -U walletpay -d walletpay -Fc > backups/walletpay-reviewed-backup.dump
```

Replace the role/database if you configured different names. The command creates an intentionally named file and can overwrite that file if repeated; use a unique dated filename for recurring backups. `backups/` is ignored by Git. Restrict directory/file permissions, verify command success and dump size, and upload through an authorized role to an encrypted backup destination. Do not send credentials in command-line arguments or store dumps in the source repository.

## Restore drill

1. Restore into a **new isolated database**, never over the running production database for a drill.
2. Check `pg_restore --list` and run a full restore with errors treated as failures.
3. Start the same reviewed application version against that database.
4. Verify users, transaction counts, wallet totals, session behavior and a sandbox transfer.
5. Record the backup's timestamp, restoration duration and any data lost since the recovery point.

For RDS, configure automated backup retention and test point-in-time restoration into a separate instance. A managed backup setting does not prove a restore procedure works.

## Updates

Review dependency updates weekly. CI should test source and container builds before publication. Production images should be scanned and pinned to reviewed immutable digests; the source Dockerfiles use maintained major-version base tags for a reproducible build procedure, not a guarantee that a future tag resolves to identical bytes. Record the resolved digests at rollout.

Take a backup before migrations. Validate the change on restored staging data. Choose additive, backward-compatible schema changes where possible. Do not roll back a database by editing Flyway history or deleting migration files. Application images and schema compatibility must be reviewed together.

For the one-time demo credit update, preserve the existing `server.env`/`.env`, `.secrets/`, database volume and certificate volumes. V3 adds persistent welcome-claim state and the `DEMO_CREDIT` transaction type; it does not automatically fund accounts or complete pending deposits. The feature is off by default and enabled explicitly with `DEMO_FUNDING_ENABLED=true`. A prior application version may not understand the new records, so treat rollback as a compatibility review, not merely an image switch. Follow [the ZIP deployment update guide](DEPLOYMENT_UPDATE.md).

## Incident handling

If a secret is exposed, rotate it, revoke affected sessions and investigate logs by request/operation identity. JWT-key rotation invalidates tokens signed by the former key; schedule a user-visible sign-in requirement rather than silently accepting a compromised key. The callback secret is independent and must be rotated separately.

If balances or transaction integrity are in doubt, stop money-changing sandbox operations, preserve evidence and reconcile balances against transaction records before resuming. A second request is not a repair for an uncertain transfer; inspect its idempotency key/reference.

## Teardown

Stop the application through the same Compose project/configuration used to create it. `docker compose down` preserves named volumes by default. **Do not add `-v` unless you explicitly intend to delete that project's database and certificate data and have a verified backup.**

In AWS, review retained EC2 disks, snapshots, Elastic IPs, ECR images, DNS zones, log groups, secrets and backup objects when ending an experiment. Stopping containers does not stop the EC2 bill, and stopping EC2 does not remove all retained-resource charges.
