# Trip incident reporting and resolution

Staff can record BREAKDOWN, ACCIDENT, DELAY, SHORTAGE, DAMAGE or OTHER incidents against a trip, then document their resolution. This is an operational register: it does not change trip status, dispatch/delivery weights, billing, inventory, payments or approved costs. Recording a shortage does not authorize a return or customer credit. Ordered-quantity billing remains unchanged.

No new environment variables or AWS resources are needed. Startup applies migration V9 to the selected application database. Evidence fields contain document/log references; private file uploads remain pending.

## Permissions

SUPER_ADMIN and OPERATIONS may report and resolve. ACCOUNTS can read. Customer, supplier and driver accounts have no access. Writes require session authentication and CSRF. Resolution records the acting staff user; unlike expense approval, the same staff member may report and resolve an operational incident. This is not financial approval. Branch-scoped staff permissions remain pending.

## Report

POST `/api/v1/trips/{tripId}/incidents`

```json
{
  "incidentId": "CLIENT_GENERATED_UUID",
  "category": "DELAY",
  "occurredAt": "2026-10-19T10:30:00+05:30",
  "description": "Road closure delayed the truck",
  "evidenceReference": "Dispatcher log / document reference"
}
```

Use the actual occurrence timestamp. It must fall between trip allocation and delivery (or now for an ongoing trip). A completed trip may receive a later report for an occurrence within that window. New reports against cancelled trips are rejected. Timestamps normalize to microseconds. Description and evidence are mandatory, limited to 2,000 and 500 characters respectively.

The first request returns 201; retry the same ID and fields to receive 200 with `replayed:true`. Conflicting reuse returns 409. A replay after resolution returns the current resolved record. The original report stays immutable; there is no edit/delete endpoint. Explain a reporting error in its resolution and create a separate correct report if needed.

## Read

- GET `/api/v1/trips/{tripId}/incidents?page=0&size=20` (size 1–100).
- GET `/api/v1/trips/{tripId}/incidents/{incidentId}`.

Records contain original report details and actor/time, plus resolution details and actor/time when resolved. Internal retry payloads are not returned. Wrong-trip record access returns 404. Lists are ordered by report time and ID; unknown trips return 404.

## Resolve

POST `/api/v1/trips/{tripId}/incidents/{incidentId}/resolve`

```json
{
  "eventId": "CLIENT_GENERATED_UUID",
  "resolution": "Road reopened; dispatcher confirmed the revised arrival time",
  "evidenceReference": "Dispatcher follow-up log reference"
}
```

An OPEN incident becomes RESOLVED. The original report and the resolution are retained. Both actions create audit entries in the same transaction. Exact event retries return 200; conflicting event reuse or a new resolution of an already resolved incident returns 409. Existing incidents can be resolved even after trip cancellation. There is no reopen/delete action.

Writes serialize through the owning order and trip, consistent with trip execution. Database uniqueness protects report IDs and resolution event IDs. Concurrent identical reports create one record; conflicting resolutions have one winner. This workflow does not block dispatch automatically, create emergency notifications, settle claims, manage physical returns, or approve financial adjustments.
