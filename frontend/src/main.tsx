import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  BrowserRouter,
  Link,
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
} from "react-router-dom";
import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import {
  ArrowUpRight,
  ClipboardList,
  ChevronRight,
  Fuel,
  Landmark,
  Layers3,
  LogOut,
  Package,
  ScrollText,
  Shield,
  Truck,
  Users,
  Warehouse,
  type LucideIcon,
} from "lucide-react";
import { ApiError, login, logout, request } from "./api";
import { Audit, People, ResetPassword } from "./admin";
import { Billing, Journal, Ledger, Money, Parties, Preparation } from "./finance";
import { Fleet, Trip, Trips } from "./fleet";
import { Branches, Customers, Materials, Suppliers } from "./masters";
import { Operating } from "./operating";
import { Enquiry, NewEnquiry, Order, Orders, Sales } from "./sales";
import { Purchase, Purchases, Reservations, StockControl, Yards } from "./supply";
import { AccessContext, ErrorBox, Field, Heading, type Access } from "./ui";
import "./style.css";

const client = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
});

type Item = { to: string; label: string; icon: LucideIcon; end?: boolean; show: boolean };

function App() {
  const session = useQuery({
    queryKey: ["session"],
    queryFn: () => request<{ username: string; roles: string[] }>("/api/v1/auth/me"),
  });
  const path = useLocation().pathname;
  useEffect(() => {
    const expire = () => {
      client.removeQueries({ predicate: (query) => query.queryKey[0] !== "session" });
      client.setQueryData(["session"], null);
    };
    window.addEventListener("session-expired", expire);
    return () => window.removeEventListener("session-expired", expire);
  }, []);
  if (path === "/reset") return <ResetPassword />;
  if (session.isPending) return <div className="loading">Opening your workspace…</div>;
  if (!session.data) {
    return (
      <Login
        error={session.error instanceof ApiError && session.error.status === 401 ? null : session.error}
      />
    );
  }
  const roles = session.data.roles;
  const access: Access = {
    username: session.data.username,
    admin: roles.includes("ROLE_SUPER_ADMIN"),
    operations: roles.includes("ROLE_OPERATIONS"),
    accounts: roles.includes("ROLE_ACCOUNTS"),
    write: roles.some((role) => ["ROLE_SUPER_ADMIN", "ROLE_OPERATIONS"].includes(role)),
    finance: roles.some((role) => ["ROLE_SUPER_ADMIN", "ROLE_ACCOUNTS"].includes(role)),
    review: roles.some((role) => ["ROLE_SUPER_ADMIN", "ROLE_ACCOUNTS"].includes(role)),
  };
  if (!access.admin && !access.operations && !access.accounts) {
    return (
      <main className="login">
        <h1>Your account is ready.</h1>
        <p>External portals are not available yet. Please contact your administrator.</p>
        <button
          onClick={() =>
            logout().then(() => {
              client.removeQueries({ predicate: (query) => query.queryKey[0] !== "session" });
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
      items: [{ to: "/", label: "Overview", icon: Layers3, end: true, show: true }],
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
        { to: "/reservations", label: "Reservations", icon: ClipboardList, show: true },
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
        { to: "/ledger", label: "Ledger", icon: Landmark, show: access.finance },
        { to: "/parties", label: "Party ledgers", icon: Users, show: access.finance },
        { to: "/money", label: "Cash & bank", icon: Landmark, show: access.finance },
        { to: "/billing", label: "Billing prep", icon: ScrollText, show: access.finance },
      ],
    },
    {
      label: "Administration",
      items: [
        { to: "/people", label: "People", icon: Shield, show: access.admin },
        { to: "/audit", label: "Audit", icon: ScrollText, show: access.admin },
      ],
    },
  ];
  return (
    <AccessContext.Provider value={access}>
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
                {access.admin ? "Administrator" : access.operations ? "Operations" : "Accounts"}
              </small>
            </div>
            <button
              className="icon-button"
              aria-label="Sign out"
              onClick={async () => {
                try {
                  await logout();
                  client.removeQueries({ predicate: (query) => query.queryKey[0] !== "session" });
                  client.setQueryData(["session"], null);
                } catch (error) {
                  window.alert(error instanceof Error ? error.message : "Sign out failed");
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
              YFC workspace <ChevronRight size={14} /> {crumb(path)}
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
              <Route path="/" element={<Overview access={access} />} />
              <Route path="/sales" element={<Sales />} />
              <Route path="/sales/new" element={access.write ? <NewEnquiry /> : <Navigate to="/sales" />} />
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
              <Route path="/fleet" element={access.write ? <Fleet /> : <Navigate to="/trips" />} />
              <Route path="/trips" element={<Trips />} />
              <Route path="/trips/:id" element={<Trip />} />
              <Route path="/operating" element={<Operating />} />
              <Route path="/ledger" element={access.finance ? <Ledger /> : <Navigate to="/" />} />
              <Route path="/ledger/:id" element={access.finance ? <Journal /> : <Navigate to="/" />} />
              <Route path="/parties" element={access.finance ? <Parties /> : <Navigate to="/" />} />
              <Route path="/money" element={access.finance ? <Money /> : <Navigate to="/" />} />
              <Route path="/billing" element={access.finance ? <Billing /> : <Navigate to="/" />} />
              <Route path="/billing/:id" element={access.finance ? <Preparation /> : <Navigate to="/" />} />
              <Route path="/people" element={access.admin ? <People /> : <Navigate to="/" />} />
              <Route path="/audit" element={access.admin ? <Audit /> : <Navigate to="/" />} />
              <Route path="*" element={<Navigate to="/" />} />
            </Routes>
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
      people: "People",
      audit: "Audit",
      reset: "Password reset",
    }[first ?? ""] ?? "Overview"
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
        <p>Material supply, fleet movement and the books, in one internal workspace.</p>
      </section>
      <form
        className="login"
        onSubmit={async (event) => {
          event.preventDefault();
          setBusy(true);
          setFailure(null);
          const form = new FormData(event.currentTarget);
          try {
            await login(String(form.get("username")), String(form.get("password")));
            await client.invalidateQueries({ queryKey: ["session"] });
          } catch (caught) {
            setFailure(caught);
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
          <input name="password" type="password" autoComplete="current-password" required />
        </Field>
        <button disabled={busy}>
          {busy ? "Signing in…" : "Sign in"} <ArrowUpRight size={16} />
        </button>
        <small>
          <Link to="/reset">Have a password reset token?</Link>
        </small>
      </form>
    </div>
  );
}

function Overview({ access }: { access: Access }) {
  const cards = [
    ["01", "Capture the requirement", "Enquiry, quotation and confirmed order.", "/sales"],
    ["02", "Source the material", "Purchases, yards, reservations and reviewed stock corrections.", "/purchases"],
    ["03", "Move it", "Trucks, drivers, trips, fuel and expenses.", access.write ? "/fleet" : "/trips"],
    ["04", "Record the books", "Ledger, party balances, cash and billing preparation.", access.finance ? "/ledger" : "/orders"],
  ];
  return (
    <>
      <Heading
        eyebrow="OPERATIONS"
        title="The work, from enquiry to the books."
        description={`${access.username} · ${access.admin ? "administrator" : access.operations ? "operations" : "accounts"}`}
      />
      <div className="hero">
        <div>
          <span className="pill">INTERNAL WORKSPACE</span>
          <h2>
            Sales, supply, movement
            <br />
            and finance in one place.
          </h2>
          <p>
            Totals come from the server. Subtotals exclude GST until the accountant confirms tax
            rules. Recording a quotation as sent does not message the customer.
          </p>
          <Link className="button light" to="/sales">
            Open sales <ArrowUpRight size={17} />
          </Link>
        </div>
        <div className="hero-art">
          <Truck size={100} strokeWidth={1} />
          <span>SUPPLY. MOVE. DELIVER.</span>
        </div>
      </div>
      <div className="cards four">
        {cards.map(([n, title, description, href]) => (
          <Link className="card" key={n} to={href}>
            <span className="step">{n}</span>
            <ArrowUpRight className="card-arrow" size={20} />
            <h3>{title}</h3>
            <p>{description}</p>
          </Link>
        ))}
      </div>
    </>
  );
}

createRoot(document.getElementById("root")!).render(
  <QueryClientProvider client={client}>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </QueryClientProvider>,
);
