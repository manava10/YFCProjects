import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Menu, X } from "lucide-react";
import { ApiError, post, request } from "./api";
import { uuid } from "./lib";
import "./public-site.css";

type Profile = {
  heroKicker: string;
  heroTitle: string;
  heroTitleRest: string;
  heroSubtitle: string;
  heroLine: string;
  aboutKicker: string;
  aboutTitle: string;
  aboutParagraph1: string;
  aboutParagraph2: string;
  aboutParagraph3: string;
  aboutImage: string;
  aboutCaption: string;
  aboutCaptionAside: string;
  contactKicker: string;
  contactTitle: string;
  contactBody: string;
  contactTagline: string;
  footerLine: string;
};
type Stat = { id: string; value: string; label: string };
type Material = {
  id: string;
  name: string;
  grade: string;
  useText: string;
  rate: number;
  image: string;
  active: boolean;
};
type Plant = {
  id: string;
  name: string;
  place: string;
  note: string;
  image: string;
  active: boolean;
};
type Destination = { id: string; name: string; active: boolean };
type Lane = { plantId: string; destinationId: string; rate: number };
type Catalog = {
  profile: Profile;
  stats: Stat[];
  materials: Material[];
  plants: Plant[];
  destinations: Destination[];
  freight: Lane[];
};
type Ticket = {
  reference: string;
  materialName: string;
  materialImage: string;
  quantity: number;
  plantName: string;
  destination: string;
  mediator: string;
  materialRate: number;
  freightRate: number;
  stage: string;
  stageLabel: string;
};

const stages = ["Confirmed", "Loading", "On the Way", "Delivered"];
const nav = [
  ["#about", "About Us"],
  ["#materials", "Materials"],
  ["#plants", "Our Plants"],
  ["#rates", "Live Rates"],
  ["#track", "Track Order"],
  ["#contact", "Contact Us"],
];

function inr(value: number) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(value);
}
function message(error: unknown) {
  return error instanceof Error ? error.message : "Something went wrong.";
}

function PublicBar({ home }: { home: boolean }) {
  const [open, setOpen] = useState(false);
  const links = nav.map(([href, label]) => [home ? href : `/${href}`, label]);
  const quote = home ? "#order" : "/#order";
  return (
    <header className="topbar">
      <div className="wrap">
        <a className="brand" href={home ? "#top" : "/"}>
          <strong>YFC</strong>
          <span>TRANSPORT</span>
        </a>
        <nav className="menu" aria-label="Primary">
          {links.map(([href, label]) => (
            <a key={href} href={href}>
              {label}
            </a>
          ))}
        </nav>
        <div className="bar-actions">
          <Link className="staff-link" to="/staff">
            Login
          </Link>
          <Link className="btn ghost" to="/signup">
            Sign up
          </Link>
          <a className="btn" href={quote}>
            Get Best Quote
          </a>
        </div>
        <button
          type="button"
          className="menu-toggle"
          aria-expanded={open}
          aria-label={open ? "Close menu" : "Open menu"}
          onClick={() => setOpen((value) => !value)}
        >
          {open ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>
      {open ? (
        <nav className="drawer" aria-label="Mobile">
          {links.map(([href, label]) => (
            <a key={href} href={href} onClick={() => setOpen(false)}>
              {label}
            </a>
          ))}
          <Link to="/signup" onClick={() => setOpen(false)}>
            Sign up
          </Link>
          <a href={quote} onClick={() => setOpen(false)}>
            Get Best Quote
          </a>
          <Link to="/staff" onClick={() => setOpen(false)}>
            Login
          </Link>
        </nav>
      ) : null}
    </header>
  );
}

export function PublicSite() {
  const site = useQuery({
    queryKey: ["public-site"],
    queryFn: () => request<Catalog>("/api/v1/site"),
  });
  const [materialId, setMaterialId] = useState("");
  const [plantId, setPlantId] = useState("");
  const [issued, setIssued] = useState("");

  useEffect(() => {
    const root = document.documentElement;
    const previous = root.style.scrollBehavior;
    root.style.scrollBehavior = "smooth";
    return () => {
      root.style.scrollBehavior = previous;
    };
  }, []);

  if (site.isPending) {
    return (
      <div className="yfc-public">
        <p className="wrap" style={{ paddingTop: 120 }}>
          Opening the yard…
        </p>
      </div>
    );
  }
  if (site.isError || !site.data) {
    return (
      <div className="yfc-public">
        <main className="wrap" style={{ paddingTop: 120 }}>
          <p className="kicker">YFC TRANSPORT</p>
          <h1 style={{ fontSize: 48, marginTop: 12 }}>The customer page is not up yet.</h1>
          <p className="status" style={{ marginTop: 16 }}>
            {site.error instanceof ApiError
              ? site.error.message
              : "Restart the workspace so the public site can load."}
          </p>
          <p style={{ marginTop: 24 }}>
            <Link className="btn" to="/staff">
              Login
            </Link>
          </p>
        </main>
      </div>
    );
  }

  const catalog = site.data;
  const chosenMaterial = materialId || catalog.materials[0]?.id || "";
  const chosenPlant = plantId || catalog.plants[0]?.id || "";

  return (
    <div className="yfc-public">
      <PublicBar home />

      <Hero catalog={catalog} />
      <Stats stats={catalog.stats} />
      <About profile={catalog.profile} />
      <Materials
        materials={catalog.materials}
        onPick={(id) => {
          setMaterialId(id);
          document.getElementById("order")?.scrollIntoView({ behavior: "smooth" });
        }}
      />
      <Plants
        plants={catalog.plants}
        onPick={(id) => {
          setPlantId(id);
          document.getElementById("order")?.scrollIntoView({ behavior: "smooth" });
        }}
      />
      <Rates materials={catalog.materials} />
      <Quote
        catalog={catalog}
        materialId={chosenMaterial}
        plantId={chosenPlant}
        onMaterial={setMaterialId}
        onPlant={setPlantId}
        onIssued={setIssued}
      />
      <Track focus={issued} />
      <Contact profile={catalog.profile} />
      <footer className="foot">
        <div className="wrap">
          <div>
            <strong>
              YFC <span>TRANSPORT</span>
            </strong>
            <p>{catalog.profile.contactTagline}</p>
          </div>
          <p>{catalog.profile.footerLine}</p>
        </div>
      </footer>
    </div>
  );
}

function Hero({ catalog }: { catalog: Catalog }) {
  const { profile, materials } = catalog;
  return (
    <section className="hero" id="top">
      <img
        className="cover"
        src="/media/hero.jpg"
        alt="Tipper on the haul road at a stone crusher plant at sunrise"
      />
      <div className="shade" />
      <div className="wrap">
        <div className="hero-copy">
          <p className="kicker">{profile.heroKicker}</p>
          <h1>
            {profile.heroTitle}
            <br />
            {profile.heroTitleRest}
          </h1>
          <p className="lead">{profile.heroSubtitle}</p>
          <p className="line">{profile.heroLine}</p>
          <div className="actions">
            <a className="btn" href="#order">
              Get Best Quote
            </a>
            <a className="btn ghost" href="#track">
              Track Order
            </a>
            <Link className="btn ghost" to="/signup">
              Sign up
            </Link>
          </div>
        </div>
        <div className="rate-card">
          <header>
            <h2>Live Market Rates</h2>
            <span className="live">
              <i />
              LIVE
            </span>
          </header>
          <p>Ex-plant desk card, per ton</p>
          <table>
            <thead>
              <tr>
                <th>Material</th>
                <th className="num">Rate</th>
              </tr>
            </thead>
            <tbody>
              {materials.map((item) => (
                <tr key={item.id}>
                  <td>{item.name}</td>
                  <td className="num">{inr(item.rate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

function Stats({ stats }: { stats: Stat[] }) {
  return (
    <section className="stats" aria-label="Company record">
      <dl className="wrap">
        {stats.map((item) => (
          <div key={item.id}>
            <dt>{item.label}</dt>
            <dd>{item.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function About({ profile }: { profile: Profile }) {
  return (
    <section className="block" id="about">
      <div className="wrap split">
        <div className="prose">
          <p className="kicker">{profile.aboutKicker}</p>
          <h2>{profile.aboutTitle}</h2>
          <p>{profile.aboutParagraph1}</p>
          <p>{profile.aboutParagraph2}</p>
          <p>{profile.aboutParagraph3}</p>
        </div>
        <figure>
          <img src={profile.aboutImage} alt="" />
          <figcaption>
            <span>{profile.aboutCaption}</span>
            <span>{profile.aboutCaptionAside}</span>
          </figcaption>
        </figure>
      </div>
    </section>
  );
}

function Materials({
  materials,
  onPick,
}: {
  materials: Material[];
  onPick: (id: string) => void;
}) {
  return (
    <section className="band block" id="materials">
      <div className="wrap">
        <div className="section-head">
          <div>
            <p className="kicker">MATERIALS</p>
            <h2>Grades on the yard card.</h2>
          </div>
          <p>Tap a grade to load it into the quote. Rates are the published ex-plant card.</p>
        </div>
        <ul className="grades">
          {materials.map((item) => (
            <li key={item.id}>
              <button type="button" onClick={() => onPick(item.id)}>
                <img src={item.image} alt="" />
                <span>
                  <strong>{item.name}</strong>
                  <small>{item.useText}</small>
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

function Plants({ plants, onPick }: { plants: Plant[]; onPick: (id: string) => void }) {
  return (
    <section className="block" id="plants">
      <div className="wrap">
        <p className="kicker">OUR PLANTS</p>
        <h2>Loading points on the published card.</h2>
        <ul className="yards">
          {plants.map((plant) => (
            <li key={plant.id}>
              <button type="button" onClick={() => onPick(plant.id)}>
                <img src={plant.image} alt="" />
                <span>
                  <strong>{plant.name}</strong>
                  <small className="place">{plant.place}</small>
                  <small>{plant.note}</small>
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

function Rates({ materials }: { materials: Material[] }) {
  return (
    <section className="band block" id="rates">
      <div className="wrap">
        <p className="kicker">LIVE RATES</p>
        <h2>Today’s ex-plant card.</h2>
        <p className="status" style={{ maxWidth: 520, marginTop: 12 }}>
          These are the published desk rates. Freight is added on the quote, by plant and drop.
        </p>
        <div className="card-table">
          <table>
            <thead>
              <tr>
                <th>Material</th>
                <th>Grade</th>
                <th className="num">Rate</th>
              </tr>
            </thead>
            <tbody>
              {materials.map((item) => (
                <tr key={item.id}>
                  <td>{item.name}</td>
                  <td>{item.grade}</td>
                  <td className="num">{inr(item.rate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

function Quote({
  catalog,
  materialId,
  plantId,
  onMaterial,
  onPlant,
  onIssued,
}: {
  catalog: Catalog;
  materialId: string;
  plantId: string;
  onMaterial: (id: string) => void;
  onPlant: (id: string) => void;
  onIssued: (reference: string) => void;
}) {
  const [destinationId, setDestinationId] = useState(catalog.destinations[0]?.id ?? "");
  const [qty, setQty] = useState("20");
  const [mediator, setMediator] = useState("");
  const [error, setError] = useState("");
  const [issued, setIssued] = useState<Ticket | null>(null);
  const [busy, setBusy] = useState(false);
  const material = catalog.materials.find((item) => item.id === materialId) ?? catalog.materials[0];
  const plant = catalog.plants.find((item) => item.id === plantId) ?? catalog.plants[0];
  const destination =
    catalog.destinations.find((item) => item.id === destinationId) ?? catalog.destinations[0];
  const preview = useMemo(() => {
    const tonnes = Number(qty);
    const freight =
      catalog.freight.find(
        (lane) => lane.plantId === plant?.id && lane.destinationId === destination?.id,
      )?.rate ?? null;
    const landed = material && freight != null ? Number(material.rate) + Number(freight) : null;
    return {
      tonnes: Number.isFinite(tonnes) ? tonnes : 0,
      freight,
      landed,
      total: landed != null && Number.isFinite(tonnes) ? landed * tonnes : null,
    };
  }, [catalog.freight, destination?.id, material, plant?.id, qty]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const tonnes = Number(qty);
    if (!material || !plant || !destination) {
      setError("The card has no material, plant, or drop to quote.");
      return;
    }
    if (!Number.isFinite(tonnes) || tonnes < 1 || tonnes > 500) {
      setError("Quantity must be between 1 and 500 ton.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const ticket = await post<Ticket>("/api/v1/site/quotes", {
        materialId: material.id,
        plantId: plant.id,
        destinationId: destination.id,
        quantity: tonnes,
        mediator: mediator.trim(),
      });
      setIssued(ticket);
      onIssued(ticket.reference);
    } catch (caught) {
      setError(message(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="block" id="order">
      <div className="wrap quote-grid">
        <div>
          <p className="kicker">QUICK ORDER</p>
          <h2>Get Best Quote</h2>
          <p className="status" style={{ marginTop: 12, maxWidth: 460 }}>
            Material, tonnes, plant, and drop. The ticket shows ex-plant rate plus the freight
            published for that lane. Operations sees the order on the public desk.
          </p>
          <form className="form-grid" onSubmit={submit}>
            <label className="field">
              Material
              <select value={material?.id ?? ""} onChange={(event) => onMaterial(event.target.value)}>
                {catalog.materials.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Quantity (Ton)
              <input inputMode="decimal" value={qty} onChange={(event) => setQty(event.target.value)} />
            </label>
            <label className="field span-2">
              From Plant / Crusher
              <select value={plant?.id ?? ""} onChange={(event) => onPlant(event.target.value)}>
                {catalog.plants.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field span-2">
              Delivery Location
              <select
                value={destination?.id ?? ""}
                onChange={(event) => setDestinationId(event.target.value)}
              >
                {catalog.destinations.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field span-2">
              Mediator / Reference
              <input
                value={mediator}
                placeholder="Rajpal"
                onChange={(event) => setMediator(event.target.value)}
              />
            </label>
            {error ? (
              <p className="alert span-2" role="alert">
                {error}
              </p>
            ) : null}
            <button className="btn wide span-2" type="submit" disabled={busy}>
              {busy ? "Sending…" : "Get Best Quote"}
            </button>
          </form>
        </div>
        <div style={{ display: "grid", gap: 16 }}>
          <div className="lane">
            <header>
              <span>LANE</span>
              <span>{material?.name}</span>
            </header>
            <svg className="route" viewBox="0 0 360 92" aria-hidden="true">
              <path
                d="M28 64 C 90 64, 110 24, 180 24 S 270 64, 332 28"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                className="route-dash"
              />
              <circle cx="28" cy="64" r="6" fill="currentColor" />
              <circle cx="332" cy="28" r="6" fill="currentColor" />
            </svg>
            <div className="lane-ends">
              <p>{plant?.name}</p>
              <p>{destination?.name}</p>
            </div>
          </div>
          <div className="ticket" style={{ marginTop: 0 }}>
            <div>
              <p className="kicker">INDICATIVE TICKET</p>
              <dl className="rows">
                <div>
                  <dt>Ex-plant</dt>
                  <dd>{material ? `${inr(material.rate)} / ton` : "—"}</dd>
                </div>
                <div>
                  <dt>Freight</dt>
                  <dd>{preview.freight == null ? "—" : `${inr(preview.freight)} / ton`}</dd>
                </div>
                <div>
                  <dt>Landed</dt>
                  <dd>{preview.landed == null ? "—" : `${inr(preview.landed)} / ton`}</dd>
                </div>
                <div>
                  <dt>Quantity</dt>
                  <dd>{preview.tonnes || 0} ton</dd>
                </div>
              </dl>
              <div className="total">
                <dt>Ticket total</dt>
                <dd>{preview.total == null ? "—" : inr(preview.total)}</dd>
              </div>
              {issued ? (
                <div className="issued">
                  <p className="kicker">ORDER {issued.reference}</p>
                  <p>
                    {issued.materialName} · {issued.quantity} ton · {issued.plantName} →{" "}
                    {issued.destination}
                  </p>
                  <p className="status">Mediator / Reference: {issued.mediator || "—"}</p>
                  <a href="#track">Track this order</a>
                </div>
              ) : (
                <p className="status" style={{ marginTop: 16 }}>
                  Issue a quote to put a trackable order number on the operations desk.
                </p>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function Track({ focus }: { focus: string }) {
  const [query, setQuery] = useState("");
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [miss, setMiss] = useState(false);
  const [recent, setRecent] = useState<string[]>([]);

  useEffect(() => {
    if (!focus) return;
    setQuery(focus);
    setRecent((items) => [focus, ...items.filter((item) => item !== focus)].slice(0, 8));
    void lookup(focus);
  }, [focus]);

  async function lookup(reference: string) {
    const key = reference.trim();
    if (!key) return;
    setMiss(false);
    try {
      setTicket(await request<Ticket>(`/api/v1/site/quotes/${encodeURIComponent(key)}`));
    } catch {
      setTicket(null);
      setMiss(true);
    }
  }

  return (
    <section className="band block" id="track">
      <div className="wrap">
        <p className="kicker">TRACK ORDER</p>
        <h2>Follow the ticket.</h2>
        <p className="status" style={{ marginTop: 12 }}>
          Operations moves the stage. This page only follows it.
        </p>
        <form
          className="track-form"
          onSubmit={(event) => {
            event.preventDefault();
            void lookup(query);
          }}
        >
          <label className="sr-only" htmlFor="order-id" style={{ position: "absolute", left: -9999 }}>
            Order number
          </label>
          <input
            id="order-id"
            placeholder="YFC-10001"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setMiss(false);
            }}
          />
          <button className="btn" type="submit">
            Track Order
          </button>
        </form>
        {miss ? (
          <p className="alert" role="status">
            No order with that number.
          </p>
        ) : null}
        {ticket ? <TicketView ticket={ticket} /> : null}
        {recent.length > 0 ? (
          <ul className="chips">
            {recent.map((reference) => (
              <li key={reference}>
                <button
                  type="button"
                  onClick={() => {
                    setQuery(reference);
                    void lookup(reference);
                  }}
                >
                  {reference}
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="status" style={{ marginTop: 24 }}>
            Quotes you issue in this visit show up here.
          </p>
        )}
      </div>
    </section>
  );
}

function TicketView({ ticket }: { ticket: Ticket }) {
  const index = stages.indexOf(ticket.stageLabel);
  return (
    <article className="ticket">
      <img src={ticket.materialImage} alt="" />
      <div>
        <p className="kicker">{ticket.reference}</p>
        <h3 style={{ fontSize: 28, marginTop: 8 }}>
          {ticket.materialName} · {ticket.quantity} ton
        </h3>
        <p className="status">
          {ticket.plantName} → {ticket.destination}
        </p>
        <p className="status">
          Mediator / Reference: {ticket.mediator || "—"} · Landed{" "}
          {inr(Number(ticket.materialRate) + Number(ticket.freightRate))} / ton
        </p>
        <ol className="stages">
          {stages.map((label, step) => (
            <li key={label} className={step <= index ? "on" : ""}>
              {label}
            </li>
          ))}
        </ol>
      </div>
    </article>
  );
}

function Contact({ profile }: { profile: Profile }) {
  const [saved, setSaved] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const name = String(form.get("name") ?? "").trim();
    const phone = String(form.get("phone") ?? "").trim();
    const note = String(form.get("note") ?? "").trim();
    if (name.length < 2 || phone.length < 7) {
      setError("Leave a name and a phone number the yard can use.");
      setSaved("");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const receipt = await post<{ reference: string }>("/api/v1/site/enquiries", {
        name,
        phone,
        note,
      });
      setSaved(receipt.reference);
      event.currentTarget.reset();
    } catch (caught) {
      setError(message(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="block" id="contact">
      <div className="wrap contact-grid">
        <div>
          <p className="kicker">{profile.contactKicker}</p>
          <h2>{profile.contactTitle}</h2>
          <p className="status" style={{ marginTop: 16, maxWidth: 460 }}>
            {profile.contactBody}
          </p>
          <p style={{ marginTop: 24, fontFamily: "Syne, sans-serif", fontSize: 28, fontWeight: 650 }}>
            {profile.contactTagline}
          </p>
        </div>
        <form className="panel" onSubmit={submit}>
          <label className="field">
            Name
            <input name="name" />
          </label>
          <label className="field">
            Phone
            <input name="phone" inputMode="tel" />
          </label>
          <label className="field">
            Note for the yard
            <textarea name="note" />
          </label>
          {error ? (
            <p className="alert" role="alert">
              {error}
            </p>
          ) : null}
          {saved ? (
            <p className="status" role="status">
              The desk has this as {saved}. Operations can see it.
            </p>
          ) : null}
          <button className="btn" type="submit" disabled={busy}>
            {busy ? "Sending…" : "Send to desk"}
          </button>
        </form>
      </div>
    </section>
  );
}

export function CustomerSignup() {
  const [done, setDone] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body: Record<string, string> = {
      applicationId: uuid(),
      role: "CUSTOMER",
      username: String(form.get("username") ?? "").trim(),
      password: String(form.get("password") ?? ""),
      name: String(form.get("name") ?? "").trim(),
      mobile: String(form.get("mobile") ?? "").trim(),
    };
    const gstin = String(form.get("gstin") ?? "").trim();
    const address = String(form.get("address") ?? "").trim();
    if (gstin) body.gstin = gstin.toUpperCase();
    if (address) body.address = address;
    setBusy(true);
    setError("");
    try {
      await post("/api/v1/account-applications", body);
      setDone(true);
    } catch (caught) {
      setError(message(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="yfc-public">
      <PublicBar home={false} />
      <main className="signup">
        <div className="wrap signup-grid">
          <div>
            <p className="kicker">CUSTOMER</p>
            <h1>Create your login.</h1>
            <p className="status">
              An administrator or operations user approves the login before it can be used. After
              that, you see only your own orders, trips, and bills.
            </p>
            <p className="status">
              Suppliers and drivers use the <Link to="/join">request form</Link>. After approval,
              everyone signs in with <Link to="/staff">Login</Link>.
            </p>
            <p>
              <Link className="text-link" to="/">
                Back to the yard
              </Link>
            </p>
          </div>
          {done ? (
            <div className="panel">
              <p className="kicker">REQUEST RECEIVED</p>
              <h2>Your login is waiting for approval.</h2>
              <p className="status">
                You can sign in after an administrator or operations user approves this request.
              </p>
              <Link className="btn" to="/staff">
                Login
              </Link>
            </div>
          ) : (
            <form className="panel" onSubmit={submit}>
              <label className="field">
                Name
                <input name="name" required maxLength={120} />
              </label>
              <label className="field">
                Mobile
                <input name="mobile" inputMode="tel" required maxLength={20} />
              </label>
              <label className="field">
                Username
                <input name="username" autoComplete="username" required maxLength={80} />
              </label>
              <label className="field">
                Password
                <input
                  name="password"
                  type="password"
                  autoComplete="new-password"
                  minLength={12}
                  maxLength={72}
                  required
                />
              </label>
              <label className="field">
                GSTIN, if you have one
                <input name="gstin" maxLength={15} />
              </label>
              <label className="field">
                Address, if you want it recorded
                <textarea name="address" maxLength={500} />
              </label>
              {error ? (
                <p className="alert" role="alert">
                  {error}
                </p>
              ) : null}
              <button className="btn wide" type="submit" disabled={busy}>
                {busy ? "Sending…" : "Request account"}
              </button>
            </form>
          )}
        </div>
      </main>
    </div>
  );
}
