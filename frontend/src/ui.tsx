import React, { createContext, useContext, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Layers3, Plus } from "lucide-react";
import { collect, post, request, type Page, type RecordData } from "./api";
import { groups, label, nameOf, ownedBy, readForm, scalars, short, text, uuid, val } from "./lib";

export type Access = {
  username: string;
  admin: boolean;
  operations: boolean;
  accounts: boolean;
  write: boolean;
  finance: boolean;
  review: boolean;
};

export const AccessContext = createContext<Access>({
  username: "",
  admin: false,
  operations: false,
  accounts: false,
  write: false,
  finance: false,
  review: false,
});
export const useAccess = () => useContext(AccessContext);

export function ErrorBox({ error }: { error: unknown }) {
  return error ? (
    <div role="alert" className="error">
      {error instanceof Error ? error.message : "Something went wrong."}
    </div>
  ) : null;
}

export function Status({ value }: { value?: string | boolean | null }) {
  const textValue =
    typeof value === "boolean" ? (value ? "ACTIVE" : "INACTIVE") : String(value ?? "");
  return <span className={`status ${textValue.toLowerCase()}`}>{label(textValue)}</span>;
}

export function Field({
  label: caption,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="field">
      <span>{caption}</span>
      {children}
    </label>
  );
}

export function Empty({ text: message }: { text: string }) {
  return (
    <div className="empty">
      <Layers3 size={30} />
      <h3>{message}</h3>
      <p>Records from the live workspace will appear here.</p>
    </div>
  );
}

export function Heading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {action}
    </div>
  );
}

export function Notice({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="notice">
      <strong>{title}</strong>
      <p>{children}</p>
    </div>
  );
}

export function Back({ to, children }: { to: string; children: React.ReactNode }) {
  return (
    <Link className="back" to={to}>
      <ArrowLeft size={16} />
      {children}
    </Link>
  );
}

export function Tabs({
  value,
  onChange,
  items,
}: {
  value: string;
  onChange: (value: string) => void;
  items: { id: string; label: string }[];
}) {
  return (
    <div className="tabs" role="tablist">
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          role="tab"
          aria-selected={value === item.id}
          className={value === item.id ? "tab active" : "tab"}
          onClick={() => onChange(item.id)}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}

export function Pager({
  page,
  setPage,
  total,
  hasNext,
}: {
  page: number;
  setPage: (page: number) => void;
  total?: number;
  hasNext: boolean;
}) {
  return (
    <div className="pager">
      <span>{total ?? "—"} records</span>
      <button className="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
        Previous
      </button>
      <span>Page {page + 1}</span>
      <button className="secondary" disabled={!hasNext} onClick={() => setPage(page + 1)}>
        Next
      </button>
    </div>
  );
}

export function usePaged(key: string, path: string) {
  const [page, setPage] = useState(0);
  const q = useQuery({
    queryKey: [key, path, page],
    queryFn: () => request<Page<RecordData> | RecordData[]>(withPage(path, page)),
  });
  const items = Array.isArray(q.data) ? q.data : (q.data?.content ?? []);
  const total = Array.isArray(q.data) ? undefined : q.data?.totalElements;
  const hasNext = Array.isArray(q.data)
    ? items.length === 20
    : !!q.data && page + 1 < q.data.totalPages;
  return { page, setPage, q, items, total, hasNext };
}

function withPage(path: string, page: number) {
  return `${path}${path.includes("?") ? "&" : "?"}page=${page}&size=20`;
}

export function Choices({
  path,
  empty,
  labelOf,
}: {
  path: string;
  empty: string;
  labelOf?: (row: RecordData) => string;
}) {
  const q = useQuery({
    queryKey: ["directory", path],
    queryFn: () => collect(path),
  });
  return (
    <>
      <option value="">{q.isPending ? "Loading…" : empty}</option>
      {q.data?.map((row) => (
        <option key={String(val(row, "id"))} value={String(val(row, "id"))}>
          {labelOf ? labelOf(row) : nameOf(row)}
        </option>
      ))}
    </>
  );
}

export function RecordView({ row }: { row?: RecordData | null }) {
  if (!row) return null;
  return (
    <>
      <Facts row={row} />
      {groups(row).map(([key, rows]) => (
        <div key={key}>
          <h3>{label(key)}</h3>
          {rows.length && typeof rows[0] === "object" ? (
            <DataTable
              rows={rows}
              columns={Object.keys(rows[0])
                .filter((name) => !["create_payload", "payload"].includes(name))
                .slice(0, 6)
                .map((name) => ({
                  label: label(name),
                  keys: [name],
                  status: /status|active/i.test(name),
                }))}
            />
          ) : (
            <p>{rows.map(String).join(", ")}</p>
          )}
        </div>
      ))}
    </>
  );
}

export function Facts({ row }: { row?: RecordData | null }) {
  if (!row) return null;
  const fields = scalars(row).filter(([key]) => key !== "replayed");
  return (
    <dl className="facts">
      {fields.map(([key, value]) => (
        <div key={key}>
          <dt>{label(key)}</dt>
          <dd>
            {/status|active/i.test(key) &&
            (typeof value === "boolean" || typeof value === "string") ? (
              <Status value={value as string | boolean} />
            ) : (
              text(value)
            )}
          </dd>
        </div>
      ))}
    </dl>
  );
}

export function DataTable({
  rows,
  columns,
  onOpen,
}: {
  rows: RecordData[];
  columns: { label: string; keys: string[]; status?: boolean }[];
  onOpen?: (row: RecordData) => void;
}) {
  if (!rows.length) return <Empty text="Nothing recorded yet" />;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.label}>{column.label}</th>
            ))}
            {onOpen && <th />}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={String(val(row, "id", "journal_id") ?? index)}>
              {columns.map((column) => {
                const value = val(row, ...column.keys);
                return (
                  <td key={column.label}>
                    {column.status ? (
                      <Status value={value as string | boolean} />
                    ) : (
                      text(value)
                    )}
                  </td>
                );
              })}
              {onOpen && (
                <td>
                  <button className="secondary" onClick={() => onOpen(row)}>
                    Open
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function IdField({ name, caption }: { name: string; caption: string }) {
  const [id] = useState(uuid);
  return (
    <Field label={caption}>
      <input name={name} value={id} readOnly />
    </Field>
  );
}

export function ReasonForm({
  path,
  extra,
  submitLabel,
  onDone,
}: {
  path: string;
  extra?: Record<string, unknown>;
  submitLabel: string;
  onDone?: () => void;
}) {
  const client = useQueryClient();
  const [stamp] = useState(uuid);
  const mutation = useMutation({
    mutationFn: (body: unknown) => post(path, body),
    onSuccess: async () => {
      await client.invalidateQueries();
      onDone?.();
    },
  });
  return (
    <form
      className="decision"
      onSubmit={(event) => {
        event.preventDefault();
        const body = readForm(event.currentTarget);
        mutation.mutate({ ...body, ...extra, eventId: stamp });
      }}
    >
      <ErrorBox error={mutation.error} />
      <Field label="Reason">
        <textarea name="reason" required maxLength={1000} />
      </Field>
      <button disabled={mutation.isPending}>
        {mutation.isPending ? "Saving…" : submitLabel}
      </button>
    </form>
  );
}

export function ReviewBox({
  approve,
  reject,
  row,
}: {
  approve: string;
  reject: string;
  row?: RecordData | null;
}) {
  const access = useAccess();
  const [mode, setMode] = useState<"approve" | "reject" | null>(null);
  if (!access.review) return null;
  if (ownedBy(row, access.username)) {
    return <p className="muted">A different user has to review this request.</p>;
  }
  return (
    <div>
      <div className="actions">
        <button type="button" onClick={() => setMode("approve")}>
          Approve
        </button>
        <button type="button" className="secondary" onClick={() => setMode("reject")}>
          Reject
        </button>
      </div>
      {mode && (
        <ReasonForm
          path={mode === "approve" ? approve : reject}
          submitLabel={mode === "approve" ? "Confirm approval" : "Confirm rejection"}
          onDone={() => setMode(null)}
        />
      )}
    </div>
  );
}

export function SaveForm({
  title,
  path,
  method = "POST",
  children,
  prepare,
  onSuccess,
}: {
  title?: string;
  path: string;
  method?: "POST" | "PUT" | "PATCH";
  children: React.ReactNode;
  prepare?: (body: Record<string, unknown>) => Record<string, unknown>;
  onSuccess?: (row: RecordData) => void;
}) {
  const client = useQueryClient();
  const [key, setKey] = useState(0);
  const mutation = useMutation({
    mutationFn: (body: unknown) =>
      request(path, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
    onSuccess: async (row) => {
      await client.invalidateQueries();
      setKey((value) => value + 1);
      onSuccess?.(row);
    },
  });
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const body = readForm(event.currentTarget);
    mutation.mutate(prepare ? prepare(body) : body);
  }
  return (
    <form key={key} className="panel form-panel" onSubmit={submit}>
      {title && <h3>{title}</h3>}
      <ErrorBox error={mutation.error} />
      {children}
      <button disabled={mutation.isPending}>
        {mutation.isPending ? "Saving…" : "Save"}
      </button>
    </form>
  );
}

export function ToggleForm({
  label: caption,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <div className="heading-action">
        <button onClick={() => setOpen((value) => !value)}>
          <Plus size={16} />
          {open ? "Close form" : caption}
        </button>
      </div>
      {open && children}
    </>
  );
}

export function CodeName({ code = "", name = "" }: { code?: string; name?: string }) {
  return (
    <div className="form-grid">
      <Field label="Code">
        <input
          name="code"
          pattern="[A-Za-z0-9][A-Za-z0-9_\-]{1,29}"
          minLength={2}
          maxLength={30}
          required
          defaultValue={code}
        />
      </Field>
      <Field label="Name">
        <input name="name" maxLength={160} required defaultValue={name} />
      </Field>
    </div>
  );
}

export function ActiveSelect({ name = "active", caption = "Active" }: { name?: string; caption?: string }) {
  return (
    <Field label={caption}>
      <select name={name} defaultValue="true">
        <option value="true">Active</option>
        <option value="false">Inactive</option>
      </select>
    </Field>
  );
}
