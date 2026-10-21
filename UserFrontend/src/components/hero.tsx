import { materials, inr } from "@/data/catalog";

export function Hero() {
  return (
    <section id="top" className="relative min-h-svh scroll-mt-16 overflow-hidden">
      <img
        src="/media/hero.jpg"
        alt="Tipper on the haul road at a stone crusher plant at sunrise"
        className="absolute inset-0 h-full w-full object-cover"
        fetchPriority="high"
      />
      <div className="absolute inset-0 bg-linear-to-r from-ink via-ink/85 to-ink/25" />
      <div className="absolute inset-0 bg-linear-to-t from-ink via-transparent to-ink/40" />
      <div className="relative mx-auto flex min-h-svh max-w-6xl flex-col justify-end gap-10 px-5 pt-28 pb-14 md:px-8 lg:flex-row lg:items-end lg:justify-between lg:pb-20">
        <div className="max-w-xl">
          <p className="text-xs font-medium tracking-widest text-orange">SINCE 2004</p>
          <h1 className="mt-4 font-display text-5xl leading-none font-bold tracking-tight text-fg sm:text-6xl lg:text-7xl">
            Building India.
            <br />
            Delivering Trust.
          </h1>
          <p className="mt-5 max-w-md text-lg text-fg/90">
            Premium Building Material Supply & Logistics
          </p>
          <p className="mt-3 text-sm tracking-wide text-muted">
            Since 2004 • 22+ Years • 79,200+ Successful Trips
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <a
              href="#order"
              className="inline-flex h-12 items-center rounded-full bg-orange px-6 text-sm font-semibold text-ink"
            >
              Get Best Quote
            </a>
            <a
              href="#track"
              className="inline-flex h-12 items-center rounded-full border border-fg/25 px-6 text-sm font-medium text-fg"
            >
              Track Order
            </a>
          </div>
        </div>
        <aside className="w-full max-w-sm rounded-2xl border border-fg/15 bg-navy/80 p-5 backdrop-blur-xl">
          <div className="flex items-center justify-between">
            <h2 className="font-display text-lg font-semibold">Live Market Rates</h2>
            <span className="inline-flex items-center gap-2 text-xs tracking-widest text-orange">
              <span className="size-1.5 rounded-full bg-orange" aria-hidden="true" />
              LIVE
            </span>
          </div>
          <p className="mt-1 text-xs text-muted">Ex-plant desk card, per ton</p>
          <table className="mt-4 w-full text-sm">
            <thead>
              <tr className="text-left text-xs tracking-widest text-muted">
                <th className="pb-2 font-medium">Material</th>
                <th className="pb-2 text-right font-medium">Rate</th>
              </tr>
            </thead>
            <tbody>
              {materials.map((item) => (
                <tr key={item.id} className="border-t border-fg/10">
                  <td className="py-2.5 text-fg">{item.name}</td>
                  <td className="py-2.5 text-right font-medium tabular-nums text-fg">
                    {inr(item.rate)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </aside>
      </div>
    </section>
  );
}
