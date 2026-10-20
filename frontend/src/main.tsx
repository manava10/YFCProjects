import React, { useState, type FormEvent } from "react";
import { createRoot } from "react-dom/client";
import {
  BrowserRouter,
  Routes,
  Route,
  NavLink,
  Link,
  useNavigate,
  useParams,
  Navigate,
} from "react-router-dom";
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import {
  ArrowUpRight,
  ArrowLeft,
  Plus,
  LogOut,
  Layers3,
  Users,
  Package,
  ClipboardList,
  Check,
  ChevronRight,
  Truck,
} from "lucide-react";
import {
  request,
  post,
  login,
  logout,
  all,
  ApiError,
  type Page,
  type RecordData,
} from "./api";
import "./style.css";
const client = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
});
const short = (id: string) => id.slice(0, 8).toUpperCase();
const label = (s: string) => s?.replaceAll("_", " ").toLowerCase();
const money = (n: number) =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(
    n,
  );
const today = () =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
function ErrorBox({ error }: { error: unknown }) {
  return error ? (
    <div role="alert" className="error">
      {error instanceof Error ? error.message : "Something went wrong."}
    </div>
  ) : null;
}
function Status({ value }: { value: string }) {
  return (
    <span className={`status ${value?.toLowerCase()}`}>{label(value)}</span>
  );
}
function Field({
  label: caption,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="field">
      <span>{caption}</span>
      {children}
    </label>
  );
}
function Empty({ text }: { text: string }) {
  return (
    <div className="empty">
      <Layers3 size={30} />
      <h3>{text}</h3>
      <p>Your live business records will appear here.</p>
    </div>
  );
}
function App() {
  const session = useQuery({
    queryKey: ["session"],
    queryFn: () =>
      request<{ username: string; roles: string[] }>("/api/v1/auth/me"),
  });
  React.useEffect(() => {
    const expire = () => {
      client.removeQueries({ predicate: query => query.queryKey[0] !== 'session' });
      client.setQueryData(["session"], null);
    };
    window.addEventListener("session-expired", expire);
    return () => window.removeEventListener("session-expired", expire);
  }, []);
  if (session.isPending)
    return <div className="loading">Opening your workspace…</div>;
  if (!session.data)
    return (
      <Login
        error={
          session.error instanceof ApiError && session.error.status === 401
            ? null
            : session.error
        }
      />
    );
  const write = session.data.roles.some((r) =>
    ["ROLE_SUPER_ADMIN", "ROLE_OPERATIONS"].includes(r),
  );
  const admin = session.data.roles.includes("ROLE_SUPER_ADMIN");
  if (
    !session.data.roles.some((r) =>
      ["ROLE_SUPER_ADMIN", "ROLE_OPERATIONS", "ROLE_ACCOUNTS"].includes(r),
    )
  )
    return (
      <main className="login">
        <h1>Your account is ready.</h1>
        <p>
          External portals are not available yet. Please contact your
          administrator.
        </p>
        <button
          onClick={() =>
            logout().then(() => {
              client.removeQueries({ predicate: query => query.queryKey[0] !== 'session' });
              client.setQueryData(["session"], null);
            })
          }
        >
          Sign out
        </button>
      </main>
    );
  return (
    <div className="shell">
      <aside>
        <Link className="brand" to="/">
          <span className="brandmark">Y</span>
          <span>
            YFC<span className="brand-sub">TRANSPORT & MATERIALS</span>
          </span>
        </Link>
        <div className="workspace-tag">
          <span className="live-dot" /> Internal workspace
        </div>
        <p className="nav-label">WORKSPACE</p>
        <nav>
          <NavLink to="/" end>
            <Layers3 size={19} />
            Overview
          </NavLink>
          <NavLink to="/sales">
            <ClipboardList size={19} />
            Sales enquiries
          </NavLink>
          <NavLink to="/orders">
            <Truck size={19} />
            Orders
          </NavLink>
        </nav>
        <p className="nav-label">BUSINESS DIRECTORY</p>
        <nav>
          <NavLink to="/customers">
            <Users size={19} />
            Customers & sites
          </NavLink>
          <NavLink to="/materials">
            <Package size={19} />
            Materials
          </NavLink>
        </nav>
        <div className="aside-bottom">
          <span className="avatar">
            {session.data.username[0].toUpperCase()}
          </span>
          <div>
            <strong>{session.data.username}</strong>
            <small>
              {admin
                ? "Administrator"
                : write
                  ? "Operations"
                  : "Accounts · read access"}
            </small>
          </div>
          <button
            className="icon-button"
            aria-label="Sign out"
            onClick={async () => {
              try {
                await logout();
                client.removeQueries({ predicate: query => query.queryKey[0] !== 'session' });
                client.setQueryData(["session"], null);
              } catch (e) {
                window.alert(
                  e instanceof Error ? e.message : "Sign out failed",
                );
              }
            }}
          >
            <LogOut size={18} />
          </button>
        </div>
      </aside>
      <div className="body">
        <header>
          <span>
            YFC workspace <ChevronRight size={14} /> Sales & operations
          </span>
          <span className="header-date">
            {new Intl.DateTimeFormat("en-IN", {
              dateStyle: "medium",
              timeZone: "Asia/Kolkata",
            }).format(new Date())}
          </span>
        </header>
        <main>
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/sales" element={<Sales write={write} />} />
            <Route
              path="/sales/new"
              element={write ? <NewEnquiry /> : <Navigate to="/sales" />}
            />
            <Route path="/sales/:id" element={<Enquiry write={write} />} />
            <Route path="/orders" element={<Orders />} />
            <Route path="/orders/:id" element={<Order />} />
            <Route path="/customers" element={<Customers write={write} />} />
            <Route path="/materials" element={<Materials admin={admin} />} />
            <Route path="*" element={<Navigate to="/" />} />
          </Routes>
        </main>
        <footer>
          YFC Transport <span>Built around your next move.</span>
        </footer>
      </div>
    </div>
  );
}
function Login({ error }: { error: unknown }) {
  const [failure, setFailure] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  return (
    <div className="login-layout">
      <section>
        <div className="brand">YFC / TRANSPORT</div>
        <p className="eyebrow">YOUR OPERATIONS, CONNECTED</p>
        <h1>
          Every order.
          <br />
          Every journey.
          <br />
          <em>One workspace.</em>
        </h1>
        <p>Manage material supply, customer enquiries and the road ahead.</p>
      </section>
      <form
        className="login"
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setFailure(null);
          const f = new FormData(e.currentTarget);
          try {
            await login(String(f.get("username")), String(f.get("password")));
            await client.invalidateQueries({ queryKey: ["session"] });
          } catch (e) {
            setFailure(e);
          } finally {
            setBusy(false);
          }
        }}
      >
        <p className="eyebrow">WELCOME TO YFC</p>
        <h2>Sign in to your workspace</h2>
        <p>Use the account provided by your administrator.</p>
        <ErrorBox error={failure || error} />
        <Field label="Username">
          <input name="username" autoComplete="username" required />
        </Field>
        <Field label="Password">
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            required
          />
        </Field>
        <button disabled={busy}>
          {busy ? "Signing in…" : "Sign in"} <ArrowUpRight size={16} />
        </button>
        <small>
          Need access or a password reset? Contact your administrator.
        </small>
      </form>
    </div>
  );
}
function Heading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action}
    </div>
  );
}
function Overview() {
  return (
    <>
      <Heading
        eyebrow="OPERATIONS START HERE"
        title="A clear view. A better next move."
        description="Your sales and material supply workspace."
      />
      <div className="hero">
        <div>
          <span className="pill">SALES WORKSPACE · FIRST RELEASE</span>
          <h2>
            From the first enquiry
            <br />
            to the confirmed order.
          </h2>
          <p>
            Keep customer requirements, quotation revisions and agreed
            quantities together.
          </p>
          <Link className="button light" to="/sales">
            Open sales workspace <ArrowUpRight size={17} />
          </Link>
        </div>
        <div className="hero-art">
          <Truck size={100} strokeWidth={1} />
          <span>SUPPLY. MOVE. DELIVER.</span>
        </div>
      </div>
      <div className="cards">
        {[
          [
            "01",
            "Capture the requirement",
            "Choose a customer, site and materials.",
            "/sales",
          ],
          [
            "02",
            "Agree the commercial terms",
            "Create revisions and record a customer decision.",
            "/sales",
          ],
          [
            "03",
            "Confirm the order",
            "Preserve agreed pricing and ordered quantities.",
            "/orders",
          ],
        ].map(([n, t, d, l]) => (
          <Link className="card" key={n} to={l}>
            <span className="step">{n}</span>
            <ArrowUpRight className="card-arrow" size={20} />
            <h3>{t}</h3>
            <p>{d}</p>
          </Link>
        ))}
      </div>
      <div className="notice">
        <strong>Built on your live records</strong>
        <p>
          Sales subtotals exclude GST. Billing preparation and tax invoicing are
          separate workflows. Dispatch, inventory and finance screens will
          follow in later releases.
        </p>
      </div>
    </>
  );
}
function Pager({
  page,
  setPage,
  data,
}: {
  page: number;
  setPage: (n: number) => void;
  data?: Page<RecordData>;
}) {
  return (
    <div className="pager">
      <span>{data?.totalElements ?? 0} records</span>
      <button
        className="secondary"
        disabled={page === 0}
        onClick={() => setPage(page - 1)}
      >
        Previous
      </button>
      <span>Page {page + 1}</span>
      <button
        className="secondary"
        disabled={!data || page + 1 >= data.totalPages}
        onClick={() => setPage(page + 1)}
      >
        Next
      </button>
    </div>
  );
}
function Sales({ write }: { write: boolean }) {
  const [page, setPage] = useState(0);
  const q = useQuery({
    queryKey: ["rfqs", page],
    queryFn: () =>
      request<Page<RecordData>>(`/api/v1/rfqs?page=${page}&size=20`),
  });
  const customers = useQuery({
    queryKey: ["customers-all"],
    queryFn: () => all("/api/v1/customers"),
  });
  return (
    <>
      <Heading
        eyebrow="SALES / ENQUIRIES"
        title="Start with the requirement."
        description="Follow each enquiry from quotation to confirmed order."
        action={
          write && (
            <Link className="button" to="/sales/new">
              <Plus size={17} />
              New enquiry
            </Link>
          )
        }
      />
      <ErrorBox error={q.error || customers.error} />
      <div className="panel">
        <div className="panel-heading">
          <h3>Customer enquiries</h3>
          <span className="muted">Latest first</span>
        </div>
        {q.isPending ? (
          <p className="empty">Loading enquiries…</p>
        ) : q.data?.content.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Enquiry / customer</th>
                  <th>Requirement</th>
                  <th>Required by</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {q.data.content.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <Link to={`/sales/${r.id}`}>ENQ-{short(r.id)}</Link>
                      <small>
                        {customers.data?.find((c) => c.id === r.customerId)
                          ?.name ?? short(r.customerId)}
                      </small>
                    </td>
                    <td>
                      {r.items.map((i: any) => i.material).join(", ")}
                      <small>{label(r.businessType)}</small>
                    </td>
                    <td>{r.requiredDate}</td>
                    <td>
                      <Status value={r.status} />
                    </td>
                    <td>
                      <Link aria-label="Open enquiry" to={`/sales/${r.id}`}>
                        <ArrowUpRight size={18} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty text="No enquiries yet" />
        )}
        <Pager page={page} setPage={setPage} data={q.data} />
      </div>
    </>
  );
}
function NewEnquiry() {
  const nav = useNavigate();
  const [customer, setCustomer] = useState("");
  const [project, setProject] = useState("");
  const [lines, setLines] = useState([{ materialId: "", quantity: "" }]);
  const customers = useQuery({
    queryKey: ["customers-all"],
    queryFn: () => all("/api/v1/customers"),
  });
  const materials = useQuery({
    queryKey: ["materials-all"],
    queryFn: () => all("/api/v1/materials"),
  });
  const projects = useQuery({
    queryKey: ["projects", customer],
    queryFn: () => all(`/api/v1/customers/${customer}/projects`),
    enabled: !!customer,
  });
  const sites = useQuery({
    queryKey: ["sites", customer, project],
    queryFn: () =>
      all(`/api/v1/customers/${customer}/projects/${project}/sites`),
    enabled: !!project,
  });
  const mutation = useMutation({
    mutationFn: (body: unknown) => post("/api/v1/rfqs", body),
    onSuccess: (r) => {
      client.invalidateQueries({ queryKey: ["rfqs"] });
      nav(`/sales/${r.id}`);
    },
  });
  return (
    <>
      <Link className="back" to="/sales">
        <ArrowLeft size={16} />
        All enquiries
      </Link>
      <Heading
        eyebrow="SALES / NEW ENQUIRY"
        title="What does your customer need?"
        description="Capture the delivery location and material quantities in tonnes."
      />
      <form
        className="panel form-panel"
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          mutation.mutate({
            customerId: customer,
            deliverySiteId: f.get("deliverySiteId"),
            businessType: f.get("businessType"),
            requiredDate: f.get("requiredDate"),
            remarks: f.get("remarks"),
            items: lines,
          });
        }}
      >
        <ErrorBox
          error={
            mutation.error ||
            customers.error ||
            materials.error ||
            projects.error ||
            sites.error
          }
        />
        <h3>01 · Customer & delivery</h3>
        <div className="form-grid">
          <Field label="Customer">
            <select
              required
              value={customer}
              onChange={(e) => {
                setCustomer(e.target.value);
                setProject("");
              }}
            >
              <option value="">Select customer</option>
              {customers.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Project">
            <select
              required
              value={project}
              onChange={(e) => setProject(e.target.value)}
            >
              <option value="">Select active project</option>
              {projects.data
                ?.filter((p) => p.active)
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
            </select>
          </Field>
          <Field label="Delivery site">
            <select name="deliverySiteId" required key={project}>
              <option value="">Select active site</option>
              {sites.data
                ?.filter((s) => s.active)
                .map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} · {s.address}
                  </option>
                ))}
            </select>
          </Field>
          <Field label="Required date">
            <input name="requiredDate" type="date" min={today()} required />
          </Field>
          <Field label="Business type">
            <select name="businessType">
              <option value="MATERIAL_AND_TRANSPORT">
                Material & transport
              </option>
              <option value="MATERIAL">Material only</option>
              <option value="TRANSPORT">Transport only</option>
            </select>
          </Field>
        </div>
        <h3>02 · Material requirements</h3>
        {lines.map((l, i) => (
          <div className="line-input" key={i}>
            <Field label={`Material ${i + 1}`}>
              <select
                required
                value={l.materialId}
                onChange={(e) =>
                  setLines(
                    lines.map((x, j) =>
                      j === i ? { ...x, materialId: e.target.value } : x,
                    ),
                  )
                }
              >
                <option value="">Select material</option>
                {materials.data
                  ?.filter(
                    (m) =>
                      m.active &&
                      !lines.some((x, j) => j !== i && x.materialId === m.id),
                  )
                  .map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name}
                    </option>
                  ))}
              </select>
            </Field>
            <Field label="Quantity (tonnes)">
              <input
                type="number"
                min="0.001"
                max="999999999.999"
                step="0.001"
                required
                value={l.quantity}
                onChange={(e) =>
                  setLines(
                    lines.map((x, j) =>
                      j === i ? { ...x, quantity: e.target.value } : x,
                    ),
                  )
                }
              />
            </Field>
            <button
              type="button"
              className="secondary"
              disabled={lines.length === 1}
              onClick={() => setLines(lines.filter((_, j) => j !== i))}
            >
              Remove
            </button>
          </div>
        ))}
        <button
          type="button"
          className="secondary"
          disabled={lines.length >= 100}
          onClick={() => setLines([...lines, { materialId: "", quantity: "" }])}
        >
          <Plus size={16} />
          Add material
        </button>
        <Field label="Remarks">
          <textarea name="remarks" maxLength={2000} rows={3} />
        </Field>
        <div className="form-end">
          <span>Requirements are saved as a permanent snapshot.</span>
          <button disabled={mutation.isPending}>
            {mutation.isPending ? "Saving…" : "Create enquiry"}
            <ArrowUpRight size={16} />
          </button>
        </div>
      </form>
    </>
  );
}
function Enquiry({ write }: { write: boolean }) {
  const { id } = useParams();
  const qc = useQueryClient();
  const nav = useNavigate();
  const [quoteForm, setQuoteForm] = useState(false);
  const [decision, setDecision] = useState<{
    id: string;
    action: string;
  } | null>(null);
  const q = useQuery({
    queryKey: ["rfq", id],
    queryFn: () => request(`/api/v1/rfqs/${id}`),
  });
  const history = useQuery({
    queryKey: ["quotes", id],
    queryFn: () => request<RecordData[]>(`/api/v1/rfqs/${id}/quotations`),
  });
  const mutation = useMutation({
    mutationFn: ({ path, body }: { path: string; body?: unknown }) =>
      post(path, body),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ["rfq", id] });
      qc.invalidateQueries({ queryKey: ["quotes", id] });
      qc.invalidateQueries({ queryKey: ["rfqs"] });
      qc.invalidateQueries({ queryKey: ["orders"] });
      setQuoteForm(false);
      setDecision(null);
      if (r.orderId) nav(`/orders/${r.orderId}`);
    },
  });
  const r = q.data;
  return (
    <>
      <Link className="back" to="/sales">
        <ArrowLeft size={16} />
        All enquiries
      </Link>
      <ErrorBox error={q.error || history.error || mutation.error} />
      {q.isPending ? (
        <p>Loading enquiry…</p>
      ) : (
        r && (
          <>
            <Heading
              eyebrow={`ENQUIRY / ${short(r.id)}`}
              title={r.items.map((i: any) => i.material).join(" + ")}
              description={`Required ${r.requiredDate} · ${r.deliveryAddress}`}
              action={<Status value={r.status} />}
            />
            <div className="panel summary">
              <div>
                <small>BUSINESS TYPE</small>
                <strong>{label(r.businessType)}</strong>
              </div>
              <div>
                <small>ORDERED-QUANTITY BILLING</small>
                <strong>Quantities agreed at order</strong>
              </div>
              <div>
                <small>REMARKS</small>
                <strong>{r.remarks || "No remarks"}</strong>
              </div>
            </div>
            <div className="panel">
              <div className="panel-heading">
                <h3>Quotation history</h3>
                {write && !["CONVERTED", "ACCEPTED"].includes(r.status) && (
                  <button
                    className="secondary"
                    onClick={() => setQuoteForm(!quoteForm)}
                  >
                    <Plus size={16} />
                    New revision
                  </button>
                )}
              </div>
              {quoteForm && (
                <form
                  className="form-panel"
                  onSubmit={(e) => {
                    e.preventDefault();
                    const f = new FormData(e.currentTarget);
                    mutation.mutate({
                      path: `/api/v1/rfqs/${id}/quotations`,
                      body: {
                        validUntil: f.get("validUntil"),
                        paymentTerms: f.get("paymentTerms"),
                        deliveryTerms: f.get("deliveryTerms"),
                        items: r.items.map((i: any) => ({
                          rfqItemId: i.id,
                          materialRate: f.get(`material-${i.id}`) || "0",
                          transportRate: f.get(`transport-${i.id}`) || "0",
                        })),
                      },
                    });
                  }}
                >
                  <Field label="Valid until">
                    <input
                      required
                      name="validUntil"
                      type="date"
                      min={today()}
                    />
                  </Field>
                  {r.items.map((i: any) => (
                    <div className="line-input" key={i.id}>
                      <strong>
                        {i.material}
                        <small>{i.quantity} tonnes</small>
                      </strong>
                      <Field label="Material ₹ / tonne">
                        <input
                          name={`material-${i.id}`}
                          type="number"
                          min="0"
                          max="9999999999.99"
                          step="0.01"
                          defaultValue="0"
                          required
                          disabled={r.businessType === "TRANSPORT"}
                        />
                      </Field>
                      <Field label="Transport ₹ / tonne">
                        <input
                          name={`transport-${i.id}`}
                          type="number"
                          min="0"
                          max="9999999999.99"
                          step="0.01"
                          defaultValue="0"
                          required
                          disabled={r.businessType === "MATERIAL"}
                        />
                      </Field>
                    </div>
                  ))}
                  <Field label="Payment terms">
                    <textarea name="paymentTerms" required maxLength={1000} />
                  </Field>
                  <Field label="Delivery terms">
                    <textarea
                      name="deliveryTerms"
                      required
                      maxLength={1000}
                      defaultValue="Customer pays loading/unloading; supplier pays royalty."
                    />
                  </Field>
                  <button disabled={mutation.isPending}>
                    Save quotation revision
                  </button>
                </form>
              )}
              {history.data?.length === 0 && (
                <Empty text="Ready for your first quotation" />
              )}
              {history.data?.map((c, index) => (
                <article className="quotation" key={c.id}>
                  <div className="quote-heading">
                    <div>
                      <span className="eyebrow">REVISION {c.revision}</span>
                      <h2>{money(c.subtotal)}</h2>
                      <small>
                        Subtotal excluding GST · valid through {c.validUntil}
                      </small>
                    </div>
                    <Status
                      value={
                        c.expired && c.status === "SENT" ? "EXPIRED" : c.status
                      }
                    />
                  </div>
                  <div className="table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Material</th>
                          <th>Tonnes</th>
                          <th>Material / t</th>
                          <th>Transport / t</th>
                          <th>Subtotal</th>
                        </tr>
                      </thead>
                      <tbody>
                        {c.items.map((i: any) => (
                          <tr key={i.rfqItemId}>
                            <td>{i.material}</td>
                            <td>{i.quantity}</td>
                            <td>{money(i.materialRate)}</td>
                            <td>{money(i.transportRate)}</td>
                            <td>{money(i.subtotal)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <p>
                    <strong>Payment:</strong> {c.paymentTerms}
                  </p>
                  <p>
                    <strong>Delivery:</strong> {c.deliveryTerms}
                  </p>
                  {c.approvalReference && (
                    <p>
                      Customer decision: {c.customerContact} ·{" "}
                      {c.approvalReference} · {c.decisionNote}
                    </p>
                  )}
                  <div className="actions">
                    {write && c.status === "DRAFT" && index === 0 && (
                      <button
                        disabled={mutation.isPending}
                        onClick={() =>
                          mutation.mutate({
                            path: `/api/v1/quotations/${c.id}/send`,
                          })
                        }
                      >
                        Record as sent
                      </button>
                    )}
                    {write && c.status === "SENT" && !c.expired && (
                      <>
                        <button
                          disabled={mutation.isPending}
                          onClick={() =>
                            setDecision({ id: c.id, action: "accept" })
                          }
                        >
                          <Check size={16} />
                          Record acceptance
                        </button>
                        <button
                          className="secondary"
                          disabled={mutation.isPending}
                          onClick={() =>
                            setDecision({ id: c.id, action: "reject" })
                          }
                        >
                          Record rejection
                        </button>
                      </>
                    )}
                    {write &&
                      c.status === "ACCEPTED" &&
                      !c.convertedOrderId && (
                        <button
                          disabled={mutation.isPending}
                          onClick={() =>
                            mutation.mutate({
                              path: `/api/v1/quotations/${c.id}/convert`,
                            })
                          }
                        >
                          Convert to order <ArrowUpRight size={16} />
                        </button>
                      )}
                    {c.convertedOrderId && (
                      <Link
                        className="button secondary"
                        to={`/orders/${c.convertedOrderId}`}
                      >
                        View confirmed order
                      </Link>
                    )}
                  </div>
                  {decision?.id === c.id && (
                    <form
                      className="decision"
                      onSubmit={(e) => {
                        e.preventDefault();
                        const f = new FormData(e.currentTarget);
                        mutation.mutate({
                          path: `/api/v1/quotations/${c.id}/${decision.action}`,
                          body: Object.fromEntries(f),
                        });
                      }}
                    >
                      <h3>
                        Record customer{" "}
                        {decision.action === "accept"
                          ? "acceptance"
                          : "rejection"}
                      </h3>
                      <Field label="Customer contact">
                        <input
                          name="customerContact"
                          maxLength={160}
                          required
                        />
                      </Field>
                      <Field label="Customer PO / email / call reference">
                        <input
                          name="approvalReference"
                          maxLength={300}
                          required
                        />
                      </Field>
                      <Field
                        label={decision.action === "accept" ? "Note" : "Reason"}
                      >
                        <textarea
                          name={
                            decision.action === "accept" ? "note" : "reason"
                          }
                          required={decision.action === "reject"}
                          maxLength={1000}
                        />
                      </Field>
                      <div className="actions">
                        <button disabled={mutation.isPending}>
                          Save decision
                        </button>
                        <button
                          type="button"
                          className="secondary"
                          onClick={() => setDecision(null)}
                        >
                          Cancel
                        </button>
                      </div>
                    </form>
                  )}
                </article>
              ))}
            </div>
            <p className="muted">
              “Record as sent” records an external staff action. It does not
              send a message to the customer.
            </p>
          </>
        )
      )}
    </>
  );
}
function Orders() {
  const [page, setPage] = useState(0);
  const q = useQuery({
    queryKey: ["orders", page],
    queryFn: () =>
      request<Page<RecordData>>(`/api/v1/orders?page=${page}&size=20`),
  });
  return (
    <>
      <Heading
        eyebrow="SALES / CONFIRMED ORDERS"
        title="Agreed. Recorded. Ready."
        description="Commercial commitments and delivery quantities, kept separate."
      />
      <ErrorBox error={q.error} />
      <div className="panel">
        {q.isPending ? (
          <p className="empty">Loading orders…</p>
        ) : q.data?.content.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Material</th>
                  <th>Ordered tonnes</th>
                  <th>Loaded</th>
                  <th>Received</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {q.data.content.map((o) => (
                  <tr key={o.id}>
                    <td>
                      <Link to={`/orders/${o.id}`}>ORD-{short(o.id)}</Link>
                    </td>
                    <td>{o.material}</td>
                    <td>{o.orderedQuantity}</td>
                    <td>{o.loadedQuantity}</td>
                    <td>{o.receivedQuantity}</td>
                    <td>
                      <Link to={`/orders/${o.id}`}>
                        <ArrowUpRight size={18} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty text="No confirmed orders yet" />
        )}
        <Pager page={page} setPage={setPage} data={q.data} />
      </div>
    </>
  );
}
function Order() {
  const { id } = useParams();
  const q = useQuery({
    queryKey: ["order", id],
    queryFn: () => request(`/api/v1/orders/${id}`),
  });
  const o = q.data;
  return (
    <>
      <Link className="back" to="/orders">
        <ArrowLeft size={16} />
        All orders
      </Link>
      <ErrorBox error={q.error} />
      {q.isPending ? (
        <p>Loading order…</p>
      ) : (
        o && (
          <>
            <Heading
              eyebrow={`ORDER / ${short(o.id)}`}
              title={o.material}
              description={o.deliverySite}
            />
            <div className="notice">
              <strong>Billing follows the agreed ordered quantity.</strong>
              <p>
                Loaded and received weights describe execution. They do not
                automatically change the billable quantity. Subtotals exclude
                tax.
              </p>
            </div>
            <div className="panel table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Material</th>
                    <th>Ordered</th>
                    <th>Billable</th>
                    <th>Loaded</th>
                    <th>Received</th>
                    <th>Subtotal</th>
                  </tr>
                </thead>
                <tbody>
                  {o.items?.map((i: any) => (
                    <tr key={i.id}>
                      <td>{i.material}</td>
                      <td>{i.orderedQuantity} t</td>
                      <td>{i.billableQuantity} t</td>
                      <td>{i.loadedQuantity} t</td>
                      <td>{i.receivedQuantity} t</td>
                      <td>{money(i.subtotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )
      )}
    </>
  );
}
function Customers({ write }: { write: boolean }) {
  const [page, setPage] = useState(0);
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState("");
  const [project, setProject] = useState("");
  const q = useQuery({
    queryKey: ["customers", page],
    queryFn: () =>
      request<Page<RecordData>>(`/api/v1/customers?page=${page}&size=20`),
  });
  const projects = useQuery({
    queryKey: ["projects", selected],
    queryFn: () => all(`/api/v1/customers/${selected}/projects`),
    enabled: !!selected,
  });
  const sites = useQuery({
    queryKey: ["sites", selected, project],
    queryFn: () =>
      all(`/api/v1/customers/${selected}/projects/${project}/sites`),
    enabled: !!project,
  });
  const branches = useQuery({
    queryKey: ["branches"],
    queryFn: () => all("/api/v1/branches"),
    enabled: write && !!selected,
  });
  const m = useMutation({
    mutationFn: ({ path, body }: { path: string; body: unknown }) =>
      post(path, body),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["customers"] });
      client.invalidateQueries({ queryKey: ["customers-all"] });
      client.invalidateQueries({ queryKey: ["projects"] });
      client.invalidateQueries({ queryKey: ["sites"] });
      setCreate(false);
    },
  });
  function submit(e: FormEvent<HTMLFormElement>, path: string) {
    e.preventDefault();
    const form = e.currentTarget;
    const body: any = Object.fromEntries(new FormData(form));
    if ("gstin" in body && !body.gstin) body.gstin = null;
    if ("mobile" in body && !body.mobile) body.mobile = null;
    m.mutate({ path, body }, { onSuccess: () => form.reset() });
  }
  return (
    <>
      <Heading
        eyebrow="DIRECTORY / CUSTOMERS"
        title="Know who you supply."
        description="Customer records, projects and delivery sites."
        action={
          write && (
            <button onClick={() => setCreate(!create)}>
              <Plus size={16} />
              New customer
            </button>
          )
        }
      />
      <ErrorBox
        error={
          q.error || m.error || projects.error || sites.error || branches.error
        }
      />
      {create && (
        <form
          className="panel form-panel"
          onSubmit={(e) => submit(e, "/api/v1/customers")}
        >
          <div className="form-grid">
            <Field label="Customer name">
              <input name="name" maxLength={160} required />
            </Field>
            <Field label="Mobile">
              <input name="mobile" pattern="[+0-9 ()\-]{7,20}" required />
            </Field>
            <Field label="GSTIN (optional)">
              <input
                name="gstin"
                pattern="[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][A-Z0-9]Z[A-Z0-9]"
              />
            </Field>
          </div>
          <button disabled={m.isPending}>Save customer</button>
        </form>
      )}
      <div className="panel">
        {q.isPending ? (
          <p className="empty">Loading customers…</p>
        ) : q.data?.content.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Mobile</th>
                  <th>GSTIN</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {q.data.content.map((c) => (
                  <tr key={c.id}>
                    <td>{c.name}</td>
                    <td>{c.mobile}</td>
                    <td>{c.gstin || "—"}</td>
                    <td>
                      <button
                        className="secondary"
                        onClick={() => {
                          setSelected(c.id);
                          setProject("");
                        }}
                      >
                        Projects & sites
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty text="No customers yet" />
        )}
        <Pager page={page} setPage={setPage} data={q.data} />
      </div>
      {selected && (
        <div className="panel form-panel">
          <h2>
            {q.data?.content.find((c) => c.id === selected)?.name} · Projects &
            sites
          </h2>
          <Field label="Project">
            <select
              value={project}
              onChange={(e) => setProject(e.target.value)}
            >
              <option value="">Select project</option>
              {projects.data?.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                  {!p.active ? " (inactive)" : ""}
                </option>
              ))}
            </select>
          </Field>
          {sites.data?.map((s) => (
            <div className="site" key={s.id}>
              <strong>{s.name}</strong>
              <p>{s.address}</p>
              <Status value={s.active ? "ACTIVE" : "INACTIVE"} />
            </div>
          ))}
          {write && (
            <details>
              <summary>Create a project</summary>
              <form
                onSubmit={(e) =>
                  submit(e, `/api/v1/customers/${selected}/projects`)
                }
              >
                <Field label="Branch">
                  <select name="branchId" required>
                    <option value="">Select active branch</option>
                    {branches.data
                      ?.filter((b) => b.active)
                      .map((b) => (
                        <option key={b.id} value={b.id}>
                          {b.name}
                        </option>
                      ))}
                  </select>
                </Field>
                <CodeName />
                <Field label="Description">
                  <input name="description" maxLength={500} />
                </Field>
                <button disabled={m.isPending}>Save project</button>
              </form>
            </details>
          )}
          {write && project && (
            <details>
              <summary>Add a delivery site</summary>
              <form
                onSubmit={(e) =>
                  submit(
                    e,
                    `/api/v1/customers/${selected}/projects/${project}/sites`,
                  )
                }
              >
                <CodeName />
                <Field label="Address">
                  <textarea name="address" maxLength={500} required />
                </Field>
                <Field label="Contact name">
                  <input name="contactName" maxLength={160} />
                </Field>
                <Field label="Mobile (optional)">
                  <input name="mobile" pattern="[+0-9 ()\-]{7,20}" />
                </Field>
                <button disabled={m.isPending}>Save delivery site</button>
              </form>
            </details>
          )}
        </div>
      )}
    </>
  );
}
function CodeName() {
  return (
    <div className="form-grid">
      <Field label="Code">
        <input
          name="code"
          pattern="[A-Za-z0-9][A-Za-z0-9_\-]{1,29}"
          minLength={2}
          maxLength={30}
          required
        />
      </Field>
      <Field label="Name">
        <input name="name" maxLength={160} required />
      </Field>
    </div>
  );
}
function Materials({ admin }: { admin: boolean }) {
  const [page, setPage] = useState(0);
  const [create, setCreate] = useState(false);
  const q = useQuery({
    queryKey: ["materials", page],
    queryFn: () =>
      request<Page<RecordData>>(`/api/v1/materials?page=${page}&size=20`),
  });
  const m = useMutation({
    mutationFn: (body: unknown) => post("/api/v1/materials", body),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["materials"] });
      client.invalidateQueries({ queryKey: ["materials-all"] });
      setCreate(false);
    },
  });
  return (
    <>
      <Heading
        eyebrow="DIRECTORY / MATERIALS"
        title="The materials you move."
        description="Your shared catalogue. Quantities are recorded in tonnes."
        action={
          admin && (
            <button onClick={() => setCreate(!create)}>
              <Plus size={16} />
              New material
            </button>
          )
        }
      />
      <ErrorBox error={q.error || m.error} />
      {create && (
        <form
          className="panel form-panel"
          onSubmit={(e) => {
            e.preventDefault();
            const b: any = Object.fromEntries(new FormData(e.currentTarget));
            if (!b.hsnCode) b.hsnCode = null;
            m.mutate(b);
          }}
        >
          <CodeName />
          <Field label="Category">
            <select name="category">
              {["AGGREGATES", "SAND", "DUST", "ROAD_MATERIALS", "OTHER"].map(
                (x) => (
                  <option key={x}>{x}</option>
                ),
              )}
            </select>
          </Field>
          <Field label="HSN code (optional)">
            <input name="hsnCode" pattern="[0-9]{4}|[0-9]{6}|[0-9]{8}" />
          </Field>
          <button disabled={m.isPending}>Save material</button>
        </form>
      )}
      <div className="panel">
        {q.isPending ? (
          <p className="empty">Loading materials…</p>
        ) : q.data?.content.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Material</th>
                  <th>Code</th>
                  <th>Category</th>
                  <th>HSN</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {q.data.content.map((x) => (
                  <tr key={x.id}>
                    <td>{x.name}</td>
                    <td>{x.code}</td>
                    <td>{label(x.category)}</td>
                    <td>{x.hsnCode || "—"}</td>
                    <td>
                      <Status value={x.active ? "ACTIVE" : "INACTIVE"} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty text="No materials yet" />
        )}
        <Pager page={page} setPage={setPage} data={q.data} />
      </div>
    </>
  );
}
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={client}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
);
