# YFC Transport ERP

Spring Boot modular-monolith backend for YFC's material supply and owned-fleet business. Database-backed user administration, session authentication, password reset and audit history are implemented. Branches, customer projects/sites, supplier plants and material masters are also implemented. Frontend implementation has not started.

See [backend setup and API examples](backend/README.md) and [scope and next milestones](docs/backend-roadmap.md).

## Confirmed business rules

- YFC sells materials and provides transport using owned trucks.
- Fulfilment can use YFC stock or direct supplier delivery.
- Billing quantity is agreed ordered quantity: 100 ordered, 99 loaded, 98 received still means 100 billable.
- YFC bears diesel and toll costs. Customer bears loading/unloading; supplier bears royalty.
- Customer GSTIN is optional at enquiry/customer creation; transaction-specific tax validation remains to be implemented.
- Full accounting is planned; this initial backend does not yet post journal entries or issue tax invoices.

Local setup: [prepared environment and startup scripts](docs/local-environment.md). Business records: [API examples](docs/business-masters.md).

AWS Aurora IAM connection is configured and verified. See [AWS startup and authentication](docs/aws-database.md).

Sales workflow: [RFQ → quotation → accepted multi-item order](docs/sales-workflow.md).

Purchasing and stock: [workflow and API guide](docs/purchasing-inventory.md).

Drivers and trips: [fleet and execution guide](docs/trips-and-fleet.md).

Fuel verification and costs: [operating workflow guide](docs/fuel-and-operating-expenses.md).
