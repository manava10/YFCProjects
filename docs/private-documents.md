# Private document uploads

Staff can attach PDF, JPEG and PNG files to trips, trip incidents, fuel entries, operating expenses, stock adjustments, stock transfers and stock openings. Files and metadata are stored together in the existing application database by migration V10. There is no public file directory, public URL or bucket. No new environment variables, AWS resources or credentials are required.

This is the initial database-backed document store. S3, malware scanning, file previews, document replacement/deletion, retention policies and external portals remain future work. Include file data when backing up/restoring the database. The configured database TLS protects transport; encryption at rest follows the database's configuration. Files are not separately application-encrypted.

## Access and limits

SUPER_ADMIN, OPERATIONS and ACCOUNTS can upload, inspect and download. Other roles and anonymous users cannot access documents. Existing internal staff access covers all branches; branch-scoped staff authorization remains pending. All writes require session authentication and CSRF. Upload and download actions are audited with staff identity.

- Maximum file size: 2 MiB (2,097,152 bytes). Multipart request limit: 3 MiB.
- Maximum 30 documents per linked record, enforced while locking that record.
- Filename: at most 160 characters, with no paths, control characters or `..`.
- Allowed content types: `application/pdf`, `image/jpeg`, `image/png`; extension and declared type must match detected content.
- Images must decode and contain no more than 20 million pixels. PDF checks verify its header and trailing EOF marker; they are basic format checks, not complete PDF parsing or malware detection.

Downloads use attachment disposition, `nosniff`, a restrictive content policy and `Cache-Control: no-store`. Uploaded documents can contain untrusted content; these checks do not establish authenticity or certify files safe. Evidence remains subject to staff review.

## Upload

POST `/api/v1/documents` with multipart form fields:

| Field | Value |
|---|---|
| `documentId` | Client-generated UUID, reused for retries |
| `targetType` | `TRIP`, `INCIDENT`, `FUEL`, `EXPENSE`, `STOCK_ADJUSTMENT`, `STOCK_TRANSFER` or `STOCK_OPENING` |
| `targetId` | Existing record UUID of that type |
| `file` | One PDF/JPEG/PNG multipart file with its content type |

First creation returns 201. An exact retry returns 200 with `replayed:true`. The same ID with a different target, filename, type or file content returns 409. Content identity uses SHA-256. An identical retry still works after the record reaches its 30-document limit. Different IDs are separate documents even if the bytes match.

The target is immutable and must exist. Upload does not change trip status, billing, operational approval or resolution. Historical and reviewed records can receive additional supporting documents; this does not imply a reviewer approved the new attachment. Existing text evidence fields remain unchanged. To reference a stored file when submitting operational evidence, use `document:<UUID>` in the relevant evidence/reference field and ensure its target matches the operational record. Text reference fields are not automatically parsed or enforced as document links.

## Metadata and downloads

- GET `/api/v1/documents?targetType=EXPENSE&targetId=UUID`: up to the record's 30 documents, ordered by upload time and UUID. Unknown targets return 404.
- GET `/api/v1/documents/{id}`: metadata, including filename, type, byte size, SHA-256, target, uploader and upload time.
- GET `/api/v1/documents/{id}/content`: original file bytes as a private attachment; successful service access records a download audit event.

Metadata/list responses exclude file bytes and disable caching. No signed/public URL is returned. A download audit indicates that the application served the request; it cannot prove the user saved or read the complete file. Missing documents return 404. Invalid file inputs return 400, size violations return 413, and conflicting IDs/record limits return 409.

## Consistency

Metadata, file bytes and upload audit share one database transaction. Exact upload retries create no extra audit or copy. Public logistics/fuel module interfaces validate and lock target records; the document module does not read other modules' tables. Parent locks serialize uploads to the same record and enforce the document count. Existing parent records have no deletion API; future deletion workflows must account for attached documents. Document bytes are immutable through this API, and no download bypass or unauthenticated retrieval route exists.

Database storage keeps this first phase operational without provisioning S3. The per-file/per-record limits bound individual requests and records, but total storage still grows with usage. A later S3 migration needs private object access, coordinated backup/retention and an explicit data migration plan.
