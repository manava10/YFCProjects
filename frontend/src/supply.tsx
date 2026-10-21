import { useState, type ReactNode } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { collect, request, type RecordData } from "./api";
import { Documents } from "./documents";
import { money, operatingYard, short, today, val, yardLabel } from "./lib";
import {
  ActiveSelect,
  Choices,
  CodeName,
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

function useOrderItems() {
  return useQuery({
    queryKey: ["order-item-choices"],
    queryFn: async () => {
      const orders = await collect("/api/v1/orders");
      return orders.flatMap((order) =>
        ((order.items ?? []) as RecordData[]).map((item) => ({
          id: String(item.id),
          label: `ORD-${short(order.id)} · ${item.material} · ${item.orderedQuantity} t`,
        })),
      );
    },
  });
}

export function Yards() {
  const { admin, write } = useAccess();
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState<RecordData | null>(null);
  const { page, setPage, q, items, hasNext } = usePaged("yards", "/api/v1/yards");
  return (
    <>
      <Heading
        eyebrow="SUPPLY / YARDS"
        title="Where material waits."
        description="An operating yard can be purchased into. A quarantine yard holds material that is not for resale. Kind cannot be changed later."
        action={
          write && (
            <button onClick={() => setCreate((value) => !value)}>
              <Plus size={16} /> New yard
            </button>
          )
        }
      />
      <ErrorBox error={q.error} />
      {create && (
        <SaveForm title="New yard" path="/api/v1/yards">
          <Field label="Branch">
            <select name="branchId" required>
              <Choices path="/api/v1/branches" empty="Select branch" />
            </select>
          </Field>
          <CodeName />
          <Field label="Kind">
            <select name="kind" defaultValue="OPERATING">
              <option value="OPERATING">Operating</option>
              <option value="QUARANTINE">Quarantine</option>
            </select>
          </Field>
          <Field label="Address">
            <textarea name="address" required maxLength={500} />
          </Field>
        </SaveForm>
      )}
      <div className="panel">
        <DataTable
          rows={items}
          onOpen={setSelected}
          columns={[
            { label: "Yard", keys: ["name"] },
            { label: "Code", keys: ["code"] },
            { label: "Kind", keys: ["kind"], status: true },
            { label: "Address", keys: ["address"] },
            { label: "Status", keys: ["active"], status: true },
          ]}
        />
        <Pager page={page} setPage={setPage} hasNext={hasNext} />
      </div>
      {selected && (
        <div className="panel form-panel">
          <RecordView row={selected} />
          {admin && (
            <SaveForm path={`/api/v1/yards/${selected.id}/status`} method="PATCH">
              <ActiveSelect />
            </SaveForm>
          )}
          <StockLookup yardId={String(selected.id)} />
        </div>
      )}
    </>
  );
}

function Movements({ yard, material }: { yard: string; material: string }) {
  const moves = usePaged("movements", `/api/v1/yards/${yard}/stock/${material}/movements`);
  return (
    <>
      <h3>Movements</h3>
      <ErrorBox error={moves.q.error} />
      <DataTable
        rows={moves.items}
        columns={[
          { label: "Kind", keys: ["kind"] },
          { label: "Quantity", keys: ["quantity"] },
          { label: "Reference", keys: ["reference"] },
          { label: "When", keys: ["created_at", "createdAt"] },
        ]}
      />
    </>
  );
}

function StockLookup({ yardId }: { yardId?: string }) {
  const [yard, setYard] = useState(yardId ?? "");
  const [material, setMaterial] = useState("");
  const q = useQuery({
    queryKey: ["stock", yard, material],
    queryFn: () => request<RecordData>(`/api/v1/yards/${yard}/stock/${material}`),
    enabled: !!yard && !!material,
  });
  return (
    <div>
      <h3>Stock balance</h3>
      <div className="form-grid">
        {!yardId && (
          <Field label="Yard">
            <select value={yard} onChange={(event) => setYard(event.target.value)}>
              <Choices path="/api/v1/yards" empty="Select yard" labelOf={yardLabel} />
            </select>
          </Field>
        )}
        <Field label="Material">
          <select value={material} onChange={(event) => setMaterial(event.target.value)}>
            <Choices path="/api/v1/materials" empty="Select material" />
          </select>
        </Field>
      </div>
      <ErrorBox error={q.error} />
      {q.data && <RecordView row={q.data} />}
      {yard && material && <Movements yard={yard} material={material} />}
    </div>
  );
}

export function Purchases() {
  const write = useAccess().write;
  const nav = useNavigate();
  const [create, setCreate] = useState(false);
  const { page, setPage, q, items, hasNext } = usePaged("purchases", "/api/v1/purchases");
  return (
    <>
      <Heading
        eyebrow="SUPPLY / PURCHASES"
        title="One material. One destination."
        description="A purchase goes to a yard or directly to one sales order line."
        action={
          write && (
            <button onClick={() => setCreate((value) => !value)}>
              <Plus size={16} /> New purchase
            </button>
          )
        }
      />
      <Notice title="This is a commitment, not a supplier invoice.">
        The subtotal is ordered quantity times rate, excluding tax. Receipt weights do not change
        customer billing.
      </Notice>
      <ErrorBox error={q.error} />
      {create && <PurchaseForm />}
      <div className="panel">
        <DataTable
          rows={items}
          onOpen={(row) => nav(`/purchases/${row.id}`)}
          columns={[
            { label: "Material", keys: ["material_name", "materialName"] },
            { label: "Supplier", keys: ["supplier_name", "supplierName"] },
            { label: "Destination", keys: ["destination"] },
            { label: "Quantity", keys: ["quantity"] },
            { label: "Rate", keys: ["rate"] },
            { label: "Status", keys: ["status"], status: true },
          ]}
        />
        <Pager page={page} setPage={setPage} hasNext={hasNext} />
      </div>
    </>
  );
}

function PurchaseForm() {
  const [destination, setDestination] = useState("YARD");
  const [supplier, setSupplier] = useState("");
  const plants = useQuery({
    queryKey: ["plants", supplier],
    queryFn: () => collect(`/api/v1/suppliers/${supplier}/plants`),
    enabled: !!supplier,
  });
  const items = useOrderItems();
  return (
    <SaveForm
      title="Issue a purchase"
      path="/api/v1/purchases"
      prepare={(body) => {
        if (body.destination === "YARD") delete body.orderItemId;
        else delete body.yardId;
        return body;
      }}
    >
      <div className="form-grid">
        <Field label="Supplier">
          <select
            name="supplierId"
            required
            value={supplier}
            onChange={(event) => setSupplier(event.target.value)}
          >
            <Choices path="/api/v1/suppliers" empty="Select supplier" />
          </select>
        </Field>
        <Field label="Plant">
          <select name="plantId" required>
            <option value="">Select plant</option>
            {plants.data
              ?.filter((row) => row.active !== false)
              .map((row) => (
                <option key={row.id} value={row.id}>
                  {row.name}
                </option>
              ))}
          </select>
        </Field>
        <Field label="Material">
          <select name="materialId" required>
            <Choices path="/api/v1/materials" empty="Select linked material" />
          </select>
        </Field>
        <Field label="Destination">
          <select
            name="destination"
            value={destination}
            onChange={(event) => setDestination(event.target.value)}
          >
            <option value="YARD">Yard</option>
            <option value="DIRECT">Direct to order line</option>
          </select>
        </Field>
        {destination === "YARD" ? (
          <Field label="Yard">
            <select name="yardId" required>
              <Choices
                path="/api/v1/yards"
                empty="Select operating yard"
                filter={operatingYard}
                labelOf={yardLabel}
              />
            </select>
          </Field>
        ) : (
          <Field label="Sales order line">
            <select name="orderItemId" required>
              <option value="">Select line</option>
              {items.data?.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.label}
                </option>
              ))}
            </select>
          </Field>
        )}
        <Field label="Quantity (tonnes)">
          <input name="quantity" type="number" min="0.001" step="0.001" required />
        </Field>
        <Field label="Rate ₹ / tonne">
          <input name="rate" type="number" min="0" step="0.01" required />
        </Field>
        <Field label="Expected date">
          <input name="expectedDate" type="date" min={today()} required />
        </Field>
      </div>
      <Field label="Terms">
        <textarea name="terms" required maxLength={1000} defaultValue="Supplier pays royalty; customer pays loading/unloading" />
      </Field>
      <ErrorBox error={items.error || plants.error} />
    </SaveForm>
  );
}

export function Purchase() {
  const write = useAccess().write;
  const { id } = useParams();
  const q = useQuery({
    queryKey: ["purchase", id],
    queryFn: () => request<RecordData>(`/api/v1/purchases/${id}`),
  });
  const row = q.data;
  const open = String(val(row, "status")) === "OPEN";
  return (
    <>
      <Link className="back" to="/purchases">
        All purchases
      </Link>
      <ErrorBox error={q.error} />
      {row && (
        <>
          <Heading
            eyebrow={`PURCHASE / ${short(String(val(row, "id")))}`}
            title={String(val(row, "material_name", "materialName") ?? "Purchase")}
            description={`${val(row, "supplier_name", "supplierName") ?? ""} · ${money(Number(val(row, "subtotal") ?? 0))}`}
          />
          <div className="panel form-panel">
            <RecordView row={row} />
          </div>
          {write && open && (
            <>
              <SaveForm title="Record a receipt" path={`/api/v1/purchases/${id}/receipts`}>
                <Notice title="Reuse this receipt id if the request is retried.">
                  Accepted quantity enters yard stock only for a yard purchase. A difference between
                  loaded, received and accepted quantities needs a reason.
                </Notice>
                <IdField name="receiptId" caption="Receipt id" />
                <div className="form-grid">
                  <Field label="Received date">
                    <input name="receivedDate" type="date" max={today()} required />
                  </Field>
                  <Field label="Loaded tonnes">
                    <input name="loadedQuantity" type="number" min="0.001" step="0.001" required />
                  </Field>
                  <Field label="Received tonnes">
                    <input name="receivedQuantity" type="number" min="0.001" step="0.001" required />
                  </Field>
                  <Field label="Accepted tonnes">
                    <input name="acceptedQuantity" type="number" min="0" step="0.001" required />
                  </Field>
                  <Field label="Challan / weighbridge reference">
                    <input name="reference" maxLength={300} required />
                  </Field>
                </div>
                <Field label="Discrepancy reason">
                  <textarea name="discrepancyReason" maxLength={1000} />
                </Field>
              </SaveForm>
              <SaveForm title="Close the remaining balance" path={`/api/v1/purchases/${id}/close`}>
                <Field label="Reason">
                  <textarea name="reason" required maxLength={1000} />
                </Field>
              </SaveForm>
            </>
          )}
          {Array.isArray(row.receipts) && row.receipts.length > 0 && (
            <div className="panel form-panel">
              <h3>Receipts</h3>
              <p className="muted">
                A standalone receipt can be reversed after a different accounts user approves.
                Trip delivery receipts are corrected on the trip, not here. Reversal does not
                change customer billing.
              </p>
              {row.receipts.map((receipt: RecordData) => (
                <article className="site" key={String(receipt.id)}>
                  <RecordView row={receipt} />
                  {write && (
                    <SaveForm
                      title="Request reversal of this receipt"
                      path={`/api/v1/purchase-receipts/${receipt.id}/reversals`}
                    >
                      <IdField name="reversalId" caption="Reversal id" />
                      <Field label="Reason">
                        <textarea name="reason" required maxLength={1000} />
                      </Field>
                      <Field label="Evidence reference">
                        <input name="evidenceReference" required maxLength={500} />
                      </Field>
                    </SaveForm>
                  )}
                </article>
              ))}
            </div>
          )}
          {write && String(val(row, "destination")) === "YARD" && (
            <SaveForm title="Return accepted material to the supplier" path={`/api/v1/purchases/${id}/supplier-returns`}>
              <IdField name="returnId" caption="Return id" />
              <div className="form-grid">
                <Field label="Quantity (tonnes)">
                  <input name="quantity" type="number" min="0.001" step="0.001" required />
                </Field>
                <Field label="Date">
                  <input name="occurredOn" type="date" max={today()} required />
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

export function Reservations() {
  const write = useAccess().write;
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState<string>("");
  const { page, setPage, q, items, hasNext } = usePaged("reservations", "/api/v1/stock-reservations");
  const detail = useQuery({
    queryKey: ["reservation", selected],
    queryFn: () => request<RecordData>(`/api/v1/stock-reservations/${selected}`),
    enabled: !!selected,
  });
  const itemsQuery = useOrderItems();
  return (
    <>
      <Heading
        eyebrow="SUPPLY / RESERVATIONS"
        title="Hold yard stock for an order."
        description="Reserving changes the reserved quantity. Issuing reduces on-hand stock."
        action={
          write && (
            <button onClick={() => setCreate((value) => !value)}>
              <Plus size={16} /> Reserve stock
            </button>
          )
        }
      />
      <ErrorBox error={q.error || detail.error || itemsQuery.error} />
      {create && (
        <SaveForm title="Reserve stock" path="/api/v1/stock-reservations">
          <div className="form-grid">
            <Field label="Yard">
              <select name="yardId" required>
                <Choices
                  path="/api/v1/yards"
                  empty="Select operating yard"
                  filter={operatingYard}
                  labelOf={yardLabel}
                />
              </select>
            </Field>
            <Field label="Order line">
              <select name="orderItemId" required>
                <option value="">Select line</option>
                {itemsQuery.data?.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Quantity (tonnes)">
              <input name="quantity" type="number" min="0.001" step="0.001" required />
            </Field>
          </div>
        </SaveForm>
      )}
      <div className="panel">
        <DataTable
          rows={items}
          onOpen={(row) => setSelected(String(row.id))}
          columns={[
            { label: "Quantity", keys: ["quantity"] },
            { label: "Issued", keys: ["issued_quantity", "issuedQuantity"] },
            { label: "Released", keys: ["released_quantity", "releasedQuantity"] },
            { label: "Order line", keys: ["order_item_id", "orderItemId"] },
          ]}
        />
        <Pager page={page} setPage={setPage} hasNext={hasNext} />
      </div>
      {detail.data && (
        <div className="panel form-panel">
          <RecordView row={detail.data} />
          {write && (
            <>
              <SaveForm title="Issue reserved stock" path={`/api/v1/stock-reservations/${selected}/issues`}>
                <IdField name="movementId" caption="Movement id" />
                <div className="form-grid">
                  <Field label="Quantity (tonnes)">
                    <input name="quantity" type="number" min="0.001" step="0.001" required />
                  </Field>
                  <Field label="Gate / dispatch reference">
                    <input name="reference" maxLength={300} required />
                  </Field>
                </div>
              </SaveForm>
              <SaveForm title="Release the unissued balance" path={`/api/v1/stock-reservations/${selected}/release`}>
                <Field label="Reason">
                  <textarea name="reason" required maxLength={1000} />
                </Field>
              </SaveForm>
            </>
          )}
        </div>
      )}
    </>
  );
}

export function StockControl() {
  const write = useAccess().write;
  const [tab, setTab] = useState("counts");
  return (
    <>
      <Heading
        eyebrow="SUPPLY / STOCK CONTROL"
        title="Correct stock only with a review."
        description="Operations submit. A different accounts user approves. Approval is what changes stock."
      />
      <div className="tabs">
        {[
          ["counts", "Counts"],
          ["openings", "Opening stock"],
          ["transfers", "Transfers"],
          ["reversals", "Reversals"],
          ["receipts", "Receipt reversals"],
          ["supplier", "Supplier returns"],
        ].map(([id, caption]) => (
          <button key={id} className={tab === id ? "tab active" : "tab"} onClick={() => setTab(id)}>
            {caption}
          </button>
        ))}
      </div>
      {tab === "counts" && (
        <Queue
          listKey="adjustments"
          path="/api/v1/stock-adjustments"
          detail={(id) => `/api/v1/stock-adjustments/${id}`}
          approve={(id) => `/api/v1/stock-adjustments/${id}/approve`}
          reject={(id) => `/api/v1/stock-adjustments/${id}/reject`}
          columns={[
            { label: "Kind", keys: ["kind"] },
            { label: "Quantity", keys: ["quantity"] },
            { label: "Status", keys: ["status"], status: true },
            { label: "Reason", keys: ["reason"] },
          ]}
          form={
            write && (
              <SaveForm title="Physical count correction" path="/api/v1/stock-adjustments">
                <IdField name="adjustmentId" caption="Adjustment id" />
                <div className="form-grid">
                  <Field label="Kind">
                    <select name="kind">
                      <option value="INCREASE">Increase</option>
                      <option value="DECREASE">Decrease</option>
                    </select>
                  </Field>
                  <Field label="Yard">
                    <select name="yardId" required>
                      <Choices path="/api/v1/yards" empty="Select yard" labelOf={yardLabel} />
                    </select>
                  </Field>
                  <Field label="Material">
                    <select name="materialId" required>
                      <Choices path="/api/v1/materials" empty="Select material" />
                    </select>
                  </Field>
                  <Field label="Delta tonnes">
                    <input name="quantity" type="number" min="0.001" step="0.001" required />
                  </Field>
                  <Field label="Expected on-hand tonnes">
                    <input name="expectedOnHand" type="number" min="0" step="0.001" required />
                  </Field>
                  <Field label="Count date">
                    <input name="occurredOn" type="date" max={today()} required />
                  </Field>
                </div>
                <Field label="Reason">
                  <textarea name="reason" required maxLength={1000} />
                </Field>
                <Field label="Evidence reference">
                  <input name="evidenceReference" required maxLength={500} />
                </Field>
              </SaveForm>
            )
          }
        />
      )}
      {tab === "openings" && (
        <Queue
          listKey="openings"
          path="/api/v1/stock-openings"
          detail={(id) => `/api/v1/stock-openings/${id}`}
          approve={(id) => `/api/v1/stock-openings/${id}/approve`}
          reject={(id) => `/api/v1/stock-openings/${id}/reject`}
          columns={[
            { label: "Quantity", keys: ["quantity"] },
            { label: "Status", keys: ["status"], status: true },
            { label: "Date", keys: ["occurred_on", "occurredOn"] },
          ]}
          form={
            write && (
              <SaveForm title="Opening quantity" path="/api/v1/stock-openings">
                <Notice title="Use this only when on-hand is zero.">
                  Opening stock is a quantity, not a valued accounting balance.
                </Notice>
                <IdField name="openingId" caption="Opening id" />
                <div className="form-grid">
                  <Field label="Yard">
                    <select name="yardId" required>
                      <Choices path="/api/v1/yards" empty="Select yard" labelOf={yardLabel} />
                    </select>
                  </Field>
                  <Field label="Material">
                    <select name="materialId" required>
                      <Choices path="/api/v1/materials" empty="Select material" />
                    </select>
                  </Field>
                  <Field label="Quantity">
                    <input name="quantity" type="number" min="0.001" step="0.001" required />
                  </Field>
                  <Field label="Date">
                    <input name="occurredOn" type="date" max={today()} required />
                  </Field>
                </div>
                <Evidence />
              </SaveForm>
            )
          }
        />
      )}
      {tab === "transfers" && (
        <Queue
          listKey="transfers"
          path="/api/v1/stock-transfers"
          detail={(id) => `/api/v1/stock-transfers/${id}`}
          approve={(id) => `/api/v1/stock-transfers/${id}/approve`}
          reject={(id) => `/api/v1/stock-transfers/${id}/reject`}
          columns={[
            { label: "Quantity", keys: ["quantity"] },
            { label: "Status", keys: ["status"], status: true },
            { label: "Date", keys: ["occurred_on", "occurredOn"] },
          ]}
          form={
            write && (
              <SaveForm title="Transfer quantity" path="/api/v1/stock-transfers">
                <Notice title="This moves quantity only.">
                  Operating yards may transfer across branches. A transfer that uses a quarantine
                  yard must stay in the same branch. No journal is posted.
                </Notice>
                <IdField name="transferId" caption="Transfer id" />
                <div className="form-grid">
                  <Field label="From yard">
                    <select name="fromYardId" required>
                      <Choices path="/api/v1/yards" empty="Select yard" labelOf={yardLabel} />
                    </select>
                  </Field>
                  <Field label="To yard">
                    <select name="toYardId" required>
                      <Choices path="/api/v1/yards" empty="Select yard" labelOf={yardLabel} />
                    </select>
                  </Field>
                  <Field label="Material">
                    <select name="materialId" required>
                      <Choices path="/api/v1/materials" empty="Select material" />
                    </select>
                  </Field>
                  <Field label="Quantity">
                    <input name="quantity" type="number" min="0.001" step="0.001" required />
                  </Field>
                  <Field label="Date">
                    <input name="occurredOn" type="date" max={today()} required />
                  </Field>
                </div>
                <Evidence />
              </SaveForm>
            )
          }
        />
      )}
      {tab === "reversals" && (
        <Queue
          listKey="reversals"
          path="/api/v1/stock-reversals"
          detail={(id) => `/api/v1/stock-reversals/${id}`}
          approve={(id) => `/api/v1/stock-reversals/${id}/approve`}
          reject={(id) => `/api/v1/stock-reversals/${id}/reject`}
          columns={[
            { label: "Status", keys: ["status"], status: true },
            { label: "Reason", keys: ["reason"] },
          ]}
        />
      )}
      {tab === "receipts" && (
        <Queue
          listKey="receipt-reversals"
          path="/api/v1/purchase-receipt-reversals"
          detail={(id) => `/api/v1/purchase-receipt-reversals/${id}`}
          approve={(id) => `/api/v1/purchase-receipt-reversals/${id}/approve`}
          reject={(id) => `/api/v1/purchase-receipt-reversals/${id}/reject`}
          columns={[
            { label: "Status", keys: ["status"], status: true },
            { label: "Reason", keys: ["reason"] },
          ]}
        />
      )}
      {tab === "supplier" && (
        <Queue
          listKey="supplier-returns"
          path="/api/v1/supplier-returns"
          detail={(id) => `/api/v1/supplier-returns/${id}`}
          approve={(id) => `/api/v1/supplier-returns/${id}/approve`}
          reject={(id) => `/api/v1/supplier-returns/${id}/reject`}
          columns={[
            { label: "Quantity", keys: ["quantity"] },
            { label: "Status", keys: ["status"], status: true },
            { label: "Date", keys: ["occurred_on", "occurredOn"] },
          ]}
        />
      )}
      <StockLookup />
    </>
  );
}

function Evidence() {
  return (
    <>
      <Field label="Reason">
        <textarea name="reason" required maxLength={1000} />
      </Field>
      <Field label="Evidence reference">
        <input name="evidenceReference" required maxLength={500} />
      </Field>
      <p className="muted">
        Uploading a file does not fill this field. If you uploaded one first, you can type
        document: and its id.
      </p>
    </>
  );
}

const DOCUMENTS: Partial<
  Record<string, "STOCK_ADJUSTMENT" | "STOCK_TRANSFER" | "STOCK_OPENING">
> = {
  adjustments: "STOCK_ADJUSTMENT",
  openings: "STOCK_OPENING",
  transfers: "STOCK_TRANSFER",
};

function Queue({
  listKey,
  path,
  detail,
  approve,
  reject,
  columns,
  form,
}: {
  listKey: string;
  path: string;
  detail: (id: string) => string;
  approve: (id: string) => string;
  reject: (id: string) => string;
  columns: { label: string; keys: string[]; status?: boolean }[];
  form?: ReactNode;
}) {
  const write = useAccess().write;
  const [selected, setSelected] = useState("");
  const list = usePaged(listKey, path);
  const one = useQuery({
    queryKey: [listKey, selected],
    queryFn: () => request<RecordData>(detail(selected)),
    enabled: !!selected,
  });
  const pending = String(val(one.data, "status")) === "PENDING";
  return (
    <>
      {form}
      <ErrorBox error={list.q.error || one.error} />
      <div className="panel">
        <DataTable rows={list.items} columns={columns} onOpen={(row) => setSelected(String(row.id))} />
        <Pager page={list.page} setPage={list.setPage} hasNext={list.hasNext} />
      </div>
      {one.data && (
        <div className="panel form-panel">
          <RecordView row={one.data} />
          {pending && (
            <ReviewBox approve={approve(selected)} reject={reject(selected)} row={one.data} />
          )}
          {write && listKey === "adjustments" && String(val(one.data, "status")) === "APPROVED" && (
            <SaveForm title="Request a reversal" path={`/api/v1/stock-adjustments/${selected}/reversals`}>
              <IdField name="reversalId" caption="Reversal id" />
              <Evidence />
            </SaveForm>
          )}
          {selected && DOCUMENTS[listKey] && (
            <Documents targetType={DOCUMENTS[listKey]} targetId={selected} />
          )}
        </div>
      )}
    </>
  );
}
