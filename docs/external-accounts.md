# External account requests

A customer, supplier or driver fills in their own details. The request stays pending until a super administrator or an operations user approves or disapproves it. Approval opens their business record and a login. Until then they cannot sign in. After approval they can read only the records linked to that login.

No email is sent. The person uses the username and password they chose, once the request is approved.

## What they submit

POST `/api/v1/account-applications` is anonymous and still requires the CSRF token.

Every request needs `applicationId`, `role` (`CUSTOMER`, `SUPPLIER` or `DRIVER`), `username`, `password` (12 to 72 characters), `name` and `mobile`.

- Customer: GSTIN and address are optional.
- Supplier: GSTIN and address are required. Email is optional. Approval also opens one plant called Main plant at that address.
- Driver: licence number, licence expiry, emergency contact and emergency mobile are required. The approver chooses the branch.

Usernames are stored in lowercase. A pending or approved username cannot be reused. Disapproval frees it so the person can apply again.

## Review

GET `/api/v1/account-applications?status=PENDING` is limited to SUPER_ADMIN and OPERATIONS. Approve with POST `/api/v1/account-applications/{id}/approve` and disapprove with `/reject`:

```json
{"eventId":"CLIENT_UUID","reason":"Details checked against the enquiry","branchId":"REQUIRED_FOR_A_DRIVER"}
```

`branchId` is required only when approving a driver, and that branch must be active.

## What they can see

GET `/api/v1/portal` after login:

- Customer: own profile, orders, trips on those orders, and sales bills.
- Supplier: own profile, plants, purchase orders and supplier bills.
- Driver: own profile, including branch and licence, and trips assigned to them.

They cannot open the internal staff screens. They cannot change orders, trips, stock or bills from this portal.
