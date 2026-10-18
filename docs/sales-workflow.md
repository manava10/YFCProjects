# RFQ, quotation and confirmed-order workflow

This milestone adds a multi-material sales workflow to the existing Spring Boot backend. No new environment variables, API keys or external services are needed. Existing AWS IAM authentication and database settings are used.

## Commercial rules

- One RFQ belongs to one customer, project, branch and delivery site. Project/branch are derived from the selected site; site ownership and active status are validated.
- An RFQ supports 1–100 distinct materials. Quantity is tonnes, with at most 3 decimal places. The current RFQ quantity limit is 9 integer digits per line.
- Quotation rates are INR per tonne, with at most 2 decimal places and 10 integer digits. Material and transport rates are separate. Fixed-price trip charges, discounts, other units and currency conversion are not implemented.
- Each component amount is `quantity × component rate`, rounded HALF_UP to 2 decimal places. Line subtotal is rounded material amount plus rounded transport amount. Document subtotal sums these line subtotals. No binary floating-point arithmetic is used for money.
- Example: 0.001 tonnes × INR 5.00 material rate rounds to INR 0.01; the same transport component also rounds to INR 0.01. The line subtotal is INR 0.02. This exact amount is copied into the order.
- MATERIAL RFQs require transport rate zero. TRANSPORT RFQs require material rate zero. MATERIAL_AND_TRANSPORT supports both components.
- Returned `taxTreatment` is EXCLUDED_PENDING_INVOICING. Subtotals exclude GST and are not tax invoices. Tax classification/rates and posting rules remain part of the accounting milestone; no tax rate is assumed here.
- Required delivery date is a scheduling request, not a GPS ETA or dispatch commitment.

## Permissions

SUPER_ADMIN and OPERATIONS can create RFQs, create quotation revisions, record sending/acceptance/rejection and convert accepted quotations. ACCOUNTS can read RFQs, quotations and orders but cannot change them. CUSTOMER, SUPPLIER and DRIVER accounts cannot use these internal endpoints until their portal ownership controls are implemented.

All writes require authenticated sessions, role permission and CSRF protection. State transitions and writes create audit events in the same database transaction.

## 1. Create an RFQ

POST `/api/v1/rfqs`

```json
{
  "customerId":"CUSTOMER_UUID",
  "deliverySiteId":"SITE_UUID",
  "businessType":"MATERIAL_AND_TRANSPORT",
  "requiredDate":"YYYY-MM-DD",
  "remarks":"Supply in agreed batches",
  "items":[
    {"materialId":"AGGREGATE_UUID","quantity":100.000},
    {"materialId":"SAND_UUID","quantity":50.000}
  ]
}
```

Use today or a future date. Dates are evaluated in Asia/Kolkata. The response includes RFQ item UUIDs, material-name snapshots and the agreed delivery address. RFQ content is immutable in this release; changed customer requirements need a new RFQ rather than silent alteration of existing quotations.

GET `/api/v1/rfqs/{id}` returns the RFQ. GET `/api/v1/rfqs?page=0&size=20` lists RFQs; maximum page size is 100.

## 2. Create a quotation revision

POST `/api/v1/rfqs/{rfqId}/quotations`

```json
{
  "validUntil":"YYYY-MM-DD",
  "paymentTerms":"Payment within 30 days",
  "deliveryTerms":"Customer pays loading/unloading; supplier pays royalty",
  "items":[
    {"rfqItemId":"RFQ_ITEM_1_UUID","materialRate":1200.00,"transportRate":200.00},
    {"rfqItemId":"RFQ_ITEM_2_UUID","materialRate":1000.00,"transportRate":100.00}
  ]
}
```

Use RFQ item UUIDs from the RFQ response, not material UUIDs. Every RFQ item must be priced exactly once. Missing, duplicate or unrelated item IDs are rejected. Negative rates, excess decimal precision and past validity dates are rejected.

The example quotation subtotal is INR 195,000.00: INR 170,000.00 material and INR 25,000.00 transport.

Revision numbers start at 1 and increase under an RFQ database lock. Every revision is an immutable pricing/terms snapshot. Creating a new draft does not withdraw an already-sent quotation; the customer can still accept the sent version until a replacement is marked sent.

GET `/api/v1/quotations/{id}` returns one revision. GET `/api/v1/rfqs/{rfqId}/quotations` returns revision history, newest first. UUIDs identify documents; these are not statutory invoice numbers.

## 3. Record the quotation as sent

POST `/api/v1/quotations/{id}/send` (no body)

Only the latest DRAFT revision can be marked SENT. Its validity must not have expired. Marking it sent supersedes earlier DRAFT/SENT versions. The RFQ becomes QUOTED.

This records a staff action after external delivery. It does not send email or WhatsApp messages, generate a PDF or notify customers automatically.

## 4. Record customer acceptance or rejection

POST `/api/v1/quotations/{id}/accept`

```json
{"customerContact":"Site engineer name","approvalReference":"Customer PO/email reference","note":"Ordered-quantity billing agreed"}
```

Contact and reference are mandatory; note is optional. The server records the staff username and timestamp, and supersedes outstanding draft alternatives. The RFQ becomes ACCEPTED. This is an internally recorded customer decision, not an electronic signature or customer-portal approval.

POST `/api/v1/quotations/{id}/reject`

```json
{"customerContact":"Site engineer name","approvalReference":"Call/email reference","reason":"Requested revised pricing"}
```

Reason is mandatory. The quotation becomes REJECTED and the RFQ returns to OPEN, allowing a new revision.

Only the current SENT quotation can receive a decision. Validity is inclusive through the end of validUntil in Asia/Kolkata. Expired quotations cannot be accepted. A quotation accepted while valid can still be converted later; validity does not erase an existing acceptance.

## 5. Convert to one order

POST `/api/v1/quotations/{id}/convert` (no body)

The quotation must be ACCEPTED. Referenced materials/site/project/branch must still be active. Conversion copies the accepted rates, rounded amounts, payment/delivery terms, requested date, material descriptions and delivery address into separate order items. Subsequent master-record edits do not change the agreed snapshots.

First successful conversion returns 201 and orderId. A retry for the same accepted quotation returns 200, the same orderId and created=false. An RFQ can produce only one order. RFQ row locking, a single transaction and database uniqueness constraints protect this rule even under concurrent requests.

RFQ status becomes CONVERTED; quotation status remains ACCEPTED as its historical decision, with convertedOrderId returned in quotation responses. GET `/api/v1/orders/{orderId}` returns the full order and lines.

Each line's billableQuantity equals orderedQuantity. For a 100-tonne line, loading 99 and receiving 98 does not automatically reduce the billable 100 tonnes.

## Dispatch weights

POST `/api/v1/orders/{orderId}/dispatches`

```json
{"reference":"UNIQUE-WEIGHBRIDGE-REF","orderItemId":"ORDER_ITEM_UUID","loadedQuantity":99.000,"receivedQuantity":98.000}
```

orderItemId is mandatory for multi-item orders. It can be omitted for a single-item order. Wrong-order item IDs are rejected, and composite database foreign keys also protect ownership. Weight totals are reported per item and for the order. Recorded weights do not recalculate agreed commercial amounts.

## Compatibility and limits

Migration V5 backfills every existing order with one order item and attaches existing dispatch records to that item. Existing text-based/manual single-item order creation remains available to Admin/Operations; the quotation acceptance requirement applies to the quotation-conversion route. These direct orders are not falsely marked quotation-approved.

For multi-item orders, header material is a summary label and header materialId is null. Header quantity sums tonnes across lines; item quantities/rates are authoritative. Historical legacy orders retain their original commercial total; where prior data had an undivided material-and-transport rate, it is not retroactively split into an invented freight allocation.

BOQ/PDF attachments, automated communication, customer portal approval, accepted-document amendments/cancellations, purchase orders, stock movements, truck/driver allocation, invoices and payment/accounting entries remain later milestones. Accepted records cannot be edited or silently re-approved through these APIs.
