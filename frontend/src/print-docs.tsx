import { Printer, Download } from "lucide-react";
import { type RecordData } from "./api";
import { money, short, text, val } from "./lib";

const ONES = [
  "",
  "One",
  "Two",
  "Three",
  "Four",
  "Five",
  "Six",
  "Seven",
  "Eight",
  "Nine",
  "Ten",
  "Eleven",
  "Twelve",
  "Thirteen",
  "Fourteen",
  "Fifteen",
  "Sixteen",
  "Seventeen",
  "Eighteen",
  "Nineteen",
];
const TENS = [
  "",
  "",
  "Twenty",
  "Thirty",
  "Forty",
  "Fifty",
  "Sixty",
  "Seventy",
  "Eighty",
  "Ninety",
];

function twoDigits(n: number): string {
  if (n < 20) return ONES[n];
  return `${TENS[Math.floor(n / 10)]}${n % 10 ? " " + ONES[n % 10] : ""}`;
}

function threeDigits(n: number): string {
  const h = Math.floor(n / 100);
  const rem = n % 100;
  return `${h ? ONES[h] + " Hundred" : ""}${h && rem ? " " : ""}${rem ? twoDigits(rem) : ""}`;
}

export function inrWords(amount: number): string {
  if (!Number.isFinite(amount) || amount < 0) return "";
  const rupees = Math.floor(amount);
  const paise = Math.round((amount - rupees) * 100);
  if (rupees === 0 && paise === 0) return "Rupees Zero Only";
  const parts: string[] = [];
  const crore = Math.floor(rupees / 10000000);
  const lakh = Math.floor((rupees % 10000000) / 100000);
  const thousand = Math.floor((rupees % 100000) / 1000);
  const rest = rupees % 1000;
  if (crore) parts.push(`${threeDigits(crore)} Crore`);
  if (lakh) parts.push(`${twoDigits(lakh)} Lakh`);
  if (thousand) parts.push(`${twoDigits(thousand)} Thousand`);
  if (rest) parts.push(threeDigits(rest));
  const rupeeText = parts.length ? `Rupees ${parts.join(" ")}` : "Rupees Zero";
  const paiseText = paise ? ` and ${twoDigits(paise)} Paise` : "";
  return `${rupeeText}${paiseText} Only`;
}

export function exportCsv(
  filename: string,
  headers: string[],
  rows: (string | number | null | undefined)[][],
) {
  const escape = (v: string | number | null | undefined) => {
    const s = v == null ? "" : String(v);
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const csv = [
    headers.map(escape).join(","),
    ...rows.map((r) => r.map(escape).join(",")),
  ].join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export function CsvExportButton({
  filename,
  headers,
  rows,
  label = "Export CSV",
}: {
  filename: string;
  headers: string[];
  rows: (string | number | null | undefined)[][];
  label?: string;
}) {
  return (
    <button
      type="button"
      className="secondary"
      disabled={rows.length === 0}
      onClick={() => exportCsv(filename, headers, rows)}
    >
      <Download size={14} /> {label}
    </button>
  );
}

export function PrintableInvoice({ bill }: { bill: RecordData }) {
  const lines = (bill.lines as RecordData[]) ?? [];
  const taxLines = (bill.tax_lines as RecordData[]) ?? [];
  const branch = (bill.branch as RecordData | undefined) ?? null;
  const sales = String(val(bill, "kind")) === "SALES_ORDER";
  const title = sales
    ? val(bill, "invoice_number")
      ? "TAX INVOICE"
      : "BILLING PREPARATION"
    : "SUPPLIER BILL";
  const payable = Number(val(bill, "payable_total", "subtotal") ?? 0);
  const supplyLabel =
    val(bill, "supply") === "INTER_STATE"
      ? "Inter-State (IGST)"
      : val(bill, "supply") === "INTRA_STATE"
        ? "Intra-State (CGST + SGST)"
        : "Pending Tax Assessment";
  return (
    <>
      <div className="no-print" style={{ marginBottom: 12 }}>
        <button
          type="button"
          className="secondary"
          onClick={() => window.print()}
        >
          <Printer size={14} /> Print / Save PDF
        </button>
      </div>
      <section className="print-sheet" aria-label="Printable invoice">
        <div className="print-header">
          <div>
            <h2>
              YFC TRANSPORT &amp; MATERIALS
              {branch?.name ? ` — ${String(branch.name)}` : ""}
            </h2>
            {branch ? (
              <>
                <p>
                  {text(branch.address)}, {text(branch.city)}, {text(branch.state)} —{" "}
                  {text(branch.pincode)}
                </p>
                {branch.gstin && (
                  <p>
                    <strong>GSTIN:</strong> {String(branch.gstin)}
                    {branch.state_code ? ` · State Code: ${String(branch.state_code)}` : ""}
                  </p>
                )}
              </>
            ) : (
              <p>Bulk Building Material Supply &amp; Owned-Fleet Logistics · Kotputli &amp; NCR</p>
            )}
          </div>
          <div className="print-doc-meta">
            <strong>{title}</strong>
            <div>
              No:{" "}
              {val(bill, "invoice_number")
                ? String(val(bill, "invoice_number"))
                : short(String(bill.id))}
            </div>
            <div>Date: {text(val(bill, "prepared_on"))}</div>
            <div>Supply: {supplyLabel}</div>
            <div>Status: {text(val(bill, "payment_status", "status"))}</div>
          </div>
        </div>
        <div className="print-party">
          <div>
            <small>{sales ? "Billed To (Customer)" : "Supplier"}</small>
            <strong>{text(val(bill, "party_name"))}</strong>
            {val(bill, "party_gstin") && (
              <div>
                GSTIN: <strong>{String(val(bill, "party_gstin"))}</strong>
                {val(bill, "party_state_code")
                  ? ` (State ${String(val(bill, "party_state_code"))})`
                  : ""}
              </div>
            )}
            {val(bill, "party_address") && <div>{String(val(bill, "party_address"))}</div>}
            {val(bill, "party_mobile") && <div>Phone: {String(val(bill, "party_mobile"))}</div>}
          </div>
          <div>
            <small>Reference / Basis</small>
            <div>
              {text(val(bill, "reference"))} · {text(val(bill, "quantity_basis"))}
            </div>
            {branch?.bank_account && (
              <div style={{ marginTop: 6 }}>
                <small>Settlement Bank Details</small>
                <div>
                  {text(branch.bank_name)} · A/c: <strong>{String(branch.bank_account)}</strong>
                  {branch.bank_ifsc ? ` · IFSC: ${String(branch.bank_ifsc)}` : ""}
                </div>
              </div>
            )}
          </div>
        </div>
        <table className="print-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Material / Service</th>
              <th>Qty (t)</th>
              <th>Material Rate</th>
              <th>Freight Rate</th>
              <th>Amount (INR)</th>
            </tr>
          </thead>
          <tbody>
            {lines.map((line, i) => {
              const lineTotal =
                Number(val(line, "material_amount") ?? 0) +
                Number(val(line, "transport_amount") ?? 0);
              return (
                <tr key={String(line.id ?? i)}>
                  <td>{String(val(line, "position") ?? i + 1)}</td>
                  <td>{text(val(line, "material"))}</td>
                  <td>{text(val(line, "quantity"))}</td>
                  <td>{money(val(line, "material_rate"))}</td>
                  <td>{money(val(line, "transport_rate"))}</td>
                  <td>{money(lineTotal)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {taxLines.length > 0 && (
          <table className="print-table">
            <thead>
              <tr>
                <th>HSN / SAC</th>
                <th>Component</th>
                <th>GST %</th>
                <th>Taxable (INR)</th>
                <th>CGST</th>
                <th>SGST</th>
                <th>IGST</th>
              </tr>
            </thead>
            <tbody>
              {taxLines.map((tl, i) => (
                <tr key={`${tl.position}-${tl.component}-${i}`}>
                  <td>{text(val(tl, "hsn_or_sac"))}</td>
                  <td>{text(val(tl, "component"))}</td>
                  <td>{text(val(tl, "rate_percent"))}%</td>
                  <td>{money(val(tl, "taxable_amount"))}</td>
                  <td>{money(val(tl, "cgst_amount"))}</td>
                  <td>{money(val(tl, "sgst_amount"))}</td>
                  <td>{money(val(tl, "igst_amount"))}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <div className="print-totals">
          <div>
            <span>Taxable Subtotal</span>
            <strong>{money(val(bill, "taxable_amount", "subtotal"))}</strong>
          </div>
          {Number(val(bill, "cgst_amount") ?? 0) > 0 && (
            <div>
              <span>CGST</span>
              <strong>{money(val(bill, "cgst_amount"))}</strong>
            </div>
          )}
          {Number(val(bill, "sgst_amount") ?? 0) > 0 && (
            <div>
              <span>SGST</span>
              <strong>{money(val(bill, "sgst_amount"))}</strong>
            </div>
          )}
          {Number(val(bill, "igst_amount") ?? 0) > 0 && (
            <div>
              <span>IGST</span>
              <strong>{money(val(bill, "igst_amount"))}</strong>
            </div>
          )}
          <div className="print-grand">
            <span>Payable Total</span>
            <strong>{money(payable)}</strong>
          </div>
          {val(bill, "allocated_amount") != null && (
            <div>
              <span>Paid / Outstanding</span>
              <strong>
                {money(val(bill, "allocated_amount"))} /{" "}
                {money(val(bill, "outstanding_amount"))}
              </strong>
            </div>
          )}
        </div>
        <p className="print-words">
          <strong>Amount in words:</strong> {inrWords(payable)}
        </p>
        <div className="print-signatures">
          <div>
            <span>Prepared By</span>
            <p>{text(val(bill, "created_by"))}</p>
          </div>
          <div>
            <span>Authorised Signatory (YFC Transport)</span>
            <p>__________________________</p>
          </div>
        </div>
      </section>
    </>
  );
}

export function PrintableChallan({ trip }: { trip: RecordData }) {
  const ewayNo = val(trip, "eway_bill_number", "ewayBillNumber");
  const ewayValid = val(trip, "eway_bill_valid_until", "ewayBillValidUntil");
  const branch = (trip.branch as RecordData | undefined) ?? null;
  const material = val(trip, "material");
  const customerName = val(trip, "customer_name");
  const customerGstin = val(trip, "customer_gstin");
  const deliverySite = val(trip, "delivery_site");
  const loadingSlip = val(trip, "loading_slip");
  const deliverySlip = val(trip, "delivery_slip");
  const receiverName = val(trip, "receiver_name");
  return (
    <>
      <div className="no-print" style={{ marginBottom: 12 }}>
        <button
          type="button"
          className="secondary"
          onClick={() => window.print()}
        >
          <Printer size={14} /> Print Delivery Challan
        </button>
      </div>
      <section className="print-sheet" aria-label="Printable delivery challan">
        <div className="print-header">
          <div>
            <h2>
              YFC TRANSPORT &amp; MATERIALS
              {branch?.name ? ` — ${String(branch.name)}` : ""}
            </h2>
            {branch ? (
              <>
                <p>
                  {text(branch.address)}, {text(branch.city)}, {text(branch.state)} —{" "}
                  {text(branch.pincode)}
                </p>
                {branch.gstin && (
                  <p>
                    <strong>GSTIN:</strong> {String(branch.gstin)}
                    {branch.state_code ? ` · State Code: ${String(branch.state_code)}` : ""}
                  </p>
                )}
              </>
            ) : (
              <p>Owned-Fleet Dispatch &amp; Delivery Challan</p>
            )}
          </div>
          <div className="print-doc-meta">
            <strong>DELIVERY CHALLAN</strong>
            <div>Trip: {short(String(trip.id))}</div>
            <div>Date: {text(val(trip, "planned_date"))}</div>
            <div>Status: {text(val(trip, "status"))}</div>
            {ewayNo && <div>E-Way Bill: {String(ewayNo)}</div>}
          </div>
        </div>
        {(customerName || material || deliverySite) && (
          <div className="print-party">
            <div>
              <small>Consignee / Customer</small>
              <strong>{text(customerName)}</strong>
              {customerGstin && <div>GSTIN: {String(customerGstin)}</div>}
              {deliverySite && <div>Site: {String(deliverySite)}</div>}
            </div>
            <div>
              <small>Material &amp; Weighbridge Slips</small>
              <strong>{text(material)}</strong>
              {loadingSlip && <div>Loading slip: {String(loadingSlip)}</div>}
              {deliverySlip && <div>Delivery slip: {String(deliverySlip)}</div>}
              {receiverName && <div>Received by: {String(receiverName)}</div>}
            </div>
          </div>
        )}
        <div className="print-party">
          <div>
            <small>Source / Mode</small>
            <strong>{text(val(trip, "source"))}</strong>
            {ewayNo && (
              <div>
                E-Way Bill: <strong>{String(ewayNo)}</strong>
                {ewayValid ? ` (Valid till ${text(ewayValid)})` : ""}
              </div>
            )}
          </div>
          <div>
            <small>Planned / Loaded / Received (tonnes)</small>
            <strong>
              {text(val(trip, "planned_quantity"))} t /{" "}
              {text(val(trip, "loaded_quantity"))} t /{" "}
              {text(val(trip, "received_quantity"))} t
            </strong>
          </div>
        </div>
        <div className="print-party">
          <div>
            <small>Truck</small>
            <strong>
              {text(val(trip, "truck_registration", "truckRegistration", "truck_id"))}
            </strong>
          </div>
          <div>
            <small>Driver</small>
            <strong>
              {text(val(trip, "driver_name", "driverName", "driver_id"))}
            </strong>
          </div>
        </div>
        <div className="print-signatures">
          <div>
            <span>Weighbridge / Yard Supervisor</span>
            <p>__________________________</p>
          </div>
          <div>
            <span>Driver Signature</span>
            <p>__________________________</p>
          </div>
          <div>
            <span>Customer Site Receiver</span>
            <p>__________________________</p>
          </div>
        </div>
      </section>
    </>
  );
}

export function PrintableAdjustmentNote({ note }: { note: RecordData }) {
  const branch = (note.branch as RecordData | undefined) ?? null;
  const isCredit = String(val(note, "note_type")) === "CREDIT_NOTE";
  const title = isCredit ? "GST CREDIT NOTE (SECTION 34)" : "GST DEBIT NOTE (SECTION 34)";
  const total = Number(val(note, "total_amount") ?? 0);
  const supplyLabel =
    val(note, "supply") === "INTER_STATE"
      ? "Inter-State (IGST)"
      : "Intra-State (CGST + SGST)";
  return (
    <>
      <div className="no-print" style={{ marginBottom: 12 }}>
        <button
          type="button"
          className="secondary"
          onClick={() => window.print()}
        >
          <Printer size={14} /> Print / Save PDF
        </button>
      </div>
      <section className="print-sheet" aria-label="Printable GST adjustment note">
        <div className="print-header">
          <div>
            <h2>
              YFC TRANSPORT &amp; MATERIALS
              {branch?.name ? ` — ${String(branch.name)}` : ""}
            </h2>
            {branch ? (
              <>
                <p>
                  {text(branch.address)}, {text(branch.city)}, {text(branch.state)} —{" "}
                  {text(branch.pincode)}
                </p>
                {branch.gstin && (
                  <p>
                    <strong>GSTIN:</strong> {String(branch.gstin)}
                    {branch.state_code ? ` · State Code: ${String(branch.state_code)}` : ""}
                  </p>
                )}
              </>
            ) : (
              <p>Owned-Fleet Bulk Material Supply &amp; Logistics</p>
            )}
          </div>
          <div className="print-doc-meta">
            <strong>{title}</strong>
            <div>Note No: {text(val(note, "note_number"))}</div>
            <div>Date: {text(val(note, "note_date"))}</div>
            <div>Supply: {supplyLabel}</div>
            <div>Status: {text(val(note, "status"))}</div>
          </div>
        </div>
        <div className="print-party">
          <div>
            <small>Party</small>
            <strong>{text(val(note, "party_name"))}</strong>
            {val(note, "party_gstin") && (
              <div>
                GSTIN: <strong>{String(val(note, "party_gstin"))}</strong>
                {val(note, "party_state_code")
                  ? ` (State ${String(val(note, "party_state_code"))})`
                  : ""}
              </div>
            )}
            {val(note, "party_address") && <div>{String(val(note, "party_address"))}</div>}
          </div>
          <div>
            <small>Against Original Invoice / Bill</small>
            <strong>{text(val(note, "original_invoice_number"))}</strong>
            <div>Original Date: {text(val(note, "original_invoice_date"))}</div>
            <div>Reason: {text(val(note, "reason_code"))}</div>
          </div>
        </div>
        <table className="print-table">
          <thead>
            <tr>
              <th>HSN / SAC</th>
              <th>Adjustment Particulars</th>
              <th>GST %</th>
              <th>Taxable (INR)</th>
              <th>CGST</th>
              <th>SGST</th>
              <th>IGST</th>
              <th>Total (INR)</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{text(val(note, "hsn_or_sac"))}</td>
              <td>{text(val(note, "reason_detail"))}</td>
              <td>{text(val(note, "gst_rate_percent"))}%</td>
              <td>{money(val(note, "taxable_amount"))}</td>
              <td>{money(val(note, "cgst_amount"))}</td>
              <td>{money(val(note, "sgst_amount"))}</td>
              <td>{money(val(note, "igst_amount"))}</td>
              <td>{money(total)}</td>
            </tr>
          </tbody>
        </table>
        <div className="print-totals">
          <div>
            <span>Taxable Adjustment</span>
            <strong>{money(val(note, "taxable_amount"))}</strong>
          </div>
          <div>
            <span>GST Total</span>
            <strong>{money(val(note, "tax_total"))}</strong>
          </div>
          <div className="print-grand">
            <span>{isCredit ? "Total Credit Value" : "Total Debit Value"}</span>
            <strong>{money(total)}</strong>
          </div>
        </div>
        <p className="print-words">
          <strong>Amount in words:</strong> {inrWords(total)}
        </p>
        <div className="print-signatures">
          <div>
            <span>Prepared By</span>
            <p>{text(val(note, "created_by"))}</p>
          </div>
          <div>
            <span>Approved By</span>
            <p>{text(val(note, "approved_by"))}</p>
          </div>
          <div>
            <span>Authorised Signatory (YFC Transport)</span>
            <p>__________________________</p>
          </div>
        </div>
      </section>
    </>
  );
}
