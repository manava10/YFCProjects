# Backend verification

Last checked: 19 October 2026.

## Fuel review, expenses, trips and core ERP workflows

`mvn verify` builds the executable Spring Boot JAR and runs the architecture and API acceptance tests.

Results: **127 local checks passed** (with 117 optional PostgreSQL cases skipped in the local-only run). All 25 affected fuel/expense PostgreSQL cases passed against AWS Aurora PostgreSQL 17.9 with IAM and verified TLS. The preceding trip milestone passed its complete 85-case Aurora regression run; those suites were not repeated in this phase.

AWS runtime: yfc and yfc_test databases exist. Eight Flyway migrations applied to the yfc application database; the initial administrator was initialized. The backend is listening on localhost:8080. Health UP, administrator login, SUPER_ADMIN role, authenticated RFQ/inventory/trip/truck/driver/compliance/fuel/expense APIs and logout were verified over HTTP without displaying credentials.

IAM checks verify fresh token generation per physical connection, removal of temporary password properties, sanitized provider errors, rejection of credential overrides and mandatory certificate/hostname verification. The signed-in default CLI session is reused through a credential-process provider; no pasted tokens or long-lived access keys are saved in the application environment.

Coverage:

- Trip incident reports and immutable resolutions, original report retention, audit entries and exact retries.
- Incident occurrence windows, cancelled trips, parent ownership, permissions/CSRF and concurrent report/resolution conflicts.

- Independent fuel/expense approval and rejection, immutable approved records and versioned pending/rejected corrections.
- Receipt evidence, actual/reference diesel price separation, exact rounding and preserved legacy fuel amounts.
- Per-fill tank limits, actual date/timestamp agreement and approved odometer chronology including backdated receipts.
- Concurrent review conflicts, incompatible odometer approvals and stale diesel-rate correction races.
- Truck/trip ownership and occurrence windows; approved-only trip cost summaries distinguish purchased fuel from estimates and consumption.

- Driver profiles, vehicle compliance/expiry listing, active branch ownership and exclusive truck/driver assignment.
- Yard/direct/transport-only trip execution, gross/tare/net weights, recorded proof references and ordered-quantity billing.
- Stock issues only at dispatch, direct receipts only at delivery, protected source holds and pre-dispatch cancellation.
- Exact allocation/event retries, cross-action UUID rejection, legacy/trip separation and rollback of failed allocations.
- Concurrent truck/driver booking across separate orders and concurrent state retries; post-dispatch status changes preserve actual delivery recording.

- Yard and direct supplier purchasing, immutable receipts with loaded/received/accepted weights, partial close/cancellation and replacement sourcing.
- Exact purchase rounding, receipt/issue idempotency, nonnegative stock and immutable stock movements.
- Reservation/issue/release lifecycle; no double allocation between direct purchasing and yard stock.
- Concurrent receipt retries, reservations against one order, competing orders against one yard and direct-versus-yard allocation races.
- Source/branch ownership, inactive records, role permissions, CSRF and database-level discrepancy constraints.

- Multi-material RFQs, immutable quotation revisions, recorded customer decisions and conversion into confirmed orders.
- Decimal component rounding, large exact amounts, expiry, required approval references and complete item pricing.
- Concurrent revision allocation and idempotent conversion into one order; audit rollback and role/CSRF restrictions.
- Per-item dispatch weights preserve ordered-quantity billing and accepted quotation snapshots.

- Branch and material administration; supplier companies/plants and plant-material associations.
- Multiple projects/sites per customer, scoped lists and parent ownership validation.
- Inactive records rejected for new linked orders/associations; historical records remain readable.
- Normalized order IDs, automatic branch/project derivation and preserved name/address snapshots.
- Composite database constraints reject cross-customer order reassignment.
- Upgrade from schema version 3 with existing customer/order data to version 9 preserves that order, its amount and dispatch item linkage, and legacy fuel amounts/status migration (H2).
- Master code uniqueness, input validation, permission restrictions and transactional audit rollback.
- Local helper script syntax checks and owner-only environment file permissions.

- Database migrations and Hibernate schema validation against H2 PostgreSQL compatibility mode.
- Module boundaries, database-backed login, rejected credentials and session ID rotation.
- Operations versus Accounts permissions; external roles denied internal records and user administration.
- Administrator-only user creation, account access changes and audit access.
- Disabled accounts and changed roles revoke existing sessions on their next request.
- BCrypt password storage and API responses without hashes.
- Administrator-assisted reset: hash-only storage, 15-minute expiry, replacement, single use, concurrent consumption and session revocation.
- Access changes invalidate outstanding reset credentials.
- Administrators cannot disable or demote their own accounts.
- CSRF checks for login, authenticated writes and unauthenticated reset completion.
- Logout invalidates its session and records the acting user.
- Audit entries include actors without password/reset secrets; failed duplicate user creation rolls back its audit entry.
- Existing ordered-quantity billing, dispatch duplication protection and historical fuel receipt costs remain covered.

CI configuration provisions PostgreSQL 17 and enables all nine PostgreSQL acceptance suites through YFC_TEST_POSTGRES_URL. No CI run has been dispatched or observed from this workspace.

## Remaining verification

Docker image build, email delivery, custom frontend UI, MFA, rate limiting and load tests have not been verified here. PostgreSQL migration, order workflows and account/reset concurrency were verified against the separate AWS test database. Reset tokens are delivered manually by an administrator; there is no configured email provider. Frontend user-management screens and external record ownership are later milestones.
