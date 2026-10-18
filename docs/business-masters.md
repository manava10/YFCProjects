# Business master records

## Scope

This milestone adds branches, customer projects and delivery sites, supplier companies and plants, a tonne-based material catalogue, and materials supplied by each plant. Each write records an audit event in the same transaction. Master records can be updated or marked inactive; there is no hard-delete endpoint for them.

Internal users currently have company-wide access according to their role. Branch records do not yet restrict users to a branch or provide separate branch accounting. Customer, supplier and driver portal access stays closed until ownership links are implemented.

## Permissions

| Record | Read | Create | Update/status |
|---|---|---|---|
| Branches | Admin, Operations, Accounts | Admin | Admin |
| Materials | Admin, Operations, Accounts | Admin | Admin |
| Customer projects and sites | Admin, Operations, Accounts | Admin, Operations | Admin, Operations |
| Suppliers | Admin, Operations, Accounts | Admin, Operations | Admin |
| Supplier plants and material links | Admin, Operations, Accounts | Admin, Operations | Admin, Operations |

Admin means SUPER_ADMIN. External roles cannot access these endpoints. Writes require a session and CSRF token. List endpoints accept `page` (zero-based) and `size` (1–100).

## Branches

`POST /api/v1/branches`

```json
{"code":"GGN","name":"Gurgaon Office","address":"Your actual office address","city":"Gurgaon","state":"Haryana","pincode":"122001"}
```

GET collection or `/{id}`; PUT `/{id}` with the same fields to update; PATCH `/{id}/status` with `{"active":false}` to deactivate. Codes are normalized uppercase and unique. No branches are seeded automatically; enter your actual offices.

## Material catalogue

`POST /api/v1/materials`

```json
{"code":"AGG20","name":"20 MM Aggregate","category":"AGGREGATES"}
```

Categories: AGGREGATES, SAND, DUST, ROAD_MATERIALS, OTHER. Unit is TONNE, consistent with current orders. Optional `hsnCode` accepts 4, 6 or 8 digits; classification and tax configuration need separate accountant approval. No tax percentage is assumed or seeded here.

GET collection or `/{id}`; PUT `/{id}` to update; PATCH `/{id}/status` to deactivate. Codes are unique after uppercase normalization. Conversion to bags, pieces, cubic metres or other units is future work.

## Customer projects and sites

A project belongs to one customer and one YFC branch. Its customer and branch cannot be reassigned through the current API. Project codes are unique within a customer; site codes are unique within a project.

`POST /api/v1/customers/{customerId}/projects`

```json
{"branchId":"BRANCH_UUID","code":"METRO01","name":"Metro Corridor","description":"Optional project information"}
```

GET collection or `/{projectId}`. PUT `/{projectId}` accepts name and optional description. PATCH `/{projectId}/status` accepts active.

`POST /api/v1/customers/{customerId}/projects/{projectId}/sites`

```json
{"code":"SITE_A","name":"Site A","address":"Actual delivery address","contactName":"Optional site contact","mobile":"9876543210"}
```

Contact name and mobile can be omitted. GET site collection or `/{siteId}`; PUT `/{siteId}` with the same fields to update; PATCH `/{siteId}/status` to deactivate. Requests with a project/site under the wrong parent return 404. Inactive branches/projects prevent creation of new dependent records; sites cannot be selected for new orders when their project, branch or own record is inactive.

## Suppliers and plants

`POST /api/v1/suppliers`

```json
{"code":"CRUSHER01","name":"Your supplier name","gstin":"SUPPLIER_GSTIN","mobile":"9876543210","address":"Supplier address","email":"accounts@example.com"}
```

Supplier GSTIN is mandatory under the current YFC business requirement and unique. GSTIN validation checks format, not government registration status. Customer GSTIN remains optional. Email is optional; bank details, PAN and supplier onboarding documents will be added with purchasing/accounting workflows.

GET collection or `/{supplierId}`. Admin can PUT the same supplier fields or PATCH `/{supplierId}/status`.

`POST /api/v1/suppliers/{supplierId}/plants`

```json
{"code":"ALWAR01","name":"Loading Plant","address":"Actual plant address","contactName":"Optional contact","mobile":"9876543210"}
```

GET plants or `/{plantId}`; PUT `/{plantId}` with the same fields; PATCH `/{plantId}/status`. Plant codes are unique within a supplier. Supplier identity is fixed after creation. Inactive suppliers prevent new plant writes; inactive suppliers/plants/materials cannot receive new plant-material links. Historical records remain readable. Active flags on child responses represent that child's own flag; an inactive parent still blocks new dependent use.

Link a material: PUT `/api/v1/suppliers/{supplierId}/plants/{plantId}/materials/{materialId}` (no body). A repeated request reuses the existing link. GET `/materials` lists links and current material status; DELETE `/materials/{materialId}` removes an availability association while keeping audit history. This association does not yet represent inventory, price or purchase commitments. Concurrent conflicting inserts can return 409 and may be retried.

## Orders using saved records

POST `/api/v1/orders`:

```json
{"customerId":"CUSTOMER_UUID","businessType":"MATERIAL_AND_TRANSPORT","materialId":"MATERIAL_UUID","deliverySiteId":"SITE_UUID","orderedQuantity":100,"unitRate":1200}
```

Project and branch are derived from the site. Optional explicit `branchId`/`projectId` must match its project. `projectId` requires `deliverySiteId`. A site cannot belong to another customer. Database composite foreign keys also protect these relationships.

Order responses include branchId, projectId, deliverySiteId and materialId. Material name and delivery address are stored as snapshots: editing their master records does not change an existing order. Billing quantity still equals ordered quantity regardless of loading/delivery weight differences.

Existing text-based order requests remain supported and are not automatically matched to catalogue records. A legacy order may have null master IDs. When providing both text and IDs, names/addresses must match. Stock, freight pricing, supplier purchase selection, multi-line quotes and invoicing remain later milestones.
