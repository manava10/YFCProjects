export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
let csrf: { headerName: string; token: string } | undefined;
export async function token() {
  csrf = await request("/api/v1/csrf");
  return csrf!;
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
    throw new ApiError(
      response.status,
      body?.detail ??
        (response.status === 401
          ? "Please sign in to continue."
          : response.status === 403
            ? "This action is not permitted, or your security token has expired. Refresh and try again."
            : `Request failed (${response.status}). Please try again.`),
    );
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
export const post = <T = any>(path: string, body?: unknown) =>
  request<T>(path, {
    method: "POST",
    ...(body === undefined
      ? {}
      : {
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        }),
  });
export async function login(username: string, password: string) {
  await token();
  await request("/login", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ username, password }),
  });
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
