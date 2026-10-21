# Frontend browser regression checks

Verified on 21 October 2026 against the local Vite app with an isolated HTTP fixture server. No business database records were changed. These checks validate browser behaviour; they do not constitute a full live-backend acceptance test.

- Opened material A, entered an unsaved name, and switched to material B. B displayed its own name, code, category and HSN.
- Saved material B and inspected the fixture server's received request. It targeted B's UUID with B's code and category; A's unsaved name was absent.
- Found and fixed a related issue: after saving, material and branch edit panels kept their previous selected object. They now use the returned saved record. Verified the material heading and input both show the saved value.
- Simulated a 503 response for the yard directory. The form showed the server explanation and a retry button. Clicking retry loaded the yard options and cleared the error in both subscribed dropdowns.
- Simulated a 503 response for a document download. Its explanation appeared beside the document list; the download control became available again. No uncaught browser errors were reported during the final check.

The fixture server and temporary sample records are not part of the application or production authentication.

## Actual backend check — 21 October 2026

- The running YFC frontend is at http://127.0.0.1:5174. Port 5173 is currently serving a separate Placement Portal project.
- The actual backend at http://127.0.0.1:8080 reported health UP.
- Administrator sign-in and sign-out succeeded in the browser with configured credentials. Credentials were not added to source or reports.
- Checked navigation through sales, master records, purchasing, fleet, trips, operating expenses, stock, finance, staff and audit screens. No browser console errors were observed.
- Through the frontend proxy, all 23 read-only API checks succeeded (HTTP 200), including identity, customer/material/branch/supplier directories, enquiries, orders, yards, purchases, reservations, adjustments, fleet, fuel/expenses, accounting lists and users.
- Most business directories contain zero records. Customer form rendering works; no business records were created or updated. Complete sales-to-delivery writes, approvals and role-specific acceptance remain unverified against this database.
- Production frontend build and all six API utility tests passed.

## Frontend refinement review

Preserved the current public-site design and existing user changes. Fixed contact and site-editor forms reading React's `event.currentTarget` after an awaited request; these now capture the form before the request. Added explicit success feedback to shared internal forms, preserved password whitespace, and kept original permission errors visible if CSRF refresh fails.

Public tracking now has a loading state, distinguishes 404 from service failures, discards late responses from previous searches, clears stale results on edits and remembers successful lookups during the visit. Public contact and quote inputs match backend length/quantity limits. Added a main landmark, a keyboard skip link, Escape dismissal for the mobile menu and reduced-motion support. Document upload state resets by target record and validates type, non-empty content and the 2 MiB limit before uploading.

Validation: production build passes; 10 frontend tests pass, including new credential-preservation, decimal-precision, timestamp and failed-CSRF-refresh checks. Browser verification against the running backend confirmed a missing public reference is displayed correctly, contact/quantity constraints are present, and the mobile menu closes with Escape. At a 390px viewport, document width remained 390px. No business submissions were made; successful contact/site-editor writes and file uploads were not exercised against the live database in this review.
