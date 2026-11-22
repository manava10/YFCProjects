import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, RefreshCw, Send } from "lucide-react";
import { post, request, type RecordData } from "./api";
import { label, money, short, text, val } from "./lib";
import { ErrorBox, Heading, Status, useAccess } from "./ui";

export function AlertsCenter() {
  const { write } = useAccess();
  const client = useQueryClient();
  const [ewayHours, setEwayHours] = useState("24");
  const [complianceDays, setComplianceDays] = useState("30");
  const [overdueDays, setOverdueDays] = useState("30");

  const summary = useQuery({
    queryKey: ["alerts-summary", ewayHours, complianceDays, overdueDays],
    queryFn: () =>
      request<RecordData>(
        `/api/v1/alerts/summary?ewayHours=${ewayHours}&complianceDays=${complianceDays}&overdueDays=${overdueDays}`,
      ),
  });
  const digests = useQuery({
    queryKey: ["alerts-digests"],
    queryFn: () => request<RecordData[]>("/api/v1/alerts/digests?limit=20"),
  });
  const runDigest = useMutation({
    mutationFn: () => post<RecordData>("/api/v1/alerts/digest/run", {}),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ["alerts-digests"] });
      await client.invalidateQueries({ queryKey: ["alerts-summary"] });
    },
  });

  const data = summary.data;
  const eway = (data?.ewayBillAlerts as RecordData[] | undefined) ?? [];
  const truckComp = (data?.truckComplianceAlerts as RecordData[] | undefined) ?? [];
  const driverComp = (data?.driverLicenceAlerts as RecordData[] | undefined) ?? [];
  const maintDue = (data?.maintenanceDueAlerts as RecordData[] | undefined) ?? [];
  const overdue = (data?.overdueReceivables as RecordData[] | undefined) ?? [];

  return (
    <>
      <Heading
        eyebrow="WORKSPACE / AUTOMATED ALERTS & DIGEST"
        title="Stay ahead of expiries, maintenance, and overdue bills."
        description="Live operational watchdog covering E-Way Bills, owned-fleet compliance, driver licences, preventive maintenance, and overdue receivables. Scheduled daily at 08:00 IST."
        action={
          <div className="heading-action">
            <button
              type="button"
              className="secondary"
              onClick={() => {
                summary.refetch();
                digests.refetch();
              }}
            >
              <RefreshCw size={15} /> Refresh
            </button>
            {write && (
              <button
                type="button"
                disabled={runDigest.isPending}
                onClick={() => runDigest.mutate()}
              >
                <Send size={15} />{" "}
                {runDigest.isPending ? "Generating…" : "Run & Save Digest Now"}
              </button>
            )}
          </div>
        }
      />

      <ErrorBox error={summary.error || digests.error || runDigest.error} />
      {runDigest.isSuccess && (
        <div className="notice">
          <strong>Daily digest recorded</strong>
          <p>
            Snapshot saved ({String(runDigest.data?.dispatched_channel)} /{" "}
            {String(runDigest.data?.dispatch_status)}) with{" "}
            {String(runDigest.data?.critical_count ?? 0)} critical and{" "}
            {String(runDigest.data?.warning_count ?? 0)} warning alerts.
          </p>
        </div>
      )}

      <div className="filter-bar">
        <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13 }}>
          E-Way window (hrs)
          <input
            type="number"
            min="1"
            max="720"
            value={ewayHours}
            onChange={(e) => setEwayHours(e.target.value || "24")}
            style={{ width: 84, margin: 0 }}
          />
        </label>
        <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13 }}>
          Renewal &amp; service window (days)
          <input
            type="number"
            min="0"
            max="365"
            value={complianceDays}
            onChange={(e) => setComplianceDays(e.target.value || "30")}
            style={{ width: 84, margin: 0 }}
          />
        </label>
        <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13 }}>
          Receivables overdue (&gt; days)
          <input
            type="number"
            min="1"
            max="365"
            value={overdueDays}
            onChange={(e) => setOverdueDays(e.target.value || "30")}
            style={{ width: 84, margin: 0 }}
          />
        </label>
      </div>

      {data && (
        <section className="portal-summary" aria-label="Alert totals">
          <div className="portal-kpi">
            <small>Critical Alerts</small>
            <strong style={{ color: Number(data.criticalCount) > 0 ? "#dc2626" : "#0f172a" }}>
              {String(data.criticalCount ?? 0)}
            </strong>
          </div>
          <div className="portal-kpi">
            <small>Warnings</small>
            <strong style={{ color: Number(data.warningCount) > 0 ? "#d97706" : "#0f172a" }}>
              {String(data.warningCount ?? 0)}
            </strong>
          </div>
          <div className="portal-kpi">
            <small>Total Active Alerts</small>
            <strong>{String(data.totalAlerts ?? 0)}</strong>
          </div>
          <div className="portal-kpi">
            <small>Overdue Receivables</small>
            <strong>{money(Number(data.totalOverdueReceivablesInr ?? 0))}</strong>
          </div>
        </section>
      )}

      <div className="panel">
        <h3>
          <Bell size={16} style={{ verticalAlign: "middle", marginRight: 6 }} />
          Expiring / Expired E-Way Bills ({eway.length})
        </h3>
        {eway.length === 0 ? (
          <p className="muted">No active trips have E-Way Bills expiring within {ewayHours} hours.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Trip</th>
                  <th>Truck</th>
                  <th>Driver</th>
                  <th>E-Way Bill</th>
                  <th>Valid Until</th>
                  <th>Severity</th>
                </tr>
              </thead>
              <tbody>
                {eway.map((row) => (
                  <tr key={String(row.id)}>
                    <td>
                      <Link to={`/trips/${row.id}`}>{short(String(row.id))}</Link>
                    </td>
                    <td>{text(row.truck_registration)}</td>
                    <td>{text(row.driver_name)}</td>
                    <td>{text(row.eway_bill_number)}</td>
                    <td>{text(row.eway_bill_valid_until)}</td>
                    <td>
                      <Status value={row.expired ? "REJECTED" : String(row.severity)} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="panel">
        <h3>Truck Compliance Renewals ({truckComp.length})</h3>
        {truckComp.length === 0 ? (
          <p className="muted">All active owned trucks have valid insurance, fitness, permit, and PUC.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Truck</th>
                  <th>Branch</th>
                  <th>Expiring / Expired Documents</th>
                  <th>Severity</th>
                </tr>
              </thead>
              <tbody>
                {truckComp.map((row) => (
                  <tr key={String(row.id)}>
                    <td>
                      <Link to="/fleet">{text(row.registration)}</Link>
                    </td>
                    <td>{text(row.branch_name)}</td>
                    <td>{Array.isArray(row.reasons) ? row.reasons.join(" · ") : "—"}</td>
                    <td>
                      <Status value={String(row.severity)} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="panel">
        <h3>Preventive Maintenance &amp; Tyre Service Due ({maintDue.length})</h3>
        {maintDue.length === 0 ? (
          <p className="muted">No owned trucks are currently due for scheduled service or tyre checks.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Truck</th>
                  <th>Category</th>
                  <th>Current Odometer</th>
                  <th>Next Due Date</th>
                  <th>Next Due Km</th>
                  <th>Severity</th>
                </tr>
              </thead>
              <tbody>
                {maintDue.map((row) => (
                  <tr key={String(row.id)}>
                    <td>
                      <Link to="/fleet">{text(row.truck_registration)}</Link>
                    </td>
                    <td>{label(text(row.category))}</td>
                    <td>{text(row.current_odometer_km)} km</td>
                    <td>{text(row.next_due_date)}</td>
                    <td>{row.next_due_odometer_km ? `${text(row.next_due_odometer_km)} km` : "—"}</td>
                    <td>
                      <Status value={String(row.severity)} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="panel">
        <h3>Driver Licence Renewals ({driverComp.length})</h3>
        {driverComp.length === 0 ? (
          <p className="muted">All active drivers have licences valid beyond {complianceDays} days.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Driver</th>
                  <th>Mobile</th>
                  <th>Licence</th>
                  <th>Expiry</th>
                  <th>Severity</th>
                </tr>
              </thead>
              <tbody>
                {driverComp.map((row) => (
                  <tr key={String(row.id)}>
                    <td>{text(row.name)}</td>
                    <td>{text(row.mobile)}</td>
                    <td>{text(row.license_number)}</td>
                    <td>{text(row.license_expiry)}</td>
                    <td>
                      <Status value={String(row.severity)} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="panel">
        <h3>Overdue Customer Receivables ({overdue.length})</h3>
        {overdue.length === 0 ? (
          <p className="muted">No taxed sales invoices are overdue beyond {overdueDays} days.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Invoice</th>
                  <th>Customer</th>
                  <th>Date</th>
                  <th>Age</th>
                  <th>Outstanding</th>
                  <th>Severity</th>
                </tr>
              </thead>
              <tbody>
                {overdue.map((row) => (
                  <tr key={String(row.id)}>
                    <td>
                      <Link to={`/billing/${row.id}`}>
                        {text(val(row, "invoice_number")) !== "—"
                          ? text(val(row, "invoice_number"))
                          : short(String(row.id))}
                      </Link>
                    </td>
                    <td>{text(row.party_name)}</td>
                    <td>{text(row.prepared_on)}</td>
                    <td>{String(row.age_days ?? 0)} days</td>
                    <td>{money(Number(row.outstanding_amount ?? 0))}</td>
                    <td>
                      <Status value={String(row.severity)} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="panel">
        <h3>Daily Digest Log (Scheduled &amp; Manual Runs)</h3>
        {!digests.data || digests.data.length === 0 ? (
          <p className="muted">No digests recorded yet. Click “Run &amp; Save Digest Now” above to capture one.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Digest Date</th>
                  <th>Critical</th>
                  <th>Warnings</th>
                  <th>Total</th>
                  <th>Channel</th>
                  <th>Status</th>
                  <th>Triggered By</th>
                </tr>
              </thead>
              <tbody>
                {digests.data.map((d) => (
                  <tr key={String(d.id)}>
                    <td>{text(d.digest_date)}</td>
                    <td>{String(d.critical_count ?? 0)}</td>
                    <td>{String(d.warning_count ?? 0)}</td>
                    <td>{String(d.total_alerts ?? 0)}</td>
                    <td>{text(d.dispatched_channel)}</td>
                    <td>
                      <Status value={String(d.dispatch_status)} />
                    </td>
                    <td>{text(d.triggered_by)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}
