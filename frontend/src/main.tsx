import { Suspense, lazy, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  BrowserRouter,
  Link,
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
} from "@tanstack/react-query";
import {
  ArrowUpRight,
  Bell,
  ClipboardList,
  ChevronRight,
  Fuel,
  Globe,
  Landmark,
  Layers3,
  LogOut,
  Package,
  ScrollText,
  Search,
  Shield,
  Truck,
  Users,
  Warehouse,
  type LucideIcon,
} from "lucide-react";
import { CommandPalette } from "./command-palette";
import { ApiError, login, logout, request } from "./api";
import {
  AccessContext,
  ErrorBox,
  Field,
  PasswordInput,
  type Access,
} from "./ui";
import { ErrorBoundary } from "./error-boundary";
import { CustomerLogin, CustomerSignup, PublicSite } from "./public-site";
import "./style.css";

const Audit = lazy(() => import("./admin").then((m) => ({ default: m.Audit })));
const People = lazy(() => import("./admin").then((m) => ({ default: m.People })));
const ResetPassword = lazy(() => import("./admin").then((m) => ({ default: m.ResetPassword })));
const AccountRequests = lazy(() => import("./portal").then((m) => ({ default: m.AccountRequests })));
const ExternalHome = lazy(() => import("./portal").then((m) => ({ default: m.ExternalHome })));
const Billing = lazy(() => import("./finance").then((m) => ({ default: m.Billing })));
const Journal = lazy(() => import("./finance").then((m) => ({ default: m.Journal })));
const Ledger = lazy(() => import("./finance").then((m) => ({ default: m.Ledger })));
const Money = lazy(() => import("./finance").then((m) => ({ default: m.Money })));
const Parties = lazy(() => import("./finance").then((m) => ({ default: m.Parties })));
const Preparation = lazy(() => import("./finance").then((m) => ({ default: m.Preparation })));
const Fleet = lazy(() => import("./fleet").then((m) => ({ default: m.Fleet })));
const Trip = lazy(() => import("./fleet").then((m) => ({ default: m.Trip })));
const Trips = lazy(() => import("./fleet").then((m) => ({ default: m.Trips })));
const Branches = lazy(() => import("./masters").then((m) => ({ default: m.Branches })));
const Customers = lazy(() => import("./masters").then((m) => ({ default: m.Customers })));
const Materials = lazy(() => import("./masters").then((m) => ({ default: m.Materials })));
const Suppliers = lazy(() => import("./masters").then((m) => ({ default: m.Suppliers })));
const Operating = lazy(() => import("./operating").then((m) => ({ default: m.Operating })));
const Enquiry = lazy(() => import("./sales").then((m) => ({ default: m.Enquiry })));
const NewEnquiry = lazy(() => import("./sales").then((m) => ({ default: m.NewEnquiry })));
const Order = lazy(() => import("./sales").then((m) => ({ default: m.Order })));
const Orders = lazy(() => import("./sales").then((m) => ({ default: m.Orders })));
const Sales = lazy(() => import("./sales").then((m) => ({ default: m.Sales })));
const Purchase = lazy(() => import("./supply").then((m) => ({ default: m.Purchase })));
const Purchases = lazy(() => import("./supply").then((m) => ({ default: m.Purchases })));
const Reservations = lazy(() => import("./supply").then((m) => ({ default: m.Reservations })));
const StockControl = lazy(() => import("./supply").then((m) => ({ default: m.StockControl })));
const Yards = lazy(() => import("./supply").then((m) => ({ default: m.Yards })));
const Overview = lazy(() => import("./overview").then((m) => ({ default: m.Overview })));
const AlertsCenter = lazy(() => import("./alerts").then((m) => ({ default: m.AlertsCenter })));
const PublicDesk = lazy(() => import("./site-desk").then((m) => ({ default: m.PublicDesk })));

const client = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false, staleTime: 30_000 },
  },
});

type Item = {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
  show: boolean;
};

function App() {
  const path = useLocation().pathname;
  const navigate = useNavigate();
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [signOutError, setSignOutError] = useState<unknown>(null);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setPaletteOpen((v) => !v);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);
  const session = useQuery({
    enabled: path !== "/" && path !== "/signup",
    queryKey: ["session"],
    queryFn: () =>
      request<{ username: string; roles: string[] }>("/api/v1/auth/me"),
  });
  useEffect(() => {
    const expire = () => {
      client.removeQueries({
        predicate: (query) => query.queryKey[0] !== "session",
      });
      client.setQueryData(["session"], null);
    };
    window.addEventListener("session-expired", expire);
    return () => window.removeEventListener("session-expired", expire);
  }, []);
  useEffect(() => {
    document.title =
      path === "/"
        ? "YFC Transport | Material Supply & Transport"
        : path === "/signup" || path === "/join"
          ? "YFC Transport | Register Account"
          : path === "/signin"
            ? "YFC Transport | Customer & Partner Login"
            : path === "/portal"
              ? "YFC Transport | Customer Portal"
              : "YFC · Staff ERP Workspace";
  }, [path]);
  if (path === "/") return <PublicSite />;
  if (path === "/signup") return <CustomerSignup />;
  if (path === "/join") return <Navigate to="/signup?role=SUPPLIER" replace />;
  if (path === "/reset") return <ResetPassword />;

  const sessionRoles = session.data?.roles ?? [];
  const isExternalSession = sessionRoles.some((r) =>
    ["ROLE_CUSTOMER", "ROLE_SUPPLIER", "ROLE_DRIVER"].includes(r),
  );
  const isStaffSession = sessionRoles.some((r) =>
    ["ROLE_SUPER_ADMIN", "ROLE_OPERATIONS", "ROLE_ACCOUNTS"].includes(r),
  );

  if (path === "/signin") {
    if (isExternalSession) return <Navigate to="/portal" replace />;
    return (
      <CustomerLogin
        signedInStaff={isStaffSession ? session.data?.username : null}
        onSignedIn={async () => {
          await client.refetchQueries({ queryKey: ["session"] });
        }}
        onSignOut={async () => {
          client.removeQueries({
            predicate: (query) => query.queryKey[0] !== "session",
          });
          client.setQueryData(["session"], null);
        }}
      />
    );
  }

  if (session.isPending)
    return <div className="loading">Opening your workspace…</div>;
  if (!session.data) {
    if (path === "/portal") return <Navigate to="/signin" replace />;
    return (
      <StaffLogin
        error={
          session.error instanceof ApiError && session.error.status === 401
            ? null
            : session.error
        }
      />
    );
  }
  const roles = session.data.roles;
  const access: Access = {
    username: session.data.username,
    admin: roles.includes("ROLE_SUPER_ADMIN"),
    operations: roles.includes("ROLE_OPERATIONS"),
    accounts: roles.includes("ROLE_ACCOUNTS"),
    customer: roles.includes("ROLE_CUSTOMER"),
    supplier: roles.includes("ROLE_SUPPLIER"),
    driver: roles.includes("ROLE_DRIVER"),
    write: roles.some((role) =>
      ["ROLE_SUPER_ADMIN", "ROLE_OPERATIONS"].includes(role),
    ),
    finance: roles.some((role) =>
      ["ROLE_SUPER_ADMIN", "ROLE_ACCOUNTS"].includes(role),
    ),
    review: roles.some((role) =>
      ["ROLE_SUPER_ADMIN", "ROLE_ACCOUNTS"].includes(role),
    ),
  };
  if (access.customer || access.supplier || access.driver) {
    if (path !== "/portal") return <Navigate to="/portal" replace />;
    return <ExternalHome username={session.data.username} />;
  }
  if (!access.admin && !access.operations && !access.accounts) {
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
              client.removeQueries({
                predicate: (query) => query.queryKey[0] !== "session",
              });
              client.setQueryData(["session"], null);
            })
          }
        >
          Sign out
        </button>
      </main>
    );
  }
  const sections: { label: string; items: Item[] }[] = [
    {
      label: "Workspace",
      items: [
        {
          to: "/dashboard",
          label: "Overview",
          icon: Layers3,
          end: true,
          show: true,
        },
        {
          to: "/alerts",
          label: "Alerts & digest",
          icon: Bell,
          show: true,
        },
      ],
    },
    {
      label: "Sales",
      items: [
        { to: "/sales", label: "Enquiries", icon: ClipboardList, show: true },
        { to: "/orders", label: "Orders", icon: Truck, show: true },
      ],
    },
    {
      label: "Masters",
      items: [
        { to: "/customers", label: "Customers", icon: Users, show: true },
        { to: "/materials", label: "Materials", icon: Package, show: true },
        { to: "/suppliers", label: "Suppliers", icon: Warehouse, show: true },
        { to: "/branches", label: "Branches", icon: Landmark, show: true },
      ],
    },
    {
      label: "Supply",
      items: [
        { to: "/yards", label: "Yards", icon: Warehouse, show: true },
        { to: "/purchases", label: "Purchases", icon: Package, show: true },
        {
          to: "/reservations",
          label: "Reservations",
          icon: ClipboardList,
          show: true,
        },
        { to: "/stock", label: "Stock control", icon: Layers3, show: true },
      ],
    },
    {
      label: "Movement",
      items: [
        { to: "/fleet", label: "Fleet", icon: Truck, show: access.write },
        { to: "/trips", label: "Trips", icon: Truck, show: true },
        { to: "/operating", label: "Fuel & expenses", icon: Fuel, show: true },
      ],
    },
    {
      label: "Finance",
      items: [
        {
          to: "/ledger",
          label: "Ledger",
          icon: Landmark,
          show: access.finance,
        },
        {
          to: "/parties",
          label: "Party ledgers",
          icon: Users,
          show: access.finance,
        },
        {
          to: "/money",
          label: "Cash & bank",
          icon: Landmark,
          show: access.finance,
        },
        {
          to: "/billing",
          label: "Billing prep",
          icon: ScrollText,
          show: access.finance,
        },
      ],
    },
    {
      label: "Administration",
      items: [
        { to: "/people", label: "People", icon: Shield, show: access.admin },
        {
          to: "/account-requests",
          label: "Account requests",
          icon: Users,
          show: access.admin || access.operations,
        },
        {
          to: "/site",
          label: "Customer site",
          icon: Globe,
          show: access.admin || access.operations,
        },
        { to: "/audit", label: "Audit", icon: ScrollText, show: access.admin },
      ],
    },
  ];
  const commandItems = [
    ...sections.flatMap((section) =>
      section.items
        .filter((item) => item.show)
        .map((item) => ({
          to: item.to,
          label: item.label,
          section: section.label,
          icon: item.icon,
        })),
    ),
    ...(access.write
      ? [
          {
            to: "/sales/new",
            label: "New customer enquiry (RFQ)",
            section: "Quick action",
            icon: ClipboardList,
          },
        ]
      : []),
  ];
  return (
    <AccessContext.Provider value={access}>
      <CommandPalette
        items={commandItems}
        open={paletteOpen}
        onClose={() => setPaletteOpen(false)}
      />
      <div className="shell">
        <aside>
          <Link className="brand" to="/">
            <span className="brandmark">Y</span>
            <span>
              YFC<span className="brand-sub">TRANSPORT ERP</span>
            </span>
          </Link>
          <button
            type="button"
            className="cmd-trigger"
            onClick={() => setPaletteOpen(true)}
          >
            <span className="cmd-trigger-left">
              <Search size={14} /> Jump to…
            </span>
            <kbd>⌘K</kbd>
          </button>
          <div className="nav-scroll">
            {sections.map((section) =>
              section.items.some((item) => item.show) ? (
                <div key={section.label}>
                  <p className="nav-label">{section.label}</p>
                  <nav>
                    {section.items
                      .filter((item) => item.show)
                      .map((item) => (
                        <NavLink key={item.to} to={item.to} end={item.end}>
                          <item.icon size={18} />
                          {item.label}
                        </NavLink>
                      ))}
                  </nav>
                </div>
              ) : null,
            )}
          </div>
          <div className="aside-bottom">
            <span className="avatar">{access.username[0]?.toUpperCase()}</span>
            <div>
              <strong>{access.username}</strong>
              <small>
                {access.admin
                  ? "Administrator"
                  : access.operations
                    ? "Operations"
                    : "Accounts"}
              </small>
            </div>
            <button
              className="icon-button"
              aria-label="Sign out"
              onClick={async () => {
                setSignOutError(null);
                try {
                  await logout();
                  client.removeQueries({
                    predicate: (query) => query.queryKey[0] !== "session",
                  });
                  client.setQueryData(["session"], null);
                  navigate("/staff", { replace: true });
                } catch (error) {
                  setSignOutError(error);
                }
              }}
            >
              <LogOut size={18} />
            </button>
          </div>
        </aside>
        <div className="body">
          <header>
            <span className="header-crumb">
              YFC ERP <ChevronRight size={14} /> <strong>{crumb(path)}</strong>
            </span>
            <span className="header-right">
              <span className="header-date">
                {new Intl.DateTimeFormat("en-IN", {
                  dateStyle: "medium",
                  timeZone: "Asia/Kolkata",
                }).format(new Date())}
              </span>
            </span>
          </header>
          <main>
            <ErrorBox error={signOutError} />
            <Suspense fallback={<div className="panel-loading">Loading view…</div>}>
            <Routes>
              <Route path="/dashboard" element={<Overview access={access} />} />
              <Route path="/alerts" element={<AlertsCenter />} />
              <Route
                path="/staff"
                element={<Navigate to="/dashboard" replace />}
              />
              <Route path="/sales" element={<Sales />} />
              <Route
                path="/sales/new"
                element={
                  access.write ? <NewEnquiry /> : <Navigate to="/sales" />
                }
              />
              <Route path="/sales/:id" element={<Enquiry />} />
              <Route path="/orders" element={<Orders />} />
              <Route path="/orders/:id" element={<Order />} />
              <Route path="/customers" element={<Customers />} />
              <Route path="/materials" element={<Materials />} />
              <Route path="/suppliers" element={<Suppliers />} />
              <Route path="/branches" element={<Branches />} />
              <Route path="/yards" element={<Yards />} />
              <Route path="/purchases" element={<Purchases />} />
              <Route path="/purchases/:id" element={<Purchase />} />
              <Route path="/reservations" element={<Reservations />} />
              <Route path="/stock" element={<StockControl />} />
              <Route
                path="/fleet"
                element={access.write ? <Fleet /> : <Navigate to="/trips" />}
              />
              <Route path="/trips" element={<Trips />} />
              <Route path="/trips/:id" element={<Trip />} />
              <Route path="/operating" element={<Operating />} />
              <Route
                path="/ledger"
                element={
                  access.finance ? <Ledger /> : <Navigate to="/dashboard" />
                }
              />
              <Route
                path="/ledger/:id"
                element={
                  access.finance ? <Journal /> : <Navigate to="/dashboard" />
                }
              />
              <Route
                path="/parties"
                element={
                  access.finance ? <Parties /> : <Navigate to="/dashboard" />
                }
              />
              <Route
                path="/money"
                element={
                  access.finance ? <Money /> : <Navigate to="/dashboard" />
                }
              />
              <Route
                path="/billing"
                element={
                  access.finance ? <Billing /> : <Navigate to="/dashboard" />
                }
              />
              <Route
                path="/billing/:id"
                element={
                  access.finance ? (
                    <Preparation />
                  ) : (
                    <Navigate to="/dashboard" />
                  )
                }
              />
              <Route
                path="/account-requests"
                element={
                  access.admin || access.operations ? (
                    <AccountRequests />
                  ) : (
                    <Navigate to="/dashboard" />
                  )
                }
              />
              <Route
                path="/site"
                element={
                  access.admin || access.operations ? (
                    <PublicDesk />
                  ) : (
                    <Navigate to="/dashboard" />
                  )
                }
              />
              <Route
                path="/people"
                element={
                  access.admin ? <People /> : <Navigate to="/dashboard" />
                }
              />
              <Route
                path="/audit"
                element={
                  access.admin ? <Audit /> : <Navigate to="/dashboard" />
                }
              />
              <Route path="*" element={<Navigate to="/dashboard" />} />
            </Routes>
            </Suspense>
          </main>
          <footer>
            YFC Transport <span>Internal operations workspace</span>
          </footer>
        </div>
      </div>
    </AccessContext.Provider>
  );
}

function crumb(path: string) {
  const [first] = path.split("/").filter(Boolean);
  return (
    {
      alerts: "Alerts & digest",
      sales: "Sales",
      orders: "Orders",
      customers: "Customers",
      materials: "Materials",
      suppliers: "Suppliers",
      branches: "Branches",
      yards: "Yards",
      purchases: "Purchases",
      reservations: "Reservations",
      stock: "Stock control",
      fleet: "Fleet",
      trips: "Trips",
      operating: "Fuel & expenses",
      ledger: "Ledger",
      parties: "Party ledgers",
      money: "Cash & bank",
      billing: "Billing preparation",
      "account-requests": "Account requests",
      site: "Customer site",
      people: "People",
      audit: "Audit",
      reset: "Password reset",
    }[first ?? ""] ?? "Overview"
  );
}

function StaffLogin({ error }: { error: unknown }) {
  const [failure, setFailure] = useState<unknown>(null);
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
        <p className="eyebrow" style={{ color: "#93c5fd" }}>INTERNAL OFFICE WORKSPACE</p>
        <h1>
          Every order.
          <br />
          Every journey.
          <br />
          <em>One workspace.</em>
        </h1>
        <p>
          Material supply, fleet movement, billing, and accounting for YFC
          operations and finance teams.
        </p>
      </section>
      <form
        className="login"
        onSubmit={async (event) => {
          event.preventDefault();
          setBusy(true);
          setFailure(null);
          const form = new FormData(event.currentTarget);
          try {
            await login(
              String(form.get("username")),
              String(form.get("password")),
              "STAFF",
            );
            await client.refetchQueries({ queryKey: ["session"] });
          } catch (caught) {
            setFailure(caught);
          } finally {
            setBusy(false);
          }
        }}
      >
        <p className="eyebrow">STAFF ERP LOGIN</p>
        <h2>Sign in to the office workspace</h2>
        <p>Administrators, operations, and accounts staff sign in here.</p>
        <ErrorBox error={failure || error} />
        <Field label="Staff Username">
          <input name="username" autoComplete="username" required />
        </Field>
        <Field label="Password">
          <PasswordInput name="password" autoComplete="current-password" />
        </Field>
        <button disabled={busy}>
          {busy ? "Signing in…" : "Sign in to Staff ERP"} <ArrowUpRight size={16} />
        </button>
        <small>
          <Link to="/signin">Customer or partner? Customer Portal Login →</Link>
          <br />
          <Link to="/reset">Have a password reset token?</Link>
        </small>
      </form>
    </div>
  );
}

createRoot(document.getElementById("root")!).render(
  <ErrorBoundary>
    <QueryClientProvider client={client}>
      <BrowserRouter>
        <Suspense fallback={<div className="loading">Loading workspace…</div>}>
          <App />
        </Suspense>
      </BrowserRouter>
    </QueryClientProvider>
  </ErrorBoundary>,
);
