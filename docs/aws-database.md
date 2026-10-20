# AWS Aurora PostgreSQL with IAM

The selected Aurora express cluster uses its internet access gateway and IAM authentication. AWS does not offer a permanent database password for this configuration. The backend now supports this configuration directly.

## Prepared configuration

Private `backend/.env.aws` contains:

- DB_AUTH_MODE=iam
- AWS_REGION=ap-south-2
- DB_USER=postgres
- DB_URL for the yfc database, using sslmode=verify-full and the verified Amazon CA bundle
- Initial YFC application-administrator credentials, separate from AWS authentication

No DB_PASSWORD, AWS access keys or pasted IAM tokens are required in that file. The prepared local profile `yfc` uses a credential-process helper to reuse your signed-in CLI profile `default`. `AWS_CONFIG_FILE` points to backend/config/aws-credentials.ini; `AWS_PROFILE=yfc` selects that bridge; `YFC_AWS_LOGIN_PROFILE=default` selects the underlying console login. No credentials are stored in the config file. The helper's credential output is consumed privately by the SDK; do not run it to display credentials.

The `yfc` and `yfc_test` databases were created on the existing Aurora cluster after a successful IAM login. As of 21 October 2026, Flyway applied V15–V17 to the live yfc application database. yfc_test reached V16 during the 204-case Aurora acceptance run, then V17 during the focused correction/trip rerun. V19 (quarantine yards, document targets, odometer resets and fuel allocations) is in source and will apply on the next live `run-local.sh` start. Live health is UP. No live treasury accounts, money vouchers, party mappings, chart accounts or financial entries were seeded, and no bank transfers were executed. Earlier phase results are retained in [verification records](verification.md). Existing data was preserved. No additional environment variables, AWS instance, cluster or S3 bucket were needed; private files continue to use database storage.

## Sign in and start

The installed AWS CLI supports browser-based console sign-in. Your completed `aws login --region ap-south-2` configured the default profile. When that session expires, sign in again. If you use a different CLI login profile, change YFC_AWS_LOGIN_PROFILE to that name; leave AWS_PROFILE=yfc for the bridge.

From the project root:

```sh
./backend/scripts/aws-db.sh check
./backend/scripts/run-local.sh backend/.env.aws
```

The maintenance check logs in to the built-in postgres database and lists whether postgres/yfc/yfc_test exist; it does not run application migrations. Normal backend startup applies Flyway migrations to yfc and initializes the first YFC admin only if the account table is empty.

The maintenance command `./backend/scripts/aws-db.sh create-databases` creates yfc/yfc_test only if absent. It never drops existing databases. Build with `mvn package` from backend before using maintenance helpers after source changes; they use the packaged JAR.

## Automatic token renewal

The application uses the AWS SDK default credentials provider, including console-login, Identity Center, environment and workload-role credentials. It generates a fresh IAM token each time the connection pool creates a physical database connection, rather than storing one temporary token as a fixed password. Existing authenticated connections are pooled normally. If the underlying AWS login session expires, a new interactive login may be necessary; application token generation cannot extend the AWS identity's allowed session.

Certificate and hostname verification remain enabled. The supplied gateway uses an Amazon Root CA 1 chain, included with the official RDS roots in backend/certs/aws-postgres-ca-bundle.pem.

## Test database

```sh
./backend/scripts/test-aws.sh
```

This selects only the PostgreSQL acceptance suites, routes them to yfc_test and enables IAM authentication for their connections. It writes disposable test records. Do not repoint it to yfc. Tests use known test-only administrator passwords in yfc_test; those are not production credentials.

Local H2 checks remain available through `mvn verify` in backend with no AWS environment loaded. Password-based PostgreSQL remains supported when DB_AUTH_MODE is absent or set to password.

## About passwords in AWS

For this Aurora cluster, no database password needs to be created. Your AWS Management Console sign-in password authenticates you during browser-based aws login; it is not a database password and must not be put in DB_PASSWORD.

If you later choose a separate standard RDS PostgreSQL instance with password authentication, use Create database → Settings/Credentials settings → Self managed → Master username and Master password. For an existing self-managed password-enabled RDS instance, select it → Modify → Credentials settings → set a new master password → Continue and review application timing. These options do not enable password authentication on the current express cluster.

## Production follow-up

The supplied postgres identity is used for initial setup. Production deployment should use a dedicated workload IAM role and an appropriately scoped database role rather than a developer's root-console session. Do not commit credentials; the AWS environment file is private and ignored by Git.

Sources:

- https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/CHAP_GettingStartedAurora.AuroraPostgreSQL.ExpressConfig.html
- https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sign-in.html
- https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-temporary.html
- https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/services/rds/RdsUtilities.html
