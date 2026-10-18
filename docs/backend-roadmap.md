# Backend scope and implementation milestones

## Implemented foundation

Java 21, Spring Boot 4.1.1, Spring Modulith 2.1.1, PostgreSQL, JPA/Hibernate, Flyway, Spring Security, Maven and Docker packaging.

Business modules: branches, materials, suppliers (including plants/material associations), customers (including projects/sites), orders (including preliminary dispatch weight records), fleet, fuel, purchasing/inventory and logistics. Identity module supplies database-backed authentication and user administration. Audit module supplies persistent change history. Platform module supplies CSRF and API errors. Architecture tests verify module dependencies; cross-module access uses CustomerRegistry and TruckRegistry rather than repositories.

The legacy manual-order API creates one material/service line per order. The RFQ/quotation workflow now supports multi-item orders with separate material/freight pricing and ordered-quantity billing. All quantities are tonnes and prices INR per tonne. A TRANSPORT order uses the material field as the carried-material description; legacy combined rates are preserved; separate material/freight rates are supported through quotations. Subtotal is a commercial preview excluding tax, not an invoice or accounting entry. No invoice can be generated yet, so cumulative invoice allocation is deferred.

Fuel and operating expense submissions now support independent approval/rejection and audited revisions. Diesel master corrections retain old/new values and reasons; actual receipt prices and submitted reference snapshots remain independent. Trip summaries include approved allocated costs, not accounting profit. Consumption and tank balance are not inferred from fuel filled. Supplier and driver endpoints remain closed until record ownership is implemented.

## Next milestones

1. **Completed:** persistent users, role permissions, session login/logout, administrator-assisted password reset, account/session revocation and audit history. **Completed next:** branches and customer projects/sites. **Remaining:** external-record ownership, branch-scoped authorization, MFA and frontend account screens.
2. **Completed:** suppliers, plants, material catalogue and plant-material links; orders can use saved sites/materials with historical snapshots. **Completed:** multi-line RFQs, immutable quotation revisions, recorded customer decisions, one-time conversion and per-item dispatch weights. **Completed:** single-material supplier purchases to yards or order items, weight receipts, yard stock, reservations and issues. **Completed:** driver profiles, vehicle compliance, trip allocation and yard/direct dispatch integration. **Completed:** immutable trip incident reports and audited resolutions. **Next:** private documents, returns and accounting foundations.
3. **Completed:** yard inventory, stock movements, reservations, direct-delivery purchases and received/accepted weights. **Remaining:** opening balances, approved corrections/reversals, returns, transfers and valuation. See [purchasing and inventory](purchasing-inventory.md).
4. **Completed:** driver profiles, vehicle compliance checks/expiry listing, exclusive truck/driver allocation, controlled trip state transitions, pre-dispatch cancellation, weighbridge net weights, proof references and yard/direct stock integration. **Completed:** trip incident reporting and resolution ([guide](trip-incidents.md)). **Remaining:** uploaded delivery files, returns, approved weight corrections, post-dispatch reassignment and legacy dispatch reconciliation. See [trips and fleet](trips-and-fleet.md).
5. **Completed:** receipt evidence references, independent approval/rejection, pending/rejected revisions, audited pump rate corrections, chronological odometer checks, per-fill capacity checks, trip/truck operating expenses and approved-cost summaries. **Remaining:** private receipt file uploads, pump/FASTag integrations, measured consumption/tank balance, fuel allocations across trips, odometer resets and approved-cost reversals. See [fuel and operating expenses](fuel-and-operating-expenses.md).
6. Accountant-approved chart of accounts, opening balances, balanced journals, AP/AR, advances, bank reconciliation, invoice allocation, tax rules, inventory valuation, fixed assets and financial period closing.
7. Private S3 document workflows, customer/supplier/driver portals, integrations and operating dashboards.

## Before production

Configure MFA, HTTPS, branch/record permissions, authentication rate limits, shared sessions for multiple replicas, secrets management, backups with restore verification, structured monitoring and migration deployment controls. Production cookie settings are provided; infrastructure still needs configuration. Review dependencies and tax/accounting rules. Run PostgreSQL-backed integration tests and load testing against expected volumes. This is a development foundation, not a deployable complete ERP.
