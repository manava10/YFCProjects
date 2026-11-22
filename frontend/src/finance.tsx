import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { post, request, type RecordData } from "./api";
import { money, ownedBy, short, today, uuid, val } from "./lib";
import { CsvExportButton, PrintableAdjustmentNote, PrintableInvoice } from "./print-docs";
import {
  Choices,
  DataTable,
  ErrorBox,
  Field,
  FilterBar,
  Heading,
  IdField,
  Notice,
  Pager,
  queryPath,
  ReasonForm,
  RecordView,
  SaveForm,
  useAccess,
  useDebounced,
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
  const [sub, setSub] = useState("trial");
  const [asOf, setAsOf] = useState(today());
  const [account, setAccount] = useState("");
  const [branchId, setBranchId] = useState("");
  const [from, setFrom] = useState(today().slice(0, 8) + "01");
  const [to, setTo] = useState(today());
  const branchParam = branchId ? `&branchId=${branchId}` : "";

  const trial = useQuery({
    queryKey: ["trial", asOf, branchId],
    queryFn: () =>
      request<RecordData>(`/api/v1/accounting/trial-balance?asOf=${asOf}${branchParam}`),
    enabled: sub === "trial" && !!asOf,
  });
  const ledger = useQuery({
    queryKey: ["ledger", account, from, to, branchId],
    queryFn: () =>
      request<RecordData>(
        `/api/v1/accounting/accounts/${account}/ledger?from=${from}&to=${to}${branchParam}&page=0&size=50`,
      ),
    enabled: sub === "trial" && !!account && !!from && !!to,
  });
  const pnl = useQuery({
    queryKey: ["report-pnl", from, to, branchId],
    queryFn: () =>
      request<RecordData>(
        `/api/v1/accounting/reports/profit-and-loss?from=${from}&to=${to}${branchParam}`,
      ),
    enabled: sub === "pnl" && !!from && !!to,
  });
  const bs = useQuery({
    queryKey: ["report-bs", asOf, branchId],
    queryFn: () =>
      request<RecordData>(
        `/api/v1/accounting/reports/balance-sheet?asOf=${asOf}${branchParam}`,
      ),
    enabled: sub === "bs" && !!asOf,
  });
  const gstr1 = useQuery({
    queryKey: ["report-gstr1", from, to, branchId],
    queryFn: () =>
      request<RecordData>(
        `/api/v1/accounting/reports/gstr1?from=${from}&to=${to}${branchParam}`,
      ),
    enabled: sub === "gstr1" && !!from && !!to,
  });
  const fleet = useQuery({
    queryKey: ["report-trucks", from, to],
    queryFn: () =>
      request<RecordData>(
        `/api/v1/accounting/reports/truck-profitability?from=${from}&to=${to}`,
      ),
    enabled: sub === "trucks" && !!from && !!to,
  });

  const accounts = (trial.data?.accounts as RecordData[] | undefined) ?? [];
  const entries = (ledger.data?.entries as RecordData[] | undefined) ?? [];
  const incomeRows = (pnl.data?.incomeAccounts as RecordData[] | undefined) ?? [];
  const expenseRows = (pnl.data?.expenseAccounts as RecordData[] | undefined) ?? [];
  const assetRows = (bs.data?.assets as RecordData[] | undefined) ?? [];
  const liabilityRows = (bs.data?.liabilities as RecordData[] | undefined) ?? [];
  const equityRows = (bs.data?.equity as RecordData[] | undefined) ?? [];
  const gstrInvoices = (gstr1.data?.invoices as RecordData[] | undefined) ?? [];
  const gstrHsn = (gstr1.data?.hsnSummary as RecordData[] | undefined) ?? [];
  const truckRows = (fleet.data?.trucks as RecordData[] | undefined) ?? [];

  return (
    <>
      <div className="tabs">
        {[
          ["trial", "Trial Balance & Ledger"],
          ["pnl", "Profit & Loss"],
          ["bs", "Balance Sheet"],
          ["gstr1", "GSTR-1 & HSN"],
          ["trucks", "Truck Profitability"],
        ].map(([id, caption]) => (
          <button
            key={id}
            type="button"
            className={sub === id ? "tab active" : "tab"}
            onClick={() => setSub(id)}
          >
            {caption}
          </button>
        ))}
      </div>

      <div className="panel form-panel">
        <div className="form-grid">
          {(sub === "trial" || sub === "bs") && (
            <Field label="As of date">
              <input
                type="date"
                value={asOf}
                max={today()}
                onChange={(event) => setAsOf(event.target.value)}
              />
            </Field>
          )}
          {sub !== "bs" && (
            <>
              <Field label="From date">
                <input
                  type="date"
                  value={from}
                  onChange={(event) => setFrom(event.target.value)}
                />
              </Field>
              <Field label="To date">
                <input
                  type="date"
                  value={to}
                  onChange={(event) => setTo(event.target.value)}
                />
              </Field>
            </>
          )}
          {sub !== "trucks" && (
            <Field label="Branch filter (optional)">
              <select
                value={branchId}
                onChange={(event) => setBranchId(event.target.value)}
              >
                <Choices
                  path="/api/v1/branches"
                  empty="All branches (Consolidated)"
                  labelOf={(row) =>
                    `${row.code} · ${row.name}${row.gstin ? ` (${row.gstin})` : ""}`
                  }
                />
              </select>
            </Field>
          )}
        </div>
      </div>

      <ErrorBox
        error={
          trial.error ||
          ledger.error ||
          pnl.error ||
          bs.error ||
          gstr1.error ||
          fleet.error
        }
      />

      {sub === "trial" && (
        <>
          <div className="panel">
            <div className="panel-heading">
              <h3>Trial balance as of {asOf}</h3>
              <CsvExportButton
                filename={`yfc-trial-balance-${asOf}.csv`}
                headers={["Code", "Name", "Type", "Debit Balance", "Credit Balance"]}
                rows={accounts.map((a) => [
                  String(a.code ?? ""),
                  String(a.name ?? ""),
                  String(a.type ?? ""),
                  String(a.debit_balance ?? "0"),
                  String(a.credit_balance ?? "0"),
                ])}
              />
            </div>
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
                <select
                  value={account}
                  onChange={(event) => setAccount(event.target.value)}
                >
                  <Choices
                    path="/api/v1/accounting/accounts"
                    empty="Select account"
                    labelOf={(row) => `${row.code} · ${row.name}`}
                  />
                </select>
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
      )}

      {sub === "pnl" && pnl.data && (
        <div className="panel form-panel">
          <div className="panel-heading">
            <div>
              <h3>
                Profit &amp; Loss ({from} to {to})
              </h3>
              <span className="muted">
                Income: {money(pnl.data.totalIncome)} · Expenses:{" "}
                {money(pnl.data.totalExpense)} ·{" "}
                <strong>Net Profit: {money(pnl.data.netProfit)}</strong>
              </span>
            </div>
            <CsvExportButton
              filename={`yfc-pnl-${from}-to-${to}.csv`}
              headers={["Section", "Code", "Account", "Amount (INR)"]}
              rows={[
                ...incomeRows.map((r) => ["INCOME", String(r.code), String(r.name), String(r.amount)]),
                ...expenseRows.map((r) => ["EXPENSE", String(r.code), String(r.name), String(r.amount)]),
                ["NET_PROFIT", "", "Net Profit / (Loss)", String(pnl.data.netProfit)],
              ]}
            />
          </div>
          <h4>Income</h4>
          <DataTable
            rows={incomeRows}
            columns={[
              { label: "Code", keys: ["code"] },
              { label: "Account", keys: ["name"] },
              { label: "Amount (INR)", keys: ["amount"] },
            ]}
          />
          <h4>Expenses</h4>
          <DataTable
            rows={expenseRows}
            columns={[
              { label: "Code", keys: ["code"] },
              { label: "Account", keys: ["name"] },
              { label: "Amount (INR)", keys: ["amount"] },
            ]}
          />
        </div>
      )}

      {sub === "bs" && bs.data && (
        <div className="panel form-panel">
          <div className="panel-heading">
            <div>
              <h3>Balance Sheet as of {asOf}</h3>
              <span className="muted">
                Assets: {money(bs.data.totalAssets)} · Liabilities:{" "}
                {money(bs.data.totalLiabilities)} · Equity + Retained Earnings:{" "}
                {money(bs.data.totalEquity)} (
                {bs.data.balanced ? "Balanced" : "Out of balance"})
              </span>
            </div>
            <CsvExportButton
              filename={`yfc-balance-sheet-${asOf}.csv`}
              headers={["Section", "Code", "Account", "Balance (INR)"]}
              rows={[
                ...assetRows.map((r) => ["ASSET", String(r.code), String(r.name), String(r.balance)]),
                ...liabilityRows.map((r) => ["LIABILITY", String(r.code), String(r.name), String(r.balance)]),
                ...equityRows.map((r) => ["EQUITY", String(r.code), String(r.name), String(r.balance)]),
                ["EQUITY", "RE", "Retained Earnings (Current + Prior)", String(bs.data.retainedEarnings)],
              ]}
            />
          </div>
          <h4>Assets ({money(bs.data.totalAssets)})</h4>
          <DataTable
            rows={assetRows}
            columns={[
              { label: "Code", keys: ["code"] },
              { label: "Account", keys: ["name"] },
              { label: "Balance (INR)", keys: ["balance"] },
            ]}
          />
          <h4>Liabilities ({money(bs.data.totalLiabilities)})</h4>
          <DataTable
            rows={liabilityRows}
            columns={[
              { label: "Code", keys: ["code"] },
              { label: "Account", keys: ["name"] },
              { label: "Balance (INR)", keys: ["balance"] },
            ]}
          />
          <h4>
            Equity ({money(bs.data.totalEquity)} · Retained Earnings:{" "}
            {money(bs.data.retainedEarnings)})
          </h4>
          <DataTable
            rows={equityRows}
            columns={[
              { label: "Code", keys: ["code"] },
              { label: "Account", keys: ["name"] },
              { label: "Balance (INR)", keys: ["balance"] },
            ]}
          />
        </div>
      )}

      {sub === "gstr1" && gstr1.data && (
        <div className="panel form-panel">
          <div className="panel-heading">
            <div>
              <h3>
                GSTR-1 Outward Sales Register ({from} to {to})
              </h3>
              <span className="muted">
                {String(gstr1.data.invoiceCount)} invoices · Taxable{" "}
                {money(gstr1.data.totalTaxable)} · CGST {money(gstr1.data.totalCgst)} ·
                SGST {money(gstr1.data.totalSgst)} · IGST {money(gstr1.data.totalIgst)} ·
                Total {money(gstr1.data.totalPayable)}
              </span>
            </div>
            <div className="heading-action">
              <CsvExportButton
                label="Export Invoices CSV"
                filename={`yfc-gstr1-invoices-${from}-to-${to}.csv`}
                headers={[
                  "Invoice No",
                  "Date",
                  "Type",
                  "Branch",
                  "Branch GSTIN",
                  "Customer",
                  "Customer GSTIN",
                  "Supply",
                  "Taxable",
                  "CGST",
                  "SGST",
                  "IGST",
                  "Total",
                ]}
                rows={gstrInvoices.map((r) => [
                  String(r.invoice_number ?? ""),
                  String(r.prepared_on ?? ""),
                  String(r.category ?? ""),
                  String(r.branch_code ?? ""),
                  String(r.branch_gstin ?? ""),
                  String(r.customer_name ?? ""),
                  String(r.customer_gstin ?? ""),
                  String(r.supply ?? ""),
                  String(r.taxable_amount ?? "0"),
                  String(r.cgst_amount ?? "0"),
                  String(r.sgst_amount ?? "0"),
                  String(r.igst_amount ?? "0"),
                  String(r.payable_total ?? "0"),
                ])}
              />
              <CsvExportButton
                label="Export HSN CSV"
                filename={`yfc-gstr1-hsn-${from}-to-${to}.csv`}
                headers={[
                  "HSN / SAC",
                  "Component",
                  "GST %",
                  "Lines",
                  "Taxable",
                  "CGST",
                  "SGST",
                  "IGST",
                  "Total Tax",
                ]}
                rows={gstrHsn.map((r) => [
                  String(r.hsn_or_sac ?? ""),
                  String(r.component ?? ""),
                  String(r.rate_percent ?? "0"),
                  String(r.line_count ?? "0"),
                  String(r.taxable_amount ?? "0"),
                  String(r.cgst_amount ?? "0"),
                  String(r.sgst_amount ?? "0"),
                  String(r.igst_amount ?? "0"),
                  String(r.tax_total ?? "0"),
                ])}
              />
            </div>
          </div>
          <h4>B2B / B2C Tax Invoices</h4>
          <DataTable
            rows={gstrInvoices}
            columns={[
              { label: "Invoice No", keys: ["invoice_number"] },
              { label: "Date", keys: ["prepared_on"] },
              { label: "Type", keys: ["category"] },
              { label: "Branch GSTIN", keys: ["branch_gstin"] },
              { label: "Customer", keys: ["customer_name"] },
              { label: "Customer GSTIN", keys: ["customer_gstin"] },
              { label: "Taxable", keys: ["taxable_amount"] },
              { label: "Tax", keys: ["tax_total"] },
              { label: "Total", keys: ["payable_total"] },
            ]}
          />
          <h4>HSN / SAC Summary</h4>
          <DataTable
            rows={gstrHsn}
            columns={[
              { label: "HSN / SAC", keys: ["hsn_or_sac"] },
              { label: "Component", keys: ["component"] },
              { label: "GST %", keys: ["rate_percent"] },
              { label: "Taxable", keys: ["taxable_amount"] },
              { label: "CGST", keys: ["cgst_amount"] },
              { label: "SGST", keys: ["sgst_amount"] },
              { label: "IGST", keys: ["igst_amount"] },
            ]}
          />
          <h4>
            GSTR-1 Table 9B — Credit &amp; Debit Notes (
            {String(gstr1.data.noteCount ?? 0)} approved · Net Payable{" "}
            {money(gstr1.data.netPayable ?? gstr1.data.totalPayable)})
          </h4>
          <DataTable
            rows={(gstr1.data.creditDebitNotes as RecordData[] | undefined) ?? []}
            columns={[
              { label: "Note No", keys: ["note_number"] },
              { label: "Type", keys: ["note_type"] },
              { label: "Table", keys: ["category"] },
              { label: "Date", keys: ["note_date"] },
              { label: "Original Invoice", keys: ["original_invoice_number"] },
              { label: "Customer", keys: ["customer_name"] },
              { label: "Taxable", keys: ["taxable_amount"] },
              { label: "Tax", keys: ["tax_total"] },
              { label: "Total", keys: ["total_amount"] },
            ]}
          />
        </div>
      )}

      {sub === "trucks" && fleet.data && (
        <div className="panel form-panel">
          <div className="panel-heading">
            <div>
              <h3>
                Owned Fleet — Truck Operating Cost &amp; Utilisation ({from} to {to})
              </h3>
              <span className="muted">
                {String(fleet.data.totalTrips)} delivered trips ·{" "}
                {String(fleet.data.totalTonnes)} t · {String(fleet.data.totalKm)} km ·
                Fuel {money(fleet.data.totalFuelCost)} · Road Expenses{" "}
                {money(fleet.data.totalRoadExpenses)} · Maintenance{" "}
                {money(fleet.data.totalMaintenanceCost ?? 0)} · Total Cost{" "}
                <strong>{money(fleet.data.totalOperatingCost)}</strong>
              </span>
            </div>
            <CsvExportButton
              filename={`yfc-truck-profitability-${from}-to-${to}.csv`}
              headers={[
                "Registration",
                "Branch",
                "Status",
                "Trips",
                "Tonnes",
                "Km",
                "Fuel (L)",
                "Fuel Cost",
                "Road Expenses",
                "Maintenance Cost",
                "Total Cost",
                "Cost / Tonne",
                "Cost / Km",
              ]}
              rows={truckRows.map((r) => [
                String(r.registration ?? ""),
                String(r.branch ?? ""),
                String(r.status ?? ""),
                String(r.tripsCompleted ?? "0"),
                String(r.tonnesDelivered ?? "0"),
                String(r.totalKm ?? "0"),
                String(r.fuelLitres ?? "0"),
                String(r.fuelCost ?? "0"),
                String(r.roadExpenses ?? "0"),
                String(r.maintenanceCost ?? "0"),
                String(r.totalOperatingCost ?? "0"),
                String(r.costPerTonne ?? "0"),
                String(r.costPerKm ?? "0"),
              ])}
            />
          </div>
          <DataTable
            rows={truckRows}
            columns={[
              { label: "Truck", keys: ["registration"] },
              { label: "Branch", keys: ["branch"] },
              { label: "Trips", keys: ["tripsCompleted"] },
              { label: "Tonnes", keys: ["tonnesDelivered"] },
              { label: "Km", keys: ["totalKm"] },
              { label: "Fuel (₹)", keys: ["fuelCost"] },
              { label: "Expenses (₹)", keys: ["roadExpenses"] },
              { label: "Maintenance (₹)", keys: ["maintenanceCost"] },
              { label: "Total Cost", keys: ["totalOperatingCost"] },
              { label: "₹ / Tonne", keys: ["costPerTonne"] },
              { label: "₹ / Km", keys: ["costPerKm"] },
            ]}
          />
        </div>
      )}
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
        description="Link each party to a fresh chart account. Customer credit limits are enforced on order conversion and trip dispatch."
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
      <AgeingReport />
    </>
  );
}

function AgeingReport() {
  const [asOf, setAsOf] = useState(today());
  const [partyType, setPartyType] = useState("");
  const q = useQuery({
    queryKey: ["ageing", asOf, partyType],
    queryFn: () =>
      request<{
        as_of: string;
        totals: RecordData;
        parties: RecordData[];
      }>(
        `/api/v1/accounting/ageing?asOf=${asOf}${partyType ? `&partyType=${partyType}` : ""}`,
      ),
  });
  const parties = q.data?.parties ?? [];
  const totals = q.data?.totals;
  const csvRows = parties.map((p) => [
    String(p.party_type),
    String(p.party_name),
    Number(p.payment_terms_days ?? 0),
    Number(p.open_bills ?? 0),
    Number(p.current ?? 0),
    Number(p.days_1_30 ?? 0),
    Number(p.days_31_60 ?? 0),
    Number(p.days_61_90 ?? 0),
    Number(p.days_90_plus ?? 0),
    Number(p.total_outstanding ?? 0),
  ]);
  return (
    <div className="panel">
      <div className="panel-heading">
        <div>
          <h3>AR / AP Ageing Report</h3>
          <p className="muted">
            Posted, unpaid bills grouped by days overdue past payment terms.
          </p>
        </div>
        <CsvExportButton
          filename={`yfc-ageing-${asOf}.csv`}
          headers={[
            "Type",
            "Party",
            "Terms (days)",
            "Open Bills",
            "Current",
            "1-30 Days",
            "31-60 Days",
            "61-90 Days",
            "90+ Days",
            "Total Outstanding",
          ]}
          rows={csvRows}
        />
      </div>
      <div className="form-grid" style={{ marginBottom: 14 }}>
        <Field label="As of date">
          <input
            type="date"
            value={asOf}
            onChange={(event) => setAsOf(event.target.value)}
          />
        </Field>
        <Field label="Party filter">
          <select
            value={partyType}
            onChange={(event) => setPartyType(event.target.value)}
          >
            <option value="">All (AR &amp; AP)</option>
            <option value="CUSTOMER">Customers (AR)</option>
            <option value="SUPPLIER">Suppliers (AP)</option>
          </select>
        </Field>
      </div>
      <ErrorBox error={q.error} />
      {totals && (
        <div className="summary">
          <div>
            <small>Total Outstanding</small>
            <strong>{money(totals.total_outstanding)}</strong>
          </div>
          <div>
            <small>Current (Not Due)</small>
            <strong>{money(totals.current)}</strong>
          </div>
          <div>
            <small>1–30 Days Overdue</small>
            <strong>{money(totals.days_1_30)}</strong>
          </div>
          <div>
            <small>31–90+ Days Overdue</small>
            <strong>
              {money(
                Number(totals.days_31_60 ?? 0) +
                  Number(totals.days_61_90 ?? 0) +
                  Number(totals.days_90_plus ?? 0),
              )}
            </strong>
          </div>
        </div>
      )}
      {parties.length === 0 && !q.isPending ? (
        <p className="muted">No unpaid posted bills as of {asOf}.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Party</th>
                <th>Type</th>
                <th>Bills</th>
                <th>Current</th>
                <th>1–30d</th>
                <th>31–60d</th>
                <th>61–90d</th>
                <th>90+d</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {parties.map((p) => (
                <tr key={`${p.party_type}-${p.party_id}`}>
                  <td>
                    <strong>{String(p.party_name)}</strong>
                  </td>
                  <td>{String(p.party_type)}</td>
                  <td>{String(p.open_bills)}</td>
                  <td>{money(p.current)}</td>
                  <td>{money(p.days_1_30)}</td>
                  <td>{money(p.days_31_60)}</td>
                  <td>{money(p.days_61_90)}</td>
                  <td>{money(p.days_90_plus)}</td>
                  <td>
                    <strong>{money(p.total_outstanding)}</strong>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
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
  const [section, setSection] = useState<"bills" | "notes">("bills");
  const [kind, setKind] = useState("SALES_ORDER");
  const [search, setSearch] = useState("");
  const [kindFilter, setKindFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [branchFilter, setBranchFilter] = useState("");
  const debouncedSearch = useDebounced(search);
  const list = usePaged(
    "billing",
    queryPath("/api/v1/accounting/billing-preparations", {
      q: debouncedSearch,
      kind: kindFilter,
      status: statusFilter,
      branchId: branchFilter,
    }),
  );
  return (
    <>
      <Heading
        eyebrow="FINANCE / BILLING & GST NOTES"
        title="Prepare bills, GST invoices, and Credit/Debit notes."
        description="Sales bills use the ordered quantity. Supplier bills use the loaded quantity. Issue CGST Section 34 Credit or Debit Notes against any taxed invoice."
        action={
          section === "bills" ? (
            <CsvExportButton
              filename={`yfc-billing-register-${today()}.csv`}
              headers={[
                "Bill ID",
                "Invoice No",
                "Kind",
                "Party",
                "Prepared On",
                "Status",
                "Payment Status",
                "Subtotal",
                "Payable Total",
                "Paid",
                "Outstanding",
              ]}
              rows={list.items.map((r) => [
                short(String(r.id)),
                String(val(r, "invoice_number") ?? ""),
                String(val(r, "kind") ?? ""),
                String(val(r, "party_name") ?? ""),
                String(val(r, "prepared_on") ?? ""),
                String(val(r, "status") ?? ""),
                String(val(r, "payment_status") ?? ""),
                String(val(r, "subtotal") ?? ""),
                String(val(r, "payable_total") ?? ""),
                String(val(r, "allocated_amount") ?? ""),
                String(val(r, "outstanding_amount") ?? ""),
              ])}
            />
          ) : undefined
        }
      />
      <div className="tabs">
        <button
          type="button"
          className={section === "bills" ? "tab active" : "tab"}
          onClick={() => setSection("bills")}
        >
          Bills &amp; Tax Invoices
        </button>
        <button
          type="button"
          className={section === "notes" ? "tab active" : "tab"}
          onClick={() => setSection("notes")}
        >
          Credit &amp; Debit Notes (GST Sec. 34)
        </button>
      </div>
      {section === "notes" ? (
        <GstNotesSection />
      ) : (
        <>
      <SaveForm title="Prepare a bill" path="/api/v1/accounting/billing-preparations">
        <IdField name="preparationId" caption="Preparation id" />
        <div className="form-grid">
          <Field label="Kind">
            <select name="kind" value={kind} onChange={(event) => setKind(event.target.value)}>
              <option value="SALES_ORDER">Sales order</option>
              <option value="PURCHASE_RECEIPT">Purchase receipt</option>
            </select>
          </Field>
          <Field label={kind === "SALES_ORDER" ? "Sales order" : "Purchase receipt"}>
            {kind === "SALES_ORDER" ? (
              <select name="sourceId" required>
                <Choices
                  path="/api/v1/orders"
                  empty="Select sales order"
                  labelOf={(r) =>
                    `${short(r.id)} · ${r.material ?? ""} · ${r.orderedQuantity ?? r.ordered_quantity ?? ""} t (${money(r.subtotal)})`
                  }
                />
              </select>
            ) : (
              <select name="sourceId" required>
                <Choices
                  path="/api/v1/purchase-receipts"
                  empty="Select purchase receipt"
                  labelOf={(r) =>
                    `${short(r.id)} · ${r.supplier_name ?? r.supplierName ?? ""} · ${r.material_name ?? r.materialName ?? ""} · ${r.loaded_quantity ?? r.loadedQuantity ?? ""} t (${r.received_date ?? r.receivedDate ?? ""})`
                  }
                />
              </select>
            )}
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
        <FilterBar
          active={!!(search || kindFilter || statusFilter || branchFilter)}
          onReset={() => {
            setSearch("");
            setKindFilter("");
            setStatusFilter("");
            setBranchFilter("");
          }}
        >
          <input
            type="search"
            aria-label="Search bills"
            placeholder="Search party, reference, or invoice #…"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <select
            aria-label="Filter bills by kind"
            value={kindFilter}
            onChange={(event) => setKindFilter(event.target.value)}
          >
            <option value="">All kinds</option>
            <option value="SALES_ORDER">Sales invoices</option>
            <option value="PURCHASE_RECEIPT">Supplier bills</option>
          </select>
          <select
            aria-label="Filter bills by status"
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
          >
            <option value="">All statuses</option>
            <option value="DRAFT">Draft</option>
            <option value="READY">Ready</option>
            <option value="REJECTED">Rejected</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
          <select
            aria-label="Filter bills by branch"
            value={branchFilter}
            onChange={(event) => setBranchFilter(event.target.value)}
          >
            <Choices path="/api/v1/branches" empty="All branches" />
          </select>
        </FilterBar>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Bill / Invoice</th>
                <th>Party</th>
                <th>Kind</th>
                <th>Status</th>
                <th>Payment</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {list.items.map((row) => (
                <tr key={String(row.id)}>
                  <td>
                    <Link to={`/billing/${row.id}`}>
                      {val(row, "invoice_number")
                        ? String(val(row, "invoice_number"))
                        : short(String(row.id))}
                    </Link>
                  </td>
                  <td>{String(val(row, "party_name") ?? "—")}</td>
                  <td>{String(val(row, "kind") ?? "—")}</td>
                  <td>{String(val(row, "status") ?? "—")}</td>
                  <td>{String(val(row, "payment_status") ?? "—")}</td>
                  <td>{money(Number(val(row, "payable_total", "subtotal") ?? 0))}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager page={list.page} setPage={list.setPage} hasNext={list.hasNext} />
      </div>
        </>
      )}
    </>
  );
}

function GstNotesSection() {
  const username = useAccess().username;
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [branchFilter, setBranchFilter] = useState("");
  const [selectedId, setSelectedId] = useState("");
  const [decision, setDecision] = useState<{ id: string; mode: "approve" | "cancel" } | null>(null);
  const debouncedSearch = useDebounced(search);

  const notes = usePaged(
    "gst-notes",
    queryPath("/api/v1/accounting/gst-notes", {
      q: debouncedSearch,
      noteType: typeFilter,
      status: statusFilter,
      branchId: branchFilter,
    }),
  );
  const detail = useQuery({
    queryKey: ["gst-note", selectedId],
    queryFn: () => request<RecordData>(`/api/v1/accounting/gst-notes/${selectedId}`),
    enabled: !!selectedId,
  });

  return (
    <>
      <SaveForm
        title="Issue a GST Credit Note or Debit Note"
        path="/api/v1/accounting/gst-notes"
      >
        <Notice title="CGST Section 34 dual-control rule">
          Select any READY bill with assessed GST. Per-state FY sequential numbers (CN-YYZZ-SS-00001 / DN-YYZZ-SS-00001) are issued automatically. A different accounts user must approve the draft before it appears in GSTR-1 Table 9B.
        </Notice>
        <div className="form-grid">
          <Field label="Original taxed bill / invoice">
            <select name="originalPreparationId" required>
              <Choices
                path="/api/v1/accounting/billing-preparations?status=READY"
                empty="Select READY taxed bill"
                filter={(r) => String(r.tax_treatment) === "ASSESSED"}
                labelOf={(r) =>
                  `${r.invoice_number ?? short(r.id)} · ${r.party_name ?? ""} (${money(r.payable_total ?? r.subtotal)})`
                }
              />
            </select>
          </Field>
          <Field label="Note type">
            <select name="noteType" required defaultValue="CREDIT_NOTE">
              <option value="CREDIT_NOTE">Credit Note (reduces invoice value)</option>
              <option value="DEBIT_NOTE">Debit Note (increases invoice value)</option>
            </select>
          </Field>
          <Field label="Note date">
            <input name="noteDate" type="date" max={today()} defaultValue={today()} required />
          </Field>
          <Field label="Reason category">
            <select name="reasonCode" required defaultValue="RATE_DIFFERENCE">
              <option value="SALES_RETURN">Sales return</option>
              <option value="RATE_DIFFERENCE">Rate difference</option>
              <option value="WEIGHT_SHORTAGE">Weight shortage / weighbridge tolerance</option>
              <option value="QUALITY_DEDUCTION">Quality deduction</option>
              <option value="POST_SALE_DISCOUNT">Post-sale discount</option>
              <option value="ADDITIONAL_CHARGE">Additional charge (detention / demurrage)</option>
            </select>
          </Field>
          <Field label="HSN / SAC">
            <input name="hsnOrSac" required pattern="[0-9]{4,8}" placeholder="e.g. 2517 or 996511" />
          </Field>
          <Field label="Taxable adjustment amount (INR)">
            <input name="taxableAmount" type="number" min="0.01" step="0.01" required />
          </Field>
          <Field label="GST rate (%)">
            <input name="gstRatePercent" type="number" min="0" max="100" step="0.01" defaultValue="5.00" required />
          </Field>
        </div>
        <Field label="Reason & supporting evidence">
          <textarea name="reasonDetail" required maxLength={1000} />
        </Field>
      </SaveForm>

      <ErrorBox error={notes.q.error || detail.error} />

      {detail.data && (
        <div className="portal-doc-preview">
          <div className="portal-doc-preview-bar no-print">
            <strong>
              {String(detail.data.note_number)} · {String(detail.data.status)}
            </strong>
            <div className="actions">
              {detail.data.status === "DRAFT" && !ownedBy(detail.data, username) && (
                <button
                  type="button"
                  onClick={() => setDecision({ id: String(detail.data.id), mode: "approve" })}
                >
                  Approve note
                </button>
              )}
              {detail.data.status === "DRAFT" && (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => setDecision({ id: String(detail.data.id), mode: "cancel" })}
                >
                  Cancel note
                </button>
              )}
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setSelectedId("");
                  setDecision(null);
                }}
              >
                Close
              </button>
            </div>
          </div>
          {decision && decision.id === String(detail.data.id) && (
            <div className="no-print" style={{ marginBottom: 14 }}>
              <ReasonForm
                path={`/api/v1/accounting/gst-notes/${decision.id}/${decision.mode}`}
                submitLabel={decision.mode === "approve" ? "Confirm approval" : "Confirm cancel"}
                onDone={() => setDecision(null)}
              />
            </div>
          )}
          <PrintableAdjustmentNote note={detail.data} />
        </div>
      )}

      <div className="panel">
        <div className="panel-heading">
          <h3>Credit &amp; Debit Note Register</h3>
          <CsvExportButton
            filename={`yfc-gst-notes-${today()}.csv`}
            headers={[
              "Note No",
              "Type",
              "Date",
              "Original Invoice",
              "Party",
              "Reason",
              "Taxable",
              "Tax",
              "Total",
              "Status",
            ]}
            rows={notes.items.map((r) => [
              String(r.note_number ?? ""),
              String(r.note_type ?? ""),
              String(r.note_date ?? ""),
              String(r.original_invoice_number ?? ""),
              String(r.party_name ?? ""),
              String(r.reason_code ?? ""),
              String(r.taxable_amount ?? "0"),
              String(r.tax_total ?? "0"),
              String(r.total_amount ?? "0"),
              String(r.status ?? ""),
            ])}
          />
        </div>
        <FilterBar
          active={!!(search || typeFilter || statusFilter || branchFilter)}
          onReset={() => {
            setSearch("");
            setTypeFilter("");
            setStatusFilter("");
            setBranchFilter("");
          }}
        >
          <input
            type="search"
            aria-label="Search GST notes"
            placeholder="Search note #, invoice #, or party…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <select
            aria-label="Filter GST notes by type"
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
          >
            <option value="">All note types</option>
            <option value="CREDIT_NOTE">Credit Notes</option>
            <option value="DEBIT_NOTE">Debit Notes</option>
          </select>
          <select
            aria-label="Filter GST notes by status"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="">All statuses</option>
            <option value="DRAFT">Draft</option>
            <option value="APPROVED">Approved</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
          <select
            aria-label="Filter GST notes by branch"
            value={branchFilter}
            onChange={(e) => setBranchFilter(e.target.value)}
          >
            <Choices path="/api/v1/branches" empty="All branches" />
          </select>
        </FilterBar>
        <DataTable
          rows={notes.items}
          onOpen={(row) => setSelectedId(String(row.id))}
          columns={[
            { label: "Note No", keys: ["note_number"] },
            { label: "Type", keys: ["note_type"] },
            { label: "Date", keys: ["note_date"] },
            { label: "Original Invoice", keys: ["original_invoice_number"] },
            { label: "Party", keys: ["party_name"] },
            { label: "Total (₹)", keys: ["total_amount"] },
            { label: "Status", keys: ["status"], status: true },
          ]}
        />
        <Pager page={notes.page} setPage={notes.setPage} hasNext={notes.hasNext} />
      </div>
    </>
  );
}

function TaxForm({
  id,
  lines,
  correcting,
  suggestedSupply,
}: {
  id: string;
  lines: RecordData[];
  correcting?: boolean;
  suggestedSupply?: string;
}) {
  const client = useQueryClient();
  const [supply, setSupply] = useState(suggestedSupply || "INTRA_STATE");
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
          <PrintableInvoice bill={q.data} />
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
              suggestedSupply={
                val(q.data, "suggested_supply")
                  ? String(val(q.data, "suggested_supply"))
                  : undefined
              }
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
          {status === "READY" && Boolean(val(q.data, "ledger_posted")) && (
            <AllocationSection
              id={id}
              kind={String(val(q.data, "kind") ?? "")}
              outstanding={Number(val(q.data, "outstanding_amount") ?? 0)}
            />
          )}
        </div>
      )}
    </>
  );
}

function AllocationSection({
  id,
  kind,
  outstanding,
}: {
  id: string;
  kind: string;
  outstanding: number;
}) {
  const [reversing, setReversing] = useState<string | null>(null);
  const expectedVoucherKind =
    kind === "SALES_ORDER" ? "CUSTOMER_RECEIPT" : "SUPPLIER_PAYMENT";
  const list = useQuery({
    queryKey: ["allocations", id],
    queryFn: () =>
      request<RecordData[]>(
        `/api/v1/accounting/allocations?preparationId=${encodeURIComponent(id)}&size=100`,
      ),
  });
  return (
    <div style={{ marginTop: "1.5rem" }}>
      <h3>Payment allocations</h3>
      <ErrorBox error={list.error} />
      {list.data && list.data.length > 0 ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Voucher</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Note</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {list.data.map((r) => (
                <tr key={String(r.id)}>
                  <td>{String(r.voucher_reference ?? short(r.voucher_id))}</td>
                  <td>{money(r.amount)}</td>
                  <td>
                    <span className={`badge ${String(r.status).toLowerCase()}`}>
                      {String(r.status)}
                    </span>
                  </td>
                  <td>{String(r.note ?? "—")}</td>
                  <td>
                    {r.status === "ACTIVE" ? (
                      <button
                        className="secondary"
                        type="button"
                        onClick={() => setReversing(String(r.id))}
                      >
                        Reverse
                      </button>
                    ) : (
                      "—"
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="muted">No payment allocations recorded against this bill yet.</p>
      )}
      {reversing && (
        <ReasonForm
          path={`/api/v1/accounting/allocations/${reversing}/reverse`}
          submitLabel="Reverse allocation"
          onDone={() => setReversing(null)}
        />
      )}
      {outstanding > 0 && (
        <SaveForm
          title="Allocate a posted receipt or payment"
          path="/api/v1/accounting/allocations"
        >
          <input type="hidden" name="preparationId" value={id} />
          <IdField name="allocationId" caption="Allocation id" />
          <div className="form-grid">
            <Field label="Posted voucher">
              <select name="voucherId" required>
                <Choices
                  path="/api/v1/accounting/money-vouchers"
                  empty="Select posted voucher"
                  filter={(row) =>
                    row.effective_status === "POSTED" &&
                    row.kind === expectedVoucherKind &&
                    Number(row.unallocated_amount ?? 0) > 0
                  }
                  labelOf={(row) =>
                    `${row.reference} · unallocated ${money(row.unallocated_amount)}`
                  }
                />
              </select>
            </Field>
            <Field label="Amount (INR)">
              <input
                name="amount"
                type="number"
                step="0.01"
                min="0.01"
                max={outstanding}
                defaultValue={outstanding.toFixed(2)}
                required
              />
            </Field>
          </div>
          <Field label="Note (optional)">
            <input name="note" maxLength={1000} />
          </Field>
        </SaveForm>
      )}
    </div>
  );
}
