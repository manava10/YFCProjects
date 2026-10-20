# Backend verification

Last checked: 21 October 2026.

## Backend operational workflows

`mvn verify` builds the executable Spring Boot JAR and runs the architecture and API acceptance tests.

Results: **217 local checks passed** (207 optional PostgreSQL cases skipped in the local-only run). Sixteen PostgreSQL acceptance suites are present. On 21 October 2026 all **204 existing PostgreSQL cases passed** against yfc_test on AWS Aurora PostgreSQL 17.9 with IAM and verified TLS, which applied Flyway V16 to yfc_test. V17 correction and trip cases were then run against the same test database after the live yfc migrate.

AWS runtime: Flyway applied V15, V16 and V17 to the live yfc application database in one start. Health is UP. No live treasury accounts, money vouchers, financial profiles, chart accounts or journals were seeded, and no bank transfers were executed. Existing operational data was preserved.

IAM checks verify fresh token generation per physical connection, removal of temporary password properties, sanitized provider errors, rejection of credential overrides and mandatory certificate/hostname verification. The signed-in default CLI session is reused through a credential-process provider; no pasted tokens or long-lived access keys are saved in the application environment.

Coverage:

- Registered CASH/BANK asset accounts are branch-bound and exclusive of party accounts; existing journal history prevents registration.
- Customer receipts, supplier payments and both refund directions generate fixed balanced draft journals with evidence and exact retries.
- Independent posting, generic journal review consistency, normalized reference reservations and release on rejection.
- Source-linked contra reversals retain original records and reference claims; source status reflects posted reversals.
- Cash posting preserves every recorded daily closing balance, including backdated and concurrent payments; bank balances may be negative.
- Customer/supplier refunds are capped by posted branch balances through the entry date, including concurrent requests and generic posting.
- Voucher input precision, role/CSRF restrictions, account activation and prevention of adopting an existing manual journal.

- Dedicated customer ASSET/supplier LIABILITY links require unused active accounts, with one permanent account per party.
- Audited terms versions, optional customer credit limits, clearing limits and actor-bound exact retries.
- Party-ledger and summary balance signs, zero/credit/advance classifications, posted reversals and branch/date filters.
- Concurrent mapping and stale terms protection; historical settlement records remain available for inactive suppliers.

- Configurable ledger accounts and activation history, non-overlapping financial periods and controlled closure.
- Exact balanced manual journals, independent posting/rejection, immutable reviewed records and idempotent events.
- Contra reversals preserve originals; rejected reversals release claims and concurrent requests cannot duplicate a reversal.
- Atomic journal numbering/audit rollback; posted-only trial balance and repeatable-read account ledgers with correct opening/running/paged balances.

- Accepted-material customer returns into same-branch yards; pending plus approved returns cannot exceed trip accepted delivery.
- Independently approved physical stock corrections, stale-count checks, nonnegative/reserved stock and combined movement history.
- Rejection releases return capacity; original order quantities and delivery weights remain unchanged.
- Non-resalable customer returns consume capacity without increasing on-hand stock unless they enter a quarantine yard.
- Opening stock on a zero balance, same-branch transfers, supplier returns from yard purchases and approved reversals of usable stock.
- Quantity-only transfers between operating yards across branches; quarantine yards cannot receive purchases, reservations or issues.
- Independently reviewed odometer resets that allow a later lower reading after the reset timestamp.
- Split of approved truck-only fuel receipts onto trips, included in trip operating-cost summaries.
- Before-tax DRAFT journals from READY billing preparations onto party accounts, with independent journal posting.
- Approved fuel/expense reversals excluded from trip operating-cost summaries.
- Driver licence expiry listing and login lockout after five failed attempts in fifteen minutes.
- Post-dispatch crew reassignment before delivery, with exclusive truck/driver assignment retained.
- Independently reviewed delivered-trip weight corrections that rewrite dispatch weights without changing ordered-quantity billing.
- Standalone purchase-receipt reversal after independent approval, restoring accepted yard stock when still available.

- Private document upload/download, metadata without bytes, SHA-256 identity and immutable target links, including stock openings/transfers/adjustments.
- File size/type/name/image validation, target existence, staff-only access, CSRF, per-record limits and concurrent identical upload retries.

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
- Upgrade from schema version 3 with existing customer/order data to version 19 preserves that order, its amount and dispatch item linkage, and legacy fuel amounts/status migration (H2).
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

CI configuration provisions PostgreSQL 17 and enables all sixteen PostgreSQL acceptance suites through YFC_TEST_POSTGRES_URL. No CI run has been dispatched or observed from this workspace.

## Remaining verification

Docker image build, email delivery, custom frontend UI, MFA and load tests have not been verified here. Application login lockout is covered by local identity tests. PostgreSQL migration, order workflows and account/reset concurrency were verified against the separate AWS test database through V14. V15/V16 still need an Aurora apply and PostgreSQL acceptance rerun. Reset tokens are delivered manually by an administrator; there is no configured email provider. Frontend user-management screens and external record ownership are later milestones.
