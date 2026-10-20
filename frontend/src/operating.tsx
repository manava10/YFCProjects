import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { request, type RecordData } from "./api";
import { money, ownedBy, today, val } from "./lib";
import { Documents } from "./documents";
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

export function Operating() {
  const { admin, write, review } = useAccess();
  const [tab, setTab] = useState("fuel");
  return (
    <>
      <Heading
        eyebrow="OPERATING"
        title="Fuel receipts and road expenses."
        description="A different accounts user approves each pending revision. Approved cost is not an accounting posting."
      />
      <div className="tabs">
        {[
          ["fuel", "Fuel"],
          ["expenses", "Expenses"],
          ["rates", "Diesel rates"],
        ].map(([id, caption]) => (
          <button key={id} className={tab === id ? "tab active" : "tab"} onClick={() => setTab(id)}>
            {caption}
          </button>
        ))}
      </div>
      {tab === "fuel" && <Fuel write={write} review={review} />}
      {tab === "expenses" && <Expenses write={write} review={review} />}
      {tab === "rates" && <Rates admin={admin} />}
    </>
  );
}

function Fuel({ write, review }: { write: boolean; review: boolean }) {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState("");
  const list = usePaged("fuel", "/api/v1/fuel/entries");
  const one = useQuery({
    queryKey: ["fuel", selected],
    queryFn: () => request<RecordData>(`/api/v1/fuel/entries/${selected}`),
    enabled: !!selected,
  });
  const history = useQuery({
    queryKey: ["fuel-history", selected],
    queryFn: () => request<RecordData[]>(`/api/v1/fuel/entries/${selected}/history`),
    enabled: !!selected,
  });
  return (
    <>
      {write && (
        <button onClick={() => setOpen((value) => !value)}>
          <Plus size={16} /> Fuel receipt
        </button>
      )}
      {open && (
        <SaveForm title="Fuel receipt" path="/api/v1/fuel/entries">
          <IdField name="entryId" caption="Receipt id" />
          <div className="form-grid">
            <TruckField write={write} />
            <Field label="Diesel rate id">
              <input name="rateId" required />
            </Field>
            <Field label="Trip id (optional)">
              <input name="tripId" />
            </Field>
            <Field label="Receipt reference">
              <input name="receiptReference" required maxLength={60} />
            </Field>
            <Field label="Filled on">
              <input name="filledOn" type="date" max={today()} required />
            </Field>
            <Field label="Litres">
              <input name="quantityLitres" type="number" min="0.001" step="0.001" required />
            </Field>
            <Field label="Actual ₹ / litre">
              <input name="actualPricePerLitre" type="number" min="0.01" step="0.01" required />
            </Field>
            <Field label="Odometer">
              <input name="odometer" type="number" min="0" step="0.1" required />
            </Field>
            <Field label="Filled at (Asia/Kolkata)">
              <input name="filledAt" type="datetime-local" required />
            </Field>
          </div>
          <Field label="Receipt evidence">
            <input name="receiptEvidence" maxLength={300} required />
          </Field>
          <Field label="Price variance reason">
            <textarea name="priceVarianceReason" maxLength={1000} />
          </Field>
        </SaveForm>
      )}
      <ErrorBox error={list.q.error || one.error || history.error} />
      <div className="panel">
        <DataTable
          rows={list.items}
          onOpen={(row) => setSelected(String(row.id))}
          columns={[
            { label: "Reference", keys: ["receiptReference", "receipt_reference"] },
            { label: "Filled", keys: ["filledOn", "filled_on"] },
            { label: "Litres", keys: ["quantityLitres", "quantity_litres"] },
            { label: "Amount", keys: ["amount"] },
            { label: "Status", keys: ["status"], status: true },
          ]}
        />
        <Pager page={list.page} setPage={list.setPage} hasNext={list.hasNext} />
      </div>
      {one.data && (
        <OperatingDetail
          row={one.data}
          history={history.data}
          review={review}
          write={write}
          kind="FUEL"
          approve={`/api/v1/fuel/entries/${selected}/approve`}
          reject={`/api/v1/fuel/entries/${selected}/reject`}
          reverse={`/api/v1/fuel/entries/${selected}/reversals`}
        />
      )}
    </>
  );
}

function Expenses({ write, review }: { write: boolean; review: boolean }) {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState("");
  const list = usePaged("expenses", "/api/v1/expenses");
  const one = useQuery({
    queryKey: ["expense", selected],
    queryFn: () => request<RecordData>(`/api/v1/expenses/${selected}`),
    enabled: !!selected,
  });
  return (
    <>
      {write && (
        <button onClick={() => setOpen((value) => !value)}>
          <Plus size={16} /> Expense
        </button>
      )}
      {open && (
        <SaveForm title="Operating expense" path="/api/v1/expenses">
          <Notice title="Loading, unloading, royalty and diesel are not expense categories.">
            Toll, driver allowance and parking require a trip. This records an operational receipt,
            not a cash payment.
          </Notice>
          <IdField name="expenseId" caption="Expense id" />
          <div className="form-grid">
            <TruckField write={write} />
            <Field label="Trip id (when required)">
              <input name="tripId" />
            </Field>
            <Field label="Category">
              <select name="category">
                {["TOLL", "DRIVER_ALLOWANCE", "PARKING", "TYRE", "MAINTENANCE", "REPAIR", "OTHER"].map(
                  (item) => (
                    <option key={item}>{item}</option>
                  ),
                )}
              </select>
            </Field>
            <Field label="Occurred at">
              <input name="occurredAt" type="datetime-local" required />
            </Field>
            <Field label="Amount ₹">
              <input name="amount" type="number" min="0.01" step="0.01" required />
            </Field>
            <Field label="Receipt reference">
              <input name="receiptReference" required maxLength={100} />
            </Field>
          </div>
          <Field label="Description">
            <textarea name="description" required maxLength={1000} />
          </Field>
          <Field label="Evidence">
            <input name="receiptEvidence" maxLength={300} />
          </Field>
        </SaveForm>
      )}
      <ErrorBox error={list.q.error || one.error} />
      <div className="panel">
        <DataTable
          rows={list.items}
          onOpen={(row) => setSelected(String(row.id))}
          columns={[
            { label: "Category", keys: ["category"] },
            { label: "Amount", keys: ["amount"] },
            { label: "Reference", keys: ["receiptReference", "receipt_reference"] },
            { label: "Status", keys: ["status"], status: true },
          ]}
        />
        <Pager page={list.page} setPage={list.setPage} hasNext={list.hasNext} />
      </div>
      {one.data && (
        <OperatingDetail
          row={one.data}
          review={review}
          write={write}
          kind="EXPENSE"
          approve={`/api/v1/expenses/${selected}/approve`}
          reject={`/api/v1/expenses/${selected}/reject`}
          reverse={`/api/v1/expenses/${selected}/reversals`}
        />
      )}
    </>
  );
}

function OperatingDetail({
  row,
  history,
  review,
  write,
  kind,
  approve,
  reject,
  reverse,
}: {
  row: RecordData;
  history?: RecordData[];
  review: boolean;
  write: boolean;
  kind: "FUEL" | "EXPENSE";
  approve: string;
  reject: string;
  reverse: string;
}) {
  const access = useAccess();
  const [mode, setMode] = useState<string | null>(null);
  const status = String(val(row, "status") ?? "");
  const revision = Number(val(row, "revision") ?? 1);
  const pending = status === "PENDING_VERIFICATION" || status === "PENDING";
  const own = ownedBy(row, access.username);
  return (
    <div className="panel form-panel">
      <RecordView row={row} />
      <p>
        Amount {money(Number(val(row, "amount") ?? 0))} · revision {revision}
      </p>
      {own && pending && <p className="muted">A different user has to review this revision.</p>}
      {review && pending && !own && (
        <div className="actions">
          <button onClick={() => setMode(approve)}>Approve</button>
          <button className="secondary" onClick={() => setMode(reject)}>
            Reject
          </button>
        </div>
      )}
      {mode && (
        <ReasonForm
          path={mode}
          extra={{ revision }}
          submitLabel="Save review"
          onDone={() => setMode(null)}
        />
      )}
      {write && val(row, "status") === "APPROVED" && !val(row, "reversed") && (
        <SaveForm title="Request reversal" path={reverse}>
          <IdField name="reversalId" caption="Reversal id" />
          <Field label="Reason">
            <textarea name="reason" required maxLength={1000} />
          </Field>
          <Field label="Evidence reference">
            <input name="evidenceReference" required maxLength={500} />
          </Field>
        </SaveForm>
      )}
      {history?.length ? (
        <>
          <h3>History</h3>
          {history.map((item, index) => (
            <RecordView key={index} row={item} />
          ))}
        </>
      ) : null}
      <Documents targetType={kind} targetId={String(row.id)} />
    </div>
  );
}

function TruckField({ write }: { write: boolean }) {
  return (
    <Field label={write ? "Truck" : "Truck id"}>
      {write ? (
        <select name="truckId" required>
          <Choices path="/api/v1/trucks" empty="Select truck" labelOf={(row) => String(row.registration)} />
        </select>
      ) : (
        <input name="truckId" required placeholder="Truck id" />
      )}
    </Field>
  );
}

function Rates({ admin }: { admin: boolean }) {
  const [id, setId] = useState("");
  const rate = useQuery({
    queryKey: ["rate", id],
    queryFn: () => request<RecordData>(`/api/v1/fuel/rates/${id}`),
    enabled: !!id,
  });
  const history = useQuery({
    queryKey: ["rate-history", id],
    queryFn: () => request<RecordData[]>(`/api/v1/fuel/rates/${id}/history`),
    enabled: !!id,
  });
  return (
    <>
      <Notice title="Diesel rates have no list endpoint.">
        Create a rate, then keep its id. Receipts copy the price at submission; a later correction
        does not rewrite old receipts.
      </Notice>
      {admin && (
        <SaveForm title="Publish a diesel rate" path="/api/v1/fuel/rates">
          <div className="form-grid">
            <Field label="Pump">
              <input name="pump" required maxLength={160} />
            </Field>
            <Field label="Location">
              <input name="location" required maxLength={160} />
            </Field>
            <Field label="Effective date">
              <input name="effectiveDate" type="date" required />
            </Field>
            <Field label="Price ₹ / litre">
              <input name="pricePerLitre" type="number" min="0.01" step="0.01" required />
            </Field>
          </div>
        </SaveForm>
      )}
      <div className="panel form-panel">
        <Field label="Look up a rate id">
          <input value={id} onChange={(event) => setId(event.target.value.trim())} />
        </Field>
        <ErrorBox error={rate.error || history.error} />
        <RecordView row={rate.data} />
        {admin && rate.data && (
          <SaveForm title="Correct the published price" path={`/api/v1/fuel/rates/${id}/corrections`}>
            <IdField name="eventId" caption="Event id" />
            <div className="form-grid">
              <Field label="Expected current price">
                <input
                  name="expectedPrice"
                  type="number"
                  min="0.01"
                  step="0.01"
                  required
                  defaultValue={String(val(rate.data, "pricePerLitre", "price_per_litre") ?? "")}
                />
              </Field>
              <Field label="New price">
                <input name="newPrice" type="number" min="0.01" step="0.01" required />
              </Field>
            </div>
            <Field label="Reason">
              <textarea name="reason" required maxLength={1000} />
            </Field>
          </SaveForm>
        )}
        {history.data?.map((row, index) => (
          <RecordView key={index} row={row} />
        ))}
      </div>
      <ReversalReview />
    </>
  );
}

function ReversalReview() {
  const review = useAccess().review;
  const [id, setId] = useState("");
  const [mode, setMode] = useState<string | null>(null);
  if (!review) return null;
  return (
    <div className="panel form-panel">
      <h3>Review an operating reversal</h3>
      <p className="muted">Paste the reversal id returned when the reversal was submitted.</p>
      <Field label="Reversal id">
        <input value={id} onChange={(event) => setId(event.target.value.trim())} />
      </Field>
      {id && (
        <div className="actions">
          <button onClick={() => setMode("approve")}>Approve</button>
          <button className="secondary" onClick={() => setMode("reject")}>
            Reject
          </button>
        </div>
      )}
      {mode && id && (
        <ReasonForm
          path={`/api/v1/operating-reversals/${id}/${mode}`}
          extra={{ revision: 1 }}
          submitLabel="Save reversal review"
          onDone={() => setMode(null)}
        />
      )}
    </div>
  );
}
