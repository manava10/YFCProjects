# Customer returns and controlled stock adjustments

Migration V11 adds independently reviewed physical stock corrections and customer returns into YFC yards. V16 adds approved reversals, non-resalable customer returns, opening stock, same-branch transfers and supplier returns from yard purchases. No new environment variables or AWS services are required. Quantities are tonnes, with up to three decimal places. This is a stock-quantity workflow; it does not issue invoices, credit notes, payments, valuation entries or accounting journals.

## Roles and review

SUPER_ADMIN and OPERATIONS submit. SUPER_ADMIN and ACCOUNTS approve/reject. The reviewer must be a different username from the submitter, including for administrators. All three internal roles may read requests. CUSTOMER, SUPPLIER and DRIVER have no access. Writes require CSRF and authenticated sessions. Branch-scoped user permissions remain pending.

Requests start PENDING. Submission does not change usable stock. Approval applies exactly one movement and changes the request to APPROVED. Rejection changes it to REJECTED without stock movement. A request is immutable after submission; reject mistakes and submit a new ID. Reviewed requests cannot be reopened or edited through these endpoints. Every submission and successful decision is audited in the same transaction as its state/stock changes.

## 1. Return accepted customer material

POST `/api/v1/trips/{tripId}/returns`

```json
{
  "returnId":"CLIENT_GENERATED_UUID",
  "yardId":"RECEIVING_YARD_UUID",
  "quantity":2.500,
  "receivedOn":"YYYY-MM-DD",
  "resalable":true,
  "reason":"Customer returned unused material after delivery",
  "evidenceReference":"Signed return weighbridge slip / trip document reference"
}
```

This records material already physically received into the yard. `resalable:true` means it is confirmed suitable for resale. `resalable:false` still consumes trip return capacity so the same delivered tonnes cannot be returned twice, but approval does not increase on-hand stock and creates no RETURN movement. Keep pending receipts segregated until approval. The endpoint is not a return authorisation for goods still at the customer, and it does not manage return trucking, dedicated quarantine yards or disposal.

Rules:

- The source trip must be DELIVERED and belong to a material sale. TRANSPORT-only material is not YFC-owned inventory and cannot enter this return workflow.
- The destination yard must be active and belong to the source order's branch. Any eligible yard in that branch can receive the material; cross-branch returns need a later workflow.
- Material is derived from the trip's order item; callers cannot substitute another material.
- `receivedOn` cannot be before the delivery date in Asia/Kolkata or after today.
- Quantity must be positive and within the trip's accepted delivered quantity less all PENDING and APPROVED return requests.
- PENDING requests hold return capacity to prevent competing submissions from claiming the same delivered material. REJECTED requests release that capacity. APPROVED quantities continue consuming it.
- `resalable` is required and must be true or false. Reason and evidence are required. Previously rejected/unaccepted delivery quantities and legacy manual dispatches remain outside this phase. Yard supplier returns use the separate purchase-return workflow below.

Example: a trip has 30 tonnes loaded, 29 received and 28 accepted. Its lifetime PENDING + APPROVED customer returns cannot exceed **28 tonnes**. This cap uses accepted delivery, not the sales order quantity.

The first request returns 201. An exact retry with the same ID/fields returns 200 with `replayed:true`; conflicting reuse returns 409. Return IDs share the stock-adjustment ID namespace. The result has kind `CUSTOMER_RETURN`; review it using the stock-adjustment endpoints below.

Return approval of a resalable request increases on-hand stock by the returned quantity, with movement kind RETURN. Non-resalable approval consumes capacity only. Reserved stock is unchanged. The original order quantity, billable quantity, delivered/accepted weights, purchase receipt and historical dispatch remain unchanged. Original supply allocations are not reopened; replacement deliveries require a later controlled workflow. Any customer credit/refund must eventually use the accounting workflow.

## 2. Submit a physical stock-count correction

POST `/api/v1/stock-adjustments`

```json
{
  "adjustmentId":"CLIENT_GENERATED_UUID",
  "kind":"DECREASE",
  "yardId":"YARD_UUID",
  "materialId":"MATERIAL_UUID",
  "quantity":1.250,
  "expectedOnHand":100.000,
  "occurredOn":"YYYY-MM-DD",
  "reason":"Physical stock count found a shortage",
  "evidenceReference":"Signed physical stock count sheet"
}
```

Kinds: INCREASE or DECREASE. `quantity` is the positive delta to add/remove, not the final counted balance. `expectedOnHand` is the system's on-hand quantity observed when preparing the request. Future count dates, zero/negative quantities, excessive precision and blank reason/evidence are rejected. Yard, branch and material must be active.

Approval requires the current on-hand balance to still equal `expectedOnHand`. Otherwise it returns 409 and leaves the request PENDING: reject it, recount/reconcile and submit a new request. This checks the current quantity, not every intervening movement; equal balances may have different movement history. Reviewers must inspect the count evidence and movement ledger.

A decrease cannot make stock negative or reduce on-hand below reserved stock. It never releases reservations automatically. An increase can create a previously absent yard/material balance at approval. Opening stock for a yard/material with a zero on-hand balance uses the dedicated opening workflow below, not a count increase. This still does not value inventory.

Count dates and return receipt dates are evidence dates; actual movements post at approval time. This API does not reconstruct historical stock balances or backdate accounting periods.

## 3. Approve or reject

POST `/api/v1/stock-adjustments/{id}/approve` or `/reject`

```json
{
  "eventId":"CLIENT_GENERATED_UUID",
  "reason":"Physical receipt/count and evidence independently verified"
}
```

Approval rechecks active yard/branch/material and the stock constraints. Rejection is still possible if the yard/material was deactivated after submission. Exact retries with the same event ID, action, request, reason and reviewing user are safe. Conflicting event reuse, self-review and new decisions against reviewed requests return 409.

Use the same UUID for retries; a new event UUID is a new action. A successful approval creates exactly one immutable adjustment movement, with the decision event ID as movement ID. Concurrent count approvals against one old balance have one winner; later stale counts fail. Failed approvals roll back stock, movement, event and audit together.

## 4. Inspect requests and movements

- GET `/api/v1/stock-adjustments/{id}`: original request, status, submitter/reviewer and decision events.
- GET `/api/v1/stock-adjustments?page=0&size=20`: all requests.
- GET `/api/v1/stock-adjustments?tripId=UUID&page=0&size=20`: requests linked to a source trip. Page size is 1–100; an unknown trip filter returns an empty list.
- GET `/api/v1/yards/{yardId}/stock/{materialId}`: current on-hand, reserved and available stock.
- GET `/api/v1/yards/{yardId}/stock/{materialId}/movements`: combined receipts/issues, approved adjustment movements and V16 correction movements, preserving original movement records.

Inventory request fields returned by these APIs use the existing snake_case convention; `replayed` and the `events` collection are additional fields. Internal retry payloads are not exposed. Adjustment movement kinds are RETURN, ADJUST_IN and ADJUST_OUT. Correction movement kinds are OPENING, TRANSFER_OUT, TRANSFER_IN, SUPPLIER_RETURN and REVERSAL. New adjustment rows include `adjustment_id`; older receipt/issue rows have that field null.

Evidence remains a reference. Return evidence can point to a private document uploaded against the source trip. The private-document API does not yet accept STOCK_ADJUSTMENT as a target type.

## 5. Reverse an approved return or count

POST `/api/v1/stock-adjustments/{id}/reversals`

```json
{
  "reversalId":"CLIENT_GENERATED_UUID",
  "reason":"Approved return recorded against the wrong yard",
  "evidenceReference":"Signed reversal evidence"
}
```

Only an APPROVED adjustment or customer return can be reversed. One PENDING or APPROVED reversal may claim that source. OPERATIONS submit; ACCOUNTS approve or reject; the reviewer must be a different username. Approval reverses usable stock except for non-resalable customer returns, which reverse capacity only. Rejected reversal claims are released so a replacement can be submitted. Do not use a second count adjustment to erase an approved return.

GET `/api/v1/stock-reversals/{id}` and GET `/api/v1/stock-reversals?page=0&size=20` inspect requests. POST `/api/v1/stock-reversals/{id}/approve` or `/reject` with the same event payload as other reviews.

## 6. Record opening stock

POST `/api/v1/stock-openings`

```json
{
  "openingId":"CLIENT_GENERATED_UUID",
  "yardId":"YARD_UUID",
  "materialId":"MATERIAL_UUID",
  "quantity":50.000,
  "occurredOn":"YYYY-MM-DD",
  "reason":"Physical opening count before live purchasing",
  "evidenceReference":"Signed opening stock sheet"
}
```

The yard and material must be active. On-hand must be zero at submission and again at approval; if stock moved, reject the opening and use a count instead. One active claim exists per yard and material, including rejected-then-replaced openings after the claim is released. Approval increases on-hand with movement kind OPENING. This is a quantity opening, not a valued opening-balance journal.

GET `/api/v1/stock-openings/{id}` and GET `/api/v1/stock-openings?page=0&size=20`. Review with `/approve` or `/reject`.

## 7. Transfer stock between yards in the same branch

POST `/api/v1/stock-transfers`

```json
{
  "transferId":"CLIENT_GENERATED_UUID",
  "fromYardId":"SOURCE_YARD_UUID",
  "toYardId":"DESTINATION_YARD_UUID",
  "materialId":"MATERIAL_UUID",
  "quantity":5.000,
  "occurredOn":"YYYY-MM-DD",
  "reason":"Move usable stock to the receiving yard",
  "evidenceReference":"Internal transfer note"
}
```

Both yards must be active and belong to the same branch. Source available stock (on-hand minus reserved) must cover the quantity at submission and approval. The destination yard/material balance is created if absent. Approval writes TRANSFER_OUT at the source and TRANSFER_IN at the destination. Cross-branch transfers remain later work.

GET `/api/v1/stock-transfers/{id}` and GET `/api/v1/stock-transfers?page=0&size=20`. Review with `/approve` or `/reject`.

## 8. Return accepted yard purchases to the supplier

POST `/api/v1/purchases/{id}/supplier-returns`

```json
{
  "returnId":"CLIENT_GENERATED_UUID",
  "quantity":1.000,
  "occurredOn":"YYYY-MM-DD",
  "reason":"Supplier collected rejected/excess accepted material",
  "evidenceReference":"Supplier collection note"
}
```

The purchase must be a YARD purchase. Quantity cannot exceed remaining accepted receipts minus PENDING and APPROVED supplier returns, and cannot exceed available yard stock. Direct-delivery purchases are outside this workflow. Approval reduces on-hand with movement kind SUPPLIER_RETURN. It does not create a supplier credit note or purchase journal.

GET `/api/v1/supplier-returns/{id}` and GET `/api/v1/supplier-returns?page=0&size=20`. Review with `/approve` or `/reject`.

Submission, review, stock changes and audit records share one transaction. Exact create and event retries follow the same UUID/payload/actor rules as count adjustments.

## Remaining work

Dedicated quarantine yards, unaccepted-delivery returns, return transport, cross-branch transfers, legacy dispatch returns, delivery-weight corrections, replacement fulfilment and financial credit notes remain pending. A manual counter-adjustment must not be used to erase a return or bypass accounting: reverse the approved record instead.
