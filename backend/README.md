# YFC backend

## Requirements

Java 21+, Maven 3.9+, PostgreSQL 17 (or Docker Compose for the provided local database).

## Prepared local environment

See [local environment instructions](../docs/local-environment.md). A private `.env` has been prepared locally, and `scripts/run-local.sh` loads it for you. From the project root, run `./backend/scripts/db-local.sh` (requires Docker) and then `./backend/scripts/run-local.sh`. With an existing PostgreSQL server, update the DB settings in `.env` and skip the database script.

Business master endpoints and normalized order examples are documented in [business masters](../docs/business-masters.md).

Sales workflow: [RFQs, revisions, acceptance and multi-item orders](../docs/sales-workflow.md). No new environment settings are required.

## AWS Aurora IAM connection

The existing AWS Aurora cluster is configured in private `.env.aws` with IAM authentication and verified TLS. Start from the project root with `./backend/scripts/run-local.sh backend/.env.aws` after signing into AWS locally. No database password is needed in IAM mode. Use `./backend/scripts/aws-db.sh check` for a read-only connection check and `./backend/scripts/test-aws.sh` for the separate AWS test database. See [AWS setup](../docs/aws-database.md).

## Run locally

Set environment variables in your shell. `.env.example` is a reference, not automatically loaded by direct Spring Boot execution. The provided run-local helper loads the real `.env` instead.

```sh
export SPRING_PROFILES_ACTIVE=local
export DB_PASSWORD='choose-a-local-database-password'
export YFC_ADMIN_PASSWORD='choose-a-local-admin-password-at-least-12-characters'
docker compose up -d
mvn spring-boot:run
```

Run those commands from `backend`. With an existing PostgreSQL server, create database `yfc` and user `yfc`, set DB_URL/DB_USER/DB_PASSWORD, and skip Docker Compose. Flyway creates tables; Hibernate only validates them.

Development server binds to localhost. On the first start against an empty database, `YFC_ADMIN_PASSWORD` creates the initial database-backed `admin` account. Set `YFC_ADMIN_USERNAME` if you want another username. Passwords must contain at least 12 characters and at most 72 UTF-8 bytes. Subsequent restarts keep the existing account and do not replace its password from environment variables. Remove the bootstrap password from runtime configuration after initialization.

All profiles now use persistent users. Production uses `SPRING_PROFILES_ACTIVE=prod`, HTTPS and secure cookies. The prod profile configures a 30-minute session timeout and secure cookies; configure TLS/reverse proxy and trusted access separately. Sessions currently reside in application memory and are lost on restart. Multiple application replicas need a shared session store before deployment.

## Security and requests

Session login and CSRF protection are enabled. GET `/api/v1/csrf` anonymously and retain its session cookie. POST form-encoded `/login` with username, password and the CSRF token parameter. Login returns 204 on success and 401 on failure and rotates the session ID. After login, GET `/api/v1/csrf` again for a fresh token. Include its headerName/token on every write request. POST `/logout` with CSRF returns 204 and invalidates the session. GET `/api/v1/auth/me` returns current username and roles; anonymous API requests return 401.

Every authenticated request rechecks the persisted account's enabled state and credential/access version. Disabling a user, changing their role or completing password reset invalidates their existing sessions on the next request. Password/role changes do not cancel work already running in another request.

Customer, supplier and driver accounts can authenticate, but their business endpoints remain closed until record ownership is implemented. No cross-origin policy is enabled; use same-origin frontend routing later.

All JSON write endpoints require authentication, permission and CSRF token. API prefixes are `/api/v1`.

| Method | Path | Access |
|---|---|---|
| GET | /actuator/health | Public, no details |
| GET | /api/v1/csrf | Public; creates CSRF session |
| GET | /api/v1/auth/me | Authenticated |
| POST | /login | Public, CSRF required |
| POST | /logout | CSRF required |
| POST | /api/v1/users | Super Admin |
| GET | /api/v1/users | Super Admin |
| PATCH | /api/v1/users/{id}/access | Super Admin |
| POST | /api/v1/users/{id}/password-reset | Super Admin |
| POST | /api/v1/auth/password-reset | Reset token and CSRF required |
| GET | /api/v1/audit-events?page=0&size=20 | Super Admin |
| POST | /api/v1/customers | Admin, Operations |
| GET | /api/v1/customers?page=0&size=20 | Admin, Operations, Accounts |
| POST | /api/v1/orders | Admin, Operations |
| GET | /api/v1/orders/{id} | Admin, Operations |
| POST | /api/v1/orders/{id}/dispatches | Admin, Operations |
| POST | /api/v1/trucks | Admin, Operations |
| POST | /api/v1/fuel/rates | Admin only |
| GET | /api/v1/fuel/rates/{id} | Admin, Operations, Accounts |
| POST | /api/v1/fuel/entries | Admin, Operations, Accounts |
| GET | /api/v1/fuel/entries/{id} | Admin, Operations, Accounts |

Roles use SUPER_ADMIN, OPERATIONS, ACCOUNTS, CUSTOMER, SUPPLIER and DRIVER internally. Each account currently has one role. SUPER_ADMIN can create other administrators, Operations/Accounts users and external users. Administrators cannot disable or demote their own account. Identity mutations are serialized with a database lock, including reset-token use, to protect access rules during concurrent requests.

### Create a user

```json
{"username":"operations1","displayName":"Operations Manager","password":"choose-a-unique-strong-password","role":"OPERATIONS"}
```

POST `/api/v1/users`. Usernames are case-insensitive and normalized to lowercase; duplicates return 409. Passwords are stored using version-tagged BCrypt hashes. API responses exclude password hashes and credential versions. Assigning a temporary password does not yet force a first-login change; administrator-assisted reset is available instead.

PATCH `/api/v1/users/{id}/access` with:

```json
{"role":"ACCOUNTS","enabled":true}
```

### Administrator-assisted password reset

1. Administrator POSTs `/api/v1/users/{id}/password-reset`. Response contains a random token and expiresAt; it is marked `Cache-Control: no-store`.
2. Deliver that token privately to the intended user through a trusted channel. No email/SMS is sent and no token is printed in server logs by application code.
3. User obtains CSRF session/token and POSTs `/api/v1/auth/password-reset`:

```json
{"token":"token-received-privately","newPassword":"choose-a-new-unique-password"}
```

Tokens expire after 15 minutes, are stored only as SHA-256 hashes and can be used once. Issuing another token replaces prior unused tokens. Account access changes also invalidate outstanding tokens. Password reset returns 204; invalid/expired/reused tokens return 400. There is no unauthenticated reset-request/email flow yet.

### Audit history

GET `/api/v1/audit-events` is restricted to Super Admin. It includes actor, timestamp, action, target type/ID and a non-secret detail. User creation/access changes, reset issue/completion, login success/failure, logout, customers, orders, dispatch records, trucks, pump rates and fuel entries are audited. Business and identity write audits share the mutation transaction; failed writes roll back their audit entries. Authentication event audits run as separate transactions. No edit/delete API is exposed for audit events; this is application-level history, not tamper-proof storage.

### Customer

```json
{"name":"Example Customer","mobile":"9876543210"}
```

GSTIN can be omitted or null; do not submit an empty string. Its current validation checks format only, not government registration status.

### Order

```json
{"customerId":"CUSTOMER_UUID","businessType":"MATERIAL_AND_TRANSPORT","material":"20 MM Aggregate","deliverySite":"Gurgaon Site A","orderedQuantity":100,"unitRate":1200}
```

Business types: MATERIAL, TRANSPORT, MATERIAL_AND_TRANSPORT. Monetary values use decimal precision; subtotal excludes taxes. View responses always expose billableQuantity equal to orderedQuantity.

### Dispatch weight record

```json
{"reference":"WEIGH-SLIP-001","loadedQuantity":99,"receivedQuantity":98}
```

Multiple dispatch records are aggregated. Reference is unique across dispatches to prevent accidental replay; a duplicate returns 409. This preliminary endpoint records completed weights; trip scheduling, over-delivery controls and shortage resolution are future work.

### Truck

```json
{"registration":"HR55AB1234","capacityTonnes":35,"tankCapacityLitres":400,"mileageKmPerLitre":3}
```

### Diesel price and filling

```json
{"pump":"Example Pump","location":"Gurgaon","effectiveDate":"2026-10-17","pricePerLitre":91.50}
```

```json
{"truckId":"TRUCK_UUID","rateId":"RATE_UUID","receiptReference":"PUMP-RECEIPT-001","filledOn":"2026-10-17","quantityLitres":100,"actualPricePerLitre":92.00}
```

The actual receipt price is preserved even if it differs from the pump master. Receipt reference is globally unique in this foundation; scoped receipt numbering will be introduced with supplier/pump identities. Fuel submissions are not approved expenses or accounting postings.

## Verify and package

```sh
mvn verify
mvn package
```

Fast tests run H2 in PostgreSQL compatibility mode and validate Flyway migrations/JPA mapping. This does not substitute for PostgreSQL integration verification. Docker packaging is provided but needs a Docker runtime. Health endpoint checks database connectivity; it will report unavailable if the database is down.

### PostgreSQL acceptance tests

CI starts PostgreSQL 17 and runs the same order, user-management and security tests against it. To run those locally, create a dedicated empty test database and set:

```sh
export YFC_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/yfc_test
export YFC_TEST_POSTGRES_USER=yfc
export YFC_TEST_POSTGRES_PASSWORD='your-test-database-password'
mvn verify
```

These tests write records; use a disposable test database, never production. Without YFC_TEST_POSTGRES_URL, PostgreSQL acceptance tests are explicitly skipped.

Local verification results are recorded in `../docs/verification.md`. The PostgreSQL acceptance suites have also passed against AWS Aurora using IAM. Docker image build remains unverified. H2 tests do not substitute for PostgreSQL verification.

Purchasing and stock: [workflow and API guide](../docs/purchasing-inventory.md).

Drivers and trips: [fleet and execution guide](../docs/trips-and-fleet.md).

Fuel verification and costs: [operating workflow guide](../docs/fuel-and-operating-expenses.md).

Trip incident reporting and resolution: [API guide](../docs/trip-incidents.md).
