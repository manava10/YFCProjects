# Recorded receipts, payments and refunds

V14 adds cash/bank account registration and four types of money voucher, each linked to one balanced journal. These endpoints record a movement that staff confirm from cash/bank evidence. They do not initiate, authorise, cancel or reverse an external bank transfer. No payment gateway, bank credentials or new environment variables are required.

Invoice allocation, outstanding-invoice ageing, reconciliation, tax/withholding, bank charges, multi-party batches and source-document attachments remain pending. Before-tax sales/purchase journals from READY billing preparations post independently of these vouchers. Customer/supplier party profiles from V13 and accountant-approved chart/periods from V12 are prerequisites.

## Permissions

SUPER_ADMIN registers cash/bank books. SUPER_ADMIN and ACCOUNTS submit/read/review vouchers. The journal's submitter cannot post/reject their own draft. Other roles are denied; writes require authenticated sessions and CSRF. Branch-scoped user authorization is still pending, so internal accounting staff can read the book across branches.

## 1. Register a cash/bank ledger account

POST `/api/v1/accounting/money-accounts`

```json
{
  "accountId":"FRESH_ASSET_ACCOUNT_UUID",
  "branchId":"ACTIVE_BRANCH_UUID",
  "kind":"BANK",
  "reference":"Accountant-confirmed bank/cash book designation"
}
```

Kinds are BANK or CASH. Use a fresh active ASSET account with no journal history, including draft/rejected lines. It cannot be a customer/supplier party account. Registered money accounts also cannot subsequently be bound to parties. Registration does not store bank routing details or connect to a bank.

The account, kind and owning branch are permanent through this API. Exact registration retries return 200 with `replayed:true`; first creation returns 201; changed details under the same account return 409. No opening balance is invented. The accountant may record independently posted opening/reclassification journals where appropriate. Chart account activation controls subsequent posting.

GET `/api/v1/accounting/money-accounts?page=0&size=20` lists registered accounts, chart code/name and activation. Size is 1–100.

All journal drafts/postings involving a registered cash/bank account must use its owning branch, including generic manual journals and reversals. Cross-branch settlement is outside this phase. Existing unregistered accounts are not automatically reclassified as cash/bank books.

## 2. Submit a recorded money voucher

POST `/api/v1/accounting/money-vouchers`

```json
{
  "voucherId":"CLIENT_UUID",
  "profileId":"PARTY_FINANCIAL_PROFILE_UUID",
  "moneyAccountId":"REGISTERED_CASH_OR_BANK_ACCOUNT_UUID",
  "periodId":"OPEN_PERIOD_UUID",
  "branchId":"MATCHING_BRANCH_UUID",
  "kind":"CUSTOMER_RECEIPT",
  "entryDate":"YYYY-MM-DD",
  "amount":1000.25,
  "reference":"UNIQUE_TREASURY_VOUCHER_OR_BANK_TRANSACTION_REFERENCE",
  "evidenceReference":"Verified bank confirmation or signed cash receipt reference"
}
```

| Kind | Party | Money account | Party account |
|---|---|---|---|
| CUSTOMER_RECEIPT | Customer | Debit | Credit |
| SUPPLIER_PAYMENT | Supplier | Credit | Debit |
| CUSTOMER_REFUND | Customer | Credit | Debit |
| SUPPLIER_REFUND | Supplier | Debit | Credit |

Amounts are INR, at least 0.01, up to 12 integer digits and 2 decimals. Entry date must be within the selected open period and not future. The registered money branch must match; all chart accounts and branch must be active. Reference and evidence are mandatory. A voucher covers one party, one money account and one full amount, with no tax/fee/split calculations.

A receipt/payment may create a customer credit or supplier advance if no invoice/bill has been booked. It does not create an invoice, allocate funds or mark an order paid. Commercial credit limits remain informational.

Creation atomically makes a DRAFT journal with the same UUID as the voucher, its fixed two lines, source record, reference claim and audit. No ledger balance changes yet. A voucher cannot adopt an already-existing generic journal UUID. Vouchers and their amounts/party/branch/evidence cannot be edited through these endpoints.

The first create returns 201. Exact UUID retries return 200 with `replayed:true`; changed data return 409. Decimal scale differences are equivalent. Reference identity strips surrounding whitespace and normalizes case; evidence strips surrounding whitespace. Stored display reference retains the first accepted casing.

## 3. Duplicate-reference protection

The normalized reference is unique per registered money account while a voucher is DRAFT or POSTED. Include enough context for cash voucher references to be unique; a supplier/pump receipt number alone may repeat. The normalized value must fit 300 characters.

A draft reserves its reference only, not cash or party credit. Rejection releases the claim, allowing a corrected voucher with a new UUID to use that reference. Existing rejected history is preserved. Posting failure leaves the draft/claim pending for review or rejection.

A POSTED voucher keeps its claim even after a contra journal reverses its accounting effect. Do not create a duplicate voucher to rebook the same transfer. Correct classification through documented accountant-approved journals; a linked replacement-voucher workflow is not implemented. Generic manual journal references are still descriptive and do not participate in voucher-reference deduplication.

## 4. Independent review and posting

POST `/api/v1/accounting/money-vouchers/{id}/post` or `/reject`

```json
{"eventId":"CLIENT_UUID","reason":"Amount, party and actual receipt/payment evidence independently verified"}
```

Review uses the existing V12 journal transaction and numbering. A different user is required. Exact event retries are safe; event IDs cannot change meaning or actor. Successful posting assigns the journal number. State, audit, number and any claim release commit/roll back together.

The generic `/accounting/journals/{id}/post|reject` endpoints work on linked voucher journals too. Cash/refund posting checks and reference release are in the central journal engine, so those routes preserve voucher invariants. Voucher status is derived from its journal rather than stored independently.

## 5. Cash-book and refund checks

**CASH accounts:** every posted journal, including manual journals and contra reversals, must leave every daily closing cash balance nonnegative. The engine recomputes dated posted movements plus the candidate entry. A backdated cash payment cannot consume funds needed by an already posted later payment. Simultaneous payments serialize under the accounting guard; they cannot both spend the same recorded funds.

This is a date-based book check, not physical cash verification or intraday transaction ordering. It includes posted amounts only. Incoming receipts/opening entries require real supporting evidence; the application cannot verify cash custody. Unregistered ASSET accounts are not treated as cash books.

**BANK accounts:** negative book balances are permitted; no real-time bank balance or overdraft agreement is available. The ledger is not a bank confirmation or reconciliation.

**Refund vouchers:** at posting, CUSTOMER_REFUND cannot exceed the branch's posted customer credit balance through that date. SUPPLIER_REFUND cannot exceed that branch's posted supplier debit/advance balance through that date. Wrong party types are rejected. Two competing refunds cannot consume the same recorded credit/advance. Incoming customer receipts and outgoing supplier payments can create advances and do not require invoices.

These refund checks apply to source vouchers. Authorised generic manual journals remain available for documented accounting adjustments; this software does not authorise external payments. Contra reversals are accounting corrections, not newly executed refunds. Cash-book checks still apply globally.

## 6. Read vouchers and bank/cash books

- GET `/api/v1/accounting/money-vouchers?page=0&size=20`.
- GET `/api/v1/accounting/money-vouchers/{id}` includes the journal, review events and reversal journals.
- GET `/api/v1/accounting/accounts/{moneyAccountId}/ledger?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=20` provides the existing posted bank/cash book, including manual entries.
- Existing V13 party ledgers and balance summaries automatically include posted money-voucher lines.

Source fields use snake_case. The detailed voucher exposes `effective_status`: DRAFT, POSTED, REJECTED, or REVERSED once a linked contra journal is actually POSTED. A pending/rejected reversal does not remove the original's posted effect. This is accounting status, not a bank execution status. `bank_transfer_executed_by_system:false` explicitly means the backend did not execute a bank transfer; staff may be recording a transfer already completed externally.

## 7. Reverse a posted voucher's ledger effect

POST `/api/v1/accounting/money-vouchers/{id}/reversals`

```json
{
  "journalId":"NEW_CLIENT_UUID",
  "periodId":"ELIGIBLE_OPEN_PERIOD_UUID",
  "entryDate":"YYYY-MM-DD",
  "reason":"Documented correction of the original accounting record",
  "reference":"Contra journal supporting reference"
}
```

This returns a new DRAFT contra journal, not a second money voucher. Independently review it with `/api/v1/accounting/journals/{newJournalId}/post|reject`. Existing reversal limits and open-period/date checks apply. The original journal/voucher stays preserved. A used cash receipt cannot be reversed if doing so makes a daily closing cash balance negative. Original bank/cash reference claims remain retained after posted reversals.

This does not refund a customer, recall a bank payment, reverse an invoice allocation or change stock. A real new refund transaction uses a new refund voucher with its actual reference and evidence.

## Remaining scope

Invoice/supplier-bill generation, allocations, tax/withholding, fees, payment requests/approval before execution, bank integrations, payment gateways, statement reconciliation, invoice ageing, batch/split settlements, bank-to-bank transfer source documents, voucher PDFs, private financial attachments and frontend screens remain pending. This phase records confirmed movements and their ledger effects only; it seeds no live money accounts or transactions.
