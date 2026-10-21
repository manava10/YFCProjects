import { Link } from "react-router-dom";
import {
  ArrowUpRight,
  ArrowRight,
  Truck,
  Layers3,
  Route,
  ShieldCheck,
  Menu,
  X,
  Warehouse,
  HardHat,
} from "lucide-react";
import { useState } from "react";
import "./company-home.css";

export function CompanyHome() {
  const [menu, setMenu] = useState(false);
  return (
    <div className="company-home">
      <div className="company-topline">
        <span>MATERIAL SUPPLY. OWNED FLEET. CONNECTED OPERATIONS.</span>
        <Link to="/staff">
          Staff workspace <ArrowUpRight size={12} />
        </Link>
      </div>
      <header className="company-nav">
        <Link to="/" className="company-logo" aria-label="YFC Transport home">
          <span className="company-monogram">Y</span>
          <span>
            YFC<small>TRANSPORT & MATERIALS</small>
          </span>
        </Link>
        <button
          className="company-menu"
          aria-label={menu ? "Close menu" : "Open menu"}
          aria-expanded={menu}
          onClick={() => setMenu(!menu)}
        >
          {menu ? <X /> : <Menu />}
        </button>
        <nav className={menu ? "company-links open" : "company-links"}>
          <a href="#about" onClick={() => setMenu(false)}>
            About YFC
          </a>
          <a href="#services" onClick={() => setMenu(false)}>
            Our services
          </a>
          <a href="#approach" onClick={() => setMenu(false)}>
            How we work
          </a>
          <Link className="company-nav-cta" to="/staff">
            Staff sign in <ArrowUpRight size={15} />
          </Link>
        </nav>
      </header>
      <main className="company-main">
        <section className="company-hero">
          <div
            className="company-hero-image"
            role="img"
            aria-label="Illustrative tipper truck, excavator and construction site"
          />
          <div className="company-hero-shade" />
          <div className="company-hero-content">
            <p className="company-eyebrow">
              <span /> THE MATERIALS. THE MOVEMENT. THE MOMENTUM.
            </p>
            <h1>
              Moving materials.
              <br />
              Building <em>possibilities.</em>
            </h1>
            <p className="company-hero-description">
              From the source to your construction site, YFC brings material
              supply and owned-fleet transport together. One connected journey.
              A stronger foundation.
            </p>
            <div className="company-hero-actions">
              <a className="company-button" href="#services">
                Explore our services <ArrowUpRight size={18} />
              </a>
              <a className="company-text-link" href="#about">
                Get to know YFC <ArrowRight size={17} />
              </a>
            </div>
          </div>
          <div className="company-hero-bottom">
            <span>BUILT AROUND THE WORK THAT BUILDS.</span>
            <span>
              01 / MATERIALS & MOVEMENT <i />
            </span>
          </div>
        </section>
        <section
          className="company-capabilities"
          aria-label="Our core capabilities"
        >
          <div>
            <Truck size={26} />
            <span>
              <strong>Owned-fleet transport</strong>
              <small>Connected from allocation to delivery</small>
            </span>
          </div>
          <div>
            <Layers3 size={26} />
            <span>
              <strong>Construction materials</strong>
              <small>Supply shaped around your requirement</small>
            </span>
          </div>
          <div>
            <Route size={26} />
            <span>
              <strong>Flexible sourcing</strong>
              <small>Yard stock or direct supplier delivery</small>
            </span>
          </div>
        </section>
        <section className="company-about company-container" id="about">
          <div>
            <p className="company-eyebrow">THE COMPANY BEHIND THE MOVEMENT</p>
            <h2>
              Construction needs materials.
              <br />
              <span>Progress needs movement.</span>
            </h2>
          </div>
          <div className="company-about-copy">
            <p>
              YFC Transport supports construction with material supply and
              transport through its owned trucks. We connect customer
              requirements, sourcing and delivery in one coordinated operation.
            </p>
            <p>
              Whether material comes from YFC stock or directly from a supplier,
              our focus is on keeping each order, load and delivery connected to
              the job it serves.
            </p>
            <a className="company-inline-link" href="#approach">
              Discover our approach <ArrowUpRight size={17} />
            </a>
          </div>
        </section>
        <section className="company-services" id="services">
          <div className="company-container">
            <div className="company-section-heading">
              <div>
                <p className="company-eyebrow">WHAT WE DO</p>
                <h2>
                  The supply behind your site.
                  <br />
                  The fleet behind your load.
                </h2>
              </div>
              <p>
                Three connected capabilities.
                <br />
                Built around the needs of construction.
              </p>
            </div>
            <div className="company-service-grid">
              {[
                {
                  n: "01",
                  icon: Layers3,
                  title: "Material supply",
                  text: "Construction materials matched to the order, with material and transport requirements clearly defined.",
                  tags: "AGGREGATES · SAND · ROAD MATERIALS",
                },
                {
                  n: "02",
                  icon: Truck,
                  title: "Owned-fleet transport",
                  text: "Material movement using YFC trucks, with planned allocation, loading records and delivery tracking.",
                  tags: "TRUCK ALLOCATION · LOADS · DELIVERY",
                },
                {
                  n: "03",
                  icon: Warehouse,
                  title: "Yard & direct supply",
                  text: "Source material from available yard stock or arrange direct supplier delivery for the customer order.",
                  tags: "YARD STOCK · DIRECT SOURCING",
                },
              ].map((s) => (
                <article className="company-service" key={s.n}>
                  <div className="company-service-top">
                    <s.icon size={31} strokeWidth={1.5} />
                    <span>{s.n}</span>
                  </div>
                  <h3>{s.title}</h3>
                  <p>{s.text}</p>
                  <small>{s.tags}</small>
                </article>
              ))}
            </div>
          </div>
        </section>
        <section className="company-approach company-container" id="approach">
          <div className="company-approach-intro">
            <p className="company-eyebrow">FROM REQUIREMENT TO RECEIPT</p>
            <h2>
              Every load is part
              <br />
              of a bigger plan.
            </h2>
            <p>
              A clear process keeps the commercial agreement, material source
              and physical delivery working together.
            </p>
            <div className="company-plan-mark">
              <HardHat size={34} />
              <span>
                Built for the demands
                <br />
                of a working construction site.
              </span>
            </div>
          </div>
          <div className="company-process">
            {[
              [
                "Understand the requirement",
                "Confirm the material, quantity and delivery site.",
              ],
              [
                "Plan the supply",
                "Agree the quotation and choose yard or direct sourcing.",
              ],
              [
                "Allocate the movement",
                "Connect the order with a truck, driver and trip.",
              ],
              [
                "Record the delivery",
                "Capture delivery weights and supporting references.",
              ],
            ].map(([title, text], i) => (
              <div key={title}>
                <span>0{i + 1}</span>
                <div>
                  <h3>{title}</h3>
                  <p>{text}</p>
                </div>
                <ArrowUpRight size={18} />
              </div>
            ))}
          </div>
        </section>
        <section className="company-closing company-container">
          <div className="company-closing-card">
            <ShieldCheck size={31} />
            <div>
              <p className="company-eyebrow">ONE CONNECTED OPERATION</p>
              <h2>
                Strong foundations.
                <br />
                From the very first load.
              </h2>
              <p>
                Material supply and transport, working together for
                construction.
              </p>
            </div>
            <a className="company-button" href="#services">
              Find your service <ArrowUpRight size={18} />
            </a>
          </div>
        </section>
      </main>
      <footer className="company-footer">
        <div className="company-container company-footer-top">
          <Link to="/" className="company-logo">
            <span className="company-monogram">Y</span>
            <span>
              YFC<small>TRANSPORT & MATERIALS</small>
            </span>
          </Link>
          <p>
            Moving materials.
            <br />
            Building possibilities.
          </p>
          <div>
            <a href="#about">About YFC</a>
            <a href="#services">Services</a>
            <Link to="/staff">
              Staff workspace <ArrowUpRight size={13} />
            </Link>
          </div>
        </div>
        <div className="company-container company-footer-bottom">
          <span>© {new Date().getFullYear()} YFC Transport</span>
          <span>Transport · Material supply · Construction</span>
        </div>
      </footer>
    </div>
  );
}
