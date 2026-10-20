# Accounting foundation: chart, periods and manual journals

Migration V12 adds an INR double-entry ledger with an admin-managed chart, global non-overlapping financial periods, immutable manual drafts, independent posting/rejection, contra reversals, trial balance and account ledgers. No new environment variables or AWS services are required. No account codes, opening balances or tax rates are seeded: YFC's accountant must approve those decisions.

This is a generic manual ledger foundation. Fuel and expenses do not automatically post to it. V13 adds dedicated customer/supplier ledger links and balance views. V16 can create a before-tax DRAFT journal from a READY sales or supplier billing preparation onto the party's account; that journal still needs independent posting. Invoice/bill subledgers, tax calculations, payment allocation, inventory valuation, bank reconciliation, fixed assets and statutory financial statements remain pending.

## Roles and accounting book

SUPER_ADMIN manages chart accounts, account activation and periods, and closes periods. SUPER_ADMIN and ACCOUNTS can submit/read/review journals and read reports. A different username must post or reject each draft, including a reversal. OPERATIONS, CUSTOMER, SUPPLIER and DRIVER have no accounting access. Writes require authenticated sessions and CSRF.

There is one INR book and one period calendar shared by all branches. Each journal belongs to one active branch; its lines balance within that branch. Accounts are shared across branches. Staff branch-scoped authorization and cross-branch balancing entries are not implemented. Reports can filter by branch; omitting that filter includes all branches. A branch filter with no matching activity returns an empty/zero report.

## 1. Create the accountant-approved chart

POST `/api/v1/accounting/accounts`

```json
{"accountId":"CLIENT_UUID","code":"ACCOUNTANT_SELECTED_CODE","name":"Accountant-approved account name","type":"ASSET"}
```

Types: ASSET, LIABILITY, EQUITY, INCOME, EXPENSE. Codes are 2–30 letters/digits/underscore/hyphen, starting with a letter/digit, normalized to uppercase and unique. Names are required, at most 160 characters. The chart has individual posting accounts only; account groups, control-account rules, subledgers and statement mapping remain pending. Code/name/type are immutable through this API.

- GET `/api/v1/accounting/accounts?page=0&size=20`.
- GET `/api/v1/accounting/accounts/{id}` includes activation history.
- PATCH `/api/v1/accounting/accounts/{id}/status`:

```json
{"expectedActive":true,"active":false,"reason":"Account retired after reconciliation"}
```

Status changes preserve historical entries and record old/new state, reason and actor. The current target state is an idempotent no-op; otherwise the expected current state must match. This is a boolean status check, not a general record revision token. Inactive accounts block new drafts and posting, including reversals. Rejected drafts and historical reports remain accessible. Reactivation requires an explicit admin action.

## 2. Define financial periods

POST `/api/v1/accounting/periods`

```json
{"periodId":"CLIENT_UUID","code":"ACCOUNTANT_SELECTED_PERIOD","startsOn":"YYYY-MM-DD","endsOn":"YYYY-MM-DD"}
```

Dates are inclusive. Start must not follow end. No period can overlap any existing period, including a closed period. New periods start OPEN; future periods may be prepared, but future-dated journals are rejected. Choose the calendar/granularity with the accountant before creating periods; they cannot be edited/deleted/reopened through these APIs.

GET `/api/v1/accounting/periods?page=0&size=20`.

POST `/api/v1/accounting/periods/{id}/close`:

```json
{"eventId":"CLIENT_UUID","reason":"Period reconciled and approved for closing"}
```

Only an admin can close. The period must end before today (Asia/Kolkata) and contain no DRAFT journals. Post or reject all drafts first. Closure blocks new submissions and posting within that period. This is a posting lock, not automated year-end accounting: no income/expense close-out, retained-earnings transfer, tax filing or reconciliation is generated. Reversals of historical entries use a later eligible open period.

## 3. Submit an immutable journal draft

POST `/api/v1/accounting/journals`

```json
{
  "journalId":"CLIENT_UUID",
  "periodId":"OPEN_PERIOD_UUID",
  "branchId":"ACTIVE_BRANCH_UUID",
  "entryDate":"YYYY-MM-DD",
  "description":"Accountant-approved transaction description",
  "reference":"Supporting voucher/reference",
  "lines":[
    {"accountId":"DEBIT_ACCOUNT_UUID","debit":100.25,"credit":0,"narration":"Supporting detail"},
    {"accountId":"CREDIT_ACCOUNT_UUID","debit":0,"credit":100.25,"narration":"Supporting detail"}
  ]
}
```

Rules:

- 2–100 lines, each referencing an active chart account.
- Each line has exactly one positive debit or credit; the other must be zero.
- Amounts allow up to 12 integer digits and 2 decimals. No automatic rounding of excess precision.
- Total debits must equal total credits exactly and be positive.
- Date must be within the selected open period and no later than today.
- Branch must be active. Description/reference are required; line narration may be empty but must be supplied.

DRAFT entries have no number or ledger/report effect. No draft edit/delete endpoint is provided. Reject incorrect drafts and submit a corrected draft under a new ID. A descriptive reference is not unique: source-document duplicate detection is not implemented. Client UUIDs protect transport retries, not separately submitted duplicates with new IDs. Accountants must review supporting documents and economic duplication.

POST returns 201. Reuse the same ID and semantic payload for an exact retry (200 with `replayed:true`). Different data under that ID returns 409. Decimal scale differences such as 100 and 100.00 are equivalent. Line order, narration, references and descriptions remain part of the identity. An exact retry returns the current record even after review.

## 4. Independent posting or rejection

POST `/api/v1/accounting/journals/{id}/post` or `/reject`:

```json
{"eventId":"CLIENT_UUID","reason":"Supporting record, account mapping and amounts independently verified"}
```

A different authorised user must review; the submitter cannot post or reject their own draft. Posting rechecks branch/account activity, period/date and all line/header totals. A successful post assigns a unique global `JV-00000001`-style sequence number and status POSTED. This journal number is not an invoice number or statutory numbering policy. REJECTED drafts have no ledger effect or number.

Posted and rejected journals are immutable. Original line amounts, dates and references cannot be overwritten. Repeating the same event ID with the same target/action/reason/actor is safe. Conflicting event reuse, new decisions against reviewed records and self-review return 409. Journal numbering, event, review state and audit share one transaction; a failed post consumes no number.

- GET `/api/v1/accounting/journals?page=0&size=20`.
- GET `/api/v1/accounting/journals/{id}` includes lines, account codes/names and decision events.

Lists use page size 1–100 and expose all statuses. There is no status-filtered review screen yet. Record/line fields use snake_case; `replayed` is additional. Internal retry payloads and reversal claim bookkeeping are not exposed.

## 5. Reverse an original posted journal

POST `/api/v1/accounting/journals/{id}/reversals`:

```json
{
  "journalId":"NEW_CLIENT_UUID",
  "periodId":"ELIGIBLE_OPEN_PERIOD_UUID",
  "entryDate":"YYYY-MM-DD",
  "reason":"Documented correction reason",
  "reference":"Reversal supporting reference"
}
```

This creates a new DRAFT inheriting the original branch and accounts, with debit/credit swapped on every line. The original stays POSTED. Reversal date must not precede the original date, must not be future and must fall in its selected open period. Posting still needs a different user. Original source-period closure does not block a reversal in another eligible open period.

Only original POSTED journals can be reversed; a reversal cannot itself be reversed through this endpoint. At most one DRAFT or POSTED reversal can claim an original. Rejecting a reversal releases that claim while retaining its rejected history, so a corrected reversal can be submitted. Reversal creation also supports exact UUID retries. Accounts/branch must be active; an authorised admin may need to reactivate a retired account before recording a required reversal.

This is ledger correction only. It does not reverse an invoice, payment, inventory movement, operational approval or customer return. Integrated source reversal rules remain future work.

## 6. Trial balance and account ledger

GET `/api/v1/accounting/trial-balance?asOf=YYYY-MM-DD&branchId=OPTIONAL_UUID`

Only POSTED journals dated on/before `asOf` contribute. Rows include account metadata, accumulated debit/credit movements and net debit/credit balances. Outer totals expose `debitBalance`, `creditBalance`, `movementDebit`, `movementCredit`, `balanced` and INR currency. Accounts without posted activity are omitted; an account offset to zero by reversal can still appear. This is from inception, not an automatically rolled-forward financial-year statement.

GET `/api/v1/accounting/accounts/{id}/ledger?from=YYYY-MM-DD&to=YYYY-MM-DD&branchId=OPTIONAL_UUID&page=0&size=20`

The report returns:

- Opening balance from posted entries before `from`.
- Dated entries within the inclusive range, with running balances.
- Closing balance including all posted entries through `to`, independent of the selected page.
- Page size 1–100. Ordering is entry date, journal UUID, then line position; ties do not imply posting order.

Balances are signed debit-minus-credit: positive means a debit balance and negative a credit balance. Running balances include entries on earlier pages. Reports use a repeatable-read snapshot so concurrent posting cannot mix opening balances and entries within one response. Separate report requests may see different newly posted data. A balanced trial balance proves debit/credit equality, not that account selection, valuation or business meaning is correct.

## Consistency and remaining scope

A single database guard serializes accounting mutations, including numbering, chart status, period creation/closure, journal review and reversal claims. This favours predictable correctness for the initial workload over concurrent write throughput. Reports use database snapshots. Database constraints protect types, positive single-sided lines, balanced header totals, review separation, unique numbers and reversal claims; cross-row line-total/period rules are checked transactionally by the application.

Manual entries can represent accountant-approved opening transactions, but there is no dedicated opening-balance import/reconciliation process. Dedicated customer/supplier account links are available in V13. V14 adds recorded money vouchers and advance balances. V16 posts before-tax sales/purchase journals from READY billing preparations. Invoiced customer/supplier subledgers, payment allocation, tax, inventory valuation, assets, bank reconciliation, bank execution, financial statements, multi-currency, inter-branch settlement, exports, period reopening and frontend screens remain pending. No historical operational data is automatically converted into journals by this migration.

## Party-specific accounts

V13 adds permanent customer/supplier ledger links, commercial terms and posted balance views. See [party financial accounts](party-financial-accounts.md). Invoice/bill subledgers, allocations and automatic source posting remain pending.

## Recorded money vouchers

V14 registers cash/bank books and records receipts, payments and refunds with linked journals. Cash-book and refund checks also apply when posting linked drafts through generic journal endpoints. See [receipts and payments](receipts-and-payments.md). This does not execute bank transfers.

Sales and supplier billing preparation is available for independent commercial review. A READY preparation can create a before-tax DRAFT journal; posted balances change only after that journal is independently posted. See [billing preparation](billing-preparation.md).
