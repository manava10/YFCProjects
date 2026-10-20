import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { post, request } from "./api";
import {
  DataTable,
  ErrorBox,
  Field,
  Heading,
  Notice,
  Pager,
  SaveForm,
  usePaged,
} from "./ui";

const ROLES = ["SUPER_ADMIN", "OPERATIONS", "ACCOUNTS", "CUSTOMER", "SUPPLIER", "DRIVER"];

export function People() {
  const [reset, setReset] = useState<{ username: string; token: string; expiresAt: string } | null>(
    null,
  );
  const list = usePaged("users", "/api/v1/users");
  const client = useQueryClient();
  const issue = useMutation({
    mutationFn: (id: string) =>
      post<{ token: string; expiresAt: string }>(`/api/v1/users/${id}/password-reset`),
    onSuccess: async (result, id) => {
      const user = list.items.find((row) => row.id === id);
      setReset({
        username: String(user?.username ?? id),
        token: result.token,
        expiresAt: result.expiresAt,
      });
      await client.invalidateQueries({ queryKey: ["users"] });
    },
  });
  return (
    <>
      <Heading
        eyebrow="ADMINISTRATION / PEOPLE"
        title="Staff accounts."
        description="Passwords are set here and never stored in the browser beyond this reset."
      />
      <SaveForm title="New account" path="/api/v1/users">
        <div className="form-grid">
          <Field label="Username">
            <input name="username" required minLength={3} maxLength={80} pattern="[a-zA-Z0-9][a-zA-Z0-9._-]{2,79}" />
          </Field>
          <Field label="Display name">
            <input name="displayName" required maxLength={120} />
          </Field>
          <Field label="Temporary password">
            <input name="password" type="password" required minLength={12} maxLength={72} autoComplete="new-password" />
          </Field>
          <Field label="Role">
            <select name="role">
              {ROLES.map((role) => (
                <option key={role}>{role}</option>
              ))}
            </select>
          </Field>
        </div>
      </SaveForm>
      <ErrorBox error={list.q.error || issue.error} />
      {reset && (
        <Notice title={`Reset for ${reset.username}`}>
          Share this token once. It expires {reset.expiresAt}. Token: {reset.token}
        </Notice>
      )}
      <div className="panel">
        <DataTable
          rows={list.items}
          columns={[
            { label: "Username", keys: ["username"] },
            { label: "Name", keys: ["displayName"] },
            { label: "Role", keys: ["role"] },
            { label: "Enabled", keys: ["enabled"], status: true },
          ]}
        />
        <Pager page={list.page} setPage={list.setPage} total={list.total} hasNext={list.hasNext} />
      </div>
      {list.items.map((user) => (
        <details key={user.id} className="panel form-panel">
          <summary>
            {user.username} · {user.role}
          </summary>
          <SaveForm path={`/api/v1/users/${user.id}/access`} method="PATCH">
            <div className="form-grid">
              <Field label="Role">
                <select name="role" defaultValue={String(user.role)}>
                  {ROLES.map((role) => (
                    <option key={role}>{role}</option>
                  ))}
                </select>
              </Field>
              <Field label="Access">
                <select name="enabled" defaultValue={String(user.enabled)}>
                  <option value="true">Enabled</option>
                  <option value="false">Disabled</option>
                </select>
              </Field>
            </div>
          </SaveForm>
          <button onClick={() => issue.mutate(user.id)} disabled={issue.isPending || !user.enabled}>
            Issue 15-minute reset token
          </button>
        </details>
      ))}
    </>
  );
}

export function Audit() {
  const list = usePaged("audit", "/api/v1/audit-events");
  return (
    <>
      <Heading
        eyebrow="ADMINISTRATION / AUDIT"
        title="What changed, and who did it."
        description="Newest events first."
      />
      <ErrorBox error={list.q.error} />
      <div className="panel">
        <DataTable
          rows={list.items}
          columns={[
            { label: "When", keys: ["occurredAt"] },
            { label: "Actor", keys: ["actor"] },
            { label: "Action", keys: ["action"] },
            { label: "Target", keys: ["targetType"] },
            { label: "Detail", keys: ["detail"] },
          ]}
        />
        <Pager page={list.page} setPage={list.setPage} total={list.total} hasNext={list.hasNext} />
      </div>
    </>
  );
}

export function ResetPassword() {
  const [done, setDone] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  return (
    <main className="login login-page">
      <p className="eyebrow">PASSWORD RESET</p>
      <h2>Choose a new password</h2>
      <p>Use the token from your administrator. It expires 15 minutes after it is issued.</p>
      <ErrorBox error={error} />
      {done ? (
        <Notice title="Password updated.">You can sign in with the new password.</Notice>
      ) : (
        <form
          onSubmit={async (event) => {
            event.preventDefault();
            setBusy(true);
            setError(null);
            const form = new FormData(event.currentTarget);
            try {
              await request("/api/v1/auth/password-reset", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  token: form.get("token"),
                  newPassword: form.get("newPassword"),
                }),
              });
              setDone(true);
            } catch (caught) {
              setError(caught);
            } finally {
              setBusy(false);
            }
          }}
        >
          <Field label="Reset token">
            <input name="token" required maxLength={100} />
          </Field>
          <Field label="New password">
            <input name="newPassword" type="password" required minLength={12} maxLength={72} autoComplete="new-password" />
          </Field>
          <button disabled={busy}>{busy ? "Saving…" : "Update password"}</button>
        </form>
      )}
    </main>
  );
}
