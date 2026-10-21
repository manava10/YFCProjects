import { useState, type FormEvent } from "react";
import { nextTicketId, useDesk } from "@/store/desk";

const fieldClass =
  "h-12 w-full rounded-xl border border-fg/15 bg-ink px-3 text-sm text-fg outline-none";

export function Contact() {
  const addEnquiry = useDesk((state) => state.addEnquiry);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [note, setNote] = useState("");
  const [saved, setSaved] = useState("");
  const [error, setError] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    if (name.trim().length < 2 || phone.trim().length < 8) {
      setError("Leave a name and a phone number the yard can use.");
      setSaved("");
      return;
    }
    const id = nextTicketId();
    addEnquiry({
      id,
      name: name.trim(),
      phone: phone.trim(),
      note: note.trim(),
      createdAt: Date.now(),
    });
    setError("");
    setSaved(id);
    setName("");
    setPhone("");
    setNote("");
  }

  return (
    <section id="contact" className="scroll-mt-20 border-t border-fg/10">
      <div className="mx-auto grid max-w-6xl gap-10 px-5 py-20 md:px-8 md:py-28 lg:grid-cols-2">
        <div>
          <p className="text-xs tracking-widest text-orange">CONTACT US</p>
          <h2 className="mt-3 font-display text-4xl font-bold tracking-tight">
            Dispatch desk, Kotputli.
          </h2>
          <p className="mt-4 max-w-md text-sm leading-relaxed text-muted">
            YFC Transport · Premium building material supply & logistics · Since 2004. Pan
            India delivery from the Kotputli crusher and the NCR yards.
          </p>
          <p className="mt-6 font-display text-2xl font-semibold">Building India. Delivering Trust.</p>
        </div>
        <form onSubmit={submit} className="rounded-2xl border border-fg/10 bg-navy p-5">
          <label className="block text-sm text-muted">
            Name
            <input className={`${fieldClass} mt-2`} value={name} onChange={(event) => setName(event.target.value)} />
          </label>
          <label className="mt-4 block text-sm text-muted">
            Phone
            <input
              className={`${fieldClass} mt-2`}
              inputMode="tel"
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
            />
          </label>
          <label className="mt-4 block text-sm text-muted">
            Note for the yard
            <textarea
              className="mt-2 min-h-28 w-full rounded-xl border border-fg/15 bg-ink px-3 py-3 text-sm text-fg outline-none"
              value={note}
              onChange={(event) => setNote(event.target.value)}
            />
          </label>
          {error ? (
            <p className="mt-3 text-sm text-orange" role="alert">
              {error}
            </p>
          ) : null}
          {saved ? (
            <p className="mt-3 text-sm text-fg" role="status">
              Saved on this desk as {saved}. It stays on this device — share it with the yard.
            </p>
          ) : null}
          <button
            type="submit"
            className="mt-5 inline-flex h-12 items-center rounded-full bg-orange px-6 text-sm font-semibold text-ink"
          >
            Send to desk
          </button>
        </form>
      </div>
    </section>
  );
}

export function Footer() {
  return (
    <footer className="border-t border-fg/10 bg-navy">
      <div className="mx-auto flex max-w-6xl flex-col gap-6 px-5 py-12 md:flex-row md:items-end md:justify-between md:px-8">
        <div>
          <p className="font-display text-4xl font-bold tracking-tight">
            YFC <span className="text-orange">TRANSPORT</span>
          </p>
          <p className="mt-2 text-sm text-muted">Building India. Delivering Trust.</p>
        </div>
        <p className="text-sm text-muted">Since 2004 · Kotputli, Rajasthan · Pan India delivery</p>
      </div>
    </footer>
  );
}
