# Billing preparation

This phase prepares reviewable sales and supplier billing records while YFC's accountant confirms GST rules. Marking a preparation READY means its commercial basis was independently checked. It does not issue a statutory invoice, invent tax amounts or allocate a payment. V16 can create a before-tax DRAFT journal from a READY preparation onto the party's ledger account; posting that journal still uses the existing independent journal review.

No new environment variables, AWS resources or bank credentials are required. Flyway migration V15 adds the preparation, line, source-claim and review-event tables. V16 links at most one current journal per preparation. Existing orders, receipts and journals are preserved. No live preparations or opening balances are seeded.

## Source rules

| Kind | Source ID | Quantity basis | Price source |
|---|---|---|---|
| SALES_ORDER | Confirmed sales order UUID | ORDERED only | Each saved order item's material and transport rates |
| PURCHASE_RECEIPT | Recorded purchase receipt UUID | Explicit LOADED, RECEIVED or ACCEPTED | Receipt's purchase-order rate |

A sales preparation includes every item on one order and the full ordered quantity. For 100 ordered, 99 loaded and 98 received, the preparation uses **100 tonnes**. Loaded/received quantities remain visible as facts captured when preparing the record. The draft can precede dispatch because billing is agreed on ordered quantity; preparing it does not assert delivery completion. Legacy combined material/freight rates remain combined exactly as saved; no split is invented. TRANSPORT-only orders retain their transport rate and zero material rate.

A supplier preparation covers one complete receipt, with a mandatory basis reason and evidence reference. A receipt with a pending or approved reversal cannot be billed; cancel or reject that reversal first. No supplier billing basis has been assumed. For example, loaded 9 / received 8 / accepted 7 at INR 1,000.55 prepares INR 9,004.95, 8,004.40 or 7,003.85 respectively, depending on the explicitly selected agreement. ACCEPTED cannot be chosen when accepted quantity is zero. Multi-receipt supplier invoices and partial quantity billing remain later work.

Prices cannot be submitted or overridden through this API. Material and transport amounts are rounded separately per line to two decimal places using HALF_UP; subtotal sums those rounded components with exact decimal arithmetic. Zero-priced source items remain zero-priced. Tax is pending, not inferred as zero tax, and there is no final payable total.

Preparations snapshot party name, source IDs, branch, source commercial terms, source quantities and rates. Later deliveries or master-name changes do not rewrite them. The source must have an active branch. Preparation dates cannot be future-dated or precede the order creation/receipt date. Historical preparation reads remain available when a branch is later disabled.

## Access and API

Only SUPER_ADMIN and ACCOUNTS may read or write these endpoints. Session login and CSRF are required for writes. Operations and external roles have no access.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/accounting/billing-preparations` | Create an immutable preparation |
| GET | `/api/v1/accounting/billing-preparations` | List summaries |
| GET | `/api/v1/accounting/billing-preparations/{id}` | Read header, source snapshot, lines and review history |
| POST | `/api/v1/accounting/billing-preparations/{id}/ready` | Independently verify the commercial draft |
| POST | `/api/v1/accounting/billing-preparations/{id}/reject` | Independently reject a draft |
| POST | `/api/v1/accounting/billing-preparations/{id}/cancel` | Independently cancel a draft or READY preparation |
| POST | `/api/v1/accounting/billing-preparations/{id}/journals` | Create a balanced before-tax DRAFT journal from a READY preparation |

List supports `page` (default 0), `size` (default 20, maximum 100), `kind`, `partyId` and `branchId`. Party IDs refer to customers/suppliers, not financial-profile IDs; use `kind` to identify the party type. Results use stable creation-time/UUID ordering. List responses omit the full source snapshot and line/event arrays.

Example sales request (replace UUIDs with actual IDs):

```json
{
  "preparationId": "89b9bbcb-0ab9-4a8f-962e-f737e708b579",
  "kind": "SALES_ORDER",
  "sourceId": "a8f04629-5016-439c-843c-276be0243d2a",
  "preparedOn": "2026-10-20",
  "quantityBasis": "ORDERED",
  "reference": "Customer PO reference",
  "evidenceReference": "Accepted quotation / customer agreement reference",
  "basisReason": "Customer agreed billing on ordered quantity"
}
```

For a supplier request, set `kind` to `PURCHASE_RECEIPT`, supply the receipt UUID and explicitly choose LOADED, RECEIVED or ACCEPTED with its agreement evidence. The reference is supporting commercial information, not an issued invoice number and not a supplier-invoice uniqueness key.

Review requests use a client-generated event UUID:

```json
{
  "eventId": "cd4accc9-45ea-4cc8-a52f-f751d7dc64ca",
  "reason": "Source quantities, rates and agreement independently checked"
}
```

Responses identify `document_type: BILLING_PREPARATION`, `currency: INR`, `unit: TONNE`, `tax_treatment: PENDING_ACCOUNTANT_CONFIGURATION` and `invoice_issued: false`. They expose `subtotal`, not a fabricated tax-inclusive total. `ledger_posted` is true only when the linked journal status is POSTED.

## Review, replacement and retries

DRAFT may become READY, REJECTED or CANCELLED. READY may only become CANCELLED. The creator cannot review or cancel their own preparation. READY requires an active branch; rejection/cancellation remain available when a branch is disabled. Reasons and actors are retained as immutable events.

Exactly one active DRAFT/READY preparation may claim an entire source. A duplicate preparation for the same source conflicts even if a different supplier quantity basis is selected. A database unique claim plus the shared accounting lock serializes competing requests. This is preparation protection; it is not yet invoice quantity allocation.

Reject or cancel a preparation to release the claim, then submit a corrected replacement with a new UUID. Original headers, lines and events remain readable. Editing or deleting a preparation is not supported. Retrying the original create request returns that original record, including its latest status, and never recreates its claim.

Create retries require the same UUID, input and actor; surrounding whitespace in reference/evidence/reason fields is normalized. Exact retries return 200 with `replayed: true`; new preparations return 201. Changed data or actor conflicts. Review event retries require the same event UUID, target, action, normalized reason and actor; a conflicting reuse returns 409. Review, claim changes and audit entries commit atomically.

## Ledger posting from READY preparations

POST `/api/v1/accounting/billing-preparations/{id}/journals`

```json
{
  "journalId": "CLIENT_GENERATED_UUID",
  "periodId": "OPEN_PERIOD_UUID",
  "entryDate": "YYYY-MM-DD",
  "counterAccountId": "INCOME_OR_EXPENSE_ACCOUNT_UUID",
  "description": "Before-tax sales posting from independently reviewed preparation",
  "reference": "Billing preparation supporting reference"
}
```

Only SUPER_ADMIN and ACCOUNTS may create this journal. The preparation must be READY, have a positive subtotal, an active branch and an existing party financial profile. The journal date cannot precede the preparation date or be outside the named open period. Zero-priced preparations cannot post.

The caller chooses the counter account. Sales require an active INCOME account; supplier receipts require an active EXPENSE account. The counter cannot be the party's dedicated account or a registered cash/bank account. The generated journal is a two-line INR draft:

- Sales: debit the customer ASSET, credit the chosen INCOME account for the preparation subtotal.
- Purchase: debit the chosen EXPENSE account, credit the supplier LIABILITY for the preparation subtotal.

Tax is not inferred. The draft uses the existing journal numbering and independent post/reject workflow. `ledger_posted` stays false until that journal is POSTED. A REJECTED linked journal may be replaced with a new journal UUID. A DRAFT or unreversed POSTED linked journal blocks cancellation of the preparation; reject it, or post a reversing journal, then cancel so a replacement preparation can claim the source. Exact journal-create retries reuse the same journal UUID, preparation, period, date, counter account, normalized description/reference and actor.

This is before-tax commercial posting, not a GST invoice, credit note or payment allocation.

## Next billing decisions

Before invoice issuance, confirm the seller legal entity and GST registrations, invoice series/financial-year policy, material and freight GST classification, place-of-supply treatment, reverse charge, rounding, credit/debit notes and any withholding. Those rules are not inferred by this phase. The accountant also needs to confirm revenue/purchase/tax account mappings and the supplier's quantity agreement.

Tax invoices, supplier invoice-number checks, multi-receipt matching, partial billing, payment allocation, ageing, credit notes and invoice PDFs remain to be implemented. READY preparation subtotals do not change party balances by themselves; only a posted linked journal does. Recorded money vouchers continue to work independently. No bank transfers are executed.
