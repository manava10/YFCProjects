import { useState, type ReactNode } from "react";
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
  ReviewBox,
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
          ["allocations", "Fuel allocations"],
          ["odometer", "Odometer resets"],
          ["rates", "Diesel rates"],
        ].map(([id, caption]) => (
          <button key={id} className={tab === id ? "tab active" : "tab"} onClick={() => setTab(id)}>
            {caption}
          </button>
        ))}
      </div>
      {tab === "fuel" && <Fuel write={write} review={review} />}
      {tab === "expenses" && <Expenses write={write} review={review} />}
      {tab === "allocations" && <FuelAllocations write={write} />}
      {tab === "odometer" && <OdometerResets write={write} />}
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
  const revisable = status === "PENDING_VERIFICATION" || status === "REJECTED";
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
      {write && revisable && kind === "FUEL" && <FuelRevision row={row} />}
      {write && revisable && kind === "EXPENSE" && <ExpenseRevision row={row} />}
      {write &&
        kind === "FUEL" &&
        status === "APPROVED" &&
        !val(row, "tripId", "trip_id") &&
        !val(row, "reversed") && (
          <SaveForm
            title="Allocate this receipt to a trip"
            path={`/api/v1/fuel/entries/${row.id}/allocations`}
          >
            <Notice title="This is purchased fuel, not consumption.">
              Only an approved truck-only receipt can be split. The truck must match the trip, and
              the fill must fall inside the trip. The server calculates the amount. Reject any
              pending allocation before reversing this receipt.
            </Notice>
            <IdField name="allocationId" caption="Allocation id" />
            <div className="form-grid">
              <Field label="Trip id">
                <input name="tripId" required />
              </Field>
              <Field label="Litres">
                <input name="quantityLitres" type="number" min="0.001" step="0.001" required />
              </Field>
            </div>
            <Field label="Reason">
              <textarea name="reason" required maxLength={1000} />
            </Field>
            <Field label="Evidence reference">
              <input name="evidenceReference" required maxLength={500} />
            </Field>
          </SaveForm>
        )}
      {write && status === "APPROVED" && !val(row, "reversed") && (
        <SaveForm title="Request reversal" path={reverse}>
          {kind === "FUEL" && (
            <Notice title="Reject pending allocations first.">
              A fuel receipt with a pending trip allocation cannot be reversed until that
              allocation is rejected.
            </Notice>
          )}
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

function stamp(value: unknown) {
  const text = String(value ?? "");
  const match = text.match(/^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})/);
  return match?.[1] ?? "";
}

function FuelRevision({ row }: { row: RecordData }) {
  return (
    <SaveForm title="Revise this fuel receipt" path={`/api/v1/fuel/entries/${row.id}/revisions`}>
      <Notice title="Approved receipts cannot be revised.">
        This replaces the pending or rejected submission. Truck, trip and receipt reference stay as
        they are. The server stores the previous version.
      </Notice>
      <IdField name="eventId" caption="Event id" />
      <input type="hidden" name="revision" value={String(val(row, "revision") ?? 1)} />
      <div className="form-grid">
        <Field label="Diesel rate id">
          <input name="rateId" required defaultValue={String(val(row, "rateId", "rate_id") ?? "")} />
        </Field>
        <Field label="Filled on">
          <input
            name="filledOn"
            type="date"
            max={today()}
            required
            defaultValue={String(val(row, "filledOn", "filled_on") ?? "").slice(0, 10)}
          />
        </Field>
        <Field label="Litres">
          <input
            name="quantityLitres"
            type="number"
            min="0.001"
            step="0.001"
            required
            defaultValue={String(val(row, "quantityLitres", "quantity_litres") ?? "")}
          />
        </Field>
        <Field label="Actual ₹ / litre">
          <input
            name="actualPricePerLitre"
            type="number"
            min="0.01"
            step="0.01"
            required
            defaultValue={String(val(row, "actualPricePerLitre", "actual_price_per_litre") ?? "")}
          />
        </Field>
        <Field label="Odometer">
          <input
            name="odometer"
            type="number"
            min="0"
            step="0.1"
            required
            defaultValue={String(val(row, "odometer") ?? "")}
          />
        </Field>
        <Field label="Filled at (Asia/Kolkata)">
          <input
            name="filledAt"
            type="datetime-local"
            required
            defaultValue={stamp(val(row, "filledAt", "filled_at"))}
          />
        </Field>
      </div>
      <Field label="Reason">
        <textarea name="reason" required maxLength={1000} />
      </Field>
      <Field label="Receipt evidence">
        <input
          name="receiptEvidence"
          maxLength={300}
          required
          defaultValue={String(val(row, "receiptEvidence", "receipt_evidence") ?? "")}
        />
      </Field>
      <Field label="Price variance reason">
        <textarea
          name="priceVarianceReason"
          maxLength={1000}
          defaultValue={String(val(row, "priceVarianceReason", "price_variance_reason") ?? "")}
        />
      </Field>
    </SaveForm>
  );
}

function ExpenseRevision({ row }: { row: RecordData }) {
  return (
    <SaveForm title="Revise this expense" path={`/api/v1/expenses/${row.id}/revisions`}>
      <Notice title="Approved expenses cannot be revised.">
        Reverse an approved expense instead. Truck, trip and receipt reference stay fixed.
      </Notice>
      <IdField name="eventId" caption="Event id" />
      <input type="hidden" name="revision" value={String(val(row, "revision") ?? 1)} />
      <div className="form-grid">
        <Field label="Category">
          <select name="category" defaultValue={String(val(row, "category") ?? "OTHER")}>
            {["TOLL", "DRIVER_ALLOWANCE", "PARKING", "TYRE", "MAINTENANCE", "REPAIR", "OTHER"].map(
              (item) => (
                <option key={item}>{item}</option>
              ),
            )}
          </select>
        </Field>
        <Field label="Occurred at">
          <input
            name="occurredAt"
            type="datetime-local"
            required
            defaultValue={stamp(val(row, "occurredAt", "occurred_at"))}
          />
        </Field>
        <Field label="Amount ₹">
          <input
            name="amount"
            type="number"
            min="0.01"
            step="0.01"
            required
            defaultValue={String(val(row, "amount") ?? "")}
          />
        </Field>
      </div>
      <Field label="Description">
        <textarea
          name="description"
          required
          maxLength={1000}
          defaultValue={String(val(row, "description") ?? "")}
        />
      </Field>
      <Field label="Evidence">
        <input
          name="receiptEvidence"
          maxLength={300}
          required
          defaultValue={String(val(row, "receiptEvidence", "receipt_evidence") ?? "")}
        />
      </Field>
      <Field label="Reason">
        <textarea name="reason" required maxLength={1000} />
      </Field>
    </SaveForm>
  );
}

function FuelAllocations({ write }: { write: boolean }) {
  return (
    <ReviewList
      listKey="fuel-allocations"
      path="/api/v1/fuel-allocations"
      detail={(id) => `/api/v1/fuel-allocations/${id}`}
      approve={(id) => `/api/v1/fuel-allocations/${id}/approve`}
      reject={(id) => `/api/v1/fuel-allocations/${id}/reject`}
      columns={[
        { label: "Litres", keys: ["quantityLitres", "quantity_litres"] },
        { label: "Amount", keys: ["amount"] },
        { label: "Status", keys: ["status"], status: true },
      ]}
      notice={
        write ? (
          <Notice title="Allocate from an approved truck-only receipt.">
            Open that receipt on the Fuel tab. A receipt already assigned to one trip cannot be
            split. This is purchased fuel, not tank consumption.
          </Notice>
        ) : undefined
      }
    />
  );
}

function OdometerResets({ write }: { write: boolean }) {
  return (
    <ReviewList
      listKey="odometer-resets"
      path="/api/v1/odometer-resets"
      detail={(id) => `/api/v1/odometer-resets/${id}`}
      approve={(id) => `/api/v1/odometer-resets/${id}/approve`}
      reject={(id) => `/api/v1/odometer-resets/${id}/reject`}
      columns={[
        { label: "New reading", keys: ["newReading", "new_reading"] },
        { label: "Status", keys: ["status"], status: true },
        { label: "Reason", keys: ["reason"] },
      ]}
      notice={
        write ? (
          <SaveForm title="Reset an odometer" path="/api/v1/odometer-resets">
            <Notice title="This is not a tank balance or a mileage calculation.">
              One pending reset is allowed per truck. The time cannot be in the future. After
              approval, later fuel readings may be lower than fills from before the reset.
            </Notice>
            <IdField name="resetId" caption="Reset id" />
            <div className="form-grid">
              <TruckField write={write} />
              <Field label="New reading">
                <input name="newReading" type="number" min="0" step="0.1" required />
              </Field>
              <Field label="Reset at (Asia/Kolkata)">
                <input name="resetAt" type="datetime-local" required />
              </Field>
            </div>
            <Field label="Reason">
              <textarea name="reason" required maxLength={1000} />
            </Field>
            <Field label="Evidence reference">
              <input name="evidenceReference" required maxLength={500} />
            </Field>
          </SaveForm>
        ) : undefined
      }
    />
  );
}

function ReviewList({
  listKey,
  path,
  detail,
  approve,
  reject,
  columns,
  notice,
}: {
  listKey: string;
  path: string;
  detail: (id: string) => string;
  approve: (id: string) => string;
  reject: (id: string) => string;
  columns: { label: string; keys: string[]; status?: boolean }[];
  notice?: ReactNode;
}) {
  const [selected, setSelected] = useState("");
  const list = usePaged(listKey, path);
  const one = useQuery({
    queryKey: [listKey, selected],
    queryFn: () => request<RecordData>(detail(selected)),
    enabled: !!selected,
  });
  return (
    <>
      {notice}
      <ErrorBox error={list.q.error || one.error} />
      <div className="panel">
        <DataTable rows={list.items} columns={columns} onOpen={(row) => setSelected(String(row.id))} />
        <Pager page={list.page} setPage={list.setPage} hasNext={list.hasNext} />
      </div>
      {one.data && (
        <div className="panel form-panel">
          <RecordView row={one.data} />
          {String(val(one.data, "status")) === "PENDING" && (
            <ReviewBox approve={approve(selected)} reject={reject(selected)} row={one.data} />
          )}
        </div>
      )}
    </>
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
