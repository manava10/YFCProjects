import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { binary, request, upload, type RecordData } from "./api";
import { uuid, val } from "./lib";
import { ErrorBox, Field } from "./ui";

type DocumentsProps = {
  targetType:
    | "TRIP"
    | "INCIDENT"
    | "FUEL"
    | "EXPENSE"
    | "STOCK_ADJUSTMENT"
    | "STOCK_TRANSFER"
    | "STOCK_OPENING";
  targetId: string;
};

export function Documents(props: DocumentsProps) {
  return (
    <RecordDocuments key={`${props.targetType}:${props.targetId}`} {...props} />
  );
}

function RecordDocuments({ targetType, targetId }: DocumentsProps) {
  const client = useQueryClient();
  const [documentId, setDocumentId] = useState(uuid);
  const [uploadError, setUploadError] = useState<unknown>(null);
  const [downloadError, setDownloadError] = useState<unknown>(null);
  const [downloading, setDownloading] = useState(false);
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
      await client.invalidateQueries({
        queryKey: ["documents", targetType, targetId],
      });
    },
  });
  return (
    <div className="panel form-panel">
      <h3>Private documents</h3>
      <p className="muted">
        PDF, JPEG or PNG, up to 2 MiB. Files stay in the private store.
      </p>
      <ErrorBox error={uploadError || mutation.error || q.error} />
      {mutation.isSuccess && (
        <p className="save-confirmation" role="status">
          Document uploaded.
        </p>
      )}
      <form
        onSubmit={(event) => {
          event.preventDefault();
          const form = event.currentTarget;
          const data = new FormData(form);
          const file = data.get("file");
          setUploadError(null);
          if (!(file instanceof File) || file.size === 0) {
            setUploadError(
              new Error("Choose a non-empty PDF, JPEG or PNG file."),
            );
            return;
          }
          if (file.size > 2 * 1024 * 1024) {
            setUploadError(
              new Error("This file is too large. Choose a file up to 2 MiB."),
            );
            return;
          }
          if (
            !["application/pdf", "image/jpeg", "image/png"].includes(file.type)
          ) {
            setUploadError(new Error("Choose a PDF, JPEG or PNG file."));
            return;
          }
          data.set("documentId", documentId);
          data.set("targetType", targetType);
          data.set("targetId", targetId);
          mutation.mutate(data, { onSuccess: () => form.reset() });
        }}
      >
        <Field label="File">
          <input
            name="file"
            type="file"
            accept="application/pdf,image/jpeg,image/png"
            required
          />
        </Field>
        <button disabled={mutation.isPending}>
          {mutation.isPending ? "Uploading…" : "Upload"}
        </button>
      </form>
      <ErrorBox error={downloadError} />
      <ul className="chip-list">
        {q.data?.map((row) => (
          <li key={String(val(row, "id"))}>
            <button
              className="secondary"
              disabled={downloading}
              onClick={async () => {
                setDownloadError(null);
                setDownloading(true);
                try {
                  const blob = await binary(
                    `/api/v1/documents/${val(row, "id")}/content`,
                  );
                  const url = URL.createObjectURL(blob);
                  const link = document.createElement("a");
                  link.href = url;
                  link.download = String(val(row, "filename") ?? "document");
                  link.click();
                  setTimeout(() => URL.revokeObjectURL(url), 1000);
                } catch (error) {
                  setDownloadError(error);
                } finally {
                  setDownloading(false);
                }
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
