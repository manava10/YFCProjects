import { useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Camera, CheckCircle2, FileText, LogOut, Truck, X } from "lucide-react";
import { logout, post, request, upload, type RecordData } from "./api";
import { label, money, readForm, short, text, uuid, val } from "./lib";
import { PrintableChallan, PrintableInvoice } from "./print-docs";
import {
  Choices,
  ErrorBox,
  Field,
  Heading,
  PasswordInput,
  Status,
} from "./ui";

export function Join() {
  const [role, setRole] = useState("CUSTOMER");
  const [done, setDone] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  return (
    <div className="login-layout">
      <section>
        <Link className="brand" to="/">
          <span className="brandmark">Y</span>
          <span>
            YFC<span className="brand-sub">TRANSPORT ERP</span>
          </span>
        </Link>
        <p className="eyebrow" style={{ color: "#93c5fd" }}>REQUEST ACCESS</p>
        <h1>
          Your own
          <br />
          view of the
          <br />
          <em>work.</em>
        </h1>
        <p>
          Customers, suppliers and drivers fill in their details here. An
          administrator or operations user approves the login before it can be
          used.
        </p>
      </section>
      <form
        className="login join"
        onSubmit={async (event) => {
          event.preventDefault();
          setBusy(true);
          setError(null);
          const form = new FormData(event.currentTarget);
          const body: Record<string, string> = {
            applicationId: uuid(),
            role,
            username: String(form.get("username") ?? ""),
            password: String(form.get("password") ?? ""),
            name: String(form.get("name") ?? ""),
            mobile: String(form.get("mobile") ?? ""),
          };
          for (const key of [
            "gstin",
            "address",
            "email",
            "licenseNumber",
            "licenseExpiry",
            "emergencyContact",
            "emergencyMobile",
          ]) {
            const value = String(form.get(key) ?? "").trim();
            if (value) body[key] = value;
          }
          if (body.gstin) body.gstin = body.gstin.toUpperCase();
          if (body.licenseNumber) body.licenseNumber = body.licenseNumber.toUpperCase();
          try {
            await post("/api/v1/account-applications", body);
            setDone(true);
          } catch (caught) {
            setError(caught);
          } finally {
            setBusy(false);
          }
        }}
      >
        <p className="eyebrow">NEW ACCOUNT</p>
        <h2>Tell us who you are</h2>
        {done ? (
          <>
            <p>
              Your request is with the administrator and the operations team.
              You can sign in after they approve it.
            </p>
            <Link to="/signin">Back to sign in</Link>
          </>
        ) : (
          <>
            <ErrorBox error={error} />
            <Field label="I am a">
              <select
                name="role"
                value={role}
                onChange={(event) => setRole(event.target.value)}
              >
                <option value="CUSTOMER">Customer</option>
                <option value="SUPPLIER">Supplier</option>
                <option value="DRIVER">Driver</option>
              </select>
            </Field>
            <Field label={role === "CUSTOMER" ? "Your name" : role === "SUPPLIER" ? "Company name" : "Full name"}>
              <input name="name" required maxLength={120} />
            </Field>
            <Field label="Mobile">
              <input name="mobile" required maxLength={20} />
            </Field>
            <Field label="Username">
              <input name="username" required autoComplete="username" />
            </Field>
            <Field label="Password">
              <PasswordInput
                name="password"
                autoComplete="new-password"
                minLength={12}
                maxLength={72}
              />
            </Field>
            {role !== "DRIVER" && (
              <Field label={role === "SUPPLIER" ? "GSTIN" : "GSTIN, if you have one"}>
                <input
                  name="gstin"
                  required={role === "SUPPLIER"}
                  maxLength={15}
                  style={{ textTransform: "uppercase" }}
                />
              </Field>
            )}
            {role === "SUPPLIER" && (
              <>
                <Field label="Address">
                  <textarea name="address" required maxLength={500} />
                </Field>
                <Field label="Email, if you have one">
                  <input name="email" type="email" maxLength={254} />
                </Field>
              </>
            )}
            {role === "CUSTOMER" && (
              <Field label="Address, if you want it recorded">
                <textarea name="address" maxLength={500} />
              </Field>
            )}
            {role === "DRIVER" && (
              <>
                <Field label="Licence number">
                  <input name="licenseNumber" required maxLength={40} />
                </Field>
                <Field label="Licence expiry">
                  <input name="licenseExpiry" type="date" required />
                </Field>
                <Field label="Emergency contact">
                  <input name="emergencyContact" required maxLength={160} />
                </Field>
                <Field label="Emergency mobile">
                  <input name="emergencyMobile" required maxLength={20} />
                </Field>
              </>
            )}
            <button disabled={busy}>
              {busy ? "Sending…" : "Request account"}
            </button>
            <small>
              <Link to="/signin">Already approved? Sign in</Link>
            </small>
          </>
        )}
      </form>
    </div>
  );
}

function Decision({
  id,
  action,
  role,
  onDone,
}: {
  id: string;
  action: "approve" | "reject";
  role: string;
  onDone: () => void;
}) {
  const client = useQueryClient();
  const [stamp] = useState(uuid);
  const [existingId, setExistingId] = useState("");
  const driver = role === "DRIVER";
  const existingPath =
    role === "CUSTOMER"
      ? "/api/v1/customers"
      : role === "SUPPLIER"
        ? "/api/v1/suppliers"
        : "/api/v1/drivers";
  const mutation = useMutation({
    mutationFn: (body: unknown) =>
      post(`/api/v1/account-applications/${id}/${action}`, body),
    onSuccess: async () => {
      await client.invalidateQueries();
      onDone();
    },
  });
  return (
    <form
      className="decision"
      onSubmit={(event) => {
        event.preventDefault();
        const data = readForm(event.currentTarget);
        if (!data.existingRecordId) delete data.existingRecordId;
        if (!data.branchId) delete data.branchId;
        mutation.mutate({ ...data, eventId: stamp });
      }}
    >
      <ErrorBox error={mutation.error} />
      {action === "approve" && (
        <Field label={`Link to existing ${label(role).toLowerCase()} (optional — leave blank to create new)`}>
          <select
            name="existingRecordId"
            value={existingId}
            onChange={(event) => setExistingId(event.target.value)}
          >
            <Choices
              path={existingPath}
              empty={`Create a new ${label(role).toLowerCase()} record`}
              labelOf={(row) =>
                `${val(row, "name")}${val(row, "mobile") ? ` · ${val(row, "mobile")}` : ""}`
              }
            />
          </select>
        </Field>
      )}
      {action === "approve" && driver && !existingId && (
        <Field label="Driver's branch">
          <select name="branchId" required>
            <Choices
              path="/api/v1/branches"
              empty="Select branch"
              labelOf={(row) => String(val(row, "name"))}
            />
          </select>
        </Field>
      )}
      <Field label="Reason">
        <textarea name="reason" required maxLength={1000} />
      </Field>
      <button disabled={mutation.isPending}>
        {mutation.isPending
          ? "Saving…"
          : action === "approve"
            ? "Approve account"
            : "Disapprove account"}
      </button>
    </form>
  );
}

export function AccountRequests() {
  const [open, setOpen] = useState<{ id: string; action: "approve" | "reject" } | null>(
    null,
  );
  const q = useQuery({
    queryKey: ["account-applications", "PENDING"],
    queryFn: () =>
      request<RecordData[]>(
        "/api/v1/account-applications?status=PENDING&size=100",
      ),
  });
  const rows = q.data ?? [];
  return (
    <>
      <Heading
        eyebrow="ADMINISTRATION / ACCOUNT REQUESTS"
        title="People waiting for a login."
        description="Approve a request to open their record and let them sign in. They then see only their own work."
      />
      <ErrorBox error={q.error} />
      <div className="panel">
        {rows.length === 0 && !q.isPending && (
          <p className="muted">No account requests are waiting.</p>
        )}
        {rows.map((row) => {
          const id = String(row.id);
          const role = String(val(row, "role"));
          return (
            <article key={id} className="request">
              <header>
                <strong>{text(val(row, "display_name"))}</strong>
                <Status value={role} />
              </header>
              <p>
                {text(val(row, "requested_username"))} · {text(val(row, "mobile"))}
                {val(row, "gstin") ? ` · ${val(row, "gstin")}` : ""}
                {val(row, "license_number")
                  ? ` · licence ${val(row, "license_number")} until ${text(val(row, "license_expiry"))}`
                  : ""}
              </p>
              {val(row, "address") && <p>{text(val(row, "address"))}</p>}
              {val(row, "emergency_contact") && (
                <p>
                  Emergency {text(val(row, "emergency_contact"))} ·{" "}
                  {text(val(row, "emergency_mobile"))}
                </p>
              )}
              <div className="actions">
                <button onClick={() => setOpen({ id, action: "approve" })}>
                  Approve
                </button>
                <button
                  className="secondary"
                  onClick={() => setOpen({ id, action: "reject" })}
                >
                  Disapprove
                </button>
              </div>
              {open?.id === id && (
                <Decision
                  id={id}
                  action={open.action}
                  role={role}
                  onDone={() => setOpen(null)}
                />
              )}
            </article>
          );
        })}
      </div>
    </>
  );
}

function Table({
  title,
  rows,
  columns,
}: {
  title: string;
  rows?: RecordData[];
  columns: { label: string; render: (row: RecordData) => ReactNode }[];
}) {
  if (!rows) return null;
  return (
    <section className="panel">
      <h2>{title}</h2>
      {rows.length === 0 ? (
        <p className="muted">Nothing here yet.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                {columns.map((column) => (
                  <th key={column.label}>{column.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={String(row.id)}>
                  {columns.map((column) => (
                    <td key={column.label}>{column.render(row)}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export function ExternalHome({ username }: { username: string }) {
  const [logoutError, setLogoutError] = useState<unknown>(null);
  const [preview, setPreview] = useState<{ kind: "bill" | "trip"; id: string } | null>(null);
  const client = useQueryClient();
  const navigate = useNavigate();
  const q = useQuery({
    queryKey: ["portal", username],
    queryFn: () => request<RecordData>("/api/v1/portal"),
  });
  const docQuery = useQuery({
    queryKey: ["portal-doc", preview?.kind, preview?.id],
    queryFn: () =>
      request<RecordData>(
        preview?.kind === "bill"
          ? `/api/v1/portal/bills/${preview.id}`
          : `/api/v1/portal/trips/${preview?.id}`,
      ),
    enabled: !!preview,
  });
  const role = String(val(q.data, "role") ?? "");
  const profile = (q.data?.profile ?? null) as RecordData | null;
  const bills = (q.data?.bills as RecordData[] | undefined) ?? [];
  const trips = (q.data?.trips as RecordData[] | undefined) ?? [];
  const totalInvoiced = bills.reduce(
    (sum, b) => sum + Number(val(b, "payable_total", "subtotal") ?? 0),
    0,
  );
  const totalPaid = bills.reduce((sum, b) => sum + Number(val(b, "allocated_amount") ?? 0), 0);
  const totalOutstanding = bills.reduce(
    (sum, b) => sum + Number(val(b, "outstanding_amount") ?? 0),
    0,
  );
  return (
    <div className="shell">
      <aside>
        <Link className="brand" to="/">
          <span className="brandmark">Y</span>
          <span>
            YFC<span className="brand-sub">YOUR RECORDS</span>
          </span>
        </Link>
        <div className="aside-bottom">
          <span className="avatar">
            {(text(val(profile, "name"))[0] || username[0] || "U").toUpperCase()}
          </span>
          <div>
            <strong>{text(val(profile, "name")) || username}</strong>
            <small>{label(role)}</small>
          </div>
          <button
            className="icon-button"
            aria-label="Sign out"
            onClick={async () => {
              setLogoutError(null);
              try {
                await logout();
                client.removeQueries({ predicate: (query) => query.queryKey[0] !== "session" });
                client.setQueryData(["session"], null);
                navigate("/signin", { replace: true });
              } catch (error) {
                setLogoutError(error);
              }
            }}
          >
            <LogOut size={18} />
          </button>
        </div>
      </aside>
      <div className="body">
        <header>
          <span>Your workspace</span>
        </header>
        <main>
          <Heading
            eyebrow={label(role).toUpperCase()}
            title="Only your own records."
            description="Orders, trips, delivery challans, and GST tax invoices shown here belong strictly to this login."
          />
          <ErrorBox error={logoutError || q.error || docQuery.error} />
          {q.isPending && <p role="status">Loading your records…</p>}
          {preview && (
            <div className="portal-doc-preview">
              <div className="portal-doc-preview-bar no-print">
                <strong>
                  {preview.kind === "bill"
                    ? "Printable GST Tax Invoice / Bill"
                    : "Printable Delivery Challan"}
                </strong>
                <button
                  type="button"
                  className="secondary portal-doc-btn"
                  onClick={() => setPreview(null)}
                >
                  <X size={14} /> Close preview
                </button>
              </div>
              {docQuery.isPending && <p role="status">Loading document…</p>}
              {docQuery.data && preview.kind === "bill" && (
                <PrintableInvoice bill={docQuery.data} />
              )}
              {docQuery.data && preview.kind === "trip" && (
                <PrintableChallan trip={docQuery.data} />
              )}
            </div>
          )}
          {(role === "CUSTOMER" || role === "SUPPLIER") && q.data && (
            <section className="portal-summary no-print" aria-label="Account summary">
              <div className="portal-kpi">
                <small>{role === "CUSTOMER" ? "Total Invoiced" : "Total Billed"}</small>
                <strong>{money(totalInvoiced)}</strong>
              </div>
              <div className="portal-kpi">
                <small>Paid / Settled</small>
                <strong>{money(totalPaid)}</strong>
              </div>
              <div className="portal-kpi">
                <small>Outstanding Balance</small>
                <strong>{money(totalOutstanding)}</strong>
              </div>
              <div className="portal-kpi">
                <small>{role === "CUSTOMER" ? "Dispatched Trips" : "Total Bills"}</small>
                <strong>{role === "CUSTOMER" ? trips.length : bills.length}</strong>
              </div>
            </section>
          )}
          {role === "DRIVER" && q.data && (
            <DriverActiveTrips
              trips={trips.filter(
                (t) => !["DELIVERED", "CANCELLED"].includes(String(val(t, "status"))),
              )}
              onUpdated={() => client.invalidateQueries({ queryKey: ["portal", username] })}
            />
          )}
          {profile && (
            <section className="panel">
              <h2>Your details</h2>
              <p>
                {text(val(profile, "name"))}
                {val(profile, "mobile") ? ` · ${val(profile, "mobile")}` : ""}
                {val(profile, "gstin") ? ` · ${val(profile, "gstin")}` : ""}
              </p>
              {val(profile, "address") && <p>{text(val(profile, "address"))}</p>}
              {val(profile, "license_number") && (
                <p>
                  Licence {text(val(profile, "license_number"))} · expires{" "}
                  {text(val(profile, "license_expiry"))}
                  {val(profile, "branch_name")
                    ? ` · ${val(profile, "branch_name")}`
                    : ""}
                </p>
              )}
            </section>
          )}
          <Table
            title="Plants"
            rows={q.data?.plants as RecordData[] | undefined}
            columns={[
              { label: "Plant", render: (row) => text(val(row, "name")) },
              { label: "Address", render: (row) => text(val(row, "address")) },
            ]}
          />
          <Table
            title="Orders"
            rows={q.data?.orders as RecordData[] | undefined}
            columns={[
              { label: "Order", render: (row) => short(String(row.id)) },
              { label: "Material", render: (row) => text(val(row, "material")) },
              {
                label: "Quantity",
                render: (row) => `${text(val(row, "ordered_quantity"))} t`,
              },
            ]}
          />
          <Table
            title="Trips & Delivery Challans"
            rows={q.data?.trips as RecordData[] | undefined}
            columns={[
              { label: "Trip", render: (row) => short(String(row.id)) },
              { label: "Material", render: (row) => text(val(row, "material")) },
              { label: "Truck", render: (row) => text(val(row, "truck_registration")) },
              { label: "E-Way Bill", render: (row) => text(val(row, "eway_bill_number")) },
              { label: "Status", render: (row) => label(text(val(row, "status"))) },
              { label: "Date", render: (row) => text(val(row, "planned_date")) },
              {
                label: "Challan",
                render: (row) => (
                  <button
                    type="button"
                    className="secondary portal-doc-btn"
                    onClick={() => setPreview({ kind: "trip", id: String(row.id) })}
                  >
                    <Truck size={14} /> Challan / Print
                  </button>
                ),
              },
            ]}
          />
          <Table
            title="Purchases"
            rows={q.data?.purchases as RecordData[] | undefined}
            columns={[
              { label: "Purchase", render: (row) => short(String(row.id)) },
              {
                label: "Material",
                render: (row) => text(val(row, "material_name")),
              },
              { label: "Status", render: (row) => label(text(val(row, "status"))) },
              {
                label: "Quantity",
                render: (row) => `${text(val(row, "quantity"))} t`,
              },
            ]}
          />
          <Table
            title="Bills & Tax Invoices"
            rows={q.data?.bills as RecordData[] | undefined}
            columns={[
              {
                label: "Bill / Invoice",
                render: (row) =>
                  text(val(row, "invoice_number")) !== "—"
                    ? text(val(row, "invoice_number"))
                    : short(String(row.id)),
              },
              { label: "Date", render: (row) => text(val(row, "prepared_on")) },
              { label: "Status", render: (row) => label(text(val(row, "status"))) },
              {
                label: "Amount",
                render: (row) =>
                  money(Number(val(row, "payable_total", "subtotal") ?? 0)),
              },
              {
                label: "Paid",
                render: (row) => money(Number(val(row, "allocated_amount") ?? 0)),
              },
              {
                label: "Outstanding",
                render: (row) =>
                  val(row, "outstanding_amount") != null
                    ? money(Number(val(row, "outstanding_amount")))
                    : "—",
              },
              {
                label: "Document",
                render: (row) => (
                  <button
                    type="button"
                    className="secondary portal-doc-btn"
                    onClick={() => setPreview({ kind: "bill", id: String(row.id) })}
                  >
                    <FileText size={14} /> Invoice / Print
                  </button>
                ),
              },
            ]}
          />
        </main>
      </div>
    </div>
  );
}

function DriverActiveTrips({
  trips,
  onUpdated,
}: {
  trips: RecordData[];
  onUpdated: () => void;
}) {
  if (trips.length === 0) {
    return (
      <section className="panel no-print">
        <h2>Active Trip Execution</h2>
        <p className="muted">No active trips assigned right now. New dispatches will appear here automatically.</p>
      </section>
    );
  }
  return (
    <section className="driver-mobile-stack no-print" aria-label="Driver mobile trip execution">
      {trips.map((trip) => (
        <DriverTripCard key={String(trip.id)} trip={trip} onUpdated={onUpdated} />
      ))}
    </section>
  );
}

function DriverTripCard({
  trip,
  onUpdated,
}: {
  trip: RecordData;
  onUpdated: () => void;
}) {
  const id = String(trip.id);
  const status = String(val(trip, "status") ?? "");
  const [uploadError, setUploadError] = useState<unknown>(null);
  const [uploading, setUploading] = useState(false);

  const docs = useQuery({
    queryKey: ["driver-trip-docs", id],
    queryFn: () => request<RecordData[]>(`/api/v1/portal/driver/trips/${id}/documents`),
  });

  const actionMutation = useMutation({
    mutationFn: ({ action, body }: { action: string; body: Record<string, unknown> }) =>
      post(`/api/v1/portal/driver/trips/${id}/${action}`, body),
    onSuccess: () => onUpdated(),
  });

  const simpleStep: Record<string, { action: string; label: string }> = {
    ALLOCATED: { action: "start-loading", label: "Start Loading at Plant / Yard" },
    LOADED: { action: "dispatch", label: "Confirm Dispatch (On Road)" },
    DISPATCHED: { action: "arrive", label: "Mark Arrived at Customer Site" },
    ARRIVED: { action: "start-unloading", label: "Start Unloading" },
  };
  const nextSimple = simpleStep[status];

  return (
    <article className="panel driver-trip-card">
      <div className="driver-trip-head">
        <div>
          <span className="eyebrow">ASSIGNED TRIP · {short(id)}</span>
          <h3>
            {text(val(trip, "material"))} · {text(val(trip, "planned_quantity"))} t
          </h3>
        </div>
        <Status value={status} />
      </div>
      <div className="driver-trip-meta">
        <span>
          <strong>Truck:</strong> {text(val(trip, "truck_registration"))}
        </span>
        <span>
          <strong>Customer:</strong> {text(val(trip, "customer_name"))}
        </span>
        <span>
          <strong>Site:</strong> {text(val(trip, "delivery_site"))}
        </span>
        {val(trip, "eway_bill_number") && (
          <span>
            <strong>E-Way Bill:</strong> {text(val(trip, "eway_bill_number"))}
          </span>
        )}
      </div>

      <ErrorBox error={actionMutation.error || uploadError} />

      {nextSimple && (
        <button
          type="button"
          className="driver-primary-btn"
          disabled={actionMutation.isPending}
          onClick={() =>
            actionMutation.mutate({
              action: nextSimple.action,
              body: { eventId: uuid() },
            })
          }
        >
          <CheckCircle2 size={16} />{" "}
          {actionMutation.isPending ? "Updating…" : nextSimple.label}
        </button>
      )}

      {status === "LOADING" && (
        <form
          className="driver-stage-form"
          onSubmit={(e) => {
            e.preventDefault();
            const form = new FormData(e.currentTarget);
            actionMutation.mutate({
              action: "load",
              body: {
                eventId: uuid(),
                gross: String(form.get("gross") ?? ""),
                tare: String(form.get("tare") ?? ""),
                slipReference: String(form.get("slipReference") ?? ""),
              },
            });
          }}
        >
          <h4>Record Loaded Weighbridge Weight</h4>
          <div className="form-grid">
            <Field label="Gross weight (tonnes)">
              <input name="gross" type="number" step="0.001" min="0.001" required />
            </Field>
            <Field label="Tare weight (tonnes)">
              <input name="tare" type="number" step="0.001" min="0" required />
            </Field>
            <Field label="Weighbridge slip #">
              <input name="slipReference" maxLength={100} required />
            </Field>
          </div>
          <button disabled={actionMutation.isPending}>
            {actionMutation.isPending ? "Saving…" : "Save Loaded Weight"}
          </button>
        </form>
      )}

      {status === "UNLOADING" && (
        <form
          className="driver-stage-form"
          onSubmit={(e) => {
            e.preventDefault();
            const form = new FormData(e.currentTarget);
            const disc = String(form.get("discrepancyReason") ?? "").trim();
            actionMutation.mutate({
              action: "deliver",
              body: {
                eventId: uuid(),
                gross: String(form.get("gross") ?? ""),
                tare: String(form.get("tare") ?? ""),
                acceptedQuantity: String(form.get("acceptedQuantity") ?? ""),
                slipReference: String(form.get("slipReference") ?? ""),
                receiverName: String(form.get("receiverName") ?? ""),
                proofReference: String(form.get("proofReference") ?? ""),
                ...(disc ? { discrepancyReason: disc } : {}),
              },
            });
          }}
        >
          <h4>Record Delivery &amp; Proof of Delivery (POD)</h4>
          <div className="form-grid">
            <Field label="Site gross (t)">
              <input name="gross" type="number" step="0.001" min="0.001" required />
            </Field>
            <Field label="Site tare (t)">
              <input name="tare" type="number" step="0.001" min="0" required />
            </Field>
            <Field label="Accepted qty (t)">
              <input name="acceptedQuantity" type="number" step="0.001" min="0" required />
            </Field>
            <Field label="Delivery slip #">
              <input name="slipReference" maxLength={100} required />
            </Field>
            <Field label="Site receiver name">
              <input name="receiverName" maxLength={160} required />
            </Field>
            <Field label="POD / Challan ref">
              <input name="proofReference" maxLength={300} required />
            </Field>
          </div>
          <Field label="Shortage / discrepancy reason (if weights differ)">
            <input name="discrepancyReason" maxLength={1000} />
          </Field>
          <button disabled={actionMutation.isPending}>
            {actionMutation.isPending ? "Submitting…" : "Complete Delivery"}
          </button>
        </form>
      )}

      <div className="driver-upload-box">
        <label className="driver-upload-label">
          <Camera size={16} />{" "}
          {uploading
            ? "Uploading slip / POD…"
            : "Upload Weighbridge Slip or POD Photo (JPEG / PNG / PDF, max 2 MB)"}
          <input
            type="file"
            accept="image/jpeg,image/png,application/pdf"
            capture="environment"
            disabled={uploading}
            onChange={async (e) => {
              const file = e.target.files?.[0];
              if (!file) return;
              setUploading(true);
              setUploadError(null);
              try {
                const fd = new FormData();
                fd.append("documentId", uuid());
                fd.append("file", file);
                await upload(`/api/v1/portal/driver/trips/${id}/documents`, fd);
                await docs.refetch();
              } catch (err) {
                setUploadError(err);
              } finally {
                setUploading(false);
                e.target.value = "";
              }
            }}
          />
        </label>
        {docs.data && docs.data.length > 0 && (
          <p className="muted" style={{ margin: "6px 0 0", fontSize: 12.5 }}>
            Attached ({docs.data.length}):{" "}
            {docs.data.map((d) => String(d.filename)).join(", ")}
          </p>
        )}
      </div>
    </article>
  );
}
