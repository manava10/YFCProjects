import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  ArrowUpRight,
  ArrowRight,
  Truck,
  Package,
  ClipboardList,
  ShieldCheck,
  Fuel,
  Plus,
  Warehouse,
  CircleCheck,
  RefreshCw,
  MapPin,
  Activity,
} from "lucide-react";
import { request, type Page, type RecordData } from "./api";
import { short, val, label, isPendingReview } from "./lib";
import { ErrorBox, Status, type Access } from "./ui";

type Results = Page<RecordData> | RecordData[];
const rows = (data?: Results) =>
  Array.isArray(data) ? data : (data?.content ?? []);
const count = (data?: Results) =>
  data ? (Array.isArray(data) ? data.length : data.totalElements) : undefined;
function useOverview(key: string, path: string, enabled = true) {
  return useQuery({
    queryKey: ["overview", key],
    queryFn: () => request<Results>(path),
    enabled,
  });
}
export function Overview({ access }: { access: Access }) {
  const orders = useOverview("orders", "/api/v1/orders?size=100");
  const enquiries = useOverview("enquiries", "/api/v1/rfqs?size=100");
  const trips = useOverview("trips", "/api/v1/trips?size=100");
  const compliance = useOverview(
    "compliance",
    "/api/v1/fleet/compliance-alerts?days=30&size=100",
    access.write,
  );
  const requests = useOverview(
    "account-requests",
    "/api/v1/account-applications?status=PENDING&size=100",
    access.admin || access.operations,
  );
  const publicDesk = useQuery({
    queryKey: ["overview", "public-desk"],
    queryFn: () =>
      request<{ enquiries: unknown[]; quotes: { stage: string }[] }>(
        "/api/v1/site/desk",
      ),
    enabled: access.admin || access.operations,
  });
  const publicLeads = publicDesk.data
    ? publicDesk.data.enquiries.length +
      publicDesk.data.quotes.filter((quote) => quote.stage !== "DELIVERED").length
    : 0;
  const alertsSummary = useQuery({
    queryKey: ["overview", "alerts-summary"],
    queryFn: () =>
      request<{ totalAlerts: number; criticalCount: number; warningCount: number }>(
        "/api/v1/alerts/summary",
      ),
  });
  const expenses = useOverview(
    "expenses",
    "/api/v1/expenses?size=100",
    access.finance,
  );
  const tripRows = rows(trips.data);
  const active = tripRows.filter(
    (r) =>
      !["DELIVERED", "CANCELLED"].includes(String(val(r, "status", "state"))),
  );
  const pending = rows(expenses.data).filter(isPendingReview);
  const queries = [
    orders,
    enquiries,
    trips,
    alertsSummary,
    ...(access.write ? [compliance] : []),
    ...(access.finance ? [expenses] : []),
    ...(access.admin || access.operations ? [requests, publicDesk] : []),
  ];
  const loading = queries.some((q) => q.isFetching);
  const stats = [
    {
      name: "Sales orders",
      number: count(orders.data),
      note: "Confirmed commercial commitments",
      icon: Package,
      to: "/orders",
      error: orders.isError,
    },
    {
      name: "Customer enquiries",
      number: count(enquiries.data),
      note: "Requirements in the sales pipeline",
      icon: ClipboardList,
      to: "/sales",
      error: enquiries.isError,
    },
    {
      name: "Active trips in view",
      number: trips.data ? active.length : undefined,
      note: "Across the latest 100 trip records",
      icon: Truck,
      to: "/trips",
      error: trips.isError,
    },
    {
      name: "Operational alerts",
      number: alertsSummary.data?.totalAlerts,
      note: alertsSummary.data
        ? `${alertsSummary.data.criticalCount} critical · ${alertsSummary.data.warningCount} warning (E-Way, compliance, maintenance, overdue)`
        : "E-Way Bills, compliance, maintenance & overdue bills",
      icon: ShieldCheck,
      to: "/alerts",
      error: alertsSummary.isError,
    },
  ];
  const stages = [
    { title: "Allocated", states: ["ALLOCATED"], color: "planning" },
    { title: "Loading", states: ["LOADING", "LOADED"], color: "stage-loading" },
    {
      title: "On the road",
      states: ["DISPATCHED", "ARRIVED", "UNLOADING"],
      color: "road",
    },
    { title: "Delivered", states: ["DELIVERED"], color: "done" },
  ];
  return (
    <div className="transport-overview">
      <div className="ops-title">
        <div>
          <p className="eyebrow">Operations Command Center</p>
          <h1>Overview</h1>
          <p>
            Live snapshot of orders, yard supply, fleet movement, and items needing review.
          </p>
        </div>
        <div className="ops-title-actions">
          <button
            className="secondary"
            disabled={loading}
            onClick={() =>
              queries.forEach((q) => {
                void q.refetch();
              })
            }
          >
            <RefreshCw size={14} className={loading ? "spinning" : ""} />
            {loading ? "Refreshing" : "Refresh"}
          </button>
          {access.write && (
            <Link className="button secondary" to="/sales/new">
              <Plus size={14} /> New enquiry
            </Link>
          )}
          <Link className="button" to={access.write ? "/trips" : "/orders"}>
            {access.write ? "Dispatch workspace" : "View sales orders"}
            <ArrowUpRight size={15} />
          </Link>
        </div>
      </div>
      {(access.admin || access.operations) &&
        (rows(requests.data).length > 0 || publicLeads > 0) && (
          <div className="alert-strip">
            {rows(requests.data).length > 0 && (
              <Link className="alert-pill" to="/account-requests">
                <div>
                  <strong>
                    {rows(requests.data).length}{" "}
                    {rows(requests.data).length === 1
                      ? "account request"
                      : "account requests"}{" "}
                    pending approval
                  </strong>
                  <span>Review submitted customer, supplier, or driver details</span>
                </div>
                <ArrowUpRight size={16} />
              </Link>
            )}
            {publicLeads > 0 && (
              <Link className="alert-pill info" to="/site">
                <div>
                  <strong>
                    {publicLeads}{" "}
                    {publicLeads === 1 ? "lead waiting" : "leads waiting"} on
                    the public desk
                  </strong>
                  <span>Enquiries and open quotes from the customer website</span>
                </div>
                <ArrowUpRight size={16} />
              </Link>
            )}
          </div>
        )}
      <div className="ops-stats">
        {stats.map((s) => (
          <Link to={s.to} className="ops-stat" key={s.name}>
            <div className="stat-top">
              <span>{s.name}</span>
              <s.icon size={19} />
            </div>
            <strong>
              {s.error
                ? "—"
                : s.number === undefined
                  ? "…"
                  : new Intl.NumberFormat("en-IN").format(s.number)}
            </strong>
            <p>
              {s.error ? "Unable to load · open workspace to retry" : s.note}
            </p>
            <ArrowUpRight className="stat-arrow" size={16} />
          </Link>
        ))}
      </div>
      <ErrorBox error={queries.find((q) => q.error)?.error} />
      <div className="ops-columns">
        <section className="ops-section">
          <div className="ops-section-title">
            <div>
              <p className="eyebrow">DISPATCH CONTROL</p>
              <h3>Trip movement</h3>
            </div>
            <Link to="/trips">
              View trips <ArrowUpRight size={15} />
            </Link>
          </div>
          <div className="movement-stages">
            {stages.map((stage, i) => (
              <div
                className={`movement-stage ${stage.color}`}
                key={stage.title}
              >
                <span>
                  <i />
                  {stage.title}
                </span>
                <strong>
                  {trips.data
                    ? tripRows.filter((r) =>
                        stage.states.includes(
                          String(val(r, "status", "state")),
                        ),
                      ).length
                    : "—"}
                </strong>
                {i < stages.length - 1 && (
                  <ArrowRight className="stage-arrow" size={16} />
                )}
              </div>
            ))}
          </div>
          <p className="scope-note">
            Status breakdown of the latest 100 trip records. Delivered totals
            cover this view.
          </p>
          <div className="ops-trip-table">
            {trips.isError ? (
              <div className="ops-empty">
                <Activity size={28} />
                <h4>Trip data is unavailable</h4>
                <p>Refresh the overview to try again.</p>
              </div>
            ) : !trips.data ? (
              <div className="ops-empty">Loading trip movement…</div>
            ) : tripRows.length ? (
              <table>
                <thead>
                  <tr>
                    <th>Trip / truck</th>
                    <th>Source</th>
                    <th>Quantity</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {tripRows.slice(0, 6).map((r) => (
                    <tr key={r.id}>
                      <td>
                        <Link to={`/trips/${r.id}`}>TRP-{short(r.id)}</Link>
                        <small>
                          {val(r, "truck_registration", "truckRegistration") ??
                            "Truck assigned"}
                        </small>
                      </td>
                      <td>{label(String(val(r, "source") ?? ""))}</td>
                      <td>
                        {val(
                          r,
                          "planned_quantity",
                          "plannedQuantity",
                          "quantity",
                        ) ?? "—"}{" "}
                        t
                      </td>
                      <td>
                        <Status
                          value={String(val(r, "status", "state") ?? "")}
                        />
                      </td>
                      <td>
                        <Link
                          to={`/trips/${r.id}`}
                          aria-label={`Open trip ${short(r.id)}`}
                        >
                          <ArrowUpRight size={16} />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="ops-empty">
                <div className="empty-truck">
                  <Truck size={32} />
                </div>
                <h4>Your next delivery starts here</h4>
                <p>
                  Trips will appear once an order has a material source,
                  <br />
                  an available truck and an assigned driver.
                </p>
                <Link to={access.write ? "/trips" : "/orders"}>
                  {access.write ? "Plan your first trip" : "View orders"}
                  <ArrowRight size={15} />
                </Link>
              </div>
            )}
          </div>
        </section>
        <div className="ops-right">
          <section className="ops-section attention">
            <div className="ops-section-title">
              <div>
                <p className="eyebrow">KEEP WORK MOVING</p>
                <h3>Attention & approvals</h3>
              </div>
              <span className="attention-dot" />
            </div>
            {access.write && (
              <Link className="attention-item" to="/fleet">
                <span className="attention-icon">
                  <ShieldCheck size={20} />
                </span>
                <div>
                  <strong>Vehicle compliance</strong>
                  <p>
                    {compliance.isError
                      ? "Unable to load compliance alerts"
                      : compliance.data
                        ? `${count(compliance.data)} alerts in view · next 30 days`
                        : "Checking compliance records…"}
                  </p>
                </div>
                <Chevron />
              </Link>
            )}
            {access.finance && (
              <Link className="attention-item" to="/operating">
                <span className="attention-icon amber">
                  <Fuel size={20} />
                </span>
                <div>
                  <strong>Expense approvals</strong>
                  <p>
                    {expenses.isError
                      ? "Unable to load expense approvals"
                      : expenses.data
                        ? `${pending.length} pending in the latest 100 records`
                        : "Checking submitted expenses…"}
                  </p>
                </div>
                <Chevron />
              </Link>
            )}
            <Link className="attention-item" to="/stock">
              <span className="attention-icon">
                <Warehouse size={20} />
              </span>
              <div>
                <strong>Stock control</strong>
                <p>Open counts, returns and transfer reviews</p>
              </div>
              <Chevron />
            </Link>
          </section>
          <section className="ops-section quick-start">
            <p className="eyebrow">QUICK ACCESS</p>
            <h3>The right workspace, one click away.</h3>
            <div className="quick-grid">
              {[
                [UsersIcon, "Customers", "/customers"],
                [Package, "Materials", "/materials"],
                [Warehouse, "Purchases", "/purchases"],
                [CircleCheck, "Orders", "/orders"],
              ].map(([Icon, title, path]) => {
                const C = Icon as typeof Package;
                return (
                  <Link key={String(path)} to={String(path)}>
                    <C size={20} />
                    <span>{String(title)}</span>
                    <ArrowUpRight size={13} />
                  </Link>
                );
              })}
            </div>
          </section>
        </div>
      </div>
      <div className="ops-bottom">
        <span>
          <CircleCheck size={15} /> Connected workflows, from enquiry to
          delivery
        </span>
        <span>Commercial amounts exclude GST · Quantities in tonnes</span>
      </div>
    </div>
  );
}
function Chevron() {
  return <ArrowUpRight size={15} />;
}
function UsersIcon(props: { size?: number }) {
  return <ClipboardList {...props} />;
}
