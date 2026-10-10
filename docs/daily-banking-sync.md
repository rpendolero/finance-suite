# Automatic bank synchronization (0.5.4)

The finance-server executes Enable Banking synchronization every day at **06:00 Europe/Madrid**.
It reuses the same `BankingSyncService.sync(connectionId)` application use case as the manual REST endpoint.

## Docker environment
- `ENABLE_BANKING_ENABLED=true` is required.
- `ENABLE_BANKING_SYNC_ENABLED=true` enables the schedule (default when banking is enabled).
- `ENABLE_BANKING_SYNC_CRON="0 0 6 * * *"` sets a Spring 6-field cron expression.
- `ENABLE_BANKING_SYNC_ZONE=Europe/Madrid` controls the scheduler time zone.

Restart the server container after deploying the new image. Leave the manual sync endpoint available.
All active, nonexpired authorizations are processed independently; expired connections are skipped.
Each account sync uses existing deduplication and classification. Failed connections are logged,
and subsequent connections continue. Authorizations expiring within seven days trigger a warning.

**Important:** A local atomic guard prevents overlapping executions in a single JVM only.
For multiple server replicas, add a distributed lock (e.g., ShedLock with MySQL).
No automated reauthorization is attempted; the account owner must re-consent through the UI.

## Build and release
Run `mvn clean verify` in the repository root and publish Docker images only after the suite passes.
The Maven parent and all child modules are set to `0.5.4`.
The frontend is unchanged; the scheduler executes entirely in finance-server.
