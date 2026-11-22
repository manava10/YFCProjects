import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowUpRight, Check } from "lucide-react";
import { post, put, request } from "./api";
import { ErrorBox, Field, Heading, Notice, Status, Tabs } from "./ui";

type Profile = Record<string, string>;
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
type Enquiry = {
  reference: string;
  name: string;
  phone: string;
  note: string;
  createdAt: string;
};
type Quote = {
  id: string;
  reference: string;
  materialName: string;
  quantity: number;
  plantName: string;
  destination: string;
  mediator: string;
  stage: string;
  stageLabel: string;
  createdAt: string;
};
type Desk = {
  catalog: {
    profile: Profile;
    stats: Stat[];
    materials: Material[];
    plants: Plant[];
    destinations: Destination[];
    freight: Lane[];
  };
  enquiries: Enquiry[];
  quotes: Quote[];
};

const images = [
  "/media/agg-20.jpg",
  "/media/agg-10.jpg",
  "/media/agg-vsi.jpg",
  "/media/agg-dust.jpg",
  "/media/agg-msand.jpg",
  "/media/plant.jpg",
  "/media/hero.jpg",
  "/media/truck.jpg",
];

type WordGroup = {
  title: string;
  fields: [string, string, boolean][];
};

const wordGroups: WordGroup[] = [
  {
    title: "Contact & Trusted Clients",
    fields: [
      ["contactPhone", "Contact phone", false],
      ["whatsappNumber", "WhatsApp number (digits only)", false],
      ["contactEmail", "Contact email", false],
      ["trustedClients", "Trusted clients (comma-separated)", false],
    ],
  },
  {
    title: "Hero Banner",
    fields: [
      ["heroKicker", "Hero badge label", false],
      ["heroTitle", "Hero title (line 1)", false],
      ["heroTitleRest", "Hero title (line 2, highlighted)", false],
      ["heroSubtitle", "Hero subtitle", false],
      ["heroLine", "Hero proof line", false],
    ],
  },
  {
    title: "About Section",
    fields: [
      ["aboutKicker", "About label", false],
      ["aboutTitle", "About heading", false],
      ["aboutCaption", "Photo caption", false],
      ["aboutCaptionAside", "Photo aside", false],
      ["aboutParagraph1", "About paragraph 1", true],
      ["aboutParagraph2", "About paragraph 2", true],
      ["aboutParagraph3", "About paragraph 3", true],
    ],
  },
  {
    title: "Enquiry Section & Footer",
    fields: [
      ["contactKicker", "Contact label", false],
      ["contactTitle", "Contact heading", false],
      ["contactTagline", "Brand tagline", false],
      ["footerLine", "Footer line", false],
      ["contactBody", "Contact description", true],
    ],
  },
];

function SavedTag({ show }: { show: boolean }) {
  if (!show) return null;
  return (
    <span className="desk-saved" role="status">
      <Check size={14} /> Saved
    </span>
  );
}

export function PublicDesk() {
  const client = useQueryClient();
  const [tab, setTab] = useState("words");
  const desk = useQuery({
    queryKey: ["site-desk"],
    queryFn: () => request<Desk>("/api/v1/site/desk"),
  });
  function saved() {
    void client.invalidateQueries({ queryKey: ["site-desk"] });
    void client.invalidateQueries({ queryKey: ["public-site"] });
    void client.invalidateQueries({ queryKey: ["overview", "public-desk"] });
  }
  if (desk.isPending)
    return <p className="loading">Opening the customer site settings…</p>;
  if (desk.isError || !desk.data) return <ErrorBox error={desk.error} />;
  const { catalog, enquiries, quotes } = desk.data;
  return (
    <div className="desk-page">
      <Heading
        eyebrow="PUBLIC PAGE"
        title="Customer site"
        description="Manage public copy, contact details, ex-plant rates, plants, freight lanes, and incoming website enquiries."
        action={
          <Link className="button secondary" to="/">
            View live site <ArrowUpRight size={15} />
          </Link>
        }
      />
      <Notice title="Live pricing notice">
        Changes saved here immediately update the public website. Existing issued
        quotes retain the rate recorded at the moment they were created.
      </Notice>

      <Tabs
        value={tab}
        onChange={setTab}
        items={[
          { id: "words", label: "Words & Contact" },
          { id: "stats", label: `Figures (${catalog.stats.length})` },
          { id: "materials", label: `Materials (${catalog.materials.length})` },
          { id: "plants", label: `Plants (${catalog.plants.length})` },
          { id: "freight", label: "Freight Matrix" },
          { id: "enquiries", label: `Enquiries (${enquiries.length})` },
          { id: "quotes", label: `Orders (${quotes.length})` },
        ]}
      />

      {tab === "words" && <Words profile={catalog.profile} onSaved={saved} />}
      {tab === "stats" && <Stats rows={catalog.stats} onSaved={saved} />}
      {tab === "materials" && (
        <Materials rows={catalog.materials} onSaved={saved} />
      )}
      {tab === "plants" && <Plants rows={catalog.plants} onSaved={saved} />}
      {tab === "freight" && (
        <Freight
          plants={catalog.plants}
          destinations={catalog.destinations}
          lanes={catalog.freight}
          onSaved={saved}
        />
      )}
      {tab === "enquiries" && <Enquiries rows={enquiries} />}
      {tab === "quotes" && <Quotes rows={quotes} onSaved={saved} />}
    </div>
  );
}

function Words({
  profile,
  onSaved,
}: {
  profile: Profile;
  onSaved: () => void;
}) {
  const [draft, setDraft] = useState(profile);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [ok, setOk] = useState(false);
  useEffect(() => setDraft(profile), [profile]);
  return (
    <form
      className="panel desk-form"
      onSubmit={async (event) => {
        event.preventDefault();
        setBusy(true);
        setError(null);
        setOk(false);
        try {
          await put("/api/v1/site/profile", draft);
          setOk(true);
          onSaved();
        } catch (caught) {
          setError(caught);
        } finally {
          setBusy(false);
        }
      }}
    >
      <div className="panel-heading">
        <div>
          <h3>Words &amp; contact details</h3>
          <span className="muted">
            Updates phone, WhatsApp, client strip, hero copy, and footer across the
            public site.
          </span>
        </div>
      </div>
      <ErrorBox error={error} />
      {wordGroups.map((group) => (
        <div className="desk-group" key={group.title}>
          <h4 className="desk-group-title">{group.title}</h4>
          <div className="desk-grid-2">
            {group.fields.map(([key, label, long]) => (
              <div className={long ? "desk-span-all" : undefined} key={key}>
                <Field label={label}>
                  {long ? (
                    <textarea
                      rows={3}
                      value={draft[key] ?? ""}
                      onChange={(event) => {
                        setOk(false);
                        setDraft({ ...draft, [key]: event.target.value });
                      }}
                    />
                  ) : (
                    <input
                      value={draft[key] ?? ""}
                      onChange={(event) => {
                        setOk(false);
                        setDraft({ ...draft, [key]: event.target.value });
                      }}
                    />
                  )}
                </Field>
              </div>
            ))}
            {group.title === "About Section" && (
              <div className="desk-span-all">
                <ImageField
                  label="About photo"
                  value={draft.aboutImage}
                  onChange={(aboutImage) => {
                    setOk(false);
                    setDraft({ ...draft, aboutImage });
                  }}
                />
              </div>
            )}
          </div>
        </div>
      ))}
      <div className="desk-actions">
        <SavedTag show={ok} />
        <button disabled={busy}>{busy ? "Saving…" : "Save words & contact"}</button>
      </div>
    </form>
  );
}

function Stats({ rows, onSaved }: { rows: Stat[]; onSaved: () => void }) {
  return (
    <section className="panel desk-section">
      <div className="panel-heading">
        <div>
          <h3>Key hero figures</h3>
          <span className="muted">
            Displayed as the 4 track-record stat cards underneath the hero banner.
          </span>
        </div>
      </div>
      <div className="desk-stack">
        {rows.map((row) => (
          <StatRow key={row.id} row={row} onSaved={onSaved} />
        ))}
      </div>
    </section>
  );
}

function StatRow({ row, onSaved }: { row: Stat; onSaved: () => void }) {
  const [value, setValue] = useState(row.value);
  const [label, setLabel] = useState(row.label);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [ok, setOk] = useState(false);
  useEffect(() => {
    setValue(row.value);
    setLabel(row.label);
  }, [row]);
  return (
    <form
      className="desk-item-card"
      onSubmit={async (event) => {
        event.preventDefault();
        setBusy(true);
        setError(null);
        setOk(false);
        try {
          await put(`/api/v1/site/stats/${row.id}`, { value, label });
          setOk(true);
          onSaved();
        } catch (caught) {
          setError(caught);
        } finally {
          setBusy(false);
        }
      }}
    >
      <div className="desk-stat-row">
        <Field label="Figure">
          <input
            value={value}
            onChange={(event) => {
              setOk(false);
              setValue(event.target.value);
            }}
          />
        </Field>
        <Field label="Caption">
          <input
            value={label}
            onChange={(event) => {
              setOk(false);
              setLabel(event.target.value);
            }}
          />
        </Field>
        <div className="desk-inline-action">
          <SavedTag show={ok} />
          <button disabled={busy}>{busy ? "Saving…" : "Save"}</button>
        </div>
      </div>
      <ErrorBox error={error} />
    </form>
  );
}

function Materials({
  rows,
  onSaved,
}: {
  rows: Material[];
  onSaved: () => void;
}) {
  return (
    <section className="panel desk-section">
      <div className="panel-heading">
        <div>
          <h3>Materials &amp; ex-plant rates</h3>
          <span className="muted">
            Rates per tonne (excl. GST) shown in Live Market Rates, Materials
            carousel, and the Price Estimator.
          </span>
        </div>
      </div>
      <div className="desk-stack">
        {rows.map((row) => (
          <MaterialRow key={row.id} row={row} onSaved={onSaved} />
        ))}
      </div>
      <AddMaterial onSaved={onSaved} />
    </section>
  );
}

function MaterialRow({ row, onSaved }: { row: Material; onSaved: () => void }) {
  const [draft, setDraft] = useState(row);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [ok, setOk] = useState(false);
  useEffect(() => setDraft(row), [row]);
  return (
    <form
      className="desk-item-card"
      onSubmit={async (event) => {
        event.preventDefault();
        setBusy(true);
        setError(null);
        setOk(false);
        try {
          await put(`/api/v1/site/materials/${row.id}`, {
            name: draft.name,
            grade: draft.grade,
            useText: draft.useText,
            rate: Number(draft.rate),
            image: draft.image,
            active: draft.active,
          });
          setOk(true);
          onSaved();
        } catch (caught) {
          setError(caught);
        } finally {
          setBusy(false);
        }
      }}
    >
      <div className="desk-grid-3">
        <Field label="Name">
          <input
            value={draft.name}
            onChange={(event) => {
              setOk(false);
              setDraft({ ...draft, name: event.target.value });
            }}
          />
        </Field>
        <Field label="Grade">
          <input
            value={draft.grade}
            onChange={(event) => {
              setOk(false);
              setDraft({ ...draft, grade: event.target.value });
            }}
          />
        </Field>
        <Field label="Rate per ton (INR)">
          <input
            inputMode="decimal"
            value={draft.rate}
            onChange={(event) => {
              setOk(false);
              setDraft({ ...draft, rate: Number(event.target.value) });
            }}
          />
        </Field>
        <div className="desk-span-2">
          <Field label="Typical use">
            <input
              value={draft.useText}
              onChange={(event) => {
                setOk(false);
                setDraft({ ...draft, useText: event.target.value });
              }}
            />
          </Field>
        </div>
        <Field label="Visibility">
          <select
            value={draft.active ? "yes" : "no"}
            onChange={(event) => {
              setOk(false);
              setDraft({ ...draft, active: event.target.value === "yes" });
            }}
          >
            <option value="yes">Shown on site</option>
            <option value="no">Hidden</option>
          </select>
        </Field>
        <div className="desk-span-2">
          <ImageField
            value={draft.image}
            onChange={(image) => {
              setOk(false);
              setDraft({ ...draft, image });
            }}
          />
        </div>
        <div className="desk-inline-action desk-align-end">
          <SavedTag show={ok} />
          <button disabled={busy}>{busy ? "Saving…" : "Save material"}</button>
        </div>
      </div>
      <ErrorBox error={error} />
    </form>
  );
}

function AddMaterial({ onSaved }: { onSaved: () => void }) {
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState(images[0]);
  return (
    <form
      className="desk-add-box"
      onSubmit={async (event) => {
        event.preventDefault();
        setBusy(true);
        setError(null);
        const formElement = event.currentTarget;
        const form = new FormData(formElement);
        try {
          await post("/api/v1/site/materials", {
            name: form.get("name"),
            grade: form.get("grade"),
            useText: form.get("useText"),
            rate: Number(form.get("rate")),
            image: form.get("image"),
          });
          formElement.reset();
          setPreview(images[0]);
          onSaved();
        } catch (caught) {
          setError(caught);
        } finally {
          setBusy(false);
        }
      }}
    >
      <h4 className="desk-group-title">Add a new material grade</h4>
      <div className="desk-grid-3">
        <Field label="Name">
          <input name="name" placeholder="e.g. 40 MM Aggregate" required />
        </Field>
        <Field label="Grade">
          <input name="grade" placeholder="e.g. 40 mm" required />
        </Field>
        <Field label="Rate per ton (INR)">
          <input name="rate" inputMode="decimal" placeholder="800" required />
        </Field>
        <div className="desk-span-2">
          <Field label="Typical use">
            <input
              name="useText"
              placeholder="Road sub-base, mass concrete, drainage"
              required
            />
          </Field>
        </div>
        <ImageField name="image" value={preview} onChange={setPreview} />
      </div>
      <ErrorBox error={error} />
      <div className="desk-actions">
        <button disabled={busy}>{busy ? "Adding…" : "Add material"}</button>
      </div>
    </form>
  );
}

function Plants({ rows, onSaved }: { rows: Plant[]; onSaved: () => void }) {
  return (
    <section className="panel desk-section">
      <div className="panel-heading">
        <div>
          <h3>Loading plants &amp; hubs</h3>
          <span className="muted">
            Shown in the Plant Directory and as loading points in the Price
            Estimator.
          </span>
        </div>
      </div>
      <div className="desk-stack">
        {rows.map((row) => (
          <PlantRow key={row.id} row={row} onSaved={onSaved} />
        ))}
      </div>
      <AddPlant onSaved={onSaved} />
    </section>
  );
}

function PlantRow({ row, onSaved }: { row: Plant; onSaved: () => void }) {
  const [draft, setDraft] = useState(row);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [ok, setOk] = useState(false);
  useEffect(() => setDraft(row), [row]);
  return (
    <form
      className="desk-item-card"
      onSubmit={async (event) => {
        event.preventDefault();
        setBusy(true);
        setError(null);
        setOk(false);
        try {
          await put(`/api/v1/site/plants/${row.id}`, {
            name: draft.name,
            place: draft.place,
            note: draft.note,
            image: draft.image,
            active: draft.active,
          });
          setOk(true);
          onSaved();
        } catch (caught) {
          setError(caught);
        } finally {
          setBusy(false);
        }
      }}
    >
      <div className="desk-grid-3">
        <Field label="Plant name">
          <input
            value={draft.name}
            onChange={(event) => {
              setOk(false);
              setDraft({ ...draft, name: event.target.value });
            }}
          />
        </Field>
        <Field label="Place / corridor">
          <input
            value={draft.place}
            onChange={(event) => {
              setOk(false);
              setDraft({ ...draft, place: event.target.value });
            }}
          />
        </Field>
        <Field label="Visibility">
          <select
            value={draft.active ? "yes" : "no"}
            onChange={(event) => {
              setOk(false);
              setDraft({ ...draft, active: event.target.value === "yes" });
            }}
          >
            <option value="yes">Shown on site</option>
            <option value="no">Hidden</option>
          </select>
        </Field>
        <div className="desk-span-2">
          <Field label="Description / dispatch note">
            <input
              value={draft.note}
              onChange={(event) => {
                setOk(false);
                setDraft({ ...draft, note: event.target.value });
              }}
            />
          </Field>
        </div>
        <ImageField
          value={draft.image}
          onChange={(image) => {
            setOk(false);
            setDraft({ ...draft, image });
          }}
        />
      </div>
      <ErrorBox error={error} />
      <div className="desk-actions">
        <SavedTag show={ok} />
        <button disabled={busy}>{busy ? "Saving…" : "Save plant"}</button>
      </div>
    </form>
  );
}

function AddPlant({ onSaved }: { onSaved: () => void }) {
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState("/media/plant.jpg");
  return (
    <form
      className="desk-add-box"
      onSubmit={async (event) => {
        event.preventDefault();
        const formElement = event.currentTarget;
        const form = new FormData(formElement);
        setBusy(true);
        setError(null);
        try {
          await post("/api/v1/site/plants", {
            name: form.get("name"),
            place: form.get("place"),
            note: form.get("note"),
            image: form.get("image"),
          });
          formElement.reset();
          setPreview("/media/plant.jpg");
          onSaved();
        } catch (caught) {
          setError(caught);
        } finally {
          setBusy(false);
        }
      }}
    >
      <h4 className="desk-group-title">Add a new plant</h4>
      <div className="desk-grid-3">
        <Field label="Plant name">
          <input name="name" placeholder="e.g. Neemrana Yard" required />
        </Field>
        <Field label="Place / corridor">
          <input name="place" placeholder="e.g. Alwar, Rajasthan" required />
        </Field>
        <ImageField name="image" value={preview} onChange={setPreview} />
        <div className="desk-span-all">
          <Field label="Description / dispatch note">
            <input
              name="note"
              placeholder="Operating hours, primary grades, weighbridge info"
              required
            />
          </Field>
        </div>
      </div>
      <ErrorBox error={error} />
      <div className="desk-actions">
        <button disabled={busy}>{busy ? "Adding…" : "Add plant"}</button>
      </div>
    </form>
  );
}

function Freight({
  plants,
  destinations,
  lanes,
  onSaved,
}: {
  plants: Plant[];
  destinations: Destination[];
  lanes: Lane[];
  onSaved: () => void;
}) {
  const [rates, setRates] = useState<Record<string, string>>({});
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [ok, setOk] = useState(false);
  useEffect(() => {
    const next: Record<string, string> = {};
    for (const lane of lanes)
      next[`${lane.plantId}:${lane.destinationId}`] = String(lane.rate);
    setRates(next);
  }, [lanes]);
  return (
    <section className="panel desk-section">
      <div className="panel-heading">
        <div>
          <h3>Delivery drops &amp; freight per ton (INR)</h3>
          <span className="muted">
            Used by the public Price Estimator and Plant Directory to compute
            landed cost.
          </span>
        </div>
      </div>
      <AddDrop onSaved={onSaved} />
      <form
        className="desk-form"
        onSubmit={async (event) => {
          event.preventDefault();
          setBusy(true);
          setError(null);
          setOk(false);
          try {
            await put("/api/v1/site/freight", {
              lanes: Object.entries(rates).map(([key, rate]) => {
                const [plantId, destinationId] = key.split(":");
                return { plantId, destinationId, rate: Number(rate) };
              }),
            });
            setOk(true);
            onSaved();
          } catch (caught) {
            setError(caught);
          } finally {
            setBusy(false);
          }
        }}
      >
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Delivery destination</th>
                {plants.map((plant) => (
                  <th key={plant.id}>{plant.name}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {destinations.map((drop) => (
                <tr key={drop.id}>
                  <td>
                    <strong>{drop.name}</strong>
                    {!drop.active && <small className="muted">Hidden</small>}
                  </td>
                  {plants.map((plant) => {
                    const key = `${plant.id}:${drop.id}`;
                    return (
                      <td key={key}>
                        <input
                          className="desk-rate-input"
                          inputMode="decimal"
                          value={rates[key] ?? ""}
                          onChange={(event) => {
                            setOk(false);
                            setRates({ ...rates, [key]: event.target.value });
                          }}
                        />
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <ErrorBox error={error} />
        <div className="desk-actions">
          <SavedTag show={ok} />
          <button disabled={busy}>{busy ? "Saving…" : "Save freight matrix"}</button>
        </div>
      </form>
    </section>
  );
}

function AddDrop({ onSaved }: { onSaved: () => void }) {
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  return (
    <form
      className="desk-add-drop"
      onSubmit={async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const formElement = event.currentTarget;
        const form = new FormData(formElement);
        setBusy(true);
        setError(null);
        try {
          await post("/api/v1/site/destinations", { name: form.get("name") });
          formElement.reset();
          onSaved();
        } catch (caught) {
          setError(caught);
        } finally {
          setBusy(false);
        }
      }}
    >
      <div className="desk-add-drop-row">
        <Field label="Add new delivery destination">
          <input name="name" placeholder="e.g. Rewari, Haryana" required />
        </Field>
        <button className="secondary" disabled={busy}>
          {busy ? "Adding…" : "Add drop"}
        </button>
      </div>
      <ErrorBox error={error} />
    </form>
  );
}

function Enquiries({ rows }: { rows: Enquiry[] }) {
  return (
    <section className="panel desk-section">
      <div className="panel-heading">
        <div>
          <h3>Incoming website enquiries</h3>
          <span className="muted">
            Estimate enquiries, callback requests, and contact messages sent from
            the public site.
          </span>
        </div>
      </div>
      {rows.length === 0 ? (
        <p className="muted">No website enquiries have been submitted yet.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Reference</th>
                <th>Name / Company</th>
                <th>Phone</th>
                <th>Note / Estimate details</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.reference}>
                  <td>
                    <code>{row.reference}</code>
                  </td>
                  <td>
                    <strong>{row.name}</strong>
                  </td>
                  <td>{row.phone}</td>
                  <td>{row.note || "—"}</td>
                  <td className="desk-td-action">
                    <Link className="button secondary" to="/sales/new">
                      New ERP RFQ
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function Quotes({ rows, onSaved }: { rows: Quote[]; onSaved: () => void }) {
  return (
    <section className="panel desk-section">
      <div className="panel-heading">
        <div>
          <h3>Trackable desk tickets</h3>
          <span className="muted">
            Advance dispatch stages shown when customers look up their order ID on
            the public page.
          </span>
        </div>
      </div>
      {rows.length === 0 ? (
        <p className="muted">No desk tickets have been issued yet.</p>
      ) : (
        <div className="desk-stack">
          {rows.map((row) => (
            <QuoteRow key={row.id} row={row} onSaved={onSaved} />
          ))}
        </div>
      )}
    </section>
  );
}

const nextStage: Record<string, string> = {
  CONFIRMED: "LOADING",
  LOADING: "ON_THE_WAY",
  ON_THE_WAY: "DELIVERED",
};
const stageLabel: Record<string, string> = {
  LOADING: "Loading",
  ON_THE_WAY: "On the Way",
  DELIVERED: "Delivered",
};

function QuoteRow({ row, onSaved }: { row: Quote; onSaved: () => void }) {
  const next = nextStage[row.stage];
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  return (
    <div className="desk-item-card">
      <div className="desk-quote-row">
        <div className="desk-quote-info">
          <div className="desk-quote-head">
            <strong>{row.reference}</strong>
            <Status value={row.stage} />
          </div>
          <p>
            {row.materialName} · {row.quantity} ton · {row.plantName} →{" "}
            {row.destination}
          </p>
          {row.mediator && (
            <small className="muted">Mediator / Ref: {row.mediator}</small>
          )}
        </div>
        {next && (
          <button
            type="button"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              setError(null);
              try {
                await post(`/api/v1/site/desk/quotes/${row.id}/stage`, {
                  stage: next,
                });
                onSaved();
              } catch (caught) {
                setError(caught);
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? "Updating…" : `Mark ${stageLabel[next]}`}
          </button>
        )}
      </div>
      <ErrorBox error={error} />
    </div>
  );
}

function ImageField({
  label = "Photo",
  value,
  onChange,
  name,
}: {
  label?: string;
  value?: string;
  onChange?: (image: string) => void;
  name?: string;
}) {
  const src = value || images[0];
  return (
    <Field label={label}>
      <div className="desk-img-picker">
        <img src={src} alt="" className="desk-thumb" />
        <select
          name={name}
          value={name ? undefined : src}
          defaultValue={name ? src : undefined}
          onChange={
            onChange ? (event) => onChange(event.target.value) : undefined
          }
        >
          {images.map((image) => (
            <option key={image} value={image}>
              {image.replace("/media/", "")}
            </option>
          ))}
        </select>
      </div>
    </Field>
  );
}
