# Local environment

## Already prepared

`backend/.env` was created only if it did not already exist, with generated local database and first-administrator passwords. It has owner-only permissions and is ignored by Git and excluded from Docker build context. No existing environment file was overwritten and no external service credentials were added.

Open the file locally to view your initial admin credentials. Do not publish it or use these development credentials for production.

No new environment variables are required for business master records. Existing variables are:

| Variable | Use |
|---|---|
| SPRING_PROFILES_ACTIVE | `local` for development; `prod` for configured HTTPS deployment |
| DB_URL | Defaults in prepared file to `jdbc:postgresql://localhost:5432/yfc` |
| DB_USER | Prepared local database user: `yfc` |
| DB_PASSWORD | Generated local database password; must match your database |
| YFC_ADMIN_USERNAME | Initial administrator username, default `admin` |
| YFC_ADMIN_PASSWORD | Used only to initialize the first admin in an empty database |
| PORT | Optional; defaults to 8080 |

Bootstrap settings do not change an existing administrator password. Once initialized, remove the bootstrap password from runtime configuration; use user-management password reset for later changes.

## Start with the supplied local database

From the project root, with Docker Desktop installed and running:

```sh
./backend/scripts/db-local.sh
./backend/scripts/run-local.sh
```

The first script provisions PostgreSQL 17 and waits for readiness. The second loads the trusted local `.env` into its process and starts Spring Boot. This avoids manually exporting variables. Both can be invoked from any directory using their paths. Java 21+ and Maven 3.9+ must be installed.

The local server is http://localhost:8080. Login uses the configured bootstrap username/password on a fresh database. Database migrations apply automatically. No email, maps, S3 or payment keys are required for this milestone.

## Use an existing PostgreSQL server instead

Edit DB_URL, DB_USER and DB_PASSWORD in `backend/.env` to match your existing server. Ensure the database exists and its user can create/alter tables for migrations. Then run only `./backend/scripts/run-local.sh`.

Values containing spaces or shell characters should be single-quoted in the trusted local file. The startup helper sources that file as shell configuration; do not use an untrusted downloaded `.env`.

## Current machine status

Java and Maven are installed. A local PostgreSQL/Docker runtime is not installed, but the AWS Aurora connection is now configured and verified; the local database is optional. Use the AWS environment file to run without Docker. Docker image PostgreSQL initialization uses DB_PASSWORD only on an empty volume; changing that setting later does not rotate an existing database password. Preserve existing volumes and update credentials deliberately.

## Production

These helper scripts are for local development. Production needs supplied database credentials, HTTPS, the prod profile and separate secret management. External services and their keys can be configured when those integrations are implemented.

## AWS database alternative

The private `backend/.env.aws` now uses `DB_AUTH_MODE=iam` and `AWS_REGION=ap-south-2` for the existing Aurora express cluster. No database password or pasted token is needed. The AWS SDK uses your signed-in default AWS profile and generates tokens for new connections. Start with `./backend/scripts/run-local.sh backend/.env.aws` from the project root. See [AWS instructions](aws-database.md). Local password configuration in the original `.env` is separate.
