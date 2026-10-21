import { materials, plants, stats } from "@/data/catalog";
import { useDesk } from "@/store/desk";

export function Stats() {
  return (
    <section className="border-y border-fg/10 bg-navy" aria-label="Company record">
      <dl className="mx-auto grid max-w-6xl grid-cols-2 gap-px bg-fg/10 sm:grid-cols-3 lg:grid-cols-5">
        {stats.map((item) => (
          <div key={item.label} className="bg-navy px-5 py-8 md:px-8">
            <dt className="text-xs tracking-widest text-muted uppercase">{item.label}</dt>
            <dd className="mt-2 font-display text-3xl font-bold tracking-tight text-fg">
              {item.value}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

export function About() {
  return (
    <section id="about" className="scroll-mt-20 mx-auto max-w-6xl px-5 py-20 md:px-8 md:py-28">
      <div className="grid items-center gap-10 lg:grid-cols-2">
        <div>
          <p className="text-xs tracking-widest text-orange">ABOUT US</p>
          <h2 className="mt-3 font-display text-4xl font-bold tracking-tight text-fg sm:text-5xl">
            Crush. Load. Deliver on a ticket.
          </h2>
          <div className="mt-6 space-y-4 text-base leading-relaxed text-muted">
            <p>
              YFC Transport has moved graded building material since 2004. The yard crushes, the
              fleet loads, and every trip leaves with a number: material, tonnes, plant, and drop.
            </p>
            <p>
              Twenty-two years on the corridor. 79,200 trips completed. Fleet partners run TATA
              Signa tippers — a working fleet, not a board of strangers.
            </p>
            <p>
              The desk stays plain. Pick the grade, name the plant, name the site. You leave with
              a quote you can track from confirmed to delivered.
            </p>
          </div>
        </div>
        <figure className="overflow-hidden rounded-2xl border border-fg/10">
          <img
            src="/media/truck.jpg"
            alt="Matte black ten-wheel tipper parked on crushed stone"
            className="aspect-video w-full object-cover"
          />
          <figcaption className="flex items-center justify-between bg-navy px-5 py-4 text-sm">
            <span className="text-fg">TATA Signa class tipper</span>
            <span className="text-muted">YFC fleet</span>
          </figcaption>
        </figure>
      </div>
    </section>
  );
}

export function Materials() {
  const pickMaterial = useDesk((state) => state.pickMaterial);

  return (
    <section id="materials" className="scroll-mt-20 border-t border-fg/10 bg-navy">
      <div className="mx-auto max-w-6xl px-5 py-20 md:px-8 md:py-28">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-xs tracking-widest text-orange">MATERIALS</p>
            <h2 className="mt-3 font-display text-4xl font-bold tracking-tight">Five grades. One yard.</h2>
          </div>
          <p className="max-w-sm text-sm leading-relaxed text-muted">
            Tap a grade to load it into the quote. Rates are the published ex-plant card.
          </p>
        </div>
        <ul className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          {materials.map((item) => (
            <li key={item.id}>
              <button
                type="button"
                onClick={() => {
                  pickMaterial(item.id);
                  document.getElementById("order")?.scrollIntoView({ behavior: "smooth" });
                }}
                className="group flex h-full w-full flex-col overflow-hidden rounded-2xl border border-fg/10 bg-ink text-left"
              >
                <img
                  src={item.image}
                  alt=""
                  className="aspect-square w-full object-cover transition-transform duration-300 group-hover:scale-105"
                />
                <span className="flex flex-1 flex-col gap-2 p-4">
                  <span className="font-medium text-fg">{item.name}</span>
                  <span className="text-sm leading-relaxed text-muted">{item.use}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

export function Plants() {
  const pickPlant = useDesk((state) => state.pickPlant);

  return (
    <section id="plants" className="scroll-mt-20 mx-auto max-w-6xl px-5 py-20 md:px-8 md:py-28">
      <p className="text-xs tracking-widest text-orange">OUR PLANTS</p>
      <h2 className="mt-3 max-w-xl font-display text-4xl font-bold tracking-tight">
        Loading points on the Rajasthan–NCR run.
      </h2>
      <ul className="mt-10 grid gap-4 lg:grid-cols-3">
        {plants.map((plant) => (
          <li key={plant.id}>
            <button
              type="button"
              onClick={() => {
                pickPlant(plant.id);
                document.getElementById("order")?.scrollIntoView({ behavior: "smooth" });
              }}
              className="flex h-full w-full flex-col overflow-hidden rounded-2xl border border-fg/10 bg-navy text-left"
            >
              <img src={plant.image} alt="" className="aspect-video w-full object-cover" />
              <span className="flex flex-1 flex-col gap-2 p-5">
                <span className="font-display text-xl font-semibold text-fg">{plant.name}</span>
                <span className="text-sm text-orange">{plant.place}</span>
                <span className="text-sm leading-relaxed text-muted">{plant.note}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
