export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}
let csrf: { headerName: string; token: string } | undefined;
export async function token() {
  csrf = await request("/api/v1/csrf");
  return csrf!;
}
async function refreshCsrf() {
  const response = await fetch("/api/v1/csrf", { credentials: "same-origin" });
  csrf = response.ok ? await response.json() : undefined;
}
function detail(body: { detail?: unknown } | null, status: number) {
  if (typeof body?.detail === "string" && body.detail.trim()) return body.detail;
  if (status === 401) return "Please sign in to continue.";
  if (status === 403)
    return "This action is not permitted, or your security token has expired. Refresh and try again.";
  return `Request failed (${status}). Please try again.`;
}
export async function request<T = any>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.method && !["GET", "HEAD"].includes(options.method)) {
    const t = csrf ?? (await token());
    headers.set(t.headerName, t.token);
  }
  const response = await fetch(path, {
    ...options,
    headers,
    credentials: "same-origin",
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    if (response.status === 401)
      window.dispatchEvent(new Event("session-expired"));
    if (response.status === 403) await refreshCsrf();
    throw new ApiError(response.status, detail(body, response.status));
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
const write = <T = any>(method: string, path: string, body?: unknown) =>
  request<T>(path, {
    method,
    ...(body === undefined
      ? {}
      : {
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        }),
  });
export const post = <T = any>(path: string, body?: unknown) =>
  write<T>("POST", path, body);
export const put = <T = any>(path: string, body?: unknown) =>
  write<T>("PUT", path, body);
export const patch = <T = any>(path: string, body?: unknown) =>
  write<T>("PATCH", path, body);
export const del = <T = any>(path: string) => request<T>(path, { method: "DELETE" });
export async function upload<T = any>(path: string, body: FormData) {
  return request<T>(path, { method: "POST", body });
}
export async function binary(path: string) {
  const response = await fetch(path, { credentials: "same-origin" });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    if (response.status === 401)
      window.dispatchEvent(new Event("session-expired"));
    throw new ApiError(
      response.status,
      body?.detail ?? `Request failed (${response.status}). Please try again.`,
    );
  }
  return response.blob();
}
export async function login(username: string, password: string) {
  await token();
  const headers = new Headers({ "Content-Type": "application/x-www-form-urlencoded" });
  headers.set(csrf!.headerName, csrf!.token);
  const response = await fetch("/login", {
    method: "POST",
    headers,
    credentials: "same-origin",
    body: new URLSearchParams({ username, password }),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const server =
      typeof body?.detail === "string"
        ? body.detail
        : typeof body?.message === "string"
          ? body.message
          : "";
    throw new ApiError(
      response.status,
      server ||
        (response.status === 401
          ? "The username or password is not correct."
          : `Request failed (${response.status}). Please try again.`),
    );
  }
  csrf = undefined;
  await token();
}
export async function logout() {
  await post("/logout");
  csrf = undefined;
}
export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
};
export type RecordData = { id: string; [key: string]: any };
export async function all(path: string): Promise<RecordData[]> {
  const rows: RecordData[] = [];
  for (let page = 0; ; page++) {
    const result = await request<Page<RecordData>>(
      `${path}${path.includes("?") ? "&" : "?"}page=${page}&size=100`,
    );
    rows.push(...result.content);
    if (page + 1 >= result.totalPages) return rows;
  }
}
export async function collect(path: string): Promise<RecordData[]> {
  const rows: RecordData[] = [];
  for (let page = 0; page < 50; page++) {
    const result = await request<Page<RecordData> | RecordData[]>(
      `${path}${path.includes("?") ? "&" : "?"}page=${page}&size=100`,
    );
    const content = Array.isArray(result) ? result : (result.content ?? []);
    rows.push(...content);
    if (Array.isArray(result)) {
      if (content.length < 100) return rows;
    } else if (page + 1 >= (result.totalPages ?? 0)) return rows;
  }
  return rows;
}
