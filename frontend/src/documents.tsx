import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { binary, request, upload, type RecordData } from "./api";
import { uuid, val } from "./lib";
import { ErrorBox, Field } from "./ui";

export function Documents({
  targetType,
  targetId,
}: {
  targetType: "TRIP" | "INCIDENT" | "FUEL" | "EXPENSE";
  targetId: string;
}) {
  const client = useQueryClient();
  const [documentId, setDocumentId] = useState(uuid);
  const q = useQuery({
    queryKey: ["documents", targetType, targetId],
    queryFn: () =>
      request<RecordData[]>(
        `/api/v1/documents?targetType=${targetType}&targetId=${targetId}`,
      ),
  });
  const mutation = useMutation({
    mutationFn: (body: FormData) => upload("/api/v1/documents", body),
    onSuccess: async () => {
      setDocumentId(uuid());
      await client.invalidateQueries({ queryKey: ["documents", targetType, targetId] });
    },
  });
  return (
    <div className="panel form-panel">
      <h3>Private documents</h3>
      <p className="muted">PDF, JPEG or PNG, up to 2 MB. Files stay in the private store.</p>
      <ErrorBox error={q.error || mutation.error} />
      <form
        onSubmit={(event) => {
          event.preventDefault();
          const data = new FormData(event.currentTarget);
          data.set("documentId", documentId);
          data.set("targetType", targetType);
          data.set("targetId", targetId);
          mutation.mutate(data);
        }}
      >
        <Field label="File">
          <input name="file" type="file" accept="application/pdf,image/jpeg,image/png" required />
        </Field>
        <button disabled={mutation.isPending}>{mutation.isPending ? "Uploading…" : "Upload"}</button>
      </form>
      <ul className="chip-list">
        {q.data?.map((row) => (
          <li key={String(val(row, "id"))}>
            <button
              className="secondary"
              onClick={async () => {
                const blob = await binary(`/api/v1/documents/${val(row, "id")}/content`);
                const url = URL.createObjectURL(blob);
                const link = document.createElement("a");
                link.href = url;
                link.download = String(val(row, "filename") ?? "document");
                link.click();
                URL.revokeObjectURL(url);
              }}
            >
              {String(val(row, "filename") ?? shortName(row))}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

function shortName(row: RecordData) {
  return String(val(row, "id") ?? "document").slice(0, 8);
}
