import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { post, request, type RecordData } from "./api";
import { money, ownedBy, short, today, uuid, val } from "./lib";
import {
  Choices,
  DataTable,
  ErrorBox,
  Field,
  Heading,
  IdField,
  Notice,
  Pager,
  ReasonForm,
  RecordView,
  SaveForm,
  useAccess,
  usePaged,
} from "./ui";

const TYPES = ["ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE"];

export function Ledger() {
  const admin = useAccess().admin;
  const [tab, setTab] = useState("journals");
  return (
    <>
      <Heading
        eyebrow="FINANCE / LEDGER"
        title="One INR book."
        description="Chart, periods and journals. A different user posts or rejects each draft."
      />
      <div className="tabs">
        {[
          ["journals", "Journals"],
          ["accounts", "Chart"],
          ["periods", "Periods"],
          ["reports", "Reports"],
        ].map(([id, caption]) => (
          <button key={id} className={tab === id ? "tab active" : "tab"} onClick={() => setTab(id)}>
            {caption}
          </button>
        ))}
      </div>
      {tab === "journals" && <Journals />}
      {tab === "accounts" && <Accounts admin={admin} />}
      {tab === "periods" && <Periods admin={admin} />}
      {tab === "reports" && <Reports />}
    </>
  );
}

function Accounts({ admin }: { admin: boolean }) {
  const list = usePaged("accounts", "/api/v1/accounting/accounts");
  const [selected, setSelected] = useState("");
  const one = useQuery({
    queryKey: ["account", selected],
    queryFn: () => request<RecordData>(`/api/v1/accounting/accounts/${selected}`),
    enabled: !!selected,
  });
  return (
    <>
      {admin && (
        <SaveForm title="Chart account" path="/api/v1/accounting/accounts">
          <Notice title="Codes and types stay as entered.">
            No opening balance is created. Choose the code with the accountant.
          </Notice>
          <IdField name="accountId" caption="Account id" />
          <div className="form-grid">
            <Field label="Code">
              <input name="code" required minLength={2} maxLength={30} pattern="[A-Za-z0-9][A-Za-z0-9_\-]{1,29}" />
            </Field>
            <Field label="Name">
              <input name="name" required maxLength={160} />
            </Field>
            <Field label="Type">
              <select name="type">
                {TYPES.map((type) => (
                  <option key={type}>{type}</option>
                ))}
              </select>
            </Field>
          </div>
        </SaveForm>
      )}
      <ErrorBox error={list.q.error || one.error} />
      <div className="panel">
        <DataTable
          rows={list.items}
          onOpen={(row) => setSelected(String(row.id))}
          columns={[
            { label: "Code", keys: ["code"] },
            { label: "Name", keys: ["name"] },
            { label: "Type", keys: ["type"] },
            { label: "Status", keys: ["active"], status: true },
          ]}
        />
        <Pager page={list.page} setPage={list.setPage} hasNext={list.hasNext} />
      </div>
      {one.data && (
        <div className="panel form-panel">
          <RecordView row={one.data} />
          {admin && (
            <SaveForm path={`/api/v1/accounting/accounts/${selected}/status`} method="PATCH">
              <div className="form-grid">
                <Field label="Expected current state">
                  <select name="expectedActive" defaultValue={String(!!val(one.data, "active"))}>
                    <option value="true">Active</option>
                    <option value="false">Inactive</option>
                  </select>
                </Field>
                <Field label="New state">
                  <select name="active">
                    <option value="false">Inactive</option>
                    <option value="true">Active</option>
                  </select>
                </Field>
              </div>
              <Field label="Reason">
                <textarea name="reason" required maxLength={1000} />
              </Field>
            </SaveForm>
          )}
        </div>
      )}
    </>
  );
}

function Periods({ admin }: { admin: boolean }) {
  const list = usePaged("periods", "/api/v1/accounting/periods");
  const [selected, setSelected] = useState("");
  return (
    <>
      {admin && (
        <SaveForm title="Financial period" path="/api/v1/accounting/periods">
          <IdField name="periodId" caption="Period id" />
          <div className="form-grid">
            <Field label="Code">
              <input name="code" required minLength={2} maxLength={30} pattern="[A-Za-z0-9][A-Za-z0-9_\-]{1,29}" />
            </Field>
            <Field label="Starts">
              <input name="startsOn" type="date" required />
            </Field>
            <Field label="Ends">
              <input name="endsOn" type="date" required />
            </Field>
          </div>
        </SaveForm>
      )}
      <div className="panel">
        <DataTable
          rows={list.items}
          onOpen={(row) => setSelected(String(row.id))}
          columns={[
            { label: "Code", keys: ["code"] },
            { label: "Starts", keys: ["starts_on", "startsOn"] },
            { label: "Ends", keys: ["ends_on", "endsOn"] },
            { label: "Status", keys: ["status"], status: true },
          ]}
        />
        <Pager page={list.page} setPage={list.setPage} hasNext={list.hasNext} />
      </div>
      {admin && selected && (
        <SaveForm title="Close period" path={`/api/v1/accounting/periods/${selected}/close`}>
          <Notice title="Close only after every draft in the period is posted or rejected.">
            The period must already have ended. Closure is a posting lock.
          </Notice>
          <IdField name="eventId" caption="Event id" />
          <Field label="Reason">
            <textarea name="reason" required maxLength={1000} />
          </Field>
        </SaveForm>
      )}
    </>
  );
}

function Journals() {
  const [open, setOpen] = useState(false);
  const list = usePaged("journals", "/api/v1/accounting/journals");
  return (
    <>
      <button onClick={() => setOpen((value) => !value)}>
        <Plus size={16} /> Journal draft
      </button>
      {open && <JournalForm />}
      <div className="panel">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Journal</th>
                <th>Date</th>
                <th>Description</th>
                <th>Debit</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {list.items.map((row) => (
                <tr key={String(row.id)}>
                  <td>
                    <Link to={`/ledger/${row.id}`}>{String(val(row, "number") ?? short(String(row.id)))}</Link>
                  </td>
                  <td>{String(val(row, "entry_date", "entryDate") ?? "—")}</td>
                  <td>{String(val(row, "description") ?? "—")}</td>
                  <td>{money(Number(val(row, "total_debit", "totalDebit") ?? 0))}</td>
                  <td>{String(val(row, "status") ?? "—")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager page={list.page} setPage={list.setPage} hasNext={list.hasNext} />
      </div>
    </>
  );
}

function JournalForm() {
  const [lines, setLines] = useState([
    { accountId: "", debit: "", credit: "0", narration: "" },
    { accountId: "", debit: "0", credit: "", narration: "" },
  ]);
  return (
    <SaveForm
      title="Balanced journal"
      path="/api/v1/accounting/journals"
      prepare={(body) => ({
        ...body,
        lines: lines.map((line) => ({
          accountId: line.accountId,
          debit: line.debit || "0",
          credit: line.credit || "0",
          narration: line.narration,
        })),
      })}
    >
      <IdField name="journalId" caption="Journal id" />
      <div className="form-grid">
        <Field label="Period">
          <select name="periodId" required>
            <Choices path="/api/v1/accounting/periods" empty="Select period" labelOf={(row) => String(row.code)} />
          </select>
        </Field>
        <Field label="Branch">
          <select name="branchId" required>
            <Choices path="/api/v1/branches" empty="Select branch" />
          </select>
        </Field>
        <Field label="Entry date">
          <input name="entryDate" type="date" max={today()} required />
        </Field>
        <Field label="Reference">
          <input name="reference" required maxLength={300} />
        </Field>
      </div>
      <Field label="Description">
        <textarea name="description" required maxLength={1000} />
      </Field>
      {lines.map((line, index) => (
        <div className="line-input" key={index}>
          <Field label={`Account ${index + 1}`}>
            <select
              required
              value={line.accountId}
              onChange={(event) =>
                setLines(lines.map((item, itemIndex) => (itemIndex === index ? { ...item, accountId: event.target.value } : item)))
              }
            >
              <Choices path="/api/v1/accounting/accounts" empty="Select account" labelOf={(row) => `${row.code} · ${row.name}`} />
            </select>
          </Field>
          <Field label="Debit">
            <input
              type="number"
              min="0"
              step="0.01"
              value={line.debit}
              onChange={(event) =>
                setLines(lines.map((item, itemIndex) => (itemIndex === index ? { ...item, debit: event.target.value } : item)))
              }
            />
          </Field>
          <Field label="Credit">
            <input
              type="number"
              min="0"
              step="0.01"
              value={line.credit}
              onChange={(event) =>
                setLines(lines.map((item, itemIndex) => (itemIndex === index ? { ...item, credit: event.target.value } : item)))
              }
            />
          </Field>
          <Field label="Narration">
            <input
              maxLength={500}
              value={line.narration}
              onChange={(event) =>
                setLines(lines.map((item, itemIndex) => (itemIndex === index ? { ...item, narration: event.target.value } : item)))
              }
            />
          </Field>
        </div>
      ))}
      <button
        type="button"
        className="secondary"
        onClick={() => setLines([...lines, { accountId: "", debit: "0", credit: "0", narration: "" }])}
      >
        Add line
      </button>
    </SaveForm>
  );
}

export function Journal() {
  const { id = "" } = useParams();
  const [mode, setMode] = useState<string | null>(null);
  const q = useQuery({
    queryKey: ["journal", id],
    queryFn: () => request<RecordData>(`/api/v1/accounting/journals/${id}`),
  });
  const own = ownedBy(q.data, useAccess().username);
  const status = String(val(q.data, "status") ?? "");
  return (
    <>
      <Link className="back" to="/ledger">
        Ledger
      </Link>
      <ErrorBox error={q.error} />
      {q.data && (
        <div className="panel form-panel">
          <Heading eyebrow="JOURNAL" title={String(val(q.data, "number") ?? short(id))} description={String(val(q.data, "description") ?? "")} />
          <RecordView row={q.data} />
          {status === "DRAFT" && own && (
            <p className="muted">A different user has to post or reject this draft.</p>
          )}
          {status === "DRAFT" && !own && (
            <div className="actions">
              <button onClick={() => setMode("post")}>Post</button>
              <button className="secondary" onClick={() => setMode("reject")}>
                Reject
              </button>
            </div>
          )}
          {mode && (
            <ReasonForm path={`/api/v1/accounting/journals/${id}/${mode}`} submitLabel="Save decision" onDone={() => setMode(null)} />
          )}
          {status === "POSTED" && (
            <SaveForm title="Contra reversal" path={`/api/v1/accounting/journals/${id}/reversals`}>
              <IdField name="journalId" caption="Reversal journal id" />
              <div className="form-grid">
                <Field label="Open period">
                  <select name="periodId" required>
                    <Choices path="/api/v1/accounting/periods" empty="Select period" labelOf={(row) => String(row.code)} />
                  </select>
                </Field>
                <Field label="Entry date">
                  <input name="entryDate" type="date" max={today()} required />
                </Field>
                <Field label="Reference">
                  <input name="reference" required maxLength={300} />
                </Field>
              </div>
              <Field label="Reason">
                <textarea name="reason" required maxLength={1000} />
              </Field>
            </SaveForm>
          )}
        </div>
      )}
    </>
  );
}

function Reports() {
  const [asOf, setAsOf] = useState(today());
  const [account, setAccount] = useState("");
  const [from, setFrom] = useState(today().slice(0, 8) + "01");
  const [to, setTo] = useState(today());
  const trial = useQuery({
    queryKey: ["trial", asOf],
    queryFn: () => request<RecordData>(`/api/v1/accounting/trial-balance?asOf=${asOf}`),
    enabled: !!asOf,
  });
  const ledger = useQuery({
    queryKey: ["ledger", account, from, to],
    queryFn: () =>
      request<RecordData>(
        `/api/v1/accounting/accounts/${account}/ledger?from=${from}&to=${to}&page=0&size=50`,
      ),
    enabled: !!account && !!from && !!to,
  });
  const accounts = (trial.data?.accounts as RecordData[] | undefined) ?? [];
  const entries = (ledger.data?.entries as RecordData[] | undefined) ?? [];
  return (
    <>
      <div className="form-grid">
        <Field label="Trial balance as of">
          <input type="date" value={asOf} max={today()} onChange={(event) => setAsOf(event.target.value)} />
        </Field>
      </div>
      <ErrorBox error={trial.error || ledger.error} />
      <div className="panel">
        <DataTable
          rows={accounts}
          columns={[
            { label: "Code", keys: ["code"] },
            { label: "Name", keys: ["name"] },
            { label: "Type", keys: ["type"] },
            { label: "Debit balance", keys: ["debit_balance"] },
            { label: "Credit balance", keys: ["credit_balance"] },
          ]}
        />
      </div>
      <div className="panel form-panel">
        <h3>Account ledger</h3>
        <div className="form-grid">
          <Field label="Account">
            <select value={account} onChange={(event) => setAccount(event.target.value)}>
              <Choices path="/api/v1/accounting/accounts" empty="Select account" labelOf={(row) => `${row.code} · ${row.name}`} />
            </select>
          </Field>
          <Field label="From">
            <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
          </Field>
          <Field label="To">
            <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
          </Field>
        </div>
        {ledger.data && (
          <p>
            Opening {money(Number(ledger.data.openingBalance ?? 0))} · closing{" "}
            {money(Number(ledger.data.closingBalance ?? 0))}
          </p>
        )}
        <DataTable
          rows={entries}
          columns={[
            { label: "Date", keys: ["entry_date"] },
            { label: "Number", keys: ["number"] },
            { label: "Debit", keys: ["debit"] },
            { label: "Credit", keys: ["credit"] },
            { label: "Balance", keys: ["running_balance"] },
            { label: "Narration", keys: ["narration"] },
          ]}
        />
      </div>
    </>
  );
}

export function Parties() {
  const admin = useAccess().admin;
  const [type, setType] = useState("CUSTOMER");
  const list = usePaged("parties", "/api/v1/accounting/parties");
  const [balanceDate, setBalanceDate] = useState(today());
  const balances = usePaged("balances", `/api/v1/accounting/party-balances?asOf=${balanceDate}`);
  const [selected, setSelected] = useState("");
  const one = useQuery({
    queryKey: ["party", selected],
    queryFn: () => request<RecordData>(`/api/v1/accounting/parties/${selected}`),
    enabled: !!selected,
  });
  return (
    <>
      <Heading
        eyebrow="FINANCE / PARTIES"
        title="Customer and supplier ledgers."
        description="Link each party to a fresh chart account. Credit limits are informational."
      />
      {admin && (
        <SaveForm title="Link a party" path="/api/v1/accounting/parties">
          <IdField name="profileId" caption="Profile id" />
          <div className="form-grid">
            <Field label="Party type">
              <select name="partyType" value={type} onChange={(event) => setType(event.target.value)}>
                <option value="CUSTOMER">Customer</option>
                <option value="SUPPLIER">Supplier</option>
              </select>
            </Field>
            <Field label="Party">
              <select name="partyId" required>
                <Choices
                  path={type === "CUSTOMER" ? "/api/v1/customers" : "/api/v1/suppliers"}
                  empty="Select party"
                />
              </select>
            </Field>
            <Field label="Fresh ledger account">
              <select name="accountId" required>
                <Choices path="/api/v1/accounting/accounts" empty="Select account" labelOf={(row) => `${row.code} · ${row.type}`} />
              </select>
            </Field>
            <Field label="Payment terms (days)">
              <input name="paymentTermsDays" type="number" min="0" max="365" required />
            </Field>
            {type === "CUSTOMER" && (
              <Field label="Credit limit (optional)">
                <input name="creditLimit" type="number" min="0" step="0.01" />
              </Field>
            )}
          </div>
        </SaveForm>
      )}
      <ErrorBox error={list.q.error || one.error || balances.q.error} />
      <div className="panel">
        <DataTable
          rows={list.items}
          onOpen={(row) => setSelected(String(row.id))}
          columns={[
            { label: "Party", keys: ["party_name", "partyName", "name"] },
            { label: "Type", keys: ["party_type", "partyType"] },
            { label: "Terms", keys: ["payment_terms_days", "paymentTermsDays"] },
            { label: "Limit", keys: ["credit_limit", "creditLimit"] },
          ]}
        />
        <Pager page={list.page} setPage={list.setPage} hasNext={list.hasNext} />
      </div>
      {one.data && (
        <div className="panel form-panel">
          <RecordView row={one.data} />
          {admin && (
            <SaveForm title="Change terms" path={`/api/v1/accounting/parties/${selected}/terms`}>
              <IdField name="eventId" caption="Event id" />
              <div className="form-grid">
                <Field label="Current version">
                  <input name="version" type="number" min="1" required defaultValue={String(val(one.data, "version") ?? 1)} />
                </Field>
                <Field label="Payment terms days">
                  <input name="paymentTermsDays" type="number" min="0" max="365" required />
                </Field>
                {String(val(one.data, "party_type", "partyType")) === "CUSTOMER" && (
                  <Field label="Credit limit">
                    <input name="creditLimit" type="number" min="0" step="0.01" />
                  </Field>
                )}
              </div>
              <Field label="Reason">
                <textarea name="reason" required maxLength={1000} />
              </Field>
            </SaveForm>
          )}
          <PartyLedger id={selected} />
        </div>
      )}
      <h3>Posted balances</h3>
      <Field label="Balances as of">
        <input type="date" required value={balanceDate} onChange={(event) => {
          if (!event.target.value) return;
          setBalanceDate(event.target.value);
          balances.setPage(0);
        }} />
      </Field>
      <div className="panel">
        <DataTable
          rows={balances.items}
          columns={[
            { label: "Party", keys: ["party_name", "partyName", "name"] },
            { label: "Kind", keys: ["balance_kind", "balanceKind"] },
            { label: "Amount", keys: ["balance_amount", "balanceAmount", "signed_balance"] },
          ]}
        />
        <Pager page={balances.page} setPage={balances.setPage} hasNext={balances.hasNext} />
      </div>
    </>
  );
}

function PartyLedger({ id }: { id: string }) {
  const [from, setFrom] = useState(today().slice(0, 8) + "01");
  const [to, setTo] = useState(today());
  const q = useQuery({
    queryKey: ["party-ledger", id, from, to],
    queryFn: () => request<RecordData>(`/api/v1/accounting/parties/${id}/ledger?from=${from}&to=${to}&page=0&size=50`),
  });
  return (
    <>
      <div className="form-grid">
        <Field label="Ledger from">
          <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        </Field>
        <Field label="To">
          <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </Field>
      </div>
      <ErrorBox error={q.error} />
      <RecordView row={q.data} />
    </>
  );
}

export function Money() {
  const admin = useAccess().admin;
  const accounts = usePaged("money-accounts", "/api/v1/accounting/money-accounts");
  const vouchers = usePaged("vouchers", "/api/v1/accounting/money-vouchers");
  const [selected, setSelected] = useState("");
  const one = useQuery({
    queryKey: ["voucher", selected],
    queryFn: () => request<RecordData>(`/api/v1/accounting/money-vouchers/${selected}`),
    enabled: !!selected,
  });
  return (
    <>
      <Heading
        eyebrow="FINANCE / CASH & BANK"
        title="Record money that already moved."
        description="Vouchers do not start a bank transfer. They draft a journal for a second person to post."
      />
      {admin && (
        <SaveForm title="Register a cash or bank book" path="/api/v1/accounting/money-accounts">
          <div className="form-grid">
            <Field label="Fresh asset account">
              <select name="accountId" required>
                <Choices path="/api/v1/accounting/accounts" empty="Select asset account" labelOf={(row) => `${row.code} · ${row.type}`} />
              </select>
            </Field>
            <Field label="Branch">
              <select name="branchId" required>
                <Choices path="/api/v1/branches" empty="Select branch" />
              </select>
            </Field>
            <Field label="Kind">
              <select name="kind">
                <option value="BANK">Bank</option>
                <option value="CASH">Cash</option>
              </select>
            </Field>
          </div>
          <Field label="Book designation">
            <input name="reference" required maxLength={300} />
          </Field>
        </SaveForm>
      )}
      <div className="panel">
        <DataTable
          rows={accounts.items}
          columns={[
            { label: "Code", keys: ["code"] },
            { label: "Name", keys: ["name"] },
            { label: "Kind", keys: ["kind"] },
            { label: "Reference", keys: ["reference"] },
          ]}
        />
      </div>
      <SaveForm title="Money voucher" path="/api/v1/accounting/money-vouchers">
        <IdField name="voucherId" caption="Voucher id" />
        <div className="form-grid">
          <Field label="Party profile">
            <select name="profileId" required>
              <Choices path="/api/v1/accounting/parties" empty="Select profile" labelOf={(row) => String(val(row, "party_name", "partyName", "name") ?? row.id)} />
            </select>
          </Field>
          <Field label="Cash / bank account">
            <select name="moneyAccountId" required>
              <Choices path="/api/v1/accounting/money-accounts" getValue={(row) => String(row.account_id)} empty="Select book" labelOf={(row) => `${val(row, "code", "kind")} · ${val(row, "reference", "name")}`} />
            </select>
          </Field>
          <Field label="Period">
            <select name="periodId" required>
              <Choices path="/api/v1/accounting/periods" empty="Select period" labelOf={(row) => String(row.code)} />
            </select>
          </Field>
          <Field label="Branch">
            <select name="branchId" required>
              <Choices path="/api/v1/branches" empty="Select branch" />
            </select>
          </Field>
          <Field label="Kind">
            <select name="kind">
              <option value="CUSTOMER_RECEIPT">Customer receipt</option>
              <option value="SUPPLIER_PAYMENT">Supplier payment</option>
              <option value="CUSTOMER_REFUND">Customer refund</option>
              <option value="SUPPLIER_REFUND">Supplier refund</option>
            </select>
          </Field>
          <Field label="Date">
            <input name="entryDate" type="date" max={today()} required />
          </Field>
          <Field label="Amount">
            <input name="amount" type="number" min="0.01" step="0.01" required />
          </Field>
          <Field label="Unique reference">
            <input name="reference" required maxLength={300} />
          </Field>
        </div>
        <Field label="Evidence reference">
          <input name="evidenceReference" required maxLength={500} />
        </Field>
      </SaveForm>
      <ErrorBox error={accounts.q.error || vouchers.q.error || one.error} />
      <div className="panel">
        <DataTable
          rows={vouchers.items}
          onOpen={(row) => setSelected(String(row.id))}
          columns={[
            { label: "Kind", keys: ["kind"] },
            { label: "Amount", keys: ["amount"] },
            { label: "Reference", keys: ["reference"] },
            { label: "Status", keys: ["status"], status: true },
          ]}
        />
        <Pager page={vouchers.page} setPage={vouchers.setPage} hasNext={vouchers.hasNext} />
      </div>
      {one.data && <VoucherReview id={selected} row={one.data} />}
    </>
  );
}

function VoucherReview({ id, row }: { id: string; row: RecordData }) {
  const own = ownedBy(row, useAccess().username);
  const [mode, setMode] = useState<string | null>(null);
  const status = String(val(row, "status", "effective_status") ?? "");
  return (
    <div className="panel form-panel">
      <RecordView row={row} />
      {status === "DRAFT" && own && (
        <p className="muted">A different user has to post or reject this voucher.</p>
      )}
      {status === "DRAFT" && !own && (
        <div className="actions">
          <button onClick={() => setMode("post")}>Post</button>
          <button className="secondary" onClick={() => setMode("reject")}>
            Reject
          </button>
        </div>
      )}
      {mode && (
        <ReasonForm path={`/api/v1/accounting/money-vouchers/${id}/${mode}`} submitLabel="Save decision" onDone={() => setMode(null)} />
      )}
    </div>
  );
}

export function Billing() {
  const [kind, setKind] = useState("SALES_ORDER");
  const list = usePaged("billing", "/api/v1/accounting/billing-preparations");
  return (
    <>
      <Heading
        eyebrow="FINANCE / BILLING PREPARATION"
        title="Prepare one bill for the whole order."
        description="Sales bills use the ordered quantity. Supplier bills use the loaded quantity. The accountant enters GST on the bill itself."
      />
      <SaveForm title="Prepare a bill" path="/api/v1/accounting/billing-preparations">
        <IdField name="preparationId" caption="Preparation id" />
        <div className="form-grid">
          <Field label="Kind">
            <select name="kind" value={kind} onChange={(event) => setKind(event.target.value)}>
              <option value="SALES_ORDER">Sales order</option>
              <option value="PURCHASE_RECEIPT">Purchase receipt</option>
            </select>
          </Field>
          <Field label={kind === "SALES_ORDER" ? "Sales order id" : "Purchase receipt id"}>
            <input name="sourceId" required />
          </Field>
          <Field label="Prepared on">
            <input name="preparedOn" type="date" max={today()} required />
          </Field>
          <Field label="Quantity basis">
            {kind === "SALES_ORDER" ? (
              <select name="quantityBasis" defaultValue="ORDERED">
                <option value="ORDERED">Ordered — whole order</option>
              </select>
            ) : (
              <select name="quantityBasis" defaultValue="LOADED">
                <option value="LOADED">Loaded</option>
              </select>
            )}
          </Field>
          <Field label="Reference">
            <input name="reference" required maxLength={300} />
          </Field>
          <Field label="Evidence reference">
            <input name="evidenceReference" required maxLength={500} />
          </Field>
        </div>
        <Field label="Why this quantity basis">
          <textarea name="basisReason" required maxLength={1000} />
        </Field>
      </SaveForm>
      <ErrorBox error={list.q.error} />
      <div className="panel">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Preparation</th>
                <th>Kind</th>
                <th>Status</th>
                <th>Subtotal</th>
              </tr>
            </thead>
            <tbody>
              {list.items.map((row) => (
                <tr key={String(row.id)}>
                  <td>
                    <Link to={`/billing/${row.id}`}>{short(String(row.id))}</Link>
                  </td>
                  <td>{String(val(row, "kind") ?? "—")}</td>
                  <td>{String(val(row, "status") ?? "—")}</td>
                  <td>{money(Number(val(row, "subtotal") ?? 0))}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager page={list.page} setPage={list.setPage} hasNext={list.hasNext} />
      </div>
    </>
  );
}

function TaxForm({
  id,
  lines,
  correcting,
}: {
  id: string;
  lines: RecordData[];
  correcting?: boolean;
}) {
  const client = useQueryClient();
  const [supply, setSupply] = useState("INTRA_STATE");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const taxable = lines.flatMap((line) => {
    const position = Number(line.position);
    const parts: { position: number; component: string; label: string }[] = [];
    if (Number(line.material_amount) > 0)
      parts.push({
        position,
        component: "MATERIAL",
        label: `${line.material} · material`,
      });
    if (Number(line.transport_amount) > 0)
      parts.push({
        position,
        component: "TRANSPORT",
        label: `${line.material} · freight`,
      });
    return parts;
  });
  return (
    <form
      className="stack"
      onSubmit={async (event) => {
        event.preventDefault();
        setBusy(true);
        setError(null);
        const form = new FormData(event.currentTarget);
        try {
          await post(`/api/v1/accounting/billing-preparations/${id}/tax`, {
            eventId: uuid(),
            supply,
            lines: taxable.map((part) => ({
              position: part.position,
              component: part.component,
              hsnOrSac: String(form.get(`hsn-${part.component}-${part.position}`)),
              ratePercent: String(form.get(`rate-${part.component}-${part.position}`)),
            })),
          });
          await client.invalidateQueries();
        } catch (caught) {
          setError(caught);
        } finally {
          setBusy(false);
        }
      }}
    >
      <h2>Enter GST on this bill</h2>
      <Notice
        title={
          correcting
            ? "Correct the GST on this bill before the ledger journal is posted."
            : "Type the rate for this bill. No rate is stored in advance."
        }
      >
        Intra-state tax is split equally into CGST and SGST. Inter-state tax is posted entirely as IGST. One bill covers the whole order. This does not file an e-invoice with the government portal.
      </Notice>
      <ErrorBox error={error} />
      <Field label="Place of supply">
        <select value={supply} onChange={(event) => setSupply(event.target.value)}>
          <option value="INTRA_STATE">Within the state · CGST and SGST</option>
          <option value="INTER_STATE">Another state · IGST</option>
        </select>
      </Field>
      {taxable.map((part) => (
        <div className="form-grid" key={`${part.component}-${part.position}`}>
          <Field label={`${part.label} · HSN or SAC`}>
            <input name={`hsn-${part.component}-${part.position}`} required pattern="[0-9]{4,8}" />
          </Field>
          <Field label="GST %">
            <input name={`rate-${part.component}-${part.position}`} required inputMode="decimal" />
          </Field>
        </div>
      ))}
      <button disabled={busy || taxable.length === 0}>{busy ? "Saving…" : "Save GST on this bill"}</button>
    </form>
  );
}

export function Preparation() {
  const { id = "" } = useParams();
  const [mode, setMode] = useState<string | null>(null);
  const q = useQuery({
    queryKey: ["preparation", id],
    queryFn: () => request<RecordData>(`/api/v1/accounting/billing-preparations/${id}`),
  });
  const own = ownedBy(q.data, useAccess().username);
  const status = String(val(q.data, "status") ?? "");
  return (
    <>
      <Link className="back" to="/billing">
        Billing preparations
      </Link>
      <ErrorBox error={q.error} />
      {q.data && (
        <div className="panel form-panel">
          <RecordView row={q.data} />
          {status === "DRAFT" && own && (
            <p className="muted">A different user has to review this preparation.</p>
          )}
          {status === "DRAFT" && !own && (
            <div className="actions">
              <button onClick={() => setMode("ready")}>Mark ready</button>
              <button className="secondary" onClick={() => setMode("reject")}>
                Reject
              </button>
              <button className="secondary" onClick={() => setMode("cancel")}>
                Cancel
              </button>
            </div>
          )}
          {status === "READY" && !own && (
            <button onClick={() => setMode("cancel")}>Cancel</button>
          )}
          {status === "READY" && own && (
            <p className="muted">A different user has to cancel this preparation.</p>
          )}
          {mode && (
            <ReasonForm
              path={`/api/v1/accounting/billing-preparations/${id}/${mode}`}
              submitLabel="Save review"
              onDone={() => setMode(null)}
            />
          )}
          {status === "READY" && Boolean(val(q.data, "gst_editable")) && !own && (
            <TaxForm
              id={id}
              lines={(q.data?.lines as RecordData[]) ?? []}
              correcting={String(val(q.data, "tax_treatment")) === "ASSESSED"}
            />
          )}
          {status === "READY" && String(val(q.data, "tax_treatment")) !== "ASSESSED" && own && (
            <p className="muted">A different user enters the GST on this bill.</p>
          )}
          {status === "READY" &&
            String(val(q.data, "tax_treatment")) === "ASSESSED" &&
            Boolean(val(q.data, "gst_editable")) &&
            own && (
              <p className="muted">A different user can correct the GST on this bill.</p>
            )}
          {status === "READY" && String(val(q.data, "tax_treatment")) === "ASSESSED" && (
            <SaveForm title="Draft the ledger journal" path={`/api/v1/accounting/billing-preparations/${id}/journals`}>
              <Notice title="GST entered on this bill is included in the journal.">
                This records the tax invoice in the ledger. It does not file an e-invoice with the government portal. A different user still posts the draft.
              </Notice>
              <IdField name="journalId" caption="Journal id" />
              <div className="form-grid">
                <Field label="Period">
                  <select name="periodId" required>
                    <Choices path="/api/v1/accounting/periods" empty="Select period" labelOf={(row) => String(row.code)} />
                  </select>
                </Field>
                <Field label="Income or expense account">
                  <select name="counterAccountId" required>
                    <Choices path="/api/v1/accounting/accounts" empty="Select account" labelOf={(row) => `${row.code} · ${row.type}`} />
                  </select>
                </Field>
                {Number(val(q.data, "cgst_amount") ?? 0) > 0 && (
                  <>
                    <Field label="CGST account">
                      <select name="cgstAccountId" required>
                        <Choices path="/api/v1/accounting/accounts" empty="Select CGST" labelOf={(row) => `${row.code} · ${row.type}`} />
                      </select>
                    </Field>
                    <Field label="SGST account">
                      <select name="sgstAccountId" required>
                        <Choices path="/api/v1/accounting/accounts" empty="Select SGST" labelOf={(row) => `${row.code} · ${row.type}`} />
                      </select>
                    </Field>
                  </>
                )}
                {Number(val(q.data, "igst_amount") ?? 0) > 0 && (
                  <Field label="IGST account">
                    <select name="igstAccountId" required>
                      <Choices path="/api/v1/accounting/accounts" empty="Select IGST" labelOf={(row) => `${row.code} · ${row.type}`} />
                    </select>
                  </Field>
                )}
                <Field label="Entry date">
                  <input name="entryDate" type="date" max={today()} required />
                </Field>
                <Field label="Reference">
                  <input name="reference" required maxLength={300} />
                </Field>
              </div>
              <Field label="Description">
                <textarea name="description" required maxLength={1000} />
              </Field>
            </SaveForm>
          )}
        </div>
      )}
    </>
  );
}
