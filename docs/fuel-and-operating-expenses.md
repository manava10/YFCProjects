# Fuel verification and operating expenses

This phase adds independent review of fuel receipts and truck/trip operating expenses. No new environment variables, API keys or AWS services are required. It uses existing session authentication and CSRF protection.

## What the amounts mean

- Fuel cost is purchased litres × actual receipt price, rounded HALF_UP to 2 decimal places. All amounts are INR. Quantities use at most 3 decimals; diesel prices at most 2. The database amount precision was increased to preserve the supported input range.
- The selected diesel master price is copied into `referencePricePerLitre` at submission. A different actual receipt price requires `priceVarianceReason`. Later master corrections do not overwrite that snapshot or the receipt cost.
- Purchased fuel is not measured consumption. There is no automatic current tank balance, actual mileage, fuel efficiency, cost-per-kilometre or profit calculation. A per-fill capacity check does not establish how much fuel was already in the tank.
- Trip cost summaries represent approved costs explicitly assigned to that trip. A whole fuel receipt is assigned to one trip or remains truck-only; splitting fuel across trips/valuing actual consumption remains future work.
- Expenses are receipt/voucher amounts without GST calculation or input-tax-credit treatment. These are operational approvals, not cash/bank payments, invoices, balanced journals or posted accounts.
- Customer ordered-quantity billing, quotations and material purchases are unaffected.

## Permissions and review separation

SUPER_ADMIN, OPERATIONS and ACCOUNTS can submit records, revise pending/rejected records and read internal operating data. Only SUPER_ADMIN and ACCOUNTS can approve/reject. A reviewer must be a different username from the current revision's submitter, including when the submitter is SUPER_ADMIN. Revising makes the revising user the new submitter.

If you currently have only the initial administrator, use existing POST `/api/v1/users` to create a separate ACCOUNTS user; see [user administration](../backend/README.md). Do not put that user's password into the backend environment file. No shared default reviewer account is created.

CUSTOMER, SUPPLIER and DRIVER accounts cannot access these internal endpoints. Staff record receipts on behalf of drivers until driver-to-account ownership is implemented. Branch-specific user access remains a later milestone.

All writes require CSRF and authenticated sessions. Actual account authentication remains database-backed; no additional login method is introduced.

## 1. Submit a fuel receipt

POST `/api/v1/fuel/entries`

```json
{
  "entryId":"CLIENT_GENERATED_UUID",
  "truckId":"TRUCK_UUID",
  "rateId":"DIESEL_RATE_UUID",
  "tripId":"OPTIONAL_TRIP_UUID",
  "receiptReference":"PUMP-A/SLIP-123",
  "filledOn":"YYYY-MM-DD",
  "quantityLitres":100.000,
  "actualPricePerLitre":92.00,
  "odometer":123456.7,
  "filledAt":"YYYY-MM-DDTHH:MM:SS+05:30",
  "receiptEvidence":"Pump receipt document/reference",
  "priceVarianceReason":"Actual pump receipt differs from the published reference rate"
}
```

Omit `tripId` for truck-only fuel. If linked, the truck must match the trip. The actual timestamp must fall between trip allocation and delivery, or between allocation and now for an ongoing trip. A fill before allocation or after delivery can be recorded truck-only; it is not automatically attributed to that trip. Cancelled trips cannot receive new valid costs or approvals. Costs already approved before cancellation remain recorded; cancellation does not reverse them.

The rate must be effective by `filledOn`. Dates cannot be future, and `filledAt` must match `filledOn` in Asia/Kolkata. Timestamps are normalized to microseconds for consistent PostgreSQL/JDBC precision. Quantity cannot exceed the truck's tank capacity; an amount rounding to zero is rejected.

`odometer`, `filledAt` and evidence remain optional at initial submission for compatibility with the older pending-only API, but all are required before approval. An actual/reference price difference requires a reason even at new submission. This is an intentional validation addition to the older endpoint.

Always provide `entryId` for retry protection. First creation returns 201; an exact retry returns 200 with `replayed:true`; conflicting UUID reuse returns 409. If omitted, the server generates an ID, and a repeated request is a new submission subject to unique receipt-reference checks. Fuel receipt references remain globally unique within fuel records; include the pump/vendor prefix when receipt numbers can repeat.

## 2. Approve or reject

POST `/api/v1/fuel/entries/{id}/approve` or `/reject`

```json
{"eventId":"CLIENT_GENERATED_UUID","revision":1,"reason":"Receipt and odometer independently verified"}
```

The current pending revision moves to APPROVED or REJECTED. Approval requires evidence, actual timestamp and odometer, valid trip/date/quantity checks, and an independent reviewer. Rejection requires a reason but can record an existing submission as rejected even if its trip has since been cancelled.

Approval serializes on the truck and compares the receipt to other APPROVED fuel entries:

- Its odometer cannot be below an earlier approved reading.
- It cannot be above a later approved reading, including for a backdated receipt.
- Equal timestamps cannot have conflicting approved readings.
- Equal readings across different fills are allowed; they do not imply consumption or a full-tank measurement.

Rejected/pending readings do not affect approved chronology. Odometer replacements/resets require a future controlled workflow; a decreasing reading is not silently accepted.

Exact event retries are safe. Reusing an event ID for another decision, record, revision or reason returns 409. Concurrent conflicting decisions have one winner. New decisions against reviewed or stale revisions return 409.

## 3. Revise a pending or rejected fuel receipt

POST `/api/v1/fuel/entries/{id}/revisions`

```json
{
  "eventId":"CLIENT_GENERATED_UUID",
  "revision":1,
  "reason":"Corrected from the original receipt",
  "rateId":"DIESEL_RATE_UUID",
  "filledOn":"YYYY-MM-DD",
  "quantityLitres":95.000,
  "actualPricePerLitre":92.00,
  "odometer":123456.7,
  "filledAt":"YYYY-MM-DDTHH:MM:SS+05:30",
  "receiptEvidence":"Verified receipt reference",
  "priceVarianceReason":"Receipt price differs from selected reference rate"
}
```

This stores the complete previous record in an immutable event snapshot, increments revision and resets to PENDING_VERIFICATION with a new submitter. Rejected decisions remain in history. The currently selected master price is snapshotted for the revised submission.

Truck, trip and receipt reference cannot change through revisions. A genuinely different receipt uses a new submission; reject the incorrect pending record. APPROVED records cannot be edited/rejected/revised through these APIs. Financial reversals and approved-record correction workflows are not implemented.

Existing legacy fuel rows are preserved as PENDING_VERIFICATION, revision 1, with submitter LEGACY_IMPORT. Their previous amounts/actual prices remain unchanged; the existing selected rate is copied as their reference. Missing evidence/timestamps/odometers are not invented. Use a documented revision with actual information before approving them. Legacy records are not automatically linked to trips.

## 4. Correct a published diesel rate

Existing POST `/api/v1/fuel/rates` continues to create pump/location/effective-date records. SUPER_ADMIN can now POST `/api/v1/fuel/rates/{id}/corrections`:

```json
{"eventId":"CLIENT_GENERATED_UUID","expectedPrice":91.50,"newPrice":93.20,"reason":"Pump corrected today's published price"}
```

The current master price is updated while old/new price, reason, actor and time are retained in a correction record. The expected price protects against concurrent/stale corrections; a losing writer receives 409 and must reload. An exact correction retry does not duplicate history. A correction that does not change price is rejected.

GET `/api/v1/fuel/rates/{id}/history` returns the correction history. Receipt amounts and submitted reference snapshots never change automatically. This is an audited correction mechanism, replacing the earlier append-only-master limitation; effective dates/pump identity are not edited by this endpoint.

## 5. Submit operating expenses

POST `/api/v1/expenses`

```json
{
  "expenseId":"CLIENT_GENERATED_UUID",
  "truckId":"TRUCK_UUID",
  "tripId":"OPTIONAL_TRIP_UUID",
  "category":"TOLL",
  "occurredAt":"YYYY-MM-DDTHH:MM:SS+05:30",
  "amount":450.50,
  "description":"Actual toll for the trip",
  "receiptReference":"TOLL/REFERENCE-123",
  "receiptEvidence":"Toll receipt / internal voucher reference"
}
```

Categories are TOLL, DRIVER_ALLOWANCE, PARKING, TYRE, MAINTENANCE, REPAIR and OTHER. TOLL, DRIVER_ALLOWANCE and PARKING require a trip. Other categories can be truck-only. All trip links enforce matching truck and occurrence window. Future occurrences and nonpositive/excess-precision amounts are rejected.

Loading, unloading and royalty are excluded because your agreed business rules assign them to customers/suppliers. DIESEL is excluded to keep fuel receipts in their dedicated workflow. Expense receipt references are unique within expenses; IDs provide exact creation retry protection as for fuel.

Approval/rejection endpoints are POST `/api/v1/expenses/{id}/approve` and `/reject`, using the same decision body and independent-review rule. Evidence is required for approval; a cash allowance can use an independently verified internal voucher reference. Recording an allowance is not proof of cash/bank settlement or an advance-accounting entry.

POST `/api/v1/expenses/{id}/revisions` accepts:

```json
{
  "eventId":"CLIENT_GENERATED_UUID",
  "revision":1,
  "reason":"Corrected receipt amount",
  "category":"REPAIR",
  "occurredAt":"YYYY-MM-DDTHH:MM:SS+05:30",
  "amount":1200.50,
  "description":"Workshop repair",
  "receiptEvidence":"Corrected workshop receipt reference"
}
```

The same history/current-revision/approved-immutability rules apply. Truck, trip and receipt reference remain fixed.

## 6. Inspect records and trip costs

- GET `/api/v1/fuel/entries/{id}` and GET `/api/v1/expenses/{id}` expose actual current status, revision, submitter and review details.
- GET `/api/v1/fuel/entries?page=0&size=20` and GET `/api/v1/expenses?page=0&size=20` list records. Page size is 1–100, ordered by UUID. No public driver dashboard or status-filtered review UI is introduced.
- GET `/api/v1/fuel/entries/{id}/history` and GET `/api/v1/expenses/{id}/history` expose decisions/revisions with previous snapshots. An exact retry returns the current record, rather than replaying the old state.
- GET `/api/v1/trips/{id}/operating-costs` returns approved fuel cost, approved other operating cost, their sum, approved fuel-purchased litres, the separate trip fuel estimate, and pending record count. `accountingPosted` is false. Pending/rejected amounts are excluded. This is not total trip profit: material cost, financial revenue recognition, inventory valuation and posted accounting are outside this summary.

Fuel/expense record fields use camelCase. Raw history/correction rows use lower-case snake_case. Event UUIDs must be reused for retries; a new UUID requests a new action.

## Consistency and remaining scope

Lock order is owning sales order/trip when linked → truck → operating record → selected rate when needed. Truck locks serialize approved odometer chronology across trip and truck-only receipts. Reviewed state, immutable history, audit entries and rate changes share the same transaction. Database constraints protect positive/exact fuel amounts, matching trip/truck ownership, review separation and approval evidence.

Receipt evidence is a reference only. This phase does not upload or secure files, validate their authenticity, integrate petrol pumps/FASTag, deliver notifications, measure tank levels, calculate actual consumed fuel, post journals, pay suppliers/drivers, or provide financial reversals. Private file storage and controlled incident/reversal workflows remain next steps.
