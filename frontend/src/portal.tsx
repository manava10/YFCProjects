import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { LogOut } from "lucide-react";
import { logout, post, request, type RecordData } from "./api";
import { label, money, readForm, short, text, uuid, val } from "./lib";
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
        <div className="brand">YFC / TRANSPORT</div>
        <p className="eyebrow">REQUEST ACCESS</p>
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
            <Link to="/staff">Back to sign in</Link>
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
              <Link to="/staff">Already approved? Sign in</Link>
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
  driver,
  onDone,
}: {
  id: string;
  action: "approve" | "reject";
  driver: boolean;
  onDone: () => void;
}) {
  const client = useQueryClient();
  const [stamp] = useState(uuid);
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
        mutation.mutate({ ...readForm(event.currentTarget), eventId: stamp });
      }}
    >
      <ErrorBox error={mutation.error} />
      {action === "approve" && driver && (
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
                  driver={role === "DRIVER"}
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
  columns: { label: string; render: (row: RecordData) => string }[];
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
  const client = useQueryClient();
  const q = useQuery({
    queryKey: ["portal", username],
    queryFn: () => request<RecordData>("/api/v1/portal"),
  });
  const role = String(val(q.data, "role") ?? "");
  const profile = (q.data?.profile ?? null) as RecordData | null;
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
          <strong>{text(val(profile, "name"))}</strong>
          <small>{label(role)}</small>
          <button
            className="icon-button"
            aria-label="Sign out"
            onClick={async () => {
              setLogoutError(null);
              try {
                await logout();
                client.removeQueries({ predicate: (query) => query.queryKey[0] !== "session" });
                client.setQueryData(["session"], null);
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
            description="Orders, trips and bills shown here belong to this login."
          />
          <ErrorBox error={logoutError || q.error} />
          {q.isPending && <p role="status">Loading your records…</p>}
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
            title="Trips"
            rows={q.data?.trips as RecordData[] | undefined}
            columns={[
              { label: "Trip", render: (row) => short(String(row.id)) },
              { label: "Material", render: (row) => text(val(row, "material")) },
              { label: "Status", render: (row) => label(text(val(row, "status"))) },
              { label: "Date", render: (row) => text(val(row, "planned_date")) },
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
            title="Bills"
            rows={q.data?.bills as RecordData[] | undefined}
            columns={[
              {
                label: "Bill",
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
            ]}
          />
        </main>
      </div>
    </div>
  );
}
