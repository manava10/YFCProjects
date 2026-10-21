import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Check } from "lucide-react";
import {
  destinations,
  freightFor,
  inr,
  materialById,
  materials,
  plantById,
  plants,
  stages,
} from "@/data/catalog";
import { nextTicketId, useDesk, type Stage, type Ticket } from "@/store/desk";

const fieldClass =
  "h-12 w-full rounded-xl border border-fg/15 bg-ink px-3 text-sm text-fg outline-none";

export function Rates() {
  return (
    <section id="rates" className="scroll-mt-20 border-t border-fg/10 bg-navy">
      <div className="mx-auto max-w-6xl px-5 py-20 md:px-8 md:py-28">
        <p className="text-xs tracking-widest text-orange">LIVE RATES</p>
        <h2 className="mt-3 font-display text-4xl font-bold tracking-tight">Today’s ex-plant card.</h2>
        <p className="mt-3 max-w-lg text-sm leading-relaxed text-muted">
          These are the published desk rates. Freight is added on the quote, by plant and drop.
        </p>
        <div className="mt-8 overflow-x-auto rounded-2xl border border-fg/10">
          <table className="w-full min-w-[36rem] text-left text-sm">
            <thead className="bg-ink text-xs tracking-widest text-muted uppercase">
              <tr>
                <th className="px-5 py-4 font-medium">Material</th>
                <th className="px-5 py-4 font-medium">Grade</th>
                <th className="px-5 py-4 text-right font-medium">Rate</th>
              </tr>
            </thead>
            <tbody>
              {materials.map((item) => (
                <tr key={item.id} className="border-t border-fg/10">
                  <td className="px-5 py-4 font-medium text-fg">{item.name}</td>
                  <td className="px-5 py-4 text-muted">{item.grade}</td>
                  <td className="px-5 py-4 text-right font-medium tabular-nums text-fg">
                    {inr(item.rate)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

export function QuoteDesk() {
  const addTicket = useDesk((state) => state.addTicket);
  const pickedMaterialId = useDesk((state) => state.pickedMaterialId);
  const pickedPlantId = useDesk((state) => state.pickedPlantId);
  const [materialId, setMaterialId] = useState("20mm");
  const [plantId, setPlantId] = useState("kotputli");
  const [delivery, setDelivery] = useState<string>(destinations[0]);
  const [qty, setQty] = useState("20");
  const [mediator, setMediator] = useState("");
  const [error, setError] = useState("");
  const [issued, setIssued] = useState<Ticket | null>(null);

  useEffect(() => {
    if (pickedMaterialId) setMaterialId(pickedMaterialId);
  }, [pickedMaterialId]);

  useEffect(() => {
    if (pickedPlantId) setPlantId(pickedPlantId);
  }, [pickedPlantId]);

  const preview = useMemo(() => {
    const material = materialById(materialId);
    const plant = plantById(plantId);
    const tonnes = Number(qty);
    const freight = freightFor(plantId, delivery);
    const landed = material.rate + freight;
    return {
      material,
      plant,
      tonnes: Number.isFinite(tonnes) ? tonnes : 0,
      freight,
      landed,
      total: Number.isFinite(tonnes) ? landed * tonnes : 0,
    };
  }, [materialId, plantId, delivery, qty]);

  function submit(event: FormEvent) {
    event.preventDefault();
    const tonnes = Number(qty);
    if (!Number.isFinite(tonnes) || tonnes < 1 || tonnes > 500) {
      setError("Quantity must be between 1 and 500 ton.");
      return;
    }
    setError("");
    const material = materialById(materialId);
    const plant = plantById(plantId);
    const freight = freightFor(plantId, delivery);
    const ticket: Ticket = {
      id: nextTicketId(),
      materialId: material.id,
      materialName: material.name,
      qty: tonnes,
      plantId: plant.id,
      plantName: plant.name,
      delivery,
      mediator: mediator.trim(),
      rate: material.rate,
      freight,
      createdAt: Date.now(),
      stage: 0,
    };
    addTicket(ticket);
    setIssued(ticket);
  }

  return (
    <section id="order" className="scroll-mt-20 mx-auto max-w-6xl px-5 py-20 md:px-8 md:py-28">
      <div className="grid gap-8 lg:grid-cols-2">
        <div>
          <p className="text-xs tracking-widest text-orange">QUICK ORDER</p>
          <h2 className="mt-3 font-display text-4xl font-bold tracking-tight">Get Best Quote</h2>
          <p className="mt-3 max-w-md text-sm leading-relaxed text-muted">
            Material, tonnes, plant, and drop. The ticket shows ex-plant rate plus an indicative
            freight for that lane.
          </p>
          <form onSubmit={submit} className="mt-8 grid gap-4 sm:grid-cols-2">
            <label className="block text-sm text-muted">
              Material
              <select
                className={`${fieldClass} mt-2`}
                value={materialId}
                onChange={(event) => setMaterialId(event.target.value)}
              >
                {materials.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-sm text-muted">
              Quantity (Ton)
              <input
                className={`${fieldClass} mt-2`}
                inputMode="decimal"
                value={qty}
                onChange={(event) => setQty(event.target.value)}
              />
            </label>
            <label className="block text-sm text-muted sm:col-span-2">
              From Plant / Crusher
              <select
                className={`${fieldClass} mt-2`}
                value={plantId}
                onChange={(event) => setPlantId(event.target.value)}
              >
                {plants.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-sm text-muted sm:col-span-2">
              Delivery Location
              <select
                className={`${fieldClass} mt-2`}
                value={delivery}
                onChange={(event) => setDelivery(event.target.value)}
              >
                {destinations.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-sm text-muted sm:col-span-2">
              Mediator / Reference
              <input
                className={`${fieldClass} mt-2`}
                value={mediator}
                placeholder="Rajpal"
                onChange={(event) => setMediator(event.target.value)}
              />
            </label>
            {error ? (
              <p className="text-sm text-orange sm:col-span-2" role="alert">
                {error}
              </p>
            ) : null}
            <button
              type="submit"
              className="inline-flex h-12 items-center justify-center rounded-full bg-orange px-6 text-sm font-semibold text-ink sm:col-span-2 sm:w-fit"
            >
              Get Best Quote
            </button>
          </form>
        </div>
        <div className="flex flex-col gap-4">
          <RouteCard
            plant={preview.plant.name}
            delivery={delivery}
            material={preview.material.name}
          />
          <div className="rounded-2xl border border-fg/10 bg-navy p-5">
            <p className="text-xs tracking-widest text-muted">INDICATIVE TICKET</p>
            <dl className="mt-4 space-y-3 text-sm">
              <Row label="Ex-plant" value={`${inr(preview.material.rate)} / ton`} />
              <Row label="Freight" value={`${inr(preview.freight)} / ton`} />
              <Row label="Landed" value={`${inr(preview.landed)} / ton`} />
              <Row label="Quantity" value={`${preview.tonnes || 0} ton`} />
              <div className="flex items-baseline justify-between border-t border-fg/10 pt-3">
                <dt className="text-muted">Ticket total</dt>
                <dd className="font-display text-3xl font-bold tabular-nums">
                  {inr(preview.total)}
                </dd>
              </div>
            </dl>
            {issued ? (
              <div className="mt-5 rounded-xl border border-orange/40 bg-ink p-4">
                <p className="text-xs tracking-widest text-orange">ORDER {issued.id}</p>
                <p className="mt-2 text-sm text-fg">
                  {issued.materialName} · {issued.qty} ton · {issued.plantName} → {issued.delivery}
                </p>
                <p className="mt-1 text-sm text-muted">
                  Mediator / Reference: {issued.mediator || "—"}
                </p>
                <a href="#track" className="mt-4 inline-flex text-sm font-medium text-orange">
                  Track this order
                </a>
              </div>
            ) : (
              <p className="mt-5 text-sm text-muted">
                Issue a quote to lock a trackable order number on this desk.
              </p>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-muted">{label}</dt>
      <dd className="tabular-nums text-fg">{value}</dd>
    </div>
  );
}

export function RouteCard({
  plant,
  delivery,
  material,
}: {
  plant: string;
  delivery: string;
  material: string;
}) {
  return (
    <div className="rounded-2xl border border-fg/10 bg-ink p-5">
      <div className="flex items-center justify-between text-xs tracking-widest text-muted">
        <span>LANE</span>
        <span>{material}</span>
      </div>
      <svg viewBox="0 0 360 92" className="mt-3 h-24 w-full text-orange" aria-hidden="true">
        <path
          d="M28 64 C 90 64, 110 24, 180 24 S 270 64, 332 28"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          className="route-dash opacity-90"
        />
        <circle cx="28" cy="64" r="6" fill="currentColor" />
        <circle cx="332" cy="28" r="6" fill="currentColor" />
      </svg>
      <div className="flex items-start justify-between gap-4 text-sm">
        <p className="max-w-[12rem] text-fg">{plant}</p>
        <p className="max-w-[12rem] text-right text-fg">{delivery}</p>
      </div>
    </div>
  );
}

export function TrackDesk() {
  const tickets = useDesk((state) => state.tickets);
  const activeId = useDesk((state) => state.activeId);
  const setActive = useDesk((state) => state.setActive);
  const setStage = useDesk((state) => state.setStage);
  const [query, setQuery] = useState("");
  const [miss, setMiss] = useState(false);

  useEffect(() => {
    if (activeId) setQuery(activeId);
  }, [activeId]);

  const match = tickets.find((ticket) => ticket.id.toLowerCase() === query.trim().toLowerCase());

  function lookup(event: FormEvent) {
    event.preventDefault();
    const found = tickets.find(
      (ticket) => ticket.id.toLowerCase() === query.trim().toLowerCase(),
    );
    setMiss(!found);
    if (found) setActive(found.id);
  }

  return (
    <section id="track" className="scroll-mt-20 border-t border-fg/10 bg-navy">
      <div className="mx-auto max-w-6xl px-5 py-20 md:px-8 md:py-28">
        <p className="text-xs tracking-widest text-orange">TRACK ORDER</p>
        <h2 className="mt-3 font-display text-4xl font-bold tracking-tight">Follow the ticket.</h2>
        <form onSubmit={lookup} className="mt-8 flex max-w-xl flex-col gap-3 sm:flex-row">
          <label className="sr-only" htmlFor="order-id">
            Order number
          </label>
          <input
            id="order-id"
            className={fieldClass}
            placeholder="YFC-00000"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setMiss(false);
            }}
          />
          <button
            type="submit"
            className="inline-flex h-12 items-center justify-center rounded-full bg-orange px-6 text-sm font-semibold text-ink"
          >
            Track Order
          </button>
        </form>
        {miss ? (
          <p className="mt-4 text-sm text-orange" role="status">
            No order with that number on this desk.
          </p>
        ) : null}
        {match ? <TicketView ticket={match} onAdvance={setStage} /> : null}
        {tickets.length > 0 ? (
          <ul className="mt-8 flex flex-wrap gap-2">
            {tickets.map((ticket) => (
              <li key={ticket.id}>
                <button
                  type="button"
                  onClick={() => {
                    setQuery(ticket.id);
                    setActive(ticket.id);
                    setMiss(false);
                  }}
                  className="h-10 rounded-full border border-fg/15 px-4 text-sm text-fg"
                >
                  {ticket.id}
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-6 text-sm text-muted">Quotes you issue on this device show up here.</p>
        )}
      </div>
    </section>
  );
}

function TicketView({
  ticket,
  onAdvance,
}: {
  ticket: Ticket;
  onAdvance: (id: string, stage: Stage) => void;
}) {
  const image = materialById(ticket.materialId).image;
  const landed = ticket.rate + ticket.freight;

  return (
    <article className="mt-8 grid gap-4 rounded-2xl border border-fg/10 bg-ink p-5 lg:grid-cols-[16rem_1fr]">
      <img
        src={image}
        alt={`Close view of ${ticket.materialName}`}
        className="aspect-square w-full rounded-xl object-cover"
      />
      <div>
        <p className="text-xs tracking-widest text-orange">{ticket.id}</p>
        <h3 className="mt-2 font-display text-2xl font-semibold">
          {ticket.materialName} · {ticket.qty} ton
        </h3>
        <p className="mt-2 text-sm text-muted">
          {ticket.plantName} → {ticket.delivery}
        </p>
        <p className="mt-1 text-sm text-muted">
          Mediator / Reference: {ticket.mediator || "—"} · Landed {inr(landed)} / ton · Total{" "}
          {inr(landed * ticket.qty)}
        </p>
        <ol className="mt-6 grid gap-3 sm:grid-cols-4">
          {stages.map((label, index) => {
            const done = index <= ticket.stage;
            return (
              <li
                key={label}
                className={`rounded-xl border px-3 py-3 text-sm ${
                  done ? "border-orange/50 text-fg" : "border-fg/10 text-muted"
                }`}
              >
                <span className="flex items-center gap-2">
                  {done ? <Check className="size-4 text-orange" aria-hidden="true" /> : null}
                  {label}
                </span>
              </li>
            );
          })}
        </ol>
        {ticket.stage < 3 ? (
          <button
            type="button"
            className="mt-5 inline-flex h-11 items-center rounded-full border border-fg/20 px-5 text-sm text-fg"
            onClick={() => onAdvance(ticket.id, (ticket.stage + 1) as Stage)}
          >
            Advance shipment
          </button>
        ) : (
          <p className="mt-5 text-sm text-orange">Delivered</p>
        )}
      </div>
    </article>
  );
}
