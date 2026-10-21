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
    access.write
      ? {
          name: "Compliance alerts in view",
          number: count(compliance.data),
          note: "First 100 alerts · due within 30 days",
          icon: ShieldCheck,
          to: "/fleet",
          error: compliance.isError,
        }
      : {
          name: "Expenses awaiting review",
          number: expenses.data ? pending.length : undefined,
          note: "Across the latest 100 expense records",
          icon: Fuel,
          to: "/operating",
          error: expenses.isError,
        },
  ];
  const stages = [
    { title: "Allocated", states: ["ALLOCATED"], color: "planning" },
    { title: "Loading", states: ["LOADING", "LOADED"], color: "loading" },
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
          <p className="eyebrow">YFC TRANSPORT / OPERATIONS OVERVIEW</p>
          <h1>Your business. In motion.</h1>
          <p>
            Material supply, fleet movement and the work that needs your
            attention.
          </p>
        </div>
        <button
          className="secondary"
          disabled={loading}
          onClick={() =>
            queries.forEach((q) => {
              void q.refetch();
            })
          }
        >
          <RefreshCw size={15} className={loading ? "spinning" : ""} />
          {loading ? "Refreshing" : "Refresh overview"}
        </button>
      </div>
      {(access.admin || access.operations) && rows(requests.data).length > 0 && (
        <Link className="dispatch-banner request-banner" to="/account-requests">
          <div>
            <span className="dispatch-kicker">ACCOUNT REQUESTS</span>
            <h2>
              {rows(requests.data).length}{" "}
              {rows(requests.data).length === 1 ? "person is" : "people are"} waiting
              for a login.
            </h2>
            <p>Review the details they submitted, then approve or disapprove.</p>
          </div>
        </Link>
      )}
      {(access.admin || access.operations) && publicLeads > 0 && (
        <Link className="dispatch-banner request-banner" to="/site">
          <div>
            <span className="dispatch-kicker">CUSTOMER PAGE</span>
            <h2>
              {publicLeads} {publicLeads === 1 ? "item is" : "items are"} waiting on the
              public desk.
            </h2>
            <p>Enquiries and quotes from the customer page, in the latest 50 of each.</p>
          </div>
        </Link>
      )}
      <section className="dispatch-banner">
        <div className="dispatch-copy">
          <span className="dispatch-kicker">
            <span /> TRANSPORT & MATERIAL SUPPLY
          </span>
          <h2>
            From customer demand
            <br />
            to the final delivery.
          </h2>
          <p>A connected view of your orders, dispatches and owned fleet.</p>
          <div className="dispatch-actions">
            <Link
              className="button light"
              to={access.write ? "/trips" : "/orders"}
            >
              {access.write ? "Open dispatch workspace" : "View sales orders"}
              <ArrowUpRight size={17} />
            </Link>
            {access.write && (
              <Link to="/sales/new">
                <Plus size={16} /> New enquiry
              </Link>
            )}
          </div>
        </div>
        <div className="dispatch-map" aria-hidden="true">
          <div className="map-grid" />
          <svg viewBox="0 0 430 210">
            <path
              d="M42 155 H140 Q164 155 164 131 V78 Q164 56 188 56 H340 Q367 56 367 81 V132"
              fill="none"
              stroke="#799b75"
              strokeWidth="2"
              strokeDasharray="5 6"
            />
            <circle cx="42" cy="155" r="7" fill="#d5e6ac" />
            <circle cx="367" cy="132" r="7" fill="#d5e6ac" />
          </svg>
          <span className="route-label route-source">
            <Warehouse size={17} />
            SOURCE YARD
          </span>
          <span className="route-truck">
            <Truck size={47} strokeWidth={1.4} />
          </span>
          <span className="route-label route-site">
            <MapPin size={17} />
            CUSTOMER SITE
          </span>
          <span className="route-caption">SOURCE → DISPATCH → DELIVERY</span>
        </div>
      </section>
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
