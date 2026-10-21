import type { RecordData } from "./api";

export const short = (id?: string) => (id ? id.slice(0, 8).toUpperCase() : "—");
export const label = (s?: string) =>
  s?.replaceAll("_", " ").toLowerCase() ?? "";
export const money = (n: number | string) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(Number(n || 0));
export const today = () =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
export const uuid = () => crypto.randomUUID();

export function val(row: RecordData | null | undefined, ...keys: string[]) {
  if (!row) return undefined;
  for (const key of keys) {
    const value = row[key];
    if (value !== undefined && value !== null && value !== "") return value;
  }
  return undefined;
}

export function text(value: unknown) {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (Array.isArray(value) || typeof value === "object") return "";
  return String(value);
}

const HIDDEN = new Set([
  "create_payload",
  "createPayload",
  "payload",
  "previous_snapshot",
  "previousSnapshot",
]);

export function scalars(row: RecordData) {
  return Object.entries(row).filter(
    ([key, value]) =>
      !HIDDEN.has(key) && (value === null || typeof value !== "object"),
  );
}

export function groups(row: RecordData) {
  return Object.entries(row).filter(
    ([key, value]) => !HIDDEN.has(key) && Array.isArray(value),
  ) as [string, RecordData[]][];
}

const BOOLEANS = new Set(["active", "resalable", "expectedActive", "enabled"]);
const INTEGERS = new Set(["revision", "version", "paymentTermsDays"]);
const INSTANTS = new Set(["filledAt", "occurredAt", "resetAt"]);

export function readForm(form: HTMLFormElement) {
  const body: Record<string, unknown> = {};
  for (const [key, value] of new FormData(form).entries()) {
    if (typeof value !== "string") continue;
    // Password whitespace is part of the credential, not form formatting.
    if (key === "password" || key === "newPassword") {
      if (value) body[key] = value;
      continue;
    }
    const trimmed = value.trim();
    if (!trimmed) continue;
    if (BOOLEANS.has(key)) body[key] = trimmed === "true";
    else if (INTEGERS.has(key)) body[key] = Number(trimmed);
    else if (INSTANTS.has(key)) body[key] = toIst(trimmed);
    else body[key] = trimmed;
  }
  return body;
}

export function toIst(value: string) {
  if (value.endsWith("Z") || /[+-]\d\d:\d\d$/.test(value)) return value;
  return `${value.length === 16 ? `${value}:00` : value}+05:30`;
}

export function ownedBy(row: RecordData | null | undefined, username: string) {
  const actor = val(
    row,
    "submitted_by",
    "submittedBy",
    "created_by",
    "createdBy",
  );
  return actor != null && String(actor) === username;
}

export function yardKind(row: RecordData) {
  const kind = val(row, "kind");
  return kind ? String(kind) : "OPERATING";
}

export function operatingYard(row: RecordData) {
  return yardKind(row) === "OPERATING";
}

export function yardLabel(row: RecordData) {
  return `${nameOf(row)} · ${yardKind(row).toLowerCase()}`;
}

export function nameOf(row: RecordData) {
  return String(
    val(
      row,
      "name",
      "code",
      "registration",
      "truck_registration",
      "license_number",
      "reference",
      "description",
    ) ?? short(String(val(row, "id") ?? "")),
  );
}

export function isPendingReview(row: RecordData) {
  return ["PENDING", "PENDING_VERIFICATION"].includes(String(val(row, "status")));
}
