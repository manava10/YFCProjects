# Drivers, compliance and trip execution

This backend milestone connects customer order items to owned trucks, drivers and physical material sources. It uses the existing database and authentication settings; no new environment variables, API keys or external integrations are required.

## Operating rules

- One trip carries one order item from one source. Material sales use a YARD reservation or a DIRECT supplier purchase. Transport-only orders use TRANSPORT and never consume YFC stock. Multi-drop/multi-material trips are not implemented.
- A truck and driver each have at most one active trip, from allocation through delivery/cancellation. This includes future planned trips: there is no overlapping-time-window booking or automatic scheduling yet.
- Truck, driver and order must belong to the same active branch. Internal roles still have organization-wide permissions; branch filtering is not branch authorization.
- Planned weight must fit the truck capacity and the unclaimed source quantity. Weights are tonnes with up to 3 fractional digits. Rates and ordered-quantity customer billing remain unchanged.
- Gross minus tare determines net load and receipt weight. No floating-point arithmetic is used. Positive load cannot exceed the trip's planned quantity. Positive receipt cannot exceed recorded load; accepted quantity is between zero and received quantity. Any load/receipt/acceptance difference needs a reason. Zero receipt, received weight above load and post-dispatch returns need a future incident/correction workflow, rather than silent adjustment.
- Vehicle insurance, fitness, permit and pollution expiry dates and the driver's licence are checked for the planned date during allocation, then for today's date before loading and dispatch. Expiry on that date is valid. These checks validate recorded details, not document authenticity or government records.
- Actual arrival/delivery can be recorded even if a truck enters maintenance or a driver's licence/status changes after dispatch. These changes must not prevent recording what happened.
- Server timestamps and actors record execution. Operational dates use Asia/Kolkata. Backdating operational events is not supported.

## 1. Complete the truck profile

Existing POST `/api/v1/trucks` still creates the basic registration, capacity, tank capacity and mileage record. New and existing trucks default to INACTIVE until their operating profile is filled; migration does not invent compliance details.

PUT `/api/v1/trucks/{id}/compliance`

```json
{
  "branchId":"BRANCH_UUID",
  "ownerName":"YFC Transport",
  "truckType":"Tipper",
  "rcNumber":"RC-REFERENCE",
  "insuranceNumber":"POLICY-REFERENCE",
  "insuranceExpiry":"YYYY-MM-DD",
  "fitnessExpiry":"YYYY-MM-DD",
  "permitExpiry":"YYYY-MM-DD",
  "pollutionExpiry":"YYYY-MM-DD",
  "status":"ACTIVE"
}
```

Status is ACTIVE, INACTIVE or MAINTENANCE. Expired dates may be entered, but block allocation/dispatch. You can change operational status during a trip; it does not cancel the trip or release its crew. Branch changes are blocked while assigned.

GET `/api/v1/trucks/{id}` includes its current active trip reference; GET `/api/v1/trucks?page=0&size=20` lists profiles.

GET `/api/v1/fleet/compliance-alerts?days=30&page=0&size=20` lists trucks with missing details, expired documents or documents expiring within the inclusive window. GET `/api/v1/fleet/driver-compliance-alerts?days=30&page=0&size=20` lists drivers whose licence expiry is missing or falls on/before that window. Optional `branchId` filters either list; days must be 0–365. These are API listings, not scheduled email/WhatsApp alerts.

## 2. Register drivers

POST `/api/v1/drivers`

```json
{
  "branchId":"BRANCH_UUID",
  "name":"Driver name",
  "mobile":"9876543210",
  "licenseNumber":"DL-REFERENCE",
  "licenseExpiry":"YYYY-MM-DD",
  "emergencyContact":"Emergency contact name",
  "emergencyMobile":"9876543211",
  "active":true
}
```

Licence references are normalized to uppercase and must be unique. PUT `/api/v1/drivers/{id}` replaces the profile. GET `/api/v1/drivers/{id}` and GET `/api/v1/drivers?page=0&size=20` expose internal driver records. A driver's branch cannot change while assigned. There is no driver-user-account mapping, Aadhaar storage or document upload in this phase.

## 3. Allocate a trip

POST `/api/v1/trips`

```json
{
  "tripId":"CLIENT_GENERATED_UUID",
  "orderItemId":"ORDER_ITEM_UUID",
  "truckId":"TRUCK_UUID",
  "driverId":"DRIVER_UUID",
  "source":"YARD",
  "reservationId":"RESERVATION_UUID",
  "quantity":30.000,
  "plannedDate":"YYYY-MM-DD",
  "distanceKm":160.000
}
```

- YARD requires `reservationId` and no `purchaseId`. The reservation must belong to this order item.
- DIRECT requires `purchaseId` and no `reservationId`. The PO must be an OPEN direct purchase for this item, with enough unreceived, unclaimed quantity.
- TRANSPORT requires a transport-only order and neither source ID. Non-cancelled trip quantities cannot exceed the transport order item's ordered quantity. Once loaded, actual load replaces planned weight in this calculation.

Orders require a saved branch. Material sources also require a saved material. A source mismatch is rejected with 400 or 409; database ownership constraints can reject the request before service-level validation.

Reuse the trip UUID for network retries. First creation returns 201; an exact retry returns 200 with `replayed:true`. Reusing the ID with different details returns 409. Numeric scale differences such as 30 and 30.000 are normalized.

The trip stores truck registration and driver-name snapshots. `estimated_fuel_litres = distanceKm / recorded mileage`, rounded upward to 3 decimals, is a planning estimate only. Distance is manually entered for the intended journey; it is not calculated by a maps service. For 160 km at 3 km/litre, the estimate is 53.334 litres. No fuel consumption, tank balance, diesel expense or profit is posted.

## 4. Execute the trip

The state sequence is:

ALLOCATED → LOADING → LOADED → DISPATCHED → ARRIVED → UNLOADING → DELIVERED

Every POST below requires its own client-generated `eventId`. Exact retries are safe and return `replayed:true`, even if the trip has since advanced. Reuse of an event UUID for a different action, trip or payload returns 409. The response shows the current trip state, not a historical snapshot of the retried event.

POST `/api/v1/trips/{id}/start-loading`

```json
{"eventId":"EVENT_UUID"}
```

The planned date must have arrived. Crew status and compliance are checked again.

POST `/api/v1/trips/{id}/load`

```json
{"eventId":"EVENT_UUID","gross":42.000,"tare":12.000,"slipReference":"Plant weighbridge slip"}
```

Net loaded quantity is 30 tonnes. This records load weights without reducing yard stock yet.

POST `/api/v1/trips/{id}/dispatch`

```json
{"eventId":"EVENT_UUID"}
```

Dispatch rechecks the crew and compliance. For YARD, it issues actual loaded weight from the held reservation, reducing on-hand and reserved stock together. Unused planned capacity becomes available for another trip. For DIRECT, it holds actual loaded weight against the PO until delivery. For TRANSPORT there is no YFC stock effect.

Dispatch creates one linked order-dispatch weight record with received weight initially zero. Its internal reference is generated from the trip UUID. An exact event retry cannot issue stock or create this record twice.

POST `/api/v1/trips/{id}/arrive` and POST `/api/v1/trips/{id}/start-unloading`, each with a fresh `{"eventId":"EVENT_UUID"}`, record the next two states. These are staff-recorded events, not GPS observations.

POST `/api/v1/trips/{id}/deliver`

```json
{
  "eventId":"EVENT_UUID",
  "gross":41.000,
  "tare":12.000,
  "acceptedQuantity":28.000,
  "slipReference":"Customer weighbridge slip",
  "receiverName":"Site engineer",
  "proofReference":"Signed POD reference",
  "discrepancyReason":"Received 29 tonnes; one tonne rejected by receiver"
}
```

Delivery records positive received net weight, accepted weight and discrepancy reason, updates the existing order dispatch, then releases the truck and driver. DIRECT delivery also creates one linked purchase receipt, using the trip UUID as its receipt UUID; it never adds yard stock. Yard-issued weight stays issued even if less is received/accepted; losses and returns are not silently restored to stock.

The proof field records a staff-supplied document/reference. It does not upload a file, verify a signature, generate a QR code or establish a customer portal approval. Sales billable quantity still equals the ordered quantity.

## 5. Cancel before dispatch

POST `/api/v1/trips/{id}/cancel`

```json
{"eventId":"EVENT_UUID","reason":"Truck unavailable; allocate a replacement"}
```

Cancellation is allowed only in ALLOCATED, LOADING or LOADED. It releases truck/driver assignment and the trip's source hold. It preserves the underlying yard reservation, which can subsequently be released through the inventory API. A replacement uses a new trip UUID.

After DISPATCHED, cancellation is rejected. Returns, breakdown reassignment, failed delivery, crew substitutions and approved weight corrections require a later incident/return workflow. There is no automatic inventory restoration.

## Integration and compatibility

- A reservation with a claimed trip cannot be released. Standalone stock issues can use only its unclaimed balance.
- A direct PO with a CLAIMED or IN_TRANSIT trip cannot be closed or receive a new standalone receipt; use the trip delivery endpoint. Existing exact receipt replays are still safe.
- An order item moves permanently to TRIP fulfillment mode when its first trip is successfully allocated, even if later cancelled. The old manual dispatch endpoint rejects that item.
- Items with pre-existing legacy dispatch records cannot enter the trip workflow. No records are automatically converted or erased; historical reconciliation/migration needs a separate controlled process.
- Inventory issued manually before this phase is not automatically reissued or associated with a truck. Trip allocation can use only the source's remaining quantity.
- No supplier acceptance portal, private file storage, driver mobile access, GPS, notification provider, expense posting or financial order closing is added here.

## Read access and transaction safety

GET `/api/v1/trips/{id}` returns weights, source, snapshots, estimated fuel, state and actor/timestamp event history. GET `/api/v1/trips?page=0&size=20` lists trips. List size is 1–100; persisted profile/trip fields use lower-case snake_case.

SUPER_ADMIN and OPERATIONS can write trips and manage truck/driver profiles. ACCOUNTS can read trips but cannot manage them or read the private fleet/driver profile endpoints. CUSTOMER, SUPPLIER and DRIVER accounts cannot use these internal endpoints. All writes require the existing authenticated session and CSRF token.

Allocation/state updates, crew assignments, stock effects, dispatch records and audit entries share one transaction. Lock order is parent sales order → trip when existing → truck → driver → supply record/yard. Order and yard locks serialize sourcing changes; unique truck/driver assignment rows protect against concurrent double booking. Composite foreign keys protect source ownership and assignment identity. There are no automatic retries of business writes with a newly generated UUID.

For staff-submitted fuel receipts, independent review and approved operating-cost summaries linked to these trips, see [fuel and operating expenses](fuel-and-operating-expenses.md). Trip execution itself does not create fuel receipts or expenses automatically.

Trip incident reports and audited resolutions are now available; see [trip incidents](trip-incidents.md). They do not change operational weights or financial amounts.

Accepted-material customer returns into YFC yards now use an independent approval workflow; see [returns and stock adjustments](returns-and-stock-adjustments.md). Delivery history and billable ordered quantities stay unchanged.
