import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { request, type RecordData } from "./api";
import { short, today, val } from "./lib";
import { Documents } from "./documents";
import {
  ActiveSelect,
  Choices,
  DataTable,
  ErrorBox,
  Field,
  Heading,
  IdField,
  Notice,
  Pager,
  RecordView,
  ReviewBox,
  SaveForm,
  useAccess,
  usePaged,
} from "./ui";

export function Fleet() {
  const { admin, write } = useAccess();
  const [tab, setTab] = useState("trucks");
  const [days, setDays] = useState("30");
  const trucks = usePaged("trucks", "/api/v1/trucks");
  const drivers = usePaged("drivers", "/api/v1/drivers");
  const alerts = usePaged("truck-alerts", `/api/v1/fleet/compliance-alerts?days=${days}`);
  const licences = usePaged(
    "driver-alerts",
    `/api/v1/fleet/driver-compliance-alerts?days=${days}`,
  );
  return (
    <>
      <Heading
        eyebrow="FLEET"
        title="Trucks, drivers and compliance."
        description="A truck stays inactive until its operating profile is complete."
      />
      <div className="tabs">
        {[
          ["trucks", "Trucks"],
          ["drivers", "Drivers"],
          ["alerts", "Compliance"],
        ].map(([id, caption]) => (
          <button key={id} className={tab === id ? "tab active" : "tab"} onClick={() => setTab(id)}>
            {caption}
          </button>
        ))}
      </div>
      {tab === "trucks" && (
        <>
          {write && (
            <SaveForm title="Register a truck" path="/api/v1/trucks">
              <div className="form-grid">
                <Field label="Registration">
                  <input name="registration" required pattern="[A-Z0-9-]{4,20}" />
                </Field>
                <Field label="Capacity (tonnes)">
                  <input name="capacityTonnes" type="number" min="0.001" step="0.001" required />
                </Field>
                <Field label="Tank capacity (litres)">
                  <input name="tankCapacityLitres" type="number" min="0.001" step="0.001" required />
                </Field>
                <Field label="Mileage (km / litre)">
                  <input name="mileageKmPerLitre" type="number" min="0.001" step="0.001" required />
                </Field>
              </div>
            </SaveForm>
          )}
          <ErrorBox error={trucks.q.error} />
          <TruckList rows={trucks.items} />
          <Pager page={trucks.page} setPage={trucks.setPage} hasNext={trucks.hasNext} />
        </>
      )}
      {tab === "drivers" && (
        <>
          {write && (
            <SaveForm title="Register a driver" path="/api/v1/drivers">
              <DriverFields />
            </SaveForm>
          )}
          <ErrorBox error={drivers.q.error} />
          <div className="panel">
            <DataTable
              rows={drivers.items}
              columns={[
                { label: "Driver", keys: ["name"] },
                { label: "Mobile", keys: ["mobile"] },
                { label: "Licence", keys: ["license_number", "licenseNumber"] },
                { label: "Expires", keys: ["license_expiry", "licenseExpiry"] },
                { label: "Status", keys: ["active"], status: true },
              ]}
            />
            <Pager page={drivers.page} setPage={drivers.setPage} hasNext={drivers.hasNext} />
          </div>
          {write && <DriverEditor />}
        </>
      )}
      {tab === "alerts" && (
        <>
          <Field label="Window in days">
            <input value={days} onChange={(event) => setDays(event.target.value)} type="number" min="0" max="365" />
          </Field>
          <h3>Trucks</h3>
          <ErrorBox error={alerts.q.error || licences.q.error} />
          <div className="panel">
            <DataTable
              rows={alerts.items}
              columns={[
                { label: "Registration", keys: ["registration"] },
                { label: "Status", keys: ["operational_status", "operationalStatus"], status: true },
                { label: "Insurance", keys: ["insurance_expiry", "insuranceExpiry"] },
                { label: "Fitness", keys: ["fitness_expiry", "fitnessExpiry"] },
              ]}
            />
          </div>
          <h3>Driver licences</h3>
          <div className="panel">
            <DataTable
              rows={licences.items}
              columns={[
                { label: "Driver", keys: ["name"] },
                { label: "Licence", keys: ["license_number", "licenseNumber"] },
                { label: "Expires", keys: ["license_expiry", "licenseExpiry"] },
              ]}
            />
          </div>
          {admin && <p className="muted">These lists are not scheduled messages.</p>}
        </>
      )}
    </>
  );
}

function TruckList({ rows }: { rows: RecordData[] }) {
  const [selected, setSelected] = useState("");
  const q = useQuery({
    queryKey: ["truck", selected],
    queryFn: () => request<RecordData>(`/api/v1/trucks/${selected}`),
    enabled: !!selected,
  });
  return (
    <>
      <div className="panel">
        <DataTable
          rows={rows}
          onOpen={(row) => setSelected(String(row.id))}
          columns={[
            { label: "Registration", keys: ["registration"] },
            { label: "Capacity t", keys: ["capacity_tonnes", "capacityTonnes"] },
            { label: "Status", keys: ["operational_status", "operationalStatus"], status: true },
            { label: "Branch", keys: ["branch_id", "branchId"] },
          ]}
        />
      </div>
      {q.data && (
        <div className="panel form-panel">
          <RecordView row={q.data} />
          <ErrorBox error={q.error} />
          <SaveForm title="Operating profile" path={`/api/v1/trucks/${selected}/compliance`} method="PUT">
            <div className="form-grid">
              <Field label="Branch">
                <select name="branchId" required>
                  <Choices path="/api/v1/branches" empty="Select branch" />
                </select>
              </Field>
              <Field label="Owner">
                <input name="ownerName" required maxLength={160} />
              </Field>
              <Field label="Truck type">
                <input name="truckType" required maxLength={80} />
              </Field>
              <Field label="RC reference">
                <input name="rcNumber" required maxLength={80} />
              </Field>
              <Field label="Insurance reference">
                <input name="insuranceNumber" required maxLength={80} />
              </Field>
              <Field label="Insurance expiry">
                <input name="insuranceExpiry" type="date" required />
              </Field>
              <Field label="Fitness expiry">
                <input name="fitnessExpiry" type="date" required />
              </Field>
              <Field label="Permit expiry">
                <input name="permitExpiry" type="date" required />
              </Field>
              <Field label="Pollution expiry">
                <input name="pollutionExpiry" type="date" required />
              </Field>
              <Field label="Status">
                <select name="status" defaultValue="ACTIVE">
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                  <option value="MAINTENANCE">Maintenance</option>
                </select>
              </Field>
            </div>
          </SaveForm>
        </div>
      )}
    </>
  );
}

function DriverFields({ current }: { current?: RecordData }) {
  return (
    <div className="form-grid">
      <Field label="Branch">
        <select name="branchId" required defaultValue={String(val(current, "branch_id", "branchId") ?? "")}>
          <Choices path="/api/v1/branches" empty="Select branch" />
        </select>
      </Field>
      <Field label="Name">
        <input name="name" required maxLength={160} defaultValue={String(val(current, "name") ?? "")} />
      </Field>
      <Field label="Mobile">
        <input name="mobile" required pattern="[+0-9 ()-]{7,20}" defaultValue={String(val(current, "mobile") ?? "")} />
      </Field>
      <Field label="Licence number">
        <input
          name="licenseNumber"
          required
          pattern="[A-Za-z0-9-]{5,40}"
          defaultValue={String(val(current, "license_number", "licenseNumber") ?? "")}
        />
      </Field>
      <Field label="Licence expiry">
        <input
          name="licenseExpiry"
          type="date"
          required
          defaultValue={String(val(current, "license_expiry", "licenseExpiry") ?? "").slice(0, 10)}
        />
      </Field>
      <Field label="Emergency contact">
        <input
          name="emergencyContact"
          required
          maxLength={160}
          defaultValue={String(val(current, "emergency_contact", "emergencyContact") ?? "")}
        />
      </Field>
      <Field label="Emergency mobile">
        <input
          name="emergencyMobile"
          required
          pattern="[+0-9 ()-]{7,20}"
          defaultValue={String(val(current, "emergency_mobile", "emergencyMobile") ?? "")}
        />
      </Field>
      <ActiveSelect />
    </div>
  );
}

function DriverEditor() {
  const [id, setId] = useState("");
  const q = useQuery({
    queryKey: ["driver", id],
    queryFn: () => request<RecordData>(`/api/v1/drivers/${id}`),
    enabled: !!id,
  });
  return (
    <div className="panel form-panel">
      <h3>Update a driver</h3>
      <Field label="Driver id">
        <input value={id} onChange={(event) => setId(event.target.value)} placeholder="Paste a driver id" />
      </Field>
      <ErrorBox error={q.error} />
      {q.data && (
        <SaveForm path={`/api/v1/drivers/${id}`} method="PUT">
          <DriverFields current={q.data} />
        </SaveForm>
      )}
    </div>
  );
}

export function Trips() {
  const write = useAccess().write;
  const [create, setCreate] = useState(false);
  const list = usePaged("trips", "/api/v1/trips");
  return (
    <>
      <Heading
        eyebrow="FLEET / TRIPS"
        title="One load. One truck. One driver."
        description="Allocate from a yard reservation, a direct purchase, or a transport-only order."
        action={
          write && (
            <button onClick={() => setCreate((value) => !value)}>
              <Plus size={16} /> Allocate trip
            </button>
          )
        }
      />
      {create && <AllocateTrip />}
      <ErrorBox error={list.q.error} />
      <div className="panel">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Trip</th>
                <th>Truck</th>
                <th>Driver</th>
                <th>Source</th>
                <th>Planned</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {list.items.map((row) => (
                <tr key={String(row.id)}>
                  <td>
                    <Link to={`/trips/${row.id}`}>{short(String(row.id))}</Link>
                  </td>
                  <td>{String(val(row, "truck_registration", "truckRegistration") ?? "—")}</td>
                  <td>{String(val(row, "driver_name", "driverName") ?? "—")}</td>
                  <td>{String(val(row, "source") ?? "—")}</td>
                  <td>{String(val(row, "planned_date", "plannedDate") ?? "—")}</td>
                  <td>{String(val(row, "status") ?? "—")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager page={list.page} setPage={list.setPage} hasNext={list.hasNext} />
      </div>
    </>
  );
}

function AllocateTrip() {
  const [source, setSource] = useState("YARD");
  const orders = useQuery({
    queryKey: ["order-item-choices"],
    queryFn: async () => {
      const { collect } = await import("./api");
      const rows = await collect("/api/v1/orders");
      return rows.flatMap((order) =>
        ((order.items ?? []) as RecordData[]).map((item) => ({
          id: String(item.id),
          label: `${short(order.id)} · ${item.material}`,
        })),
      );
    },
  });
  return (
    <SaveForm
      title="Allocate a trip"
      path="/api/v1/trips"
      prepare={(body) => {
        if (body.source === "YARD") delete body.purchaseId;
        else if (body.source === "DIRECT") delete body.reservationId;
        else {
          delete body.purchaseId;
          delete body.reservationId;
        }
        return body;
      }}
    >
      <Notice title="Reuse the trip id if the network retries.">
        The truck, driver and order must share a branch. Planned weight must fit the truck and the
        unclaimed source.
      </Notice>
      <IdField name="tripId" caption="Trip id" />
      <div className="form-grid">
        <Field label="Order line">
          <select name="orderItemId" required>
            <option value="">Select line</option>
            {orders.data?.map((item) => (
              <option key={item.id} value={item.id}>
                {item.label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Truck">
          <select name="truckId" required>
            <Choices path="/api/v1/trucks" empty="Select truck" labelOf={(row) => String(row.registration)} />
          </select>
        </Field>
        <Field label="Driver">
          <select name="driverId" required>
            <Choices path="/api/v1/drivers" empty="Select driver" />
          </select>
        </Field>
        <Field label="Source">
          <select name="source" value={source} onChange={(event) => setSource(event.target.value)}>
            <option value="YARD">Yard reservation</option>
            <option value="DIRECT">Direct purchase</option>
            <option value="TRANSPORT">Transport only</option>
          </select>
        </Field>
        {source === "YARD" && (
          <Field label="Reservation id">
            <input name="reservationId" required />
          </Field>
        )}
        {source === "DIRECT" && (
          <Field label="Purchase id">
            <input name="purchaseId" required />
          </Field>
        )}
        <Field label="Planned tonnes">
          <input name="quantity" type="number" min="0.001" step="0.001" required />
        </Field>
        <Field label="Planned date">
          <input name="plannedDate" type="date" required />
        </Field>
        <Field label="Distance (km)">
          <input name="distanceKm" type="number" min="0.001" step="0.001" required />
        </Field>
      </div>
      <ErrorBox error={orders.error} />
    </SaveForm>
  );
}

const NEXT: Record<string, { path: string; label: string; kind: "event" | "load" | "deliver" | "cancel" }> = {
  ALLOCATED: { path: "start-loading", label: "Start loading", kind: "event" },
  LOADING: { path: "load", label: "Record load", kind: "load" },
  LOADED: { path: "dispatch", label: "Dispatch", kind: "event" },
  DISPATCHED: { path: "arrive", label: "Record arrival", kind: "event" },
  ARRIVED: { path: "start-unloading", label: "Start unloading", kind: "event" },
  UNLOADING: { path: "deliver", label: "Record delivery", kind: "deliver" },
};

export function Trip() {
  const write = useAccess().write;
  const { id = "" } = useParams();
  const q = useQuery({
    queryKey: ["trip", id],
    queryFn: () => request<RecordData>(`/api/v1/trips/${id}`),
  });
  const costs = useQuery({
    queryKey: ["trip-costs", id],
    queryFn: () => request<RecordData>(`/api/v1/trips/${id}/operating-costs`),
  });
  const incidents = useQuery({
    queryKey: ["incidents", id],
    queryFn: () => request<RecordData[]>(`/api/v1/trips/${id}/incidents?page=0&size=20`),
  });
  const row = q.data;
  const status = String(val(row, "status") ?? "");
  const step = NEXT[status];
  return (
    <>
      <Link className="back" to="/trips">
        All trips
      </Link>
      <ErrorBox error={q.error || costs.error || incidents.error} />
      {row && (
        <>
          <Heading
            eyebrow={`TRIP / ${short(id)}`}
            title={String(val(row, "truck_registration", "truckRegistration") ?? "Trip")}
            description={String(val(row, "driver_name", "driverName") ?? "")}
          />
          <div className="panel form-panel">
            <RecordView row={row} />
          </div>
          <div className="panel form-panel">
            <h3>Approved operating cost</h3>
            <RecordView row={costs.data} />
          </div>
          {write && step && (
            <SaveForm title={step.label} path={`/api/v1/trips/${id}/${step.path}`}>
              <IdField name="eventId" caption="Event id" />
              {step.kind === "load" && <Weights />}
              {step.kind === "deliver" && <Delivery />}
            </SaveForm>
          )}
          {write && ["ALLOCATED", "LOADING", "LOADED"].includes(status) && (
            <SaveForm title="Cancel before dispatch" path={`/api/v1/trips/${id}/cancel`}>
              <IdField name="eventId" caption="Event id" />
              <Field label="Reason">
                <textarea name="reason" required maxLength={1000} />
              </Field>
            </SaveForm>
          )}
          {write && ["DISPATCHED", "ARRIVED", "UNLOADING"].includes(status) && (
            <SaveForm title="Change the crew" path={`/api/v1/trips/${id}/reassignments`}>
              <Notice title="Weights and stock stay as recorded.">
                The replacement truck and driver must be free and compliant. The load must fit the
                replacement truck.
              </Notice>
              <IdField name="eventId" caption="Event id" />
              <div className="form-grid">
                <Field label="Replacement truck">
                  <select name="truckId" required>
                    <Choices path="/api/v1/trucks" empty="Select truck" />
                  </select>
                </Field>
                <Field label="Replacement driver">
                  <select name="driverId" required>
                    <Choices path="/api/v1/drivers" empty="Select driver" />
                  </select>
                </Field>
              </div>
              <Field label="Reason">
                <textarea name="reason" required maxLength={1000} />
              </Field>
            </SaveForm>
          )}
          {status === "DELIVERED" && <WeightCorrection tripId={id} write={write} />}
          <Documents targetType="TRIP" targetId={id} />
          <div className="panel form-panel">
            <h3>Incidents</h3>
            <p className="muted">An incident does not change weights, billing or stock.</p>
            {incidents.data?.map((incident) => (
              <article className="site" key={String(incident.id)}>
                <RecordView row={incident} />
                <Documents targetType="INCIDENT" targetId={String(incident.id)} />
                {write && !val(incident, "resolution", "resolved_at", "resolvedAt") && (
                  <SaveForm
                    title="Resolve"
                    path={`/api/v1/trips/${id}/incidents/${incident.id}/resolve`}
                  >
                    <IdField name="eventId" caption="Event id" />
                    <Field label="Resolution">
                      <textarea name="resolution" required maxLength={2000} />
                    </Field>
                    <Field label="Evidence reference">
                      <input name="evidenceReference" required maxLength={500} />
                    </Field>
                  </SaveForm>
                )}
              </article>
            ))}
            {write && status && status !== "CANCELLED" && (
              <SaveForm title="Report an incident" path={`/api/v1/trips/${id}/incidents`}>
                <IdField name="incidentId" caption="Incident id" />
                <div className="form-grid">
                  <Field label="Category">
                    <select name="category">
                      {["BREAKDOWN", "ACCIDENT", "DELAY", "SHORTAGE", "DAMAGE", "OTHER"].map((item) => (
                        <option key={item}>{item}</option>
                      ))}
                    </select>
                  </Field>
                  <Field label="Occurred at (Asia/Kolkata)">
                    <input name="occurredAt" type="datetime-local" required />
                  </Field>
                </div>
                <Field label="Description">
                  <textarea name="description" required maxLength={2000} />
                </Field>
                <Field label="Evidence reference">
                  <input name="evidenceReference" required maxLength={500} />
                </Field>
              </SaveForm>
            )}
          </div>
          {write && status === "DELIVERED" && (
            <SaveForm title="Customer return into a yard" path={`/api/v1/trips/${id}/returns`}>
              <Notice title="Returns wait for accounts approval.">
                Only accepted delivered material can be returned. Resalable approval increases yard
                stock. It does not change the billable order quantity.
              </Notice>
              <IdField name="returnId" caption="Return id" />
              <div className="form-grid">
                <Field label="Receiving yard">
                  <select name="yardId" required>
                    <Choices path="/api/v1/yards" empty="Select yard" />
                  </select>
                </Field>
                <Field label="Quantity">
                  <input name="quantity" type="number" min="0.001" step="0.001" required />
                </Field>
                <Field label="Received on">
                  <input name="receivedOn" type="date" max={today()} required />
                </Field>
                <Field label="Resalable">
                  <select name="resalable" defaultValue="true">
                    <option value="true">Suitable for resale</option>
                    <option value="false">Not resalable</option>
                  </select>
                </Field>
              </div>
              <Field label="Reason">
                <textarea name="reason" required maxLength={1000} />
              </Field>
              <Field label="Evidence reference">
                <input name="evidenceReference" required maxLength={500} />
              </Field>
            </SaveForm>
          )}
        </>
      )}
    </>
  );
}

function WeightCorrection({ tripId, write }: { tripId: string; write: boolean }) {
  const [draft, setDraft] = useState("");
  const [correctionId, setCorrectionId] = useState("");
  const correction = useQuery({
    queryKey: ["weight-correction", correctionId],
    queryFn: () => request<RecordData>(`/api/v1/trips/weight-corrections/${correctionId}`),
    enabled: !!correctionId,
  });
  const pending = String(val(correction.data, "status")) === "PENDING";
  return (
    <>
      {write && (
        <SaveForm
          title="Correct delivered weights"
          path={`/api/v1/trips/${tripId}/weight-corrections`}
          onSuccess={(row) => setCorrectionId(String(row.id))}
        >
          <Notice title="Billing stays on the ordered quantity.">
            A different accounts user approves this. Approval rewrites the trip and its dispatch
            weights. It does not create a tax invoice.
          </Notice>
          <IdField name="correctionId" caption="Correction id" />
          <div className="form-grid">
            <Field label="Loaded gross">
              <input name="loadedGross" type="number" min="0.001" step="0.001" required />
            </Field>
            <Field label="Loaded tare">
              <input name="loadedTare" type="number" min="0" step="0.001" required />
            </Field>
            <Field label="Received gross">
              <input name="receivedGross" type="number" min="0.001" step="0.001" required />
            </Field>
            <Field label="Received tare">
              <input name="receivedTare" type="number" min="0" step="0.001" required />
            </Field>
            <Field label="Accepted tonnes">
              <input name="acceptedQuantity" type="number" min="0" step="0.001" required />
            </Field>
          </div>
          <Field label="Discrepancy reason">
            <textarea name="discrepancyReason" maxLength={1000} />
          </Field>
          <Field label="Reason">
            <textarea name="reason" required maxLength={1000} />
          </Field>
          <Field label="Evidence reference">
            <input name="evidenceReference" required maxLength={500} />
          </Field>
        </SaveForm>
      )}
      <form
        className="panel form-panel"
        onSubmit={(event) => {
          event.preventDefault();
          setCorrectionId(draft.trim());
        }}
      >
        <h3>Review a weight correction</h3>
        <p className="muted">Open the correction id returned when it was submitted.</p>
        <ErrorBox error={correction.error} />
        <Field label="Correction id">
          <input value={draft} onChange={(event) => setDraft(event.target.value)} required />
        </Field>
        <button type="submit">Open</button>
        {correction.data && (
          <>
            <RecordView row={correction.data} />
            {pending && (
              <ReviewBox
                approve={`/api/v1/trips/weight-corrections/${correctionId}/approve`}
                reject={`/api/v1/trips/weight-corrections/${correctionId}/reject`}
                row={correction.data}
              />
            )}
          </>
        )}
      </form>
    </>
  );
}

function Weights() {
  return (
    <div className="form-grid">
      <Field label="Gross tonnes">
        <input name="gross" type="number" min="0.001" step="0.001" required />
      </Field>
      <Field label="Tare tonnes">
        <input name="tare" type="number" min="0" step="0.001" required />
      </Field>
      <Field label="Slip reference">
        <input name="slipReference" maxLength={100} required />
      </Field>
    </div>
  );
}

function Delivery() {
  return (
    <>
      <Weights />
      <div className="form-grid">
        <Field label="Accepted tonnes">
          <input name="acceptedQuantity" type="number" min="0" step="0.001" required />
        </Field>
        <Field label="Receiver">
          <input name="receiverName" maxLength={160} required />
        </Field>
        <Field label="Proof reference">
          <input name="proofReference" maxLength={300} required />
        </Field>
      </div>
      <Field label="Discrepancy reason">
        <textarea name="discrepancyReason" maxLength={1000} />
      </Field>
    </>
  );
}
