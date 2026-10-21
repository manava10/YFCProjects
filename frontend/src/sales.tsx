import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowUpRight, Check, Plus } from "lucide-react";
import { all, post, request, type RecordData } from "./api";
import { label, money, short, today } from "./lib";
import {
  Back,
  Choices,
  ErrorBox,
  Facts,
  Field,
  Heading,
  Notice,
  Pager,
  SaveForm,
  Status,
  useAccess,
  usePaged,
} from "./ui";

export function Sales() {
  const write = useAccess().write;
  const { page, setPage, q, items, total, hasNext } = usePaged("rfqs", "/api/v1/rfqs");
  const customers = useQuery({
    queryKey: ["directory", "/api/v1/customers"],
    queryFn: () => all("/api/v1/customers"),
  });
  return (
    <>
      <Heading
        eyebrow="SALES / ENQUIRIES"
        title="Start with the requirement."
        description="Follow each enquiry from quotation to confirmed order."
        action={
          write && (
            <Link className="button" to="/sales/new">
              <Plus size={17} /> New enquiry
            </Link>
          )
        }
      />
      <ErrorBox error={q.error || customers.error} />
      <div className="panel">
        <div className="panel-heading">
          <h3>Customer enquiries</h3>
          <span className="muted">Latest first</span>
        </div>
        {q.isPending ? (
          <p className="empty">Loading enquiries…</p>
        ) : items.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Enquiry / customer</th>
                  <th>Requirement</th>
                  <th>Required by</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((row) => (
                  <tr key={row.id}>
                    <td>
                      <Link to={`/sales/${row.id}`}>ENQ-{short(row.id)}</Link>
                      <small>
                        {customers.data?.find((c) => c.id === row.customerId)?.name ??
                          short(row.customerId)}
                      </small>
                    </td>
                    <td>
                      {row.items.map((item: RecordData) => item.material).join(", ")}
                      <small>{label(row.businessType)}</small>
                    </td>
                    <td>{row.requiredDate}</td>
                    <td>
                      <Status value={row.status} />
                    </td>
                    <td>
                      <Link aria-label="Open enquiry" to={`/sales/${row.id}`}>
                        <ArrowUpRight size={18} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <h3>No enquiries yet</h3>
          </div>
        )}
        <Pager page={page} setPage={setPage} total={total} hasNext={hasNext} />
      </div>
    </>
  );
}

export function NewEnquiry() {
  const nav = useNavigate();
  const client = useQueryClient();
  const [customer, setCustomer] = useState("");
  const [project, setProject] = useState("");
  const [lines, setLines] = useState([{ materialId: "", quantity: "" }]);
  const customers = useQuery({
    queryKey: ["directory", "/api/v1/customers"],
    queryFn: () => all("/api/v1/customers"),
  });
  const materials = useQuery({
    queryKey: ["directory", "/api/v1/materials"],
    queryFn: () => all("/api/v1/materials"),
  });
  const projects = useQuery({
    queryKey: ["projects", customer],
    queryFn: () => all(`/api/v1/customers/${customer}/projects`),
    enabled: !!customer,
  });
  const sites = useQuery({
    queryKey: ["sites", customer, project],
    queryFn: () => all(`/api/v1/customers/${customer}/projects/${project}/sites`),
    enabled: !!project,
  });
  const mutation = useMutation({
    mutationFn: (body: unknown) => post("/api/v1/rfqs", body),
    onSuccess: (row) => {
      client.invalidateQueries({ queryKey: ["rfqs"] });
      nav(`/sales/${row.id}`);
    },
  });
  return (
    <>
      <Back to="/sales">All enquiries</Back>
      <Heading
        eyebrow="SALES / NEW ENQUIRY"
        title="What does your customer need?"
        description="Capture the delivery location and material quantities in tonnes."
      />
      <form
        className="panel form-panel"
        onSubmit={(event) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          mutation.mutate({
            customerId: customer,
            deliverySiteId: form.get("deliverySiteId"),
            businessType: form.get("businessType"),
            requiredDate: form.get("requiredDate"),
            remarks: form.get("remarks"),
            items: lines,
          });
        }}
      >
        <ErrorBox
          error={
            mutation.error ||
            customers.error ||
            materials.error ||
            projects.error ||
            sites.error
          }
        />
        <h3>01 · Customer & delivery</h3>
        <div className="form-grid">
          <Field label="Customer">
            <select
              required
              value={customer}
              onChange={(event) => {
                setCustomer(event.target.value);
                setProject("");
              }}
            >
              <option value="">Select customer</option>
              {customers.data?.map((row) => (
                <option key={row.id} value={row.id}>
                  {row.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Project">
            <select required value={project} onChange={(event) => setProject(event.target.value)}>
              <option value="">Select active project</option>
              {projects.data
                ?.filter((row) => row.active)
                .map((row) => (
                  <option key={row.id} value={row.id}>
                    {row.name}
                  </option>
                ))}
            </select>
          </Field>
          <Field label="Delivery site">
            <select name="deliverySiteId" required key={project}>
              <option value="">Select active site</option>
              {sites.data
                ?.filter((row) => row.active)
                .map((row) => (
                  <option key={row.id} value={row.id}>
                    {row.name} · {row.address}
                  </option>
                ))}
            </select>
          </Field>
          <Field label="Required date">
            <input name="requiredDate" type="date" min={today()} required />
          </Field>
          <Field label="Business type">
            <select name="businessType">
              <option value="MATERIAL_AND_TRANSPORT">Material & transport</option>
              <option value="MATERIAL">Material only</option>
              <option value="TRANSPORT">Transport only</option>
            </select>
          </Field>
        </div>
        <h3>02 · Material requirements</h3>
        {lines.map((line, index) => (
          <div className="line-input" key={index}>
            <Field label={`Material ${index + 1}`}>
              <select
                required
                value={line.materialId}
                onChange={(event) =>
                  setLines(
                    lines.map((item, itemIndex) =>
                      itemIndex === index ? { ...item, materialId: event.target.value } : item,
                    ),
                  )
                }
              >
                <option value="">Select material</option>
                {materials.data
                  ?.filter(
                    (material) =>
                      material.active &&
                      !lines.some(
                        (item, itemIndex) =>
                          itemIndex !== index && item.materialId === material.id,
                      ),
                  )
                  .map((material) => (
                    <option key={material.id} value={material.id}>
                      {material.name}
                    </option>
                  ))}
              </select>
            </Field>
            <Field label="Quantity (tonnes)">
              <input
                type="number"
                min="0.001"
                max="999999999.999"
                step="0.001"
                required
                value={line.quantity}
                onChange={(event) =>
                  setLines(
                    lines.map((item, itemIndex) =>
                      itemIndex === index ? { ...item, quantity: event.target.value } : item,
                    ),
                  )
                }
              />
            </Field>
            <button
              type="button"
              className="secondary"
              disabled={lines.length === 1}
              onClick={() => setLines(lines.filter((_, itemIndex) => itemIndex !== index))}
            >
              Remove
            </button>
          </div>
        ))}
        <button
          type="button"
          className="secondary"
          disabled={lines.length >= 100}
          onClick={() => setLines([...lines, { materialId: "", quantity: "" }])}
        >
          <Plus size={16} /> Add material
        </button>
        <Field label="Remarks">
          <textarea name="remarks" maxLength={2000} rows={3} />
        </Field>
        <div className="form-end">
          <span>Requirements are saved as a permanent snapshot.</span>
          <button disabled={mutation.isPending}>
            {mutation.isPending ? "Saving…" : "Create enquiry"}
            <ArrowUpRight size={16} />
          </button>
        </div>
      </form>
    </>
  );
}

export function Enquiry() {
  const write = useAccess().write;
  const { id } = useParams();
  const client = useQueryClient();
  const nav = useNavigate();
  const [quoteForm, setQuoteForm] = useState(false);
  const [decision, setDecision] = useState<{ id: string; action: string } | null>(null);
  const q = useQuery({
    queryKey: ["rfq", id],
    queryFn: () => request<RecordData>(`/api/v1/rfqs/${id}`),
  });
  const history = useQuery({
    queryKey: ["quotes", id],
    queryFn: () => request<RecordData[]>(`/api/v1/rfqs/${id}/quotations`),
  });
  const mutation = useMutation({
    mutationFn: ({ path, body }: { path: string; body?: unknown }) => post(path, body),
    onSuccess: (row) => {
      client.invalidateQueries({ queryKey: ["rfq", id] });
      client.invalidateQueries({ queryKey: ["quotes", id] });
      client.invalidateQueries({ queryKey: ["rfqs"] });
      client.invalidateQueries({ queryKey: ["orders"] });
      setQuoteForm(false);
      setDecision(null);
      if (row.orderId) nav(`/orders/${row.orderId}`);
    },
  });
  const row = q.data;
  return (
    <>
      <Back to="/sales">All enquiries</Back>
      <ErrorBox error={q.error || history.error || mutation.error} />
      {q.isPending ? (
        <p>Loading enquiry…</p>
      ) : (
        row && (
          <>
            <Heading
              eyebrow={`ENQUIRY / ${short(row.id)}`}
              title={row.items.map((item: RecordData) => item.material).join(" + ")}
              description={`Required ${row.requiredDate} · ${row.deliveryAddress}`}
              action={<Status value={row.status} />}
            />
            <div className="panel summary">
              <div>
                <small>BUSINESS TYPE</small>
                <strong>{label(row.businessType)}</strong>
              </div>
              <div>
                <small>ORDERED-QUANTITY BILLING</small>
                <strong>Quantities agreed at order</strong>
              </div>
              <div>
                <small>REMARKS</small>
                <strong>{row.remarks || "No remarks"}</strong>
              </div>
            </div>
            <div className="panel">
              <div className="panel-heading">
                <h3>Quotation history</h3>
                {write && !["CONVERTED", "ACCEPTED"].includes(row.status) && (
                  <button className="secondary" onClick={() => setQuoteForm(!quoteForm)}>
                    <Plus size={16} /> New revision
                  </button>
                )}
              </div>
              {quoteForm && (
                <form
                  className="form-panel"
                  onSubmit={(event) => {
                    event.preventDefault();
                    const form = new FormData(event.currentTarget);
                    mutation.mutate({
                      path: `/api/v1/rfqs/${id}/quotations`,
                      body: {
                        validUntil: form.get("validUntil"),
                        paymentTerms: form.get("paymentTerms"),
                        deliveryTerms: form.get("deliveryTerms"),
                        items: row.items.map((item: RecordData) => ({
                          rfqItemId: item.id,
                          materialRate:
                            row.businessType === "TRANSPORT"
                              ? "0"
                              : String(form.get(`material-${item.id}`) ?? ""),
                          transportRate:
                            row.businessType === "MATERIAL"
                              ? "0"
                              : String(form.get(`transport-${item.id}`) ?? ""),
                        })),
                      },
                    });
                  }}
                >
                  <Field label="Valid until">
                    <input required name="validUntil" type="date" min={today()} />
                  </Field>
                  {row.items.map((item: RecordData) => (
                    <div className="line-input" key={item.id}>
                      <strong>
                        {item.material}
                        <small>{item.quantity} tonnes</small>
                      </strong>
                      <Field label="Material ₹ / tonne">
                        <input
                          name={`material-${item.id}`}
                          type="number"
                          min="0"
                          max="9999999999.99"
                          step="0.01"
                          required
                          disabled={row.businessType === "TRANSPORT"}
                        />
                      </Field>
                      <Field label="Transport ₹ / tonne">
                        <input
                          name={`transport-${item.id}`}
                          type="number"
                          min="0"
                          max="9999999999.99"
                          step="0.01"
                          required
                          disabled={row.businessType === "MATERIAL"}
                        />
                      </Field>
                    </div>
                  ))}
                  <Field label="Payment terms">
                    <textarea name="paymentTerms" required maxLength={1000} />
                  </Field>
                  <Field label="Delivery terms">
                    <textarea
                      name="deliveryTerms"
                      required
                      maxLength={1000}
                      defaultValue="Customer pays loading/unloading; supplier pays royalty."
                    />
                  </Field>
                  <button disabled={mutation.isPending}>Save quotation revision</button>
                </form>
              )}
              {history.data?.length === 0 && (
                <div className="empty">
                  <h3>Ready for your first quotation</h3>
                </div>
              )}
              {history.data?.map((quote, index) => (
                <article className="quotation" key={quote.id}>
                  <div className="quote-heading">
                    <div>
                      <span className="eyebrow">REVISION {quote.revision}</span>
                      <h2>{money(quote.subtotal)}</h2>
                      <small>Subtotal excluding GST · valid through {quote.validUntil}</small>
                    </div>
                    <Status value={quote.expired && quote.status === "SENT" ? "EXPIRED" : quote.status} />
                  </div>
                  <div className="table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Material</th>
                          <th>Tonnes</th>
                          <th>Material / t</th>
                          <th>Transport / t</th>
                          <th>Subtotal</th>
                        </tr>
                      </thead>
                      <tbody>
                        {quote.items.map((item: RecordData) => (
                          <tr key={item.rfqItemId}>
                            <td>{item.material}</td>
                            <td>{item.quantity}</td>
                            <td>{money(item.materialRate)}</td>
                            <td>{money(item.transportRate)}</td>
                            <td>{money(item.subtotal)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <p>
                    <strong>Payment:</strong> {quote.paymentTerms}
                  </p>
                  <p>
                    <strong>Delivery:</strong> {quote.deliveryTerms}
                  </p>
                  {quote.approvalReference && (
                    <p>
                      Customer decision: {quote.customerContact} · {quote.approvalReference} ·{" "}
                      {quote.decisionNote}
                    </p>
                  )}
                  <div className="actions">
                    {write && quote.status === "DRAFT" && index === 0 && (
                      <button
                        disabled={mutation.isPending}
                        onClick={() => mutation.mutate({ path: `/api/v1/quotations/${quote.id}/send` })}
                      >
                        Record as sent
                      </button>
                    )}
                    {write && quote.status === "SENT" && !quote.expired && (
                      <>
                        <button
                          disabled={mutation.isPending}
                          onClick={() => setDecision({ id: quote.id, action: "accept" })}
                        >
                          <Check size={16} /> Record acceptance
                        </button>
                        <button
                          className="secondary"
                          disabled={mutation.isPending}
                          onClick={() => setDecision({ id: quote.id, action: "reject" })}
                        >
                          Record rejection
                        </button>
                      </>
                    )}
                    {write && quote.status === "ACCEPTED" && !quote.convertedOrderId && (
                      <button
                        disabled={mutation.isPending}
                        onClick={() =>
                          mutation.mutate({ path: `/api/v1/quotations/${quote.id}/convert` })
                        }
                      >
                        Convert to order <ArrowUpRight size={16} />
                      </button>
                    )}
                    {quote.convertedOrderId && (
                      <Link className="button secondary" to={`/orders/${quote.convertedOrderId}`}>
                        View confirmed order
                      </Link>
                    )}
                  </div>
                  {decision?.id === quote.id && (
                    <form
                      className="decision"
                      onSubmit={(event) => {
                        event.preventDefault();
                        mutation.mutate({
                          path: `/api/v1/quotations/${quote.id}/${decision.action}`,
                          body: Object.fromEntries(new FormData(event.currentTarget)),
                        });
                      }}
                    >
                      <h3>
                        Record customer {decision.action === "accept" ? "acceptance" : "rejection"}
                      </h3>
                      <Field label="Customer contact">
                        <input name="customerContact" maxLength={160} required />
                      </Field>
                      <Field label="Customer PO / email / call reference">
                        <input name="approvalReference" maxLength={300} required />
                      </Field>
                      <Field label={decision.action === "accept" ? "Note" : "Reason"}>
                        <textarea
                          name={decision.action === "accept" ? "note" : "reason"}
                          required={decision.action === "reject"}
                          maxLength={1000}
                        />
                      </Field>
                      <div className="actions">
                        <button disabled={mutation.isPending}>Save decision</button>
                        <button type="button" className="secondary" onClick={() => setDecision(null)}>
                          Cancel
                        </button>
                      </div>
                    </form>
                  )}
                </article>
              ))}
            </div>
            <p className="muted">
              “Record as sent” records an external staff action. It does not send a message to the
              customer.
            </p>
          </>
        )
      )}
    </>
  );
}

export function Orders() {
  const write = useAccess().write;
  const [open, setOpen] = useState(false);
  const [kind, setKind] = useState("MATERIAL_AND_TRANSPORT");
  const { page, setPage, q, items, total, hasNext } = usePaged("orders", "/api/v1/orders");
  return (
    <>
      <Heading
        eyebrow="SALES / CONFIRMED ORDERS"
        title="Agreed. Recorded. Ready."
        description="Commercial commitments and delivery quantities, kept separate."
        action={
          write && (
            <button onClick={() => setOpen((value) => !value)}>
              <Plus size={16} /> Manual order
            </button>
          )
        }
      />
      <Notice title="Billing follows the ordered quantity.">
        Loaded and received weights describe execution. They do not change the billable quantity.
        Subtotals exclude tax. A manual order is not a quotation acceptance.
      </Notice>
      {open && (
        <SaveForm title="Manual order" path="/api/v1/orders">
          <div className="form-grid">
            <Field label="Customer">
              <select name="customerId" required>
                <Choices path="/api/v1/customers" empty="Select customer" />
              </select>
            </Field>
            <Field label="Business type">
              <select
                name="businessType"
                required
                value={kind}
                onChange={(event) => setKind(event.target.value)}
              >
                <option value="MATERIAL_AND_TRANSPORT">Material & transport</option>
                <option value="MATERIAL">Material only</option>
                <option value="TRANSPORT">Transport only</option>
              </select>
            </Field>
            <Field label="Material description">
              <input name="material" maxLength={160} required />
            </Field>
            <Field label="Delivery site description">
              <input name="deliverySite" maxLength={500} required />
            </Field>
            <Field label="Ordered tonnes">
              <input name="orderedQuantity" type="number" min="0.001" step="0.001" required />
            </Field>
            {kind === "TRANSPORT" ? (
              <input type="hidden" name="materialRate" value="0" />
            ) : (
              <Field label="Material rate ₹ / tonne">
                <input name="materialRate" type="number" min="0" step="0.01" required />
              </Field>
            )}
            {kind === "MATERIAL" ? (
              <input type="hidden" name="transportRate" value="0" />
            ) : (
              <Field label="Transport rate ₹ / tonne">
                <input name="transportRate" type="number" min="0" step="0.01" required />
              </Field>
            )}
            <Field label="Branch (optional)">
              <select name="branchId">
                <Choices path="/api/v1/branches" empty="No saved branch" />
              </select>
            </Field>
            <Field label="Saved material (optional)">
              <select name="materialId">
                <Choices path="/api/v1/materials" empty="Free-text material" />
              </select>
            </Field>
          </div>
        </SaveForm>
      )}
      <ErrorBox error={q.error} />
      <div className="panel">
        {q.isPending ? (
          <p className="empty">Loading orders…</p>
        ) : items.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Material</th>
                  <th>Ordered tonnes</th>
                  <th>Loaded</th>
                  <th>Received</th>
                  <th>Subtotal</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((order) => (
                  <tr key={order.id}>
                    <td>
                      <Link to={`/orders/${order.id}`}>ORD-{short(order.id)}</Link>
                      <small>{label(order.businessType)}</small>
                    </td>
                    <td>{order.material}</td>
                    <td>{order.orderedQuantity}</td>
                    <td>{order.loadedQuantity}</td>
                    <td>{order.receivedQuantity}</td>
                    <td>{money(order.subtotal)}</td>
                    <td>
                      <Link to={`/orders/${order.id}`}>
                        <ArrowUpRight size={18} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <h3>No confirmed orders yet</h3>
          </div>
        )}
        <Pager page={page} setPage={setPage} total={total} hasNext={hasNext} />
      </div>
    </>
  );
}

export function Order() {
  const write = useAccess().write;
  const { id } = useParams();
  const q = useQuery({
    queryKey: ["order", id],
    queryFn: () => request<RecordData>(`/api/v1/orders/${id}`),
  });
  const order = q.data;
  return (
    <>
      <Back to="/orders">All orders</Back>
      <ErrorBox error={q.error} />
      {q.isPending ? (
        <p>Loading order…</p>
      ) : (
        order && (
          <>
            <Heading
              eyebrow={`ORDER / ${short(order.id)}`}
              title={order.material}
              description={order.deliverySite}
              action={<Status value={order.businessType} />}
            />
            <div className="panel summary">
              <div>
                <small>BILLABLE</small>
                <strong>{order.billableQuantity} t</strong>
              </div>
              <div>
                <small>SUBTOTAL EXCLUDING GST</small>
                <strong>{money(order.subtotal)}</strong>
              </div>
              <div>
                <small>REQUIRED</small>
                <strong>{order.requiredDate || "Not set"}</strong>
              </div>
            </div>
            <div className="panel table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Material</th>
                    <th>Ordered</th>
                    <th>Billable</th>
                    <th>Loaded</th>
                    <th>Received</th>
                    <th>Subtotal</th>
                  </tr>
                </thead>
                <tbody>
                  {order.items?.map((item: RecordData) => (
                    <tr key={item.id}>
                      <td>
                        {item.material}
                        <small>{short(item.id)}</small>
                      </td>
                      <td>{item.orderedQuantity} t</td>
                      <td>{item.billableQuantity} t</td>
                      <td>{item.loadedQuantity} t</td>
                      <td>{item.receivedQuantity} t</td>
                      <td>{money(item.subtotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {order.items?.map((item: RecordData) => (
              <Fulfillment key={item.id} item={item} />
            ))}
            {write && (
              <SaveForm title="Record a legacy weighbridge weight" path={`/api/v1/orders/${id}/dispatches`}>
                <Notice title="Use trips for new deliveries.">
                  This records a weight against the order. It does not allocate a truck or change
                  the billable quantity. An item already on the trip workflow rejects this entry.
                </Notice>
                {(order.items?.length ?? 0) > 1 && (
                  <Field label="Order line">
                    <select name="orderItemId" required>
                      <option value="">Select line</option>
                      {order.items.map((item: RecordData) => (
                        <option key={item.id} value={item.id}>
                          {item.material}
                        </option>
                      ))}
                    </select>
                  </Field>
                )}
                <div className="form-grid">
                  <Field label="Weighbridge reference">
                    <input name="reference" maxLength={30} required />
                  </Field>
                  <Field label="Loaded tonnes">
                    <input name="loadedQuantity" type="number" min="0.001" step="0.001" required />
                  </Field>
                  <Field label="Received tonnes">
                    <input name="receivedQuantity" type="number" min="0" step="0.001" required />
                  </Field>
                </div>
              </SaveForm>
            )}
            {order.paymentTerms && (
              <div className="panel form-panel">
                <p>
                  <strong>Payment:</strong> {order.paymentTerms}
                </p>
                <p>
                  <strong>Delivery:</strong> {order.deliveryTerms}
                </p>
              </div>
            )}
          </>
        )
      )}
    </>
  );
}

function Fulfillment({ item }: { item: RecordData }) {
  const q = useQuery({
    queryKey: ["fulfillment", item.id],
    queryFn: () => request<RecordData>(`/api/v1/order-items/${item.id}/fulfillment`),
    retry: false,
  });
  return (
    <div className="panel form-panel">
      <h3>{item.material} · sourcing</h3>
      <ErrorBox error={q.error} />
      {q.isPending ? <p>Loading fulfillment…</p> : <Facts row={q.data} />}
    </div>
  );
}
