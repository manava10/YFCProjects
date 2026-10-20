# Purchasing and inventory workflow

This phase adds supplier purchasing, yard receipts, direct deliveries and stock allocation to the backend. No new environment variables or external services are required. All endpoints use the existing authenticated session and CSRF token.

## Scope and commercial rules

- A purchase order represents one material from one supplier plant, destined either for one YFC yard or one customer order item. Use separate purchase orders for additional materials or destinations. Purchase orders are issued immediately, immutable and identified by UUID; there is no purchase approval/revision workflow or printed PO numbering yet.
- Only active suppliers, plants, linked plant/material combinations, materials and destination branches/yards can be used for new purchases. The supplier, plant and material names are saved as historical snapshots.
- Quantities are tonnes, up to 9 integer and 3 fractional digits per request. Rates are INR per tonne, up to 10 integer and 2 fractional digits. Purchase subtotal is ordered purchase quantity × rate, rounded HALF_UP to 2 decimals. This is a commitment estimate excluding taxes, not a supplier invoice, payable or inventory valuation.
- Expected date must be today or later; receipt dates cannot be in the future. Date rules use Asia/Kolkata. Received dates may precede entry for historical documentation.
- Each receipt records loaded, physically received and accepted quantities. Accepted quantity may be zero but cannot exceed received quantity. Any difference requires a reason. Positive received weights count against the PO's ordered quantity, even when some material is rejected. Over-receipt is rejected.
- Only accepted yard receipt quantity enters usable stock. Rejected quantity is documented in the receipt but has no stock or quarantine balance. Direct-delivery receipts never enter yard stock. Existing POs can still be received if their supplier/material is later deactivated; yard receipts and issues require an active yard and branch.
- Customer billing stays based on ordered quantity. Purchase receipt weights and yard issues do not alter sales prices or invoice quantities. YFC/customer/supplier expense responsibilities remain as agreed; this phase creates no expense or accounting postings.

## 1. Create a yard

POST `/api/v1/yards`

```json
{"branchId":"BRANCH_UUID","code":"YARD01","name":"Main yard","address":"Yard address","kind":"OPERATING"}
```

GET `/api/v1/yards?page=0&size=20` lists yards. SUPER_ADMIN can PATCH `/api/v1/yards/{id}/status` with `{"active":false}`. Inactive yards retain history and allow reservation release, but block new purchasing, reservations, receipts and issues. Optional `kind` is `OPERATING` (default) or `QUARANTINE`. Purchases, reservations and issues require an operating yard. Yard kind cannot be changed after creation. Yard master edits and deletion are not included. Quantity-only transfers between operating yards may cross branches; quarantine involvement stays in the same branch. See [returns and stock adjustments](returns-and-stock-adjustments.md).

## 2. Issue a purchase order

POST `/api/v1/purchases`

```json
{
  "supplierId":"SUPPLIER_UUID",
  "plantId":"PLANT_UUID",
  "materialId":"MATERIAL_UUID",
  "destination":"YARD",
  "yardId":"YARD_UUID",
  "quantity":100.000,
  "rate":1000.55,
  "expectedDate":"YYYY-MM-DD",
  "terms":"Supplier pays royalty; customer pays loading/unloading"
}
```

For direct delivery, set `destination` to DIRECT, omit `yardId` and supply `orderItemId`. The saved material must match the sales order item. Orders with only free-text material, no branch, or TRANSPORT-only business type cannot receive inventory allocations. Legacy orders must already have saved material/branch data; no automatic guesses are made.

GET `/api/v1/purchases/{id}` includes receipt history and tax/currency metadata. GET `/api/v1/purchases?page=0&size=20` lists purchases.

## 3. Record receipts

POST `/api/v1/purchases/{id}/receipts`

```json
{
  "receiptId":"CLIENT_GENERATED_UUID",
  "receivedDate":"YYYY-MM-DD",
  "loadedQuantity":100.000,
  "receivedQuantity":99.000,
  "acceptedQuantity":98.000,
  "reference":"Supplier challan / weighbridge reference",
  "discrepancyReason":"One tonne rejected; loading/receiving difference recorded"
}
```

Generate the receipt UUID once and reuse it on network retries. An exact retry returns 200 with `replayed:true`; the first entry returns 201. Reusing the UUID with changed details returns 409. Reference and reason are normalized by trimming; blank optional reasons are treated as absent. The reference is a descriptive document reference, not a deduplication key: use the original receipt UUID for retries. A new UUID is a new receipt, subject to the PO quantity limit.

Partial receipts leave the PO OPEN. When cumulative received weight reaches ordered purchase quantity, the PO becomes RECEIVED. Receipts have server-recorded actor/time, are immutable, and create an accepted-quantity stock movement only for YARD receipts with positive accepted quantity.

POST `/api/v1/purchases/{id}/close` with `{"reason":"Delivery complete; balance will be sourced elsewhere"}` closes an OPEN PO. A PO with no receipts becomes CANCELLED; a partially received PO becomes CLOSED. Either prevents later receipts. Closing releases undelivered direct allocation; it does not erase receipts or stock.

## 4. Reserve yard stock for an order item

POST `/api/v1/stock-reservations`

```json
{"yardId":"YARD_UUID","orderItemId":"SALES_ORDER_ITEM_UUID","quantity":60.000}
```

Yard and order must belong to the same branch. Reservations require an active saved material and enough available stock. `available = on_hand - reserved`. Reserving changes reserved quantity, without changing on-hand stock.

GET `/api/v1/stock-reservations/{id}` includes `remaining_quantity`. GET `/api/v1/stock-reservations?page=0&size=20` lists reservations.

## 5. Issue or release reserved stock

POST `/api/v1/stock-reservations/{id}/issues`

```json
{"movementId":"CLIENT_GENERATED_UUID","quantity":30.000,"reference":"Yard gate / dispatch slip"}
```

Issue quantity cannot exceed the reservation's unissued, unreleased balance. Issuing reduces both on-hand and reserved quantity. Reuse the movement UUID for exact retries: first entry 201, exact replay 200, conflicting reuse 409. Issues remain committed to their order item even after leaving the yard.

POST `/api/v1/stock-reservations/{id}/release` with `{"reason":"Unissued balance no longer needed"}` releases the entire unissued balance. Already-issued stock cannot be released. Release affects reserved quantity only. This release endpoint does not delete stock, correct quantities or perform returns. Use the independent V11 adjustment workflow for supported physical corrections and customer returns.

These standalone inventory endpoints record staff actions and do not themselves create trips or update legacy dispatch weights. The [trip workflow](trips-and-fleet.md) now links reservations/direct purchases to trucks and drivers, issues yard stock atomically at dispatch, and records direct receipts at delivery. Use that workflow for new trip execution. A reservation with an allocated trip cannot be released, standalone issues can consume only unclaimed quantity, and a direct PO with active trips cannot be closed or receive new standalone receipts. Yard-issued quantity does not prove customer acceptance.

## 6. Inspect balances and fulfillment

- GET `/api/v1/yards/{yardId}/stock/{materialId}` returns exact `on_hand`, `reserved`, `available` quantities and unit. A known yard/material without movements returns zero.
- GET `/api/v1/yards/{yardId}/stock/{materialId}/movements?page=0&size=20` returns accepted receipts, issues and V11 approved return/count corrections with references, actors and timestamps. Reservations/releases are represented by reservation records and audit events, not physical stock movements.
- GET `/api/v1/order-items/{id}/fulfillment` returns ordered, allocated, unallocated, direct accepted and yard-issued quantities. Direct accepted and yard-issued are separate measures; the latter is not a delivery confirmation.

Allocation prevents sourcing the same order quantity twice:

- An OPEN direct PO commits its unreceived balance plus accepted receipts.
- A RECEIVED or CLOSED direct PO commits only accepted receipts. A CANCELLED PO commits zero.
- Yard reservations commit their original quantity minus released quantity; issued quantity remains allocated.
- Total direct and yard commitments cannot exceed the order item's ordered quantity.

Example: a direct PO for 100 tonnes receives 99 and accepts 98. While OPEN it commits 99 tonnes (98 accepted + 1 outstanding). Closing it commits 98 and allows a replacement purchase for 2 tonnes. Customer billable quantity stays 100.

## Consistency, permissions and limits

SUPER_ADMIN and OPERATIONS can write. ACCOUNTS has read access. CUSTOMER, SUPPLIER and DRIVER are denied these internal endpoints. Yard status changes require SUPER_ADMIN. Branch-specific user authorization remains a future milestone; current internal roles have organization-wide access.

Writes, balance updates and audit records share one transaction. Order allocations serialize on the owning order. Yard writes serialize on the yard, including first-stock-row creation; this prioritizes correctness for current expected volumes. Concurrent over-allocation/overdraw is rejected. Database constraints also protect nonnegative stock, receipt weights, material ownership and source relationships.

List endpoints return arrays, accept zero-based `page` and `size` (1–100), and sort newest first with UUID tie-breaking. Persisted inventory fields use lower-case snake_case; fulfillment summary fields use camelCase as shown. Audit and document UUIDs are stable; printed/statutory document numbering is not provided.

Valuation, tax invoices, purchase-specific document attachments and customer/driver portals remain later milestones. Standalone purchase-receipt reversal is available in V17: POST `/api/v1/purchase-receipts/{id}/reversals`, then independent ACCOUNTS review on `/api/v1/purchase-receipt-reversals/{id}/approve|reject`. Approval subtracts the receipt from the PO totals and, for yard receipts, removes accepted stock if it is still available. Trip delivery receipts, closed purchases, receipts claimed by a billing preparation, and receipts whose accepted quantity is already consumed by supplier returns cannot be reversed this way. Opening stock, supplier returns, non-resalable customer returns, approved reversals and same-branch transfers are available in V16. Quarantine yards and quantity-only cross-branch operating transfers are available in V19. Independently approved customer returns and count corrections are available in V11; private attachments for trips/incidents/fuel/expenses/stock openings, transfers and adjustments are available in V10/V19. Use the opening-stock workflow for genuine zero-balance yards; do not invent purchases to represent opening balances. There are no automatic external notifications or new environment settings.

## Controlled corrections and customer returns

Accepted customer material can return into same-branch yards after independent approval, including non-resalable returns that consume capacity only unless they enter a quarantine yard. Physical stock count increases/decreases, opening stock, same-branch and cross-branch operating transfers, supplier returns from yard purchases, approved reversals and standalone purchase-receipt reversals are also supported. See [returns and stock adjustments](returns-and-stock-adjustments.md). Valuation remains pending.
