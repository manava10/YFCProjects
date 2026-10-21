# YFC Transport ERP

Spring Boot modular-monolith backend for YFC's material supply and owned-fleet business. Database-backed user administration, session authentication, password reset and audit history are implemented. Branches, customer projects/sites, supplier plants and material masters are also implemented. Frontend work is separate and not part of this backend phase.

See [backend setup and API examples](backend/README.md) and [scope and next milestones](docs/backend-roadmap.md).

## Confirmed business rules

- YFC sells materials and provides transport using owned trucks.
- Fulfilment can use YFC stock or direct supplier delivery.
- Billing quantity is agreed ordered quantity: 100 ordered, 99 loaded, 98 received still means 100 billable.
- YFC bears diesel and toll costs. Customer bears loading/unloading; supplier bears royalty.
- Customer GSTIN is optional at enquiry/customer creation. On a bill, the accountant types the GST rate; it is not a fixed rate in the software. Supplier bills use the loaded quantity. There is no TDS and no opening-balance import.
- Balanced journal posting and recorded money vouchers are implemented. A READY bill can be given GST and then posted to the party ledger, including the tax the accountant entered. Invoice-to-payment allocation and government e-invoice filing remain pending.
- Customers, suppliers and drivers can request a login. An administrator or an operations user approves it. After that they see only their own records.

Local setup: [prepared environment and startup scripts](docs/local-environment.md). Business records: [API examples](docs/business-masters.md).

AWS Aurora IAM connection is configured and verified. See [AWS startup and authentication](docs/aws-database.md).

Sales workflow: [RFQ → quotation → accepted multi-item order](docs/sales-workflow.md).

Purchasing and stock: [workflow and API guide](docs/purchasing-inventory.md).

Drivers and trips: [fleet and execution guide](docs/trips-and-fleet.md).

Fuel verification and costs: [operating workflow guide](docs/fuel-and-operating-expenses.md).

Trip incident reporting and resolution: [API guide](docs/trip-incidents.md).

Private document uploads and downloads: [API guide](docs/private-documents.md).

Customer returns and controlled stock corrections: [API guide](docs/returns-and-stock-adjustments.md).

Accounting foundation and manual journals: [API guide](docs/accounting-foundation.md).

Customer/supplier financial profiles and party ledgers: [API guide](docs/party-financial-accounts.md).

Recorded receipts, payments and refunds: [API guide](docs/receipts-and-payments.md).

Sales and supplier billing preparation: [workflow and API guide](docs/billing-preparation.md).

Customer, supplier and driver account requests: [signup and approval](docs/external-accounts.md).
