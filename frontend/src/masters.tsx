import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { all, del, put, request, type RecordData } from "./api";
import { label } from "./lib";
import {
  ActiveSelect,
  Choices,
  CodeName,
  DataTable,
  ErrorBox,
  Facts,
  Field,
  Heading,
  Pager,
  SaveForm,
  Status,
  useAccess,
  usePaged,
} from "./ui";

export function Customers() {
  const write = useAccess().write;
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState("");
  const [project, setProject] = useState("");
  const { page, setPage, q, items, total, hasNext } = usePaged("customers", "/api/v1/customers");
  const projects = useQuery({
    queryKey: ["projects", selected],
    queryFn: () => all(`/api/v1/customers/${selected}/projects`),
    enabled: !!selected,
  });
  const sites = useQuery({
    queryKey: ["sites", selected, project],
    queryFn: () => all(`/api/v1/customers/${selected}/projects/${project}/sites`),
    enabled: !!project,
  });
  return (
    <>
      <Heading
        eyebrow="MASTERS / CUSTOMERS"
        title="Know who you supply."
        description="Customer records, projects and delivery sites."
        action={
          write && (
            <button onClick={() => setCreate((value) => !value)}>
              <Plus size={16} /> New customer
            </button>
          )
        }
      />
      <ErrorBox error={q.error || projects.error || sites.error} />
      {create && (
        <SaveForm title="New customer" path="/api/v1/customers">
          <div className="form-grid">
            <Field label="Customer name">
              <input name="name" maxLength={160} required />
            </Field>
            <Field label="Mobile">
              <input name="mobile" pattern="[+0-9 ()-]{7,20}" required />
            </Field>
            <Field label="GSTIN (optional)">
              <input name="gstin" pattern="[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][A-Z0-9]Z[A-Z0-9]" />
            </Field>
          </div>
        </SaveForm>
      )}
      <div className="panel">
        {q.isPending ? (
          <p className="empty">Loading customers…</p>
        ) : (
          <DataTable
            rows={items}
            onOpen={(row) => {
              setSelected(row.id);
              setProject("");
            }}
            columns={[
              { label: "Customer", keys: ["name"] },
              { label: "Mobile", keys: ["mobile"] },
              { label: "GSTIN", keys: ["gstin"] },
            ]}
          />
        )}
        <Pager page={page} setPage={setPage} total={total} hasNext={hasNext} />
      </div>
      {selected && (
        <div className="panel form-panel">
          <h2>{items.find((row) => row.id === selected)?.name ?? "Customer"} · projects & sites</h2>
          <Field label="Project">
            <select value={project} onChange={(event) => setProject(event.target.value)}>
              <option value="">Select project</option>
              {projects.data?.map((row) => (
                <option key={row.id} value={row.id}>
                  {row.name}
                  {row.active ? "" : " (inactive)"}
                </option>
              ))}
            </select>
          </Field>
          {sites.data?.map((site) => (
            <SiteEditor key={site.id} customer={selected} project={project} site={site} write={write} />
          ))}
          {write && (
            <details>
              <summary>Create a project</summary>
              <SaveForm path={`/api/v1/customers/${selected}/projects`}>
                <Field label="Branch">
                  <select name="branchId" required>
                    <Choices
                      path="/api/v1/branches"
                      empty="Select active branch"
                      labelOf={(row) => `${row.code} · ${row.name}`}
                    />
                  </select>
                </Field>
                <CodeName />
                <Field label="Description">
                  <input name="description" maxLength={500} />
                </Field>
              </SaveForm>
            </details>
          )}
          {write && project && (
            <details>
              <summary>Add a delivery site</summary>
              <SaveForm path={`/api/v1/customers/${selected}/projects/${project}/sites`}>
                <CodeName />
                <Field label="Address">
                  <textarea name="address" maxLength={500} required />
                </Field>
                <Field label="Contact name">
                  <input name="contactName" maxLength={160} />
                </Field>
                <Field label="Mobile (optional)">
                  <input name="mobile" pattern="[+0-9 ()-]{7,20}" />
                </Field>
              </SaveForm>
            </details>
          )}
        </div>
      )}
    </>
  );
}

function SiteEditor({
  customer,
  project,
  site,
  write,
}: {
  customer: string;
  project: string;
  site: RecordData;
  write: boolean;
}) {
  const path = `/api/v1/customers/${customer}/projects/${project}/sites/${site.id}`;
  return (
    <div className="site">
      <strong>
        {site.code} · {site.name}
      </strong>
      <p>{site.address}</p>
      <Status value={site.active ? "ACTIVE" : "INACTIVE"} />
      {write && (
        <details>
          <summary>Update site</summary>
          <SaveForm path={path} method="PUT">
            <CodeName code={site.code} name={site.name} />
            <Field label="Address">
              <textarea name="address" maxLength={500} required defaultValue={site.address} />
            </Field>
            <Field label="Contact name">
              <input name="contactName" maxLength={160} defaultValue={site.contactName ?? ""} />
            </Field>
            <Field label="Mobile">
              <input name="mobile" pattern="[+0-9 ()-]{7,20}" defaultValue={site.mobile ?? ""} />
            </Field>
          </SaveForm>
          <SaveForm path={`${path}/status`} method="PATCH">
            <ActiveSelect />
          </SaveForm>
        </details>
      )}
    </div>
  );
}

export function Materials() {
  const admin = useAccess().admin;
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState<RecordData | null>(null);
  const { page, setPage, q, items, total, hasNext } = usePaged("materials", "/api/v1/materials");
  return (
    <>
      <Heading
        eyebrow="MASTERS / MATERIALS"
        title="The materials you move."
        description="Shared catalogue. Quantities are recorded in tonnes."
        action={
          admin && (
            <button onClick={() => setCreate((value) => !value)}>
              <Plus size={16} /> New material
            </button>
          )
        }
      />
      <ErrorBox error={q.error} />
      {create && (
        <SaveForm title="New material" path="/api/v1/materials">
          <MaterialFields />
        </SaveForm>
      )}
      <div className="panel">
        {q.isPending ? (
          <p className="empty">Loading materials…</p>
        ) : (
          <DataTable
            rows={items}
            onOpen={setSelected}
            columns={[
              { label: "Material", keys: ["name"] },
              { label: "Code", keys: ["code"] },
              { label: "Category", keys: ["category"] },
              { label: "HSN", keys: ["hsnCode"] },
              { label: "Status", keys: ["active"], status: true },
            ]}
          />
        )}
        <Pager page={page} setPage={setPage} total={total} hasNext={hasNext} />
      </div>
      {selected && admin && (
        <div className="panel form-panel">
          <h3>
            {selected.name} · {selected.code}
          </h3>
          <SaveForm path={`/api/v1/materials/${selected.id}`} method="PUT" onSuccess={setSelected}>
            <MaterialFields current={selected} />
          </SaveForm>
          <SaveForm path={`/api/v1/materials/${selected.id}/status`} method="PATCH">
            <ActiveSelect />
          </SaveForm>
        </div>
      )}
    </>
  );
}

function MaterialFields({ current }: { current?: RecordData }) {
  return (
    <>
      <CodeName code={current?.code ?? ""} name={current?.name ?? ""} />
      <div className="form-grid">
        <Field label="Category">
          <select name="category" defaultValue={current?.category ?? "AGGREGATES"}>
            {["AGGREGATES", "SAND", "DUST", "ROAD_MATERIALS", "OTHER"].map((item) => (
              <option key={item} value={item}>
                {label(item)}
              </option>
            ))}
          </select>
        </Field>
        <Field label="HSN code (optional)">
          <input
            name="hsnCode"
            pattern="[0-9]{4}|[0-9]{6}|[0-9]{8}"
            defaultValue={current?.hsnCode ?? ""}
          />
        </Field>
      </div>
    </>
  );
}

export function Branches() {
  const admin = useAccess().admin;
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState<RecordData | null>(null);
  const { page, setPage, q, items, total, hasNext } = usePaged("branches", "/api/v1/branches");
  return (
    <>
      <Heading
        eyebrow="MASTERS / BRANCHES"
        title="Where the work belongs."
        description="Offices used by projects, yards, fleet and journals."
        action={
          admin && (
            <button onClick={() => setCreate((value) => !value)}>
              <Plus size={16} /> New branch
            </button>
          )
        }
      />
      <ErrorBox error={q.error} />
      {create && (
        <SaveForm title="New branch" path="/api/v1/branches">
          <BranchFields />
        </SaveForm>
      )}
      <div className="panel">
        <DataTable
          rows={items}
          onOpen={setSelected}
          columns={[
            { label: "Branch", keys: ["name"] },
            { label: "Code", keys: ["code"] },
            { label: "City", keys: ["city"] },
            { label: "State", keys: ["state"] },
            { label: "Status", keys: ["active"], status: true },
          ]}
        />
        <Pager page={page} setPage={setPage} total={total} hasNext={hasNext} />
      </div>
      {selected && admin && (
        <div className="panel form-panel">
          <h3>{selected.name}</h3>
          <SaveForm path={`/api/v1/branches/${selected.id}`} method="PUT" onSuccess={setSelected}>
            <BranchFields current={selected} />
          </SaveForm>
          <SaveForm path={`/api/v1/branches/${selected.id}/status`} method="PATCH">
            <ActiveSelect />
          </SaveForm>
        </div>
      )}
    </>
  );
}

function BranchFields({ current }: { current?: RecordData }) {
  return (
    <div className="form-grid">
      <Field label="Code">
        <input
          name="code"
          required
          minLength={2}
          maxLength={30}
          pattern="[A-Za-z0-9][A-Za-z0-9_\-]{1,29}"
          defaultValue={current?.code ?? ""}
        />
      </Field>
      <Field label="Name">
        <input name="name" required maxLength={160} defaultValue={current?.name ?? ""} />
      </Field>
      <Field label="Address">
        <input name="address" required maxLength={500} defaultValue={current?.address ?? ""} />
      </Field>
      <Field label="City">
        <input name="city" required maxLength={100} defaultValue={current?.city ?? ""} />
      </Field>
      <Field label="State">
        <input name="state" required maxLength={100} defaultValue={current?.state ?? ""} />
      </Field>
      <Field label="PIN code">
        <input name="pincode" required pattern="[1-9][0-9]{5}" defaultValue={current?.pincode ?? ""} />
      </Field>
    </div>
  );
}

export function Suppliers() {
  const { admin, write } = useAccess();
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState("");
  const { page, setPage, q, items, total, hasNext } = usePaged("suppliers", "/api/v1/suppliers");
  const detail = useQuery({
    queryKey: ["supplier", selected],
    queryFn: () => request<RecordData>(`/api/v1/suppliers/${selected}`),
    enabled: !!selected,
  });
  const plants = useQuery({
    queryKey: ["plants", selected],
    queryFn: () => all(`/api/v1/suppliers/${selected}/plants`),
    enabled: !!selected,
  });
  return (
    <>
      <Heading
        eyebrow="MASTERS / SUPPLIERS"
        title="Who supplies the material."
        description="Supplier companies, loading plants and the materials each plant can supply."
        action={
          write && (
            <button onClick={() => setCreate((value) => !value)}>
              <Plus size={16} /> New supplier
            </button>
          )
        }
      />
      <ErrorBox error={q.error || detail.error || plants.error} />
      {create && (
        <SaveForm title="New supplier" path="/api/v1/suppliers">
          <SupplierFields />
        </SaveForm>
      )}
      <div className="panel">
        <DataTable
          rows={items}
          onOpen={(row) => setSelected(row.id)}
          columns={[
            { label: "Supplier", keys: ["name"] },
            { label: "Code", keys: ["code"] },
            { label: "GSTIN", keys: ["gstin"] },
            { label: "Mobile", keys: ["mobile"] },
            { label: "Status", keys: ["active"], status: true },
          ]}
        />
        <Pager page={page} setPage={setPage} total={total} hasNext={hasNext} />
      </div>
      {selected && detail.data && (
        <div className="panel form-panel">
          <h2>{detail.data.name}</h2>
          <Facts row={detail.data} />
          {admin && (
            <>
              <SaveForm path={`/api/v1/suppliers/${selected}`} method="PUT">
                <SupplierFields current={detail.data} />
              </SaveForm>
              <SaveForm path={`/api/v1/suppliers/${selected}/status`} method="PATCH">
                <ActiveSelect />
              </SaveForm>
            </>
          )}
          <h3>Plants</h3>
          {plants.data?.map((plant) => (
            <Plant key={plant.id} supplier={selected} plant={plant} write={write} />
          ))}
          {write && (
            <SaveForm title="New plant" path={`/api/v1/suppliers/${selected}/plants`}>
              <CodeName />
              <Field label="Address">
                <textarea name="address" required maxLength={500} />
              </Field>
              <div className="form-grid">
                <Field label="Contact">
                  <input name="contactName" maxLength={160} />
                </Field>
                <Field label="Mobile">
                  <input name="mobile" pattern="[+0-9 ()-]{7,20}" />
                </Field>
              </div>
            </SaveForm>
          )}
        </div>
      )}
    </>
  );
}

function SupplierFields({ current }: { current?: RecordData }) {
  return (
    <div className="form-grid">
      <Field label="Code">
        <input
          name="code"
          required
          minLength={2}
          maxLength={30}
          pattern="[A-Za-z0-9][A-Za-z0-9_\-]{1,29}"
          defaultValue={current?.code ?? ""}
        />
      </Field>
      <Field label="Name">
        <input name="name" required maxLength={160} defaultValue={current?.name ?? ""} />
      </Field>
      <Field label="GSTIN">
        <input
          name="gstin"
          required
          pattern="[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][A-Z0-9]Z[A-Z0-9]"
          defaultValue={current?.gstin ?? ""}
        />
      </Field>
      <Field label="Mobile">
        <input
          name="mobile"
          required
          pattern="[+0-9 ()-]{7,20}"
          defaultValue={current?.mobile ?? ""}
        />
      </Field>
      <Field label="Email">
        <input name="email" type="email" maxLength={254} defaultValue={current?.email ?? ""} />
      </Field>
      <Field label="Address">
        <input name="address" required maxLength={500} defaultValue={current?.address ?? ""} />
      </Field>
    </div>
  );
}

function Plant({ supplier, plant, write }: { supplier: string; plant: RecordData; write: boolean }) {
  const materials = useQuery({
    queryKey: ["plant-materials", plant.id],
    queryFn: () => all(`/api/v1/suppliers/${supplier}/plants/${plant.id}/materials`),
  });
  return (
    <div className="site">
      <strong>
        {plant.code} · {plant.name}
      </strong>
      <p>{plant.address}</p>
      <Status value={plant.active ? "ACTIVE" : "INACTIVE"} />
      <ErrorBox error={materials.error} />
      <ul className="chip-list">
        {materials.data?.map((row) => (
          <li key={row.materialId}>
            {row.name}
            {write && (
              <button
                className="secondary"
                onClick={() =>
                  del(
                    `/api/v1/suppliers/${supplier}/plants/${plant.id}/materials/${row.materialId}`,
                  ).then(() => materials.refetch())
                }
              >
                Remove
              </button>
            )}
          </li>
        ))}
      </ul>
      {write && (
        <>
          <AttachMaterial supplier={supplier} plant={plant.id} />
          <SaveForm
            path={`/api/v1/suppliers/${supplier}/plants/${plant.id}/status`}
            method="PATCH"
          >
            <ActiveSelect />
          </SaveForm>
        </>
      )}
    </div>
  );
}

function AttachMaterial({ supplier, plant }: { supplier: string; plant: string }) {
  const client = useQueryClient();
  const [material, setMaterial] = useState("");
  const [error, setError] = useState<unknown>(null);
  return (
    <form
      className="line-input"
      onSubmit={async (event) => {
        event.preventDefault();
        setError(null);
        try {
          await put(`/api/v1/suppliers/${supplier}/plants/${plant}/materials/${material}`);
          await client.invalidateQueries({ queryKey: ["plant-materials", plant] });
          setMaterial("");
        } catch (caught) {
          setError(caught);
        }
      }}
    >
      <Field label="Link a catalogue material">
        <select required value={material} onChange={(event) => setMaterial(event.target.value)}>
          <Choices path="/api/v1/materials" empty="Select material" />
        </select>
      </Field>
      <button disabled={!material}>Link</button>
      <ErrorBox error={error} />
    </form>
  );
}
