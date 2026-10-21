import { useEffect, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { post, put, request } from "./api";
import { ErrorBox, Field, Heading } from "./ui";

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
const words: [string, string, boolean][] = [
  ["heroKicker", "Hero label", false],
  ["heroTitle", "Hero title", false],
  ["heroTitleRest", "Hero second line", false],
  ["heroSubtitle", "Hero subtitle", false],
  ["heroLine", "Hero proof line", false],
  ["aboutKicker", "About label", false],
  ["aboutTitle", "About title", false],
  ["aboutParagraph1", "About paragraph 1", true],
  ["aboutParagraph2", "About paragraph 2", true],
  ["aboutParagraph3", "About paragraph 3", true],
  ["aboutCaption", "Photo caption", false],
  ["aboutCaptionAside", "Photo aside", false],
  ["contactKicker", "Contact label", false],
  ["contactTitle", "Contact title", false],
  ["contactBody", "Contact paragraph", true],
  ["contactTagline", "Tagline", false],
  ["footerLine", "Footer line", false],
];

export function PublicDesk() {
  const client = useQueryClient();
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
    return <p className="loading">Opening the customer page…</p>;
  if (desk.isError || !desk.data) return <ErrorBox error={desk.error} />;
  const { catalog, enquiries, quotes } = desk.data;
  return (
    <>
      <Heading
        eyebrow="PUBLIC PAGE"
        title="Customer site"
        description="Words, rates, plants, and the orders people send from the public page."
      />
      <p>
        The opening figures came with the brochure. Change any number here
        before you treat it as the company’s record. A quote keeps the rate from
        the moment it was issued.
      </p>
      <Words profile={catalog.profile} onSaved={saved} />
      <Stats rows={catalog.stats} onSaved={saved} />
      <Materials rows={catalog.materials} onSaved={saved} />
      <Plants rows={catalog.plants} onSaved={saved} />
      <Freight
        plants={catalog.plants}
        destinations={catalog.destinations}
        lanes={catalog.freight}
        onSaved={saved}
      />
      <section className="panel">
        <h2>Enquiries</h2>
        {enquiries.length === 0 ? (
          <p>No one has written in yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Reference</th>
                <th>Name</th>
                <th>Phone</th>
                <th>Note</th>
              </tr>
            </thead>
            <tbody>
              {enquiries.map((row) => (
                <tr key={row.reference}>
                  <td>{row.reference}</td>
                  <td>{row.name}</td>
                  <td>{row.phone}</td>
                  <td>{row.note || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
      <Quotes rows={quotes} onSaved={saved} />
    </>
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
  useEffect(() => setDraft(profile), [profile]);
  return (
    <form
      className="panel"
      onSubmit={async (event) => {
        event.preventDefault();
        setBusy(true);
        setError(null);
        try {
          await put("/api/v1/site/profile", draft);
          onSaved();
        } catch (caught) {
          setError(caught);
        } finally {
          setBusy(false);
        }
      }}
    >
      <h2>Words on the page</h2>
      <ErrorBox error={error} />
      <div className="form-grid">
        {words.map(([key, label, long]) => (
          <Field key={key} label={label}>
            {long ? (
              <textarea
                value={draft[key] ?? ""}
                onChange={(event) =>
                  setDraft({ ...draft, [key]: event.target.value })
                }
              />
            ) : (
              <input
                value={draft[key] ?? ""}
                onChange={(event) =>
                  setDraft({ ...draft, [key]: event.target.value })
                }
              />
            )}
          </Field>
        ))}
        <Field label="About photo">
          <select
            value={draft.aboutImage}
            onChange={(event) =>
              setDraft({ ...draft, aboutImage: event.target.value })
            }
          >
            {images.map((image) => (
              <option key={image} value={image}>
                {image}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <button disabled={busy}>{busy ? "Saving…" : "Save words"}</button>
    </form>
  );
}

function Stats({ rows, onSaved }: { rows: Stat[]; onSaved: () => void }) {
  return (
    <section className="panel">
      <h2>Figures</h2>
      {rows.map((row) => (
        <StatRow key={row.id} row={row} onSaved={onSaved} />
      ))}
    </section>
  );
}

function StatRow({ row, onSaved }: { row: Stat; onSaved: () => void }) {
  const [value, setValue] = useState(row.value);
  const [label, setLabel] = useState(row.label);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    setValue(row.value);
    setLabel(row.label);
  }, [row]);
  return (
    <form
      className="form-grid"
      onSubmit={async (event) => {
        event.preventDefault();
        setError(null);
        try {
          await put(`/api/v1/site/stats/${row.id}`, { value, label });
          onSaved();
        } catch (caught) {
          setError(caught);
        }
      }}
    >
      <Field label="Figure">
        <input
          value={value}
          onChange={(event) => setValue(event.target.value)}
        />
      </Field>
      <Field label="Caption">
        <input
          value={label}
          onChange={(event) => setLabel(event.target.value)}
        />
      </Field>
      <ErrorBox error={error} />
      <button>Save figure</button>
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
    <section className="panel">
      <h2>Materials and ex-plant rates</h2>
      {rows.map((row) => (
        <MaterialRow key={row.id} row={row} onSaved={onSaved} />
      ))}
      <AddMaterial onSaved={onSaved} />
    </section>
  );
}

function MaterialRow({ row, onSaved }: { row: Material; onSaved: () => void }) {
  const [draft, setDraft] = useState(row);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => setDraft(row), [row]);
  return (
    <form
      className="form-grid"
      onSubmit={async (event) => {
        event.preventDefault();
        setError(null);
        try {
          await put(`/api/v1/site/materials/${row.id}`, {
            name: draft.name,
            grade: draft.grade,
            useText: draft.useText,
            rate: Number(draft.rate),
            image: draft.image,
            active: draft.active,
          });
          onSaved();
        } catch (caught) {
          setError(caught);
        }
      }}
    >
      <Field label="Name">
        <input
          value={draft.name}
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
        />
      </Field>
      <Field label="Grade">
        <input
          value={draft.grade}
          onChange={(event) =>
            setDraft({ ...draft, grade: event.target.value })
          }
        />
      </Field>
      <Field label="Use">
        <input
          value={draft.useText}
          onChange={(event) =>
            setDraft({ ...draft, useText: event.target.value })
          }
        />
      </Field>
      <Field label="Rate per ton">
        <input
          inputMode="decimal"
          value={draft.rate}
          onChange={(event) =>
            setDraft({ ...draft, rate: Number(event.target.value) })
          }
        />
      </Field>
      <ImageField
        value={draft.image}
        onChange={(image) => setDraft({ ...draft, image })}
      />
      <Field label="On the page">
        <select
          value={draft.active ? "yes" : "no"}
          onChange={(event) =>
            setDraft({ ...draft, active: event.target.value === "yes" })
          }
        >
          <option value="yes">Shown</option>
          <option value="no">Hidden</option>
        </select>
      </Field>
      <ErrorBox error={error} />
      <button>Save material</button>
    </form>
  );
}

function AddMaterial({ onSaved }: { onSaved: () => void }) {
  const [error, setError] = useState<unknown>(null);
  return (
    <form
      className="form-grid"
      onSubmit={async (event) => {
        event.preventDefault();
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
          onSaved();
        } catch (caught) {
          setError(caught);
        }
      }}
    >
      <h3>Add a grade</h3>
      <Field label="Name">
        <input name="name" required />
      </Field>
      <Field label="Grade">
        <input name="grade" required />
      </Field>
      <Field label="Use">
        <input name="useText" required />
      </Field>
      <Field label="Rate per ton">
        <input name="rate" inputMode="decimal" required />
      </Field>
      <ImageField name="image" />
      <ErrorBox error={error} />
      <button>Add material</button>
    </form>
  );
}

function Plants({ rows, onSaved }: { rows: Plant[]; onSaved: () => void }) {
  return (
    <section className="panel">
      <h2>Plants</h2>
      {rows.map((row) => (
        <PlantRow key={row.id} row={row} onSaved={onSaved} />
      ))}
      <AddPlant onSaved={onSaved} />
    </section>
  );
}

function PlantRow({ row, onSaved }: { row: Plant; onSaved: () => void }) {
  const [draft, setDraft] = useState(row);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => setDraft(row), [row]);
  return (
    <form
      className="form-grid"
      onSubmit={async (event) => {
        event.preventDefault();
        setError(null);
        try {
          await put(`/api/v1/site/plants/${row.id}`, {
            name: draft.name,
            place: draft.place,
            note: draft.note,
            image: draft.image,
            active: draft.active,
          });
          onSaved();
        } catch (caught) {
          setError(caught);
        }
      }}
    >
      <Field label="Name">
        <input
          value={draft.name}
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
        />
      </Field>
      <Field label="Place">
        <input
          value={draft.place}
          onChange={(event) =>
            setDraft({ ...draft, place: event.target.value })
          }
        />
      </Field>
      <Field label="Note">
        <input
          value={draft.note}
          onChange={(event) => setDraft({ ...draft, note: event.target.value })}
        />
      </Field>
      <ImageField
        value={draft.image}
        onChange={(image) => setDraft({ ...draft, image })}
      />
      <Field label="On the page">
        <select
          value={draft.active ? "yes" : "no"}
          onChange={(event) =>
            setDraft({ ...draft, active: event.target.value === "yes" })
          }
        >
          <option value="yes">Shown</option>
          <option value="no">Hidden</option>
        </select>
      </Field>
      <ErrorBox error={error} />
      <button>Save plant</button>
    </form>
  );
}

function AddPlant({ onSaved }: { onSaved: () => void }) {
  const [error, setError] = useState<unknown>(null);
  return (
    <form
      className="form-grid"
      onSubmit={async (event) => {
        event.preventDefault();
        const formElement = event.currentTarget;
        const form = new FormData(formElement);
        setError(null);
        try {
          await post("/api/v1/site/plants", {
            name: form.get("name"),
            place: form.get("place"),
            note: form.get("note"),
            image: form.get("image"),
          });
          formElement.reset();
          onSaved();
        } catch (caught) {
          setError(caught);
        }
      }}
    >
      <h3>Add a plant</h3>
      <Field label="Name">
        <input name="name" required />
      </Field>
      <Field label="Place">
        <input name="place" required />
      </Field>
      <Field label="Note">
        <input name="note" required />
      </Field>
      <ImageField name="image" />
      <ErrorBox error={error} />
      <button>Add plant</button>
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
  useEffect(() => {
    const next: Record<string, string> = {};
    for (const lane of lanes)
      next[`${lane.plantId}:${lane.destinationId}`] = String(lane.rate);
    setRates(next);
  }, [lanes]);
  return (
    <section className="panel">
      <h2>Drops and freight per ton</h2>
      <AddDrop onSaved={onSaved} />
      <form
        onSubmit={async (event) => {
          event.preventDefault();
          setBusy(true);
          setError(null);
          try {
            await put("/api/v1/site/freight", {
              lanes: Object.entries(rates).map(([key, rate]) => {
                const [plantId, destinationId] = key.split(":");
                return { plantId, destinationId, rate: Number(rate) };
              }),
            });
            onSaved();
          } catch (caught) {
            setError(caught);
          } finally {
            setBusy(false);
          }
        }}
      >
        <div style={{ overflowX: "auto" }}>
          <table>
            <thead>
              <tr>
                <th>Drop</th>
                {plants.map((plant) => (
                  <th key={plant.id}>{plant.name}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {destinations.map((drop) => (
                <tr key={drop.id}>
                  <td>
                    {drop.name}
                    {drop.active ? "" : " (hidden)"}
                  </td>
                  {plants.map((plant) => {
                    const key = `${plant.id}:${drop.id}`;
                    return (
                      <td key={key}>
                        <input
                          inputMode="decimal"
                          value={rates[key] ?? ""}
                          onChange={(event) =>
                            setRates({ ...rates, [key]: event.target.value })
                          }
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
        <button disabled={busy}>{busy ? "Saving…" : "Save freight"}</button>
      </form>
    </section>
  );
}

function AddDrop({ onSaved }: { onSaved: () => void }) {
  const [error, setError] = useState<unknown>(null);
  return (
    <form
      onSubmit={async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const formElement = event.currentTarget;
        const form = new FormData(formElement);
        setError(null);
        try {
          await post("/api/v1/site/destinations", { name: form.get("name") });
          formElement.reset();
          onSaved();
        } catch (caught) {
          setError(caught);
        }
      }}
    >
      <Field label="New drop">
        <input name="name" placeholder="Rewari, Haryana" required />
      </Field>
      <ErrorBox error={error} />
      <button className="secondary">Add drop</button>
    </form>
  );
}

function Quotes({ rows, onSaved }: { rows: Quote[]; onSaved: () => void }) {
  return (
    <section className="panel">
      <h2>Orders from the customer page</h2>
      {rows.length === 0 ? (
        <p>No quote has been issued yet.</p>
      ) : (
        rows.map((row) => <QuoteRow key={row.id} row={row} onSaved={onSaved} />)
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
  if (!next) {
    return (
      <div>
        <strong>{row.reference}</strong>
        <p>
          {row.materialName} · {row.quantity} ton · {row.plantName} →{" "}
          {row.destination} · Delivered
        </p>
      </div>
    );
  }
  return (
    <form
      className="form-grid"
      onSubmit={async (event) => {
        event.preventDefault();
        setError(null);
        try {
          await post(`/api/v1/site/desk/quotes/${row.id}/stage`, {
            stage: next,
          });
          onSaved();
        } catch (caught) {
          setError(caught);
        }
      }}
    >
      <div>
        <strong>{row.reference}</strong>
        <p>
          {row.materialName} · {row.quantity} ton · {row.plantName} →{" "}
          {row.destination}
        </p>
        <p>Mediator / Reference: {row.mediator || "—"}</p>
      </div>
      <ErrorBox error={error} />
      <button>Mark {stageLabel[next]}</button>
    </form>
  );
}

function ImageField({
  value,
  onChange,
  name,
}: {
  value?: string;
  onChange?: (image: string) => void;
  name?: string;
}) {
  return (
    <Field label="Photo">
      <select
        name={name}
        value={name ? undefined : value}
        defaultValue={name ? images[0] : undefined}
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
    </Field>
  );
}
