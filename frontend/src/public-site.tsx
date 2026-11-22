import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
} from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  Award,
  Building2,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  Download,
  Eye,
  EyeOff,
  FileText,
  Globe,
  Headphones,
  Mail,
  MapPin,
  Menu,
  MessageCircle,
  Package,
  Phone,
  PhoneCall,
  Receipt,
  Search,
  ShieldCheck,
  TrendingUp,
  Truck,
  Users,
  X,
} from "lucide-react";
import { login, logout, post, request } from "./api";
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
  contactPhone?: string;
  contactEmail?: string;
  whatsappNumber?: string;
  trustedClients?: string;
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
type Freight = { plantId: string; destinationId: string; rate: number };
type Catalog = {
  profile: Profile;
  stats: Stat[];
  materials: Material[];
  plants: Plant[];
  destinations: Destination[];
  freight: Freight[];
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

const STAGES = ["Confirmed", "Loading", "On the Way", "Delivered"];
const STAGE_KEYS = ["CONFIRMED", "LOADING", "ON_THE_WAY", "DELIVERED"];

const SERVICES = [
  "Building Material Supply — graded crushed stone, aggregates & manufactured sand",
  "Transportation & Logistics — scheduled tipper movement across Haryana, Rajasthan & NCR",
  "Plant & Fleet Management — direct crusher-head loading with zero broker handoffs",
  "Project Logistic Support — continuous bulk supply for highways, townships & RMC plants",
  "Bulk Material Delivery — computerised weighbridge slips & GST billing on every trip",
];

const WHY_POINTS = [
  "Owned TATA Signa tipper fleet — dependable dispatch directly from our yards",
  "Direct ex-crusher supply from Kotputli, Behror & Bhiwadi dispatch hubs",
  "Computerised weighbridge slips & digital delivery challans on every trip",
  "Transparent GST tax invoices & E-Way bill documentation",
  "22+ years serving major infrastructure and real estate corridors",
];

const NAV_LINKS: [string, string][] = [
  ["#top", "Home"],
  ["#estimator", "Live Rates"],
  ["#materials", "Materials"],
  ["#services", "Services"],
  ["#track", "Track Order"],
  ["#plants", "Plants"],
  ["#about", "About Us"],
  ["#contact", "Contact"],
];

function inr(value: number) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(value);
}

function errMsg(error: unknown) {
  return error instanceof Error ? error.message : "Something went wrong. Please try again.";
}

function smoothScroll(id: string) {
  document.getElementById(id)?.scrollIntoView({
    behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
      ? "instant"
      : "smooth",
  });
}

function statIconFor(label: string) {
  const lower = label.toLowerCase();
  if (lower.includes("year")) return Award;
  if (lower.includes("trip") || lower.includes("ton")) return Truck;
  if (lower.includes("client") || lower.includes("buyer")) return Users;
  return Building2;
}

function YfcTriLogo({ to = "/" }: { to?: string }) {
  return (
    <Link className="yfc-tri-brand" to={to} aria-label="YFC Transport home">
      <span className="yfc-tri-letters">
        <span className="yfc-y">Y</span>
        <span className="yfc-f">F</span>
        <span className="yfc-c">C</span>
      </span>
      <span className="yfc-tri-sub">
        <strong>TRANSPORT</strong>
        <small>MATERIAL SUPPLY</small>
      </span>
    </Link>
  );
}

function PublicHeader({
  phone,
  email,
  home = true,
}: {
  phone: string;
  email: string;
  home?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const telHref = `tel:${phone.replace(/[^+0-9]/g, "")}`;
  return (
    <header className="pub-header">
      <div className="pub-utility">
        <div className="pub-wrap pub-utility-row">
          <div className="pub-utility-left">
            <span className="pub-since">Since 2004</span>
            <span className="pub-sep">·</span>
            <span className="pub-inline-icon">
              <MapPin size={13} /> Pan India Delivery
            </span>
          </div>
          <div className="pub-utility-right">
            <a href={telHref} className="pub-inline-icon">
              <Phone size={13} /> {phone}
            </a>
            <a href={`mailto:${email}`} className="pub-inline-icon">
              <Mail size={13} /> {email}
            </a>
          </div>
        </div>
      </div>

      <div className="pub-nav-bar">
        <div className="pub-wrap pub-nav-row">
          <YfcTriLogo to="/" />
          <nav className="pub-nav-links" aria-label="Primary">
            {NAV_LINKS.map(([href, label]) => (
              <a key={href} href={home ? href : `/${href}`}>
                {label}
              </a>
            ))}
          </nav>
          <div className="pub-nav-actions">
            <Link className="pub-link-btn" to="/signin">
              Login
            </Link>
            <Link className="pub-btn ghost sm" to="/signup">
              Sign up
            </Link>
            <a className="pub-btn sm" href={home ? "#estimator" : "/#estimator"}>
              Price Estimate
            </a>
          </div>
          <button
            type="button"
            className="pub-mobile-toggle"
            aria-label={open ? "Close menu" : "Open menu"}
            aria-expanded={open}
            onClick={() => setOpen((v) => !v)}
          >
            {open ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
        {open && (
          <nav className="pub-mobile-drawer" aria-label="Mobile navigation">
            {NAV_LINKS.map(([href, label]) => (
              <a
                key={href}
                href={home ? href : `/${href}`}
                onClick={() => setOpen(false)}
              >
                {label}
              </a>
            ))}
            <div className="pub-mobile-cta">
              <Link className="pub-btn ghost" to="/signin" onClick={() => setOpen(false)}>
                Login
              </Link>
              <Link className="pub-btn" to="/signup" onClick={() => setOpen(false)}>
                Sign up
              </Link>
            </div>
          </nav>
        )}
      </div>
    </header>
  );
}

export function PublicSite() {
  const siteQuery = useQuery({
    queryKey: ["public-site"],
    queryFn: () => request<Catalog>("/api/v1/site"),
    staleTime: 5 * 60_000,
  });
  const catalog = siteQuery.data ?? null;
  const error = siteQuery.error ? errMsg(siteQuery.error) : "";
  const [materialId, setMaterialId] = useState("");
  const [plantId, setPlantId] = useState("");
  const [destinationId, setDestinationId] = useState("");
  const [qty, setQty] = useState("20");
  const [showEstimate, setShowEstimate] = useState(false);
  const [estimateError, setEstimateError] = useState("");
  const [prefillNote, setPrefillNote] = useState("");
  const [showAllMaterials, setShowAllMaterials] = useState(false);
  const [plantDropFilter, setPlantDropFilter] = useState("");
  const carouselRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!catalog) return;
    setMaterialId((v) => v || catalog.materials[0]?.id || "");
    setPlantId((v) => v || catalog.plants[0]?.id || "");
    setDestinationId((v) => v || catalog.destinations[0]?.id || "");
  }, [catalog]);

  const phone = catalog?.profile.contactPhone || "+91 96672 89071";
  const email = catalog?.profile.contactEmail || "sales@yfctransport.com";
  const whatsapp = catalog?.profile.whatsappNumber || "919667289071";

  const selectedMaterial = useMemo(
    () =>
      catalog?.materials.find((m) => m.id === materialId) ??
      catalog?.materials[0],
    [catalog, materialId],
  );
  const selectedPlant = useMemo(
    () =>
      catalog?.plants.find((p) => p.id === plantId) ?? catalog?.plants[0],
    [catalog, plantId],
  );
  const pricedDestinations = useMemo(() => {
    if (!catalog || !selectedPlant) return [];
    return catalog.destinations.filter((d) => {
      const rate = catalog.freight.find(
        (l) => l.plantId === selectedPlant.id && l.destinationId === d.id,
      )?.rate;
      return rate != null && Number(rate) > 0;
    });
  }, [catalog, selectedPlant]);

  const selectedDestination = useMemo(
    () =>
      pricedDestinations.find((d) => d.id === destinationId) ??
      pricedDestinations[0],
    [pricedDestinations, destinationId],
  );

  const estimate = useMemo(() => {
    const tonnes = Number(qty);
    const freightRaw =
      catalog?.freight.find(
        (l) =>
          l.plantId === selectedPlant?.id &&
          l.destinationId === selectedDestination?.id,
      )?.rate ?? null;
    const freight =
      freightRaw != null && Number(freightRaw) > 0 ? Number(freightRaw) : null;
    const landed =
      selectedMaterial && freight != null
        ? Number(selectedMaterial.rate) + freight
        : null;
    return {
      tonnes: Number.isFinite(tonnes) ? tonnes : 0,
      freight,
      landed,
      total:
        landed != null && Number.isFinite(tonnes) && tonnes > 0
          ? landed * tonnes
          : null,
    };
  }, [catalog, selectedMaterial, selectedPlant, selectedDestination, qty]);

  function handleCalculate(event: FormEvent) {
    event.preventDefault();
    const tonnes = Number(qty);
    if (!selectedMaterial || !selectedPlant || !selectedDestination || estimate.freight == null) {
      setEstimateError("Select a plant and delivery location with a published freight lane.");
      return;
    }
    if (!Number.isFinite(tonnes) || tonnes < 1 || tonnes > 500) {
      setEstimateError("Enter a quantity between 1 and 500 tonnes.");
      return;
    }
    setEstimateError("");
    setShowEstimate(true);
  }

  function sendEstimateToEnquiry() {
    if (!selectedMaterial || !selectedPlant || !selectedDestination) return;
    const totalText = estimate.total != null ? inr(estimate.total) : "—";
    setPrefillNote(
      `Estimate enquiry: ${selectedMaterial.name}, ${estimate.tonnes} ton, ${selectedPlant.name} → ${selectedDestination.name} (indicative ${totalText} excl. GST)`,
    );
    smoothScroll("contact");
  }

  function downloadBrochure() {
    const lines = [
      "YFC TRANSPORT & MATERIALS — COMPANY PROFILE",
      "============================================",
      `Since 2004 · Pan India Building Material Supply & Owned-Fleet Logistics`,
      `Phone / WhatsApp: ${phone}`,
      `Email: ${email}`,
      "",
      "SERVICES:",
      ...SERVICES.map((s) => `  • ${s}`),
      "",
      "PUBLISHED EX-PLANT MATERIAL RATES (INR / TONNE, EXCL. GST):",
      ...(catalog?.materials ?? []).map(
        (m) => `  • ${m.name} (${m.grade}) — ${inr(m.rate)}/ton — ${m.useText}`,
      ),
      "",
      "LOADING PLANTS:",
      ...(catalog?.plants ?? []).map((p) => `  • ${p.name} (${p.place}) — ${p.note}`),
      "",
      "Contact our dispatch desk for firm quotations and credit accounts.",
    ];
    const blob = new Blob([lines.join("\n")], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "YFC-Transport-Company-Brochure.txt";
    a.click();
    URL.revokeObjectURL(url);
  }

  if (error) {
    return (
      <div className="yfc-public">
        <PublicHeader phone={phone} email={email} />
        <main className="pub-wrap pub-loading-box">
          <p className="pub-kicker">PUBLIC SITE UNAVAILABLE</p>
          <h1>Could not load the live desk card.</h1>
          <p className="pub-muted">{error}</p>
          <div className="pub-hero-actions" style={{ marginTop: 18 }}>
            <button type="button" className="pub-btn" onClick={() => window.location.reload()}>
              Try again
            </button>
            <Link className="pub-btn ghost" to="/signin">
              Customer Portal Login
            </Link>
            <Link className="pub-btn ghost" to="/staff">
              Staff ERP Login
            </Link>
          </div>
        </main>
      </div>
    );
  }

  if (!catalog) {
    return (
      <div className="yfc-public">
        <PublicHeader phone={phone} email={email} />
        <main className="pub-wrap pub-loading-box">
          <p className="pub-muted">Loading YFC live rates and plant directory…</p>
        </main>
      </div>
    );
  }

  const { profile, stats, materials, plants, destinations, freight } = catalog;
  const trustedList = (profile.trustedClients || "L&T, Bestech, DLF, Ahluwalia, Emaar")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);

  return (
    <div className="yfc-public" id="top">
      <PublicHeader phone={phone} email={email} />

      {/* ==================== BAND 1: SPACIOUS 2-COLUMN HERO ==================== */}
      <section className="pub-hero">
        <div className="pub-wrap">
          <div className="pub-hero-grid">
            <div className="pub-hero-copy">
              <span className="pub-pill-badge">
                <span className="pub-pulse" /> {profile.heroKicker} · Owned Fleet &amp; Crusher Supply
              </span>
              <h1>
                {profile.heroTitle}
                <br />
                <span>{profile.heroTitleRest}</span>
              </h1>
              <p className="pub-hero-sub">{profile.heroSubtitle}</p>
              <p className="pub-hero-desc">
                From logistics to delivery — we ensure quality, timely, and safe delivery of
                building materials across India with our own tipper fleet and verified crusher yards.
              </p>
              <div className="pub-hero-actions">
                <a className="pub-btn lg" href="#estimator">
                  Estimate &amp; Enquire <ArrowRight size={16} />
                </a>
                <a className="pub-btn ghost lg" href="#track">
                  <MapPin size={16} /> Track Your Order
                </a>
                <Link className="pub-link-cta" to="/signin">
                  Customer Login →
                </Link>
              </div>
            </div>

            <div className="pub-hero-visual">
              <figure className="pub-truck-frame">
                <img
                  src="/media/truck.jpg"
                  alt="YFC Transport owned TATA Signa tipper fleet"
                  fetchPriority="high"
                  decoding="async"
                />
                <figcaption>
                  <strong>YFC Owned Fleet</strong>
                  <span>TATA Signa · Computerised Weighbridge</span>
                </figcaption>
              </figure>
            </div>
          </div>

          {/* 4-COLUMN HORIZONTAL STATS ROW BELOW HERO */}
          <div className="pub-hero-stats" aria-label="Company track record">
            {stats.slice(0, 4).map((s) => {
              const Icon = statIconFor(s.label);
              return (
                <div className="pub-stat-chip" key={s.id}>
                  <Icon size={20} />
                  <div>
                    <strong>{s.value}</strong>
                    <span>{s.label}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* ==================== BAND 2: PRICE ESTIMATOR & LIVE MARKET RATES ==================== */}
      <section className="pub-band pub-band-white" id="estimator">
        <div className="pub-wrap">
          <div className="pub-band-head">
            <div>
              <p className="pub-kicker">LIVE DESK PRICING &amp; FREIGHT CALCULATOR</p>
              <h2>Instant Landed Price Estimate</h2>
              <p className="pub-lead">
                Select your aggregate grade, tonnage, loading plant, and delivery corridor for an
                immediate indicative landed price before speaking with dispatch.
              </p>
            </div>
            <span className="pub-live-pill">
              <i /> LIVE RATES UPDATED BY DESK
            </span>
          </div>

          <form className="pub-estimator-bar" onSubmit={handleCalculate}>
            <label className="pub-est-field">
              <span>
                <Package size={13} /> Select Material
              </span>
              <select
                value={selectedMaterial?.id ?? ""}
                onChange={(e) => {
                  setMaterialId(e.target.value);
                  setShowEstimate(true);
                }}
              >
                {materials.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name} ({inr(m.rate)}/t)
                  </option>
                ))}
              </select>
            </label>

            <label className="pub-est-field">
              <span>
                <Truck size={13} /> Quantity (Ton)
              </span>
              <input
                type="number"
                inputMode="decimal"
                min="1"
                max="500"
                step="0.001"
                required
                value={qty}
                onChange={(e) => {
                  setQty(e.target.value);
                  setShowEstimate(true);
                }}
              />
            </label>

            <label className="pub-est-field">
              <span>
                <Building2 size={13} /> From (Plant Location)
              </span>
              <select
                value={selectedPlant?.id ?? ""}
                onChange={(e) => {
                  setPlantId(e.target.value);
                  setShowEstimate(true);
                }}
              >
                {plants.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="pub-est-field">
              <span>
                <MapPin size={13} /> To (Delivery Location)
              </span>
              <select
                value={selectedDestination?.id ?? ""}
                onChange={(e) => {
                  setDestinationId(e.target.value);
                  setShowEstimate(true);
                }}
              >
                {pricedDestinations.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </label>

            <button className="pub-btn pub-est-submit" type="submit">
              Get Best Estimate <ArrowRight size={15} />
            </button>
          </form>

          {estimateError && (
            <p className="pub-alert" role="alert" style={{ marginTop: 12 }}>
              {estimateError}
            </p>
          )}

          {showEstimate && selectedMaterial && selectedPlant && selectedDestination && (
            <div className="pub-estimate-result">
              <div className="pub-est-metrics">
                <div>
                  <small>Ex-Plant Rate</small>
                  <strong>{inr(selectedMaterial.rate)} / t</strong>
                </div>
                <div>
                  <small>Published Freight</small>
                  <strong>
                    {estimate.freight != null ? `${inr(estimate.freight)} / t` : "—"}
                  </strong>
                </div>
                <div>
                  <small>Landed Rate</small>
                  <strong>
                    {estimate.landed != null ? `${inr(estimate.landed)} / t` : "—"}
                  </strong>
                </div>
                <div className="pub-est-total">
                  <small>Estimated Total ({estimate.tonnes} t · excl. GST)</small>
                  <strong>{estimate.total != null ? inr(estimate.total) : "—"}</strong>
                </div>
              </div>
              <div className="pub-est-cta">
                <p>
                  Indicative estimate only — does not place an order. Send an enquiry for
                  a confirmed quotation or sign in as an approved buyer.
                </p>
                <div className="pub-est-buttons">
                  <button
                    type="button"
                    className="pub-btn sm"
                    onClick={sendEstimateToEnquiry}
                  >
                    Send Enquiry with Estimate <ArrowRight size={14} />
                  </button>
                  <Link className="pub-btn ghost sm" to="/signin">
                    Customer Login
                  </Link>
                </div>
              </div>
            </div>
          )}

          {/* LIVE MARKET RATES STRIP (SHIFTED DOWN BELOW ESTIMATOR FOR BREATHING ROOM) */}
          <div className="pub-rates-strip" id="rates" aria-label="Live market rates">
            <div className="pub-rates-strip-head">
              <h3>Today&apos;s Ex-Plant Market Rates</h3>
              <span>Per tonne · Excl. GST · Click any grade to load into estimator</span>
            </div>
            <div className="pub-rates-cards">
              {materials.map((m) => (
                <button
                  type="button"
                  key={m.id}
                  className={`pub-rate-tile ${selectedMaterial?.id === m.id ? "active" : ""}`}
                  onClick={() => {
                    setMaterialId(m.id);
                    setShowEstimate(true);
                  }}
                >
                  <span className="pub-rate-grade">{m.grade}</span>
                  <strong>{m.name}</strong>
                  <span className="pub-rate-price">
                    {inr(m.rate)} <small>/ t</small>
                  </span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* ==================== BAND 3: OUR MATERIALS & 6 GUARANTEES ==================== */}
      <section className="pub-band pub-band-slate" id="materials">
        <div className="pub-wrap">
          <div className="pub-section-head">
            <div>
              <p className="pub-kicker">OUR MATERIALS</p>
              <h2>Graded Aggregates &amp; Construction Sand</h2>
              <p className="pub-lead">
                Washed, screened, and crusher-certified building materials ready for daily bulk
                dispatch.
              </p>
            </div>
            <div className="pub-head-controls">
              {!showAllMaterials && (
                <div className="pub-carousel-arrows">
                  <button
                    type="button"
                    className="pub-icon-circle"
                    aria-label="Scroll materials left"
                    onClick={() =>
                      carouselRef.current?.scrollBy({ left: -320, behavior: "smooth" })
                    }
                  >
                    <ChevronLeft size={16} />
                  </button>
                  <button
                    type="button"
                    className="pub-icon-circle"
                    aria-label="Scroll materials right"
                    onClick={() =>
                      carouselRef.current?.scrollBy({ left: 320, behavior: "smooth" })
                    }
                  >
                    <ChevronRight size={16} />
                  </button>
                </div>
              )}
              <button
                type="button"
                className="pub-text-link"
                onClick={() => setShowAllMaterials((v) => !v)}
              >
                {showAllMaterials ? "Show carousel" : "View All Materials"}{" "}
                <ArrowRight size={14} />
              </button>
            </div>
          </div>

          <div
            ref={carouselRef}
            className={showAllMaterials ? "pub-materials-grid" : "pub-materials-carousel"}
          >
            {materials.map((m) => (
              <button
                type="button"
                key={m.id}
                className={`pub-mat-card ${selectedMaterial?.id === m.id ? "selected" : ""}`}
                onClick={() => {
                  setMaterialId(m.id);
                  setShowEstimate(true);
                  smoothScroll("estimator");
                }}
              >
                <div className="pub-mat-img">
                  <img
                    src={m.image}
                    alt={`${m.name} crushed building material`}
                    loading="lazy"
                    decoding="async"
                  />
                  <span className="pub-mat-price">{inr(m.rate)}/t</span>
                </div>
                <div className="pub-mat-body">
                  <strong>{m.name}</strong>
                  <span className="pub-mat-grade">{m.grade}</span>
                  <p>{m.useText}</p>
                </div>
              </button>
            ))}
          </div>

          {/* 6-PILL TRUST STRIP */}
          <div className="pub-trust-pills" aria-label="Key guarantees">
            <span>
              <ShieldCheck size={16} /> Quality Assured
            </span>
            <span>
              <Clock size={16} /> Timely Delivery
            </span>
            <span>
              <MapPin size={16} /> Stage Tracking
            </span>
            <span>
              <TrendingUp size={16} /> Best Market Price
            </span>
            <span>
              <Receipt size={16} /> GST Invoice
            </span>
            <span>
              <Globe size={16} /> Pan India
            </span>
          </div>
        </div>
      </section>

      {/* ==================== BAND 4: OUR SERVICES & WHY CHOOSE YFC ==================== */}
      <section className="pub-band pub-band-white" id="services">
        <div className="pub-wrap">
          <div className="pub-band-head">
            <div>
              <p className="pub-kicker">CAPABILITIES &amp; PROMISE</p>
              <h2>Built for High-Volume Project Sites</h2>
              <p className="pub-lead">
                From crusher procurement to last-mile unloading, every trip is owned, weighed, and
                billed transparently.
              </p>
            </div>
          </div>

          <div className="pub-two-col pub-spacious-grid">
            <article className="pub-card pub-feature-card">
              <p className="pub-kicker">OUR SERVICES</p>
              <h3>End-to-End Material &amp; Fleet</h3>
              <img
                className="pub-service-img"
                src="/media/plant.jpg"
                alt="YFC stone crusher loading plant"
                loading="lazy"
                decoding="async"
              />
              <ul className="pub-check-list">
                {SERVICES.map((s) => (
                  <li key={s}>
                    <CheckCircle2 size={16} /> <span>{s}</span>
                  </li>
                ))}
              </ul>
            </article>

            <article className="pub-card pub-feature-card pub-why-card">
              <div className="pub-why-header">
                <div>
                  <p className="pub-kicker">WHY CHOOSE YFC?</p>
                  <h3>Direct Fleet. Verified Weights.</h3>
                  <p className="pub-muted" style={{ marginTop: 6 }}>
                    Consistent grading and predictable arrival windows for contractors, RMC plants,
                    and infrastructure developers.
                  </p>
                </div>
                <div className="pub-shield-wrap" aria-hidden="true">
                  <svg viewBox="0 0 88 98" className="pub-shield-svg">
                    <path
                      d="M44 4 L80 18 V47 C80 71 64 86 44 94 C24 86 8 71 8 47 V18 Z"
                      fill="#eff6ff"
                      stroke="#1d4ed8"
                      strokeWidth="3"
                    />
                    <path
                      d="M44 12 L72 23 V47 C72 66 59 79 44 86 C29 79 16 66 16 47 V23 Z"
                      fill="#ffffff"
                      stroke="#dbeafe"
                      strokeWidth="1.5"
                    />
                    <text
                      x="44"
                      y="55"
                      textAnchor="middle"
                      fontFamily="Inter, sans-serif"
                      fontWeight="800"
                      fontSize="20"
                    >
                      <tspan fill="#1d4ed8">Y</tspan>
                      <tspan fill="#dc2626">F</tspan>
                      <tspan fill="#16a34a">C</tspan>
                    </text>
                  </svg>
                </div>
              </div>
              <ul className="pub-check-list">
                {WHY_POINTS.map((point) => (
                  <li key={point}>
                    <CheckCircle2 size={16} /> <span>{point}</span>
                  </li>
                ))}
              </ul>
            </article>
          </div>
        </div>
      </section>

      {/* ==================== BAND 5: TRACK YOUR ORDER & DISPATCH DESK ==================== */}
      <section className="pub-band pub-band-slate" id="track">
        <div className="pub-wrap">
          <div className="pub-track-desk-grid">
            <TrackOrderCard />
            <QuickActionsCard
              phone={phone}
              whatsapp={whatsapp}
              onDownloadBrochure={downloadBrochure}
            />
          </div>
        </div>
      </section>

      {/* ==================== BAND 6: TRUSTED CONSTRUCTION PARTNERS ==================== */}
      {trustedList.length > 0 && (
        <section className="pub-band pub-band-white pub-band-compact" aria-label="Trusted clients">
          <div className="pub-wrap pub-clients-wrap">
            <p className="pub-kicker">TRUSTED BY INDIA&apos;S TOP CONSTRUCTION COMPANIES</p>
            <h2>Supplying Leading Infrastructure &amp; Real Estate Sites</h2>
            <div className="pub-client-logos">
              {trustedList.map((name) => (
                <span key={name} className="pub-client-badge">
                  {name}
                </span>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* ==================== BAND 7: PLANT DIRECTORY & INDIA PRESENCE MAP ==================== */}
      <section className="pub-band pub-band-slate" id="plants">
        <div className="pub-wrap">
          <div className="pub-band-head">
            <div>
              <p className="pub-kicker">LOADING NETWORK &amp; COVERAGE</p>
              <h2>Plant Directory &amp; Regional Presence</h2>
              <p className="pub-lead">
                Find the nearest active crusher yard for your delivery location and compare
                published freight per tonne.
              </p>
            </div>
          </div>

          <div className="pub-plants-map-grid">
            <article className="pub-card pub-feature-card">
              <div className="pub-plant-head">
                <div>
                  <p className="pub-kicker">PLANT DIRECTORY</p>
                  <h3>Find Your Nearest Plant</h3>
                </div>
                <label className="pub-inline-filter">
                  <span>Filter freight by destination</span>
                  <select
                    value={plantDropFilter}
                    onChange={(e) => setPlantDropFilter(e.target.value)}
                  >
                    <option value="">All active plants</option>
                    {destinations.map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.name}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <div className="pub-plant-list">
                {plants.map((p) => {
                  const laneRate = plantDropFilter
                    ? freight.find(
                        (f) => f.plantId === p.id && f.destinationId === plantDropFilter,
                      )?.rate
                    : null;
                  return (
                    <button
                      type="button"
                      key={p.id}
                      className="pub-plant-item"
                      onClick={() => {
                        setPlantId(p.id);
                        if (plantDropFilter) setDestinationId(plantDropFilter);
                        setShowEstimate(true);
                        smoothScroll("estimator");
                      }}
                    >
                      <div>
                        <strong>{p.name}</strong>
                        <small>{p.place}</small>
                        <p>{p.note}</p>
                      </div>
                      <span className="pub-plant-rate">
                        {laneRate && Number(laneRate) > 0
                          ? `${inr(Number(laneRate))}/t freight`
                          : "Use in Estimator →"}
                      </span>
                    </button>
                  );
                })}
              </div>
            </article>

            <IndiaPresenceCard />
          </div>
        </div>
      </section>

      {/* ==================== BAND 8: ABOUT YFC EDITORIAL SECTION ==================== */}
      <section className="pub-band pub-band-white" id="about">
        <div className="pub-wrap">
          <div className="pub-about-grid">
            <div className="pub-about-copy">
              <p className="pub-kicker">{profile.aboutKicker}</p>
              <h2>{profile.aboutTitle}</h2>
              <div className="pub-prose">
                <p>{profile.aboutParagraph1}</p>
                <p>{profile.aboutParagraph2}</p>
                <p>{profile.aboutParagraph3}</p>
              </div>
              <div className="pub-about-actions">
                <a className="pub-btn sm" href="#estimator">
                  Calculate Freight Estimate <ArrowRight size={14} />
                </a>
                <Link className="pub-btn ghost sm" to="/signup?role=SUPPLIER">
                  Partner as Supplier / Driver
                </Link>
              </div>
            </div>

            <figure className="pub-about-photo">
              <img
                src={profile.aboutImage || "/media/plant.jpg"}
                alt={profile.aboutCaption || "YFC crusher and transport operations"}
                loading="lazy"
                decoding="async"
              />
              <figcaption>
                <strong>{profile.aboutCaption}</strong>
                <span>{profile.aboutCaptionAside}</span>
              </figcaption>
            </figure>
          </div>
        </div>
      </section>

      {/* ==================== BAND 9: REQUEST FOR QUOTATION ==================== */}
      <section className="pub-band pub-band-slate" id="contact">
        <div className="pub-wrap">
          <EnquirySection
            profile={profile}
            phone={phone}
            email={email}
            whatsapp={whatsapp}
            prefillNote={prefillNote}
          />
        </div>
      </section>

      {/* ==================== BAND 10: BOTTOM FOOTER TRUST STRIP ==================== */}
      <footer className="pub-footer">
        <div className="pub-footer-trust">
          <div className="pub-wrap pub-footer-trust-row">
            <span>
              <ShieldCheck size={16} /> Verified Bank Payments
            </span>
            <span>
              <FileText size={16} /> Digital GST Invoicing
            </span>
            <span>
              <Truck size={16} /> E-Way Bill Ready
            </span>
            <span>
              <Headphones size={16} /> 24/7 Dispatch Support
            </span>
            <a href={`tel:${phone.replace(/[^+0-9]/g, "")}`} className="pub-footer-call">
              <Phone size={16} /> Need Help? Call {phone}
            </a>
          </div>
        </div>
        <div className="pub-wrap pub-footer-bottom">
          <div className="pub-footer-brand">
            <YfcTriLogo to="/" />
            <p>{profile.contactTagline}</p>
          </div>
          <div className="pub-footer-right">
            <p className="pub-footer-copy">{profile.footerLine}</p>
            <Link className="pub-footer-staff-link" to="/staff">
              Staff ERP Login →
            </Link>
          </div>
        </div>
      </footer>

      {/* FLOATING WHATSAPP PILL FOR EASY REACH ANYWHERE ON PAGE */}
      <a
        className="pub-floating-wa"
        href={`https://wa.me/${whatsapp}?text=${encodeURIComponent("Hello YFC Transport, I need a material supply & freight quotation.")}`}
        target="_blank"
        rel="noopener noreferrer"
        aria-label={`Chat on WhatsApp ${phone}`}
      >
        <MessageCircle size={18} />
        <span>WhatsApp Dispatch</span>
      </a>
    </div>
  );
}

function TrackOrderCard() {
  const [query, setQuery] = useState("");
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [miss, setMiss] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function lookup(event: FormEvent) {
    event.preventDefault();
    const key = query.trim().toUpperCase();
    if (!key) return;
    setBusy(true);
    setMiss(false);
    setError("");
    try {
      const found = await request<Ticket>(
        `/api/v1/site/quotes/${encodeURIComponent(key)}`,
      );
      setTicket(found);
    } catch (caught) {
      setTicket(null);
      const status =
        typeof caught === "object" && caught && "status" in caught
          ? Number((caught as { status: number }).status)
          : 0;
      if (status === 404) setMiss(true);
      else setError(errMsg(caught));
    } finally {
      setBusy(false);
    }
  }

  const stageIndex = ticket ? Math.max(0, STAGE_KEYS.indexOf(ticket.stage)) : -1;

  return (
    <article className="pub-card pub-feature-card pub-track-card">
      <div className="pub-track-head">
        <div>
          <p className="pub-kicker">TRACK YOUR ORDER</p>
          <h3>Live Dispatch Stage Lookup</h3>
        </div>
        <span className="pub-live-pill">
          <i /> Live Tracking
        </span>
      </div>
      <p className="pub-muted" style={{ marginTop: 4 }}>
        Enter your YFC dispatch ticket reference (for example <code>YFC-10001-XXXX</code>) to
        check loading, transit, and delivery progress in real time.
      </p>
      <form className="pub-track-form" onSubmit={lookup}>
        <label className="pub-sr-only" htmlFor="track-order-input">
          Order ID
        </label>
        <input
          id="track-order-input"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="YFC-10001-XXXX"
          autoCapitalize="characters"
          maxLength={40}
        />
        <button className="pub-btn" type="submit" disabled={busy}>
          <Search size={15} /> {busy ? "Searching…" : "Track Order"}
        </button>
      </form>
      {miss && (
        <p className="pub-alert" role="status">
          No ticket found for <strong>{query.trim().toUpperCase()}</strong>. Check the code on
          your dispatch slip or call our desk.
        </p>
      )}
      {error && (
        <p className="pub-alert" role="alert">
          {error}
        </p>
      )}
      {ticket && (
        <div className="pub-ticket-box">
          <div className="pub-ticket-top">
            <strong>{ticket.reference}</strong>
            <span className="pub-stage-badge">{ticket.stageLabel}</span>
          </div>
          <p>
            {ticket.materialName} · {ticket.quantity} ton · {ticket.plantName} →{" "}
            {ticket.destination}
          </p>
          <ol className="pub-stage-steps">
            {STAGES.map((label, i) => (
              <li key={label} className={i <= stageIndex ? "done" : ""}>
                {label}
              </li>
            ))}
          </ol>
        </div>
      )}
      <div className="pub-track-footer">
        <p className="pub-track-portal-note">
          Already an approved customer?{" "}
          <Link to="/signin">Sign in to view all orders, trips &amp; GST invoices →</Link>
        </p>
      </div>
    </article>
  );
}

function QuickActionsCard({
  phone,
  whatsapp,
  onDownloadBrochure,
}: {
  phone: string;
  whatsapp: string;
  onDownloadBrochure: () => void;
}) {
  const [callOpen, setCallOpen] = useState(false);
  const [saved, setSaved] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function requestCall(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formEl = event.currentTarget;
    const data = new FormData(formEl);
    const name = String(data.get("name") ?? "").trim();
    const mobile = String(data.get("phone") ?? "").trim();
    if (name.length < 2 || mobile.length < 7) {
      setError("Enter your name and phone number.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const res = await post<{ reference: string }>("/api/v1/site/enquiries", {
        name,
        phone: mobile,
        note: "Callback requested from public website.",
      });
      setSaved(res.reference);
      formEl.reset();
    } catch (caught) {
      setError(errMsg(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="pub-card pub-feature-card pub-rail-card">
      <p className="pub-kicker">INSTANT DESK</p>
      <h3>Talk to Dispatch</h3>
      <p className="pub-muted" style={{ marginBottom: 16 }}>
        Need immediate truck availability or bulk project rates? Reach our operations desk directly.
      </p>
      <div className="pub-rail-actions">
        <a
          className="pub-rail-btn whatsapp"
          href={`https://wa.me/${whatsapp}?text=${encodeURIComponent("Hello YFC Transport, I need a material supply & freight quotation.")}`}
          target="_blank"
          rel="noopener noreferrer"
        >
          <MessageCircle size={17} /> Chat on WhatsApp
        </a>
        <button
          type="button"
          className="pub-rail-btn"
          onClick={() => {
            setCallOpen((v) => !v);
            setSaved("");
            setError("");
          }}
        >
          <PhoneCall size={17} /> Request a Call
        </button>
        <button
          type="button"
          className="pub-rail-btn"
          onClick={onDownloadBrochure}
        >
          <Download size={17} /> Download Brochure
        </button>
      </div>
      {callOpen && (
        <form className="pub-callback-form" onSubmit={requestCall}>
          <input name="name" placeholder="Your name" required minLength={2} maxLength={120} />
          <input
            name="phone"
            type="tel"
            placeholder="Mobile number"
            required
            pattern="[+0-9 ()\-]{7,20}"
            maxLength={20}
          />
          {error && <p className="pub-alert">{error}</p>}
          {saved && (
            <p className="pub-success">
              Logged as <strong>{saved}</strong> — our desk will call you shortly.
            </p>
          )}
          <button className="pub-btn sm" type="submit" disabled={busy}>
            {busy ? "Sending…" : "Call me back"}
          </button>
        </form>
      )}
      <p className="pub-rail-phone">
        Direct line: <a href={`tel:${phone.replace(/[^+0-9]/g, "")}`}>{phone}</a>
      </p>
    </div>
  );
}

function IndiaPresenceCard() {
  return (
    <div className="pub-card pub-feature-card pub-presence-card">
      <p className="pub-kicker">OUR PRESENCE IN INDIA</p>
      <h3>Haryana, Rajasthan &amp; NCR</h3>
      <p className="pub-muted">
        Daily tipper dispatches from Kotputli, Behror, and Bhiwadi hubs across NCR and Pan-India
        project corridors.
      </p>
      <div className="pub-india-map" aria-label="Map highlighting Haryana and NCR operations in India">
        <svg viewBox="0 0 280 250" role="img" aria-label="India supply network map">
          {/* Stylized clean outline of India shifted left to give callouts clean room */}
          <path
            d="M84 14 L106 10 L122 22 L134 36 L128 52 L144 64 L166 72 L188 68 L202 82 L192 98 L172 96 L160 112 L148 134 L132 162 L114 198 L100 232 L88 214 L76 184 L62 152 L44 128 L24 114 L20 96 L40 88 L46 68 L62 48 L72 28 Z"
            fill="#f1f5f9"
            stroke="#cbd5e1"
            strokeWidth="2"
            strokeLinejoin="round"
          />
          {/* Northern corridor highlight (Haryana / NCR / Kotputli) */}
          <path
            d="M72 60 L102 58 L112 76 L94 94 L68 92 L58 76 Z"
            fill="#dbeafe"
            stroke="#2563eb"
            strokeWidth="1.5"
          />
          {/* Supply routes radiating across India */}
          <path
            d="M86 76 Q116 98 148 118 M86 76 Q66 116 56 138 M86 76 Q98 140 100 186"
            fill="none"
            stroke="#3b82f6"
            strokeWidth="1.5"
            strokeDasharray="4 4"
          />
          {/* Hub dots */}
          <circle cx="88" cy="72" r="5" fill="#1d4ed8" />
          <circle cx="88" cy="72" r="10" fill="rgba(37, 99, 235, 0.2)" />
          <circle cx="80" cy="84" r="3.5" fill="#15803d" />

          {/* Clean leader lines + non-overlapping callout labels */}
          <line x1="96" y1="68" x2="176" y2="44" stroke="#1d4ed8" strokeWidth="1" strokeDasharray="2 2" />
          <rect x="176" y="30" width="96" height="26" rx="6" fill="#eff6ff" stroke="#bfdbfe" />
          <text x="224" y="47" textAnchor="middle" fontSize="10.5" fontWeight="700" fill="#1e3a8a">
            Haryana / NCR
          </text>

          <line x1="86" y1="86" x2="176" y2="112" stroke="#15803d" strokeWidth="1" strokeDasharray="2 2" />
          <rect x="176" y="100" width="96" height="26" rx="6" fill="#ecfdf5" stroke="#bbf7d0" />
          <text x="224" y="117" textAnchor="middle" fontSize="10.5" fontWeight="700" fill="#14532d">
            Kotputli Hub
          </text>
        </svg>
      </div>
      <div className="pub-region-chips">
        <span>Haryana</span>
        <span>Kotputli (RJ)</span>
        <span>Delhi NCR</span>
        <span>Pan India</span>
      </div>
    </div>
  );
}

function EnquirySection({
  profile,
  phone,
  email,
  whatsapp,
  prefillNote,
}: {
  profile: Profile;
  phone: string;
  email: string;
  whatsapp: string;
  prefillNote: string;
}) {
  const [note, setNote] = useState(prefillNote);
  const [saved, setSaved] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (prefillNote) setNote(prefillNote);
  }, [prefillNote]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formEl = event.currentTarget;
    const form = new FormData(formEl);
    const name = String(form.get("name") ?? "").trim();
    const mobile = String(form.get("phone") ?? "").trim();
    if (name.length < 2 || mobile.length < 7) {
      setError("Leave your name and phone number so the dispatch desk can call you.");
      setSaved("");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const receipt = await post<{ reference: string }>("/api/v1/site/enquiries", {
        name,
        phone: mobile,
        note: note.trim(),
      });
      setSaved(receipt.reference);
      formEl.reset();
      setNote("");
    } catch (caught) {
      setError(errMsg(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="pub-card pub-rfq-card">
      <div className="pub-rfq-grid">
        <div className="pub-rfq-info">
          <p className="pub-kicker">REQUEST FOR QUOTATION</p>
          <h2>{profile.contactTitle}</h2>
          <p className="pub-muted" style={{ marginTop: 12 }}>
            {profile.contactBody}
          </p>
          <div className="pub-contact-meta">
            <a href={`tel:${phone.replace(/[^+0-9]/g, "")}`}>
              <Phone size={16} /> {phone}
            </a>
            <a href={`mailto:${email}`}>
              <Mail size={16} /> {email}
            </a>
            <a
              href={`https://wa.me/${whatsapp}?text=${encodeURIComponent("Hello YFC Transport, I would like to request a bulk quotation.")}`}
              target="_blank"
              rel="noopener noreferrer"
              className="pub-wa-inline"
            >
              <MessageCircle size={16} /> WhatsApp {phone}
            </a>
          </div>
          <p className="pub-rfq-tagline">{profile.contactTagline}</p>
        </div>
        <form className="pub-rfq-form" onSubmit={submit}>
          <div className="pub-form-row">
            <label className="pub-field">
              <span>Your Name / Company</span>
              <input name="name" autoComplete="name" required minLength={2} maxLength={160} />
            </label>
            <label className="pub-field">
              <span>Mobile Number</span>
              <input
                name="phone"
                type="tel"
                autoComplete="tel"
                required
                pattern="[+0-9 ()\-]{7,20}"
                maxLength={20}
              />
            </label>
          </div>
          <label className="pub-field">
            <span>Material, Tonnes &amp; Delivery Site Details</span>
            <textarea
              name="note"
              maxLength={1000}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="e.g., 50 ton 20 MM Aggregate to Gurugram, Sector 82"
            />
          </label>
          {error && (
            <p className="pub-alert" role="alert">
              {error}
            </p>
          )}
          {saved && (
            <p className="pub-success" role="status">
              Enquiry received — reference <strong>{saved}</strong>. Operations will reach out
              shortly.
            </p>
          )}
          <button className="pub-btn lg" type="submit" disabled={busy}>
            {busy ? "Sending…" : "Submit Enquiry to Desk"} <ArrowRight size={16} />
          </button>
        </form>
      </div>
    </section>
  );
}

type SignupRole = "CUSTOMER" | "SUPPLIER" | "DRIVER";

const SIGNUP_ROLES: {
  id: SignupRole;
  label: string;
  sub: string;
  kicker: string;
  heading: string;
  desc: string;
  cta: string;
  icon: typeof Building2;
}[] = [
  {
    id: "CUSTOMER",
    label: "Customer",
    sub: "Buy materials & track orders",
    kicker: "CUSTOMER ACCOUNT",
    heading: "Create Your Buyer Account",
    desc: "Once approved by our office, sign in with your username to track orders, dispatches, weighbridge slips, and GST invoices.",
    cta: "Request Customer Account",
    icon: Building2,
  },
  {
    id: "SUPPLIER",
    label: "Supplier",
    sub: "Crusher & material partner",
    kicker: "SUPPLIER REGISTRATION",
    heading: "Register as a Material Supplier",
    desc: "Crusher plants and aggregate partners register here. After verification by operations, view purchase orders, receipts, and settlements.",
    cta: "Request Supplier Account",
    icon: Package,
  },
  {
    id: "DRIVER",
    label: "Driver",
    sub: "Fleet & delivery driver",
    kicker: "DRIVER REGISTRATION",
    heading: "Register as a Fleet Driver",
    desc: "Drivers register with licence and emergency details. Once approved by our office, sign in to view assigned trips and dispatches.",
    cta: "Request Driver Account",
    icon: Truck,
  },
];

function parseSignupRole(raw: string | null): SignupRole {
  const upper = (raw ?? "").toUpperCase();
  if (upper === "SUPPLIER" || upper === "DRIVER" || upper === "CUSTOMER") {
    return upper;
  }
  return "CUSTOMER";
}

export function CustomerSignup() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [role, setRole] = useState<SignupRole>(() => parseSignupRole(searchParams.get("role")));
  const [shown, setShown] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const param = searchParams.get("role");
    if (param) setRole(parseSignupRole(param));
  }, [searchParams]);

  const activeRole = SIGNUP_ROLES.find((r) => r.id === role) ?? SIGNUP_ROLES[0];

  function selectRole(next: SignupRole) {
    setRole(next);
    setError("");
    setDone(false);
    setSearchParams(next === "CUSTOMER" ? {} : { role: next }, { replace: true });
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body: Record<string, string> = {
      applicationId: uuid(),
      role,
      username: String(form.get("username") ?? "").trim(),
      password: String(form.get("password") ?? ""),
      name: String(form.get("name") ?? "").trim(),
      mobile: String(form.get("mobile") ?? "").trim(),
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
    setBusy(true);
    setError("");
    try {
      await post("/api/v1/account-applications", body);
      setDone(true);
    } catch (caught) {
      setError(errMsg(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="yfc-public">
      <PublicHeader phone="+91 96672 89071" email="sales@yfctransport.com" home={false} />
      <main className="pub-wrap pub-signup-wrap">
        <div className="pub-card pub-signup-card">
          <div className="pub-role-tabs" role="tablist" aria-label="Account type">
            {SIGNUP_ROLES.map((item) => {
              const Icon = item.icon;
              const selected = item.id === role;
              return (
                <button
                  key={item.id}
                  type="button"
                  role="tab"
                  aria-selected={selected}
                  className={`pub-role-tab ${selected ? "active" : ""}`}
                  onClick={() => selectRole(item.id)}
                >
                  <Icon size={17} />
                  <div>
                    <strong>{item.label}</strong>
                    <span>{item.sub}</span>
                  </div>
                </button>
              );
            })}
          </div>

          <p className="pub-kicker" style={{ marginTop: 24 }}>
            {activeRole.kicker}
          </p>
          <h1>{activeRole.heading}</h1>
          <p className="pub-muted" style={{ marginTop: 8 }}>
            {activeRole.desc}
          </p>

          {done ? (
            <div className="pub-success" style={{ marginTop: 22 }}>
              <strong>{activeRole.label} application submitted!</strong> Your request is now in
              our office review queue. Once approved by administration or operations, you can sign
              in immediately.
              <div style={{ marginTop: 14 }}>
                <Link className="pub-btn sm" to="/signin">
                  Go to Portal Login
                </Link>
              </div>
            </div>
          ) : (
            <form
              key={role}
              className="pub-rfq-form"
              style={{ marginTop: 24 }}
              onSubmit={submit}
            >
              <div className="pub-form-row">
                <label className="pub-field">
                  <span>
                    {role === "CUSTOMER"
                      ? "Your Name / Company"
                      : role === "SUPPLIER"
                        ? "Company / Crusher Plant Name"
                        : "Driver Full Name"}
                  </span>
                  <input name="name" required maxLength={120} />
                </label>
                <label className="pub-field">
                  <span>Mobile Number</span>
                  <input
                    name="mobile"
                    type="tel"
                    required
                    pattern="[+0-9 ()\-]{7,20}"
                    maxLength={20}
                    placeholder="+91 98765 43210"
                  />
                </label>
              </div>

              {role === "CUSTOMER" && (
                <>
                  <label className="pub-field">
                    <span>Delivery Address (optional)</span>
                    <input
                      name="address"
                      maxLength={500}
                      placeholder="Project site or billing address"
                    />
                  </label>
                  <label className="pub-field">
                    <span>GSTIN (optional)</span>
                    <input
                      name="gstin"
                      maxLength={15}
                      placeholder="06ABCDE1234F1Z5"
                      style={{ textTransform: "uppercase" }}
                    />
                  </label>
                </>
              )}

              {role === "SUPPLIER" && (
                <>
                  <div className="pub-form-row">
                    <label className="pub-field">
                      <span>GSTIN *</span>
                      <input
                        name="gstin"
                        required
                        maxLength={15}
                        placeholder="08ABCDE1234F1Z5"
                        style={{ textTransform: "uppercase" }}
                      />
                    </label>
                    <label className="pub-field">
                      <span>Business Email (optional)</span>
                      <input
                        name="email"
                        type="email"
                        maxLength={254}
                        placeholder="billing@crusherplant.com"
                      />
                    </label>
                  </div>
                  <label className="pub-field">
                    <span>Plant / Billing Address *</span>
                    <textarea
                      name="address"
                      required
                      maxLength={500}
                      placeholder="Crusher location, village/industrial area, district & state"
                    />
                  </label>
                </>
              )}

              {role === "DRIVER" && (
                <>
                  <div className="pub-form-row">
                    <label className="pub-field">
                      <span>Driving Licence Number *</span>
                      <input
                        name="licenseNumber"
                        required
                        minLength={5}
                        maxLength={40}
                        placeholder="RJ14-20210012345"
                        style={{ textTransform: "uppercase" }}
                      />
                    </label>
                    <label className="pub-field">
                      <span>Licence Expiry Date *</span>
                      <input name="licenseExpiry" type="date" required />
                    </label>
                  </div>
                  <div className="pub-form-row">
                    <label className="pub-field">
                      <span>Emergency Contact Name *</span>
                      <input name="emergencyContact" required maxLength={160} />
                    </label>
                    <label className="pub-field">
                      <span>Emergency Mobile *</span>
                      <input
                        name="emergencyMobile"
                        type="tel"
                        required
                        pattern="[+0-9 ()\-]{7,20}"
                        maxLength={20}
                      />
                    </label>
                  </div>
                </>
              )}

              <div className="pub-form-row">
                <label className="pub-field">
                  <span>Choose Username</span>
                  <input
                    name="username"
                    required
                    minLength={3}
                    maxLength={80}
                    autoComplete="username"
                    placeholder="e.g. sharma_crusher"
                  />
                </label>
                <label className="pub-field">
                  <span>Password (min. 12 chars)</span>
                  <div className="pub-password-wrap">
                    <input
                      name="password"
                      type={shown ? "text" : "password"}
                      required
                      minLength={12}
                      maxLength={72}
                      autoComplete="new-password"
                    />
                    <button
                      type="button"
                      className="pub-password-toggle"
                      aria-label={shown ? "Hide password" : "Show password"}
                      onClick={() => setShown((v) => !v)}
                    >
                      {shown ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                </label>
              </div>

              {error && (
                <p className="pub-alert" role="alert">
                  {error}
                </p>
              )}
              <button className="pub-btn lg" type="submit" disabled={busy}>
                {busy ? "Submitting…" : activeRole.cta} <ArrowRight size={16} />
              </button>
              <p className="pub-muted" style={{ marginTop: 8 }}>
                Already approved? <Link to="/signin">Sign in to Portal</Link> · YFC staff?{" "}
                <Link to="/staff">Staff ERP Login</Link>
              </p>
            </form>
          )}
        </div>
      </main>
    </div>
  );
}

export function CustomerLogin({
  signedInStaff,
  onSignedIn,
  onSignOut,
}: {
  signedInStaff?: string | null;
  onSignedIn: () => Promise<void>;
  onSignOut: () => Promise<void>;
}) {
  const navigate = useNavigate();
  const [shown, setShown] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const username = String(form.get("username") ?? "").trim();
    const password = String(form.get("password") ?? "");
    if (!username || !password) return;
    setBusy(true);
    setError("");
    try {
      await login(username, password, "CUSTOMER");
      await onSignedIn();
      navigate("/portal", { replace: true });
    } catch (caught) {
      setError(errMsg(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="yfc-public">
      <PublicHeader phone="+91 96672 89071" email="sales@yfctransport.com" home={false} />
      <main className="pub-wrap pub-signup-wrap">
        <div className="pub-card pub-signup-card pub-signin-card">
          <p className="pub-kicker">CUSTOMER &amp; PARTNER PORTAL</p>
          <h1>Sign in to Your Account</h1>
          <p className="pub-muted" style={{ marginTop: 8 }}>
            Approved customers, suppliers, and drivers sign in here to track orders,
            dispatches, weighbridge slips, and GST invoices.
          </p>

          {signedInStaff ? (
            <div className="pub-switch-notice" style={{ marginTop: 22 }}>
              <p>
                You are currently signed in as office staff (<strong>{signedInStaff}</strong>).
                Open your Staff ERP workspace or sign out to switch to a customer account.
              </p>
              <div className="pub-switch-actions">
                <Link className="pub-btn sm" to="/dashboard">
                  Open Staff ERP <ArrowRight size={14} />
                </Link>
                <button
                  type="button"
                  className="pub-btn ghost sm"
                  disabled={busy}
                  onClick={async () => {
                    setBusy(true);
                    setError("");
                    try {
                      await logout();
                      await onSignOut();
                    } catch (caught) {
                      setError(errMsg(caught));
                    } finally {
                      setBusy(false);
                    }
                  }}
                >
                  {busy ? "Signing out…" : "Sign out"}
                </button>
              </div>
              {error && (
                <p className="pub-alert" role="alert" style={{ marginTop: 12 }}>
                  {error}
                </p>
              )}
            </div>
          ) : (
            <form className="pub-rfq-form" style={{ marginTop: 24 }} onSubmit={submit}>
              <label className="pub-field">
                <span>Username</span>
                <input
                  name="username"
                  required
                  autoComplete="username"
                  placeholder="Enter your portal username"
                />
              </label>
              <label className="pub-field">
                <span>Password</span>
                <div className="pub-password-wrap">
                  <input
                    name="password"
                    type={shown ? "text" : "password"}
                    required
                    autoComplete="current-password"
                    placeholder="Enter your password"
                  />
                  <button
                    type="button"
                    className="pub-password-toggle"
                    aria-label={shown ? "Hide password" : "Show password"}
                    onClick={() => setShown((v) => !v)}
                  >
                    {shown ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </label>
              {error && (
                <p className="pub-alert" role="alert">
                  {error}
                </p>
              )}
              <button className="pub-btn lg" type="submit" disabled={busy}>
                {busy ? "Signing in…" : "Sign in to Customer Portal"} <ArrowRight size={16} />
              </button>
              <div className="pub-signin-links">
                <p className="pub-muted">
                  New buyer? <Link to="/signup">Create a customer account</Link>
                </p>
                <p className="pub-muted">
                  Supplier or driver? <Link to="/signup?role=SUPPLIER">Register here</Link> ·{" "}
                  <Link to="/reset">Reset password</Link>
                </p>
              </div>
            </form>
          )}

          <div className="pub-signin-staff-bar">
            <span>YFC office employee?</span>
            <Link to="/staff">Go to Staff ERP Login →</Link>
          </div>
        </div>
      </main>
    </div>
  );
}
