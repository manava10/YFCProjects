# Customer and supplier financial profiles

Migration V13 links existing customers and suppliers to permanent dedicated ledger accounts. It adds commercial terms, versioned term-change history, party ledger views and posted-journal balance summaries. No new environment variables, AWS services, chart accounts, party links or balances are seeded.

The profile API is party-specific ledger organisation. V14 separately adds recorded receipts/payments; invoices, supplier bills, ageing, due dates and allocations remain pending. A ledger balance is not proof that an invoice exists or that money has moved through a bank. READY billing preparations can post before-tax journals onto these accounts in V16; expenses still do not automatically post.

## Roles and ownership

SUPER_ADMIN creates links and changes terms. SUPER_ADMIN and ACCOUNTS may read financial profiles, balances and party ledgers and continue using the V12 journal workflow. OPERATIONS and external CUSTOMER/SUPPLIER/DRIVER users are denied. Authenticated writes require CSRF. There is no self-service portal or branch-scoped staff permission in this phase.

One financial profile per customer and one per supplier are allowed across the INR book. Each profile owns one dedicated ledger account; the same account cannot serve another party. Branches share the profile and commercial terms, while reports may filter posted activity by branch.

## 1. Create a permanent link

First create a fresh accountant-approved posting account using the V12 account API. Use a dedicated customer/supplier account, not a generic cash, bank or shared control account.

POST `/api/v1/accounting/parties`

```json
{
  "profileId":"CLIENT_UUID",
  "partyType":"CUSTOMER",
  "partyId":"EXISTING_CUSTOMER_UUID",
  "accountId":"FRESH_ASSET_ACCOUNT_UUID",
  "paymentTermsDays":30,
  "creditLimit":100000.00
}
```

For a supplier use `partyType:SUPPLIER`, its supplier UUID and a fresh LIABILITY account; omit `creditLimit`.

Rules:

- Customer accounts must be active ASSET accounts; supplier accounts must be active LIABILITY accounts.
- The ledger account must have no journal lines in any status, including draft or rejected history. This prevents retroactively reclassifying existing transactions as belonging to a newly linked party.
- Each customer/supplier and account can have only one profile. These unique links cannot be reassigned, unlinked or deleted through this API.
- The master must exist. Inactive suppliers may receive financial profiles and retain ledgers for historical settlement; their purchasing restrictions still apply. An inactive ledger account cannot be newly bound.
- `paymentTermsDays` is mandatory, 0–365. Customer `creditLimit` is optional, nonnegative, with up to 12 integer digits and 2 decimals. Supplier credit limits are not modelled; supplying one is rejected.
- The party name is snapshotted when linked. It is not automatically renamed when the source master changes.

First creation returns 201. Reuse the same UUID and fields for 200 with `replayed:true`; conflicting reuse returns 409. Decimal scale differences in limits are equivalent. An exact create retry returns current profile terms, even after later edits. A different UUID for a party/account already linked returns 409.

If balances already exist on an unbound ledger account, do not erase or reassign that history. The accountant can link a fresh dedicated account and use independently posted reclassification/opening journals as appropriate. No migration of historical balances is performed automatically.

## 2. Read and find profiles

- GET `/api/v1/accounting/parties?page=0&size=20`.
- GET `/api/v1/accounting/parties/{profileId}` includes terms history.
- GET `/api/v1/accounting/parties/by-customer/{customerId}`.
- GET `/api/v1/accounting/parties/by-supplier/{supplierId}`.

Page size is 1–100. A missing/unconfigured profile returns 404 on direct lookups. Fields use snake_case, with `replayed` added on exact retries. Internal creation/event payloads are not returned.

## 3. Change commercial terms with a version check

POST `/api/v1/accounting/parties/{profileId}/terms`

```json
{
  "eventId":"CLIENT_UUID",
  "version":1,
  "paymentTermsDays":45,
  "creditLimit":150000.00,
  "reason":"Updated agreement independently confirmed by administration"
}
```

Only admins may change terms. This is an audited master-setting change, not a journal approval; no second-user approval workflow is provided for terms. The current version must match. A successful change increments it and records old/new days and credit limits, reason, actor and time. Stale versions return 409; a no-op returns 400. Customer limits can be cleared by omitting `creditLimit` or sending null. Supplier updates must omit it.

Exact event retries with the same profile, expected version, values, reason and actor are safe even after later changes; they return the current profile. Conflicting event reuse returns 409. Two concurrent updates against the same version have one winner. Original party/account identity stays unchanged.

Credit limits are informational settings in this phase. They do not block orders/journals or calculate available credit, because invoice allocation, unbilled exposure, advances and overdue enforcement are not implemented. Payment terms do not yet create due dates. Future invoices/bills must snapshot agreed terms; changing a profile must not silently rewrite historical agreements.

## 4. Party ledger

GET `/api/v1/accounting/parties/{profileId}/ledger?from=YYYY-MM-DD&to=YYYY-MM-DD&branchId=OPTIONAL_UUID&page=0&size=20`

The existing account ledger is returned with profile metadata, opening/closing/running balances and extra `signed_balance`, `balance_kind`, `balance_amount` fields. Only POSTED journal lines contribute. Drafts/rejected journals do not; posted reversals offset originals. Signed balances use debit minus credit; `balance_amount` is the absolute closing amount.

| Party | Signed balance | Meaning / `balance_kind` |
|---|---|---|
| Customer | Positive | Booked receivable: RECEIVABLE |
| Customer | Negative | Customer credit balance: CUSTOMER_CREDIT |
| Supplier | Negative | Booked payable: PAYABLE |
| Supplier | Positive | Supplier debit/advance balance: SUPPLIER_ADVANCE |
| Either | Zero | ZERO_BALANCE |

These labels classify the ledger balance, not invoice/payment status. ZERO_BALANCE does not mean all orders are billed or all payments settled. An advance/credit balance is not independent proof of a bank transfer. Accountants must ensure the manual journals reflect actual supporting transactions.

Opening balance is before `from`; closing includes posted activity through `to`, regardless of page. Running balances include earlier pages. Each response uses a repeatable-read snapshot, including its profile and underlying ledger. Inactive accounts and suppliers retain historical reports; ledger account activation controls new journal entry/posting.

## 5. Balance summary

GET `/api/v1/accounting/party-balances?asOf=YYYY-MM-DD&branchId=OPTIONAL_UUID&page=0&size=20`

Returns financial profiles with their dedicated account code/activation, current terms/version, INR currency, `as_of` and posted-journal balance classification. Profiles with no posted activity appear as ZERO_BALANCE. Accounts retired after linking remain visible.

The amount includes entries dated on/before `asOf`, optionally for one branch. Terms and name snapshots come from the current profile; the summary does not reconstruct historical commercial terms for `asOf`. The branch filter affects amounts, not the profile list or global credit-limit settings. A branch with no activity returns zero balances for the profiles on the selected page. Different report requests may see newly posted journals between snapshots.

## Consistency and next steps

Profile creation and terms updates share the V12 accounting guard with journal/chart mutations. An account cannot acquire journal history concurrently between the unused-account check and binding. Database uniqueness protects dedicated accounts/masters. Profile/term changes and audit records commit together. References to customer/supplier masters are checked through their public module interfaces; the accounting module does not query their private tables.

V14 adds bank/cash vouchers and advance balances. V16 can post before-tax sales/purchase journals onto these accounts from READY billing preparations. Remaining: customer invoices, supplier bills, verified tax treatment, bank execution, allocations, ageing, credit enforcement, payment reminders, reconciled opening balances, grouped control accounts, statement exports and frontend screens.

## Recorded money movements

V14 adds recorded receipts/payments/refunds against these profiles. See [receipts and payments](receipts-and-payments.md). Invoice allocation, ageing and credit enforcement remain pending; the backend does not execute bank transfers.

Sales and supplier billing preparation can create a before-tax DRAFT journal against these profiles; posted balances change only after independent journal posting. See [billing preparation](billing-preparation.md).
