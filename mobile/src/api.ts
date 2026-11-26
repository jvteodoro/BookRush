export type Book = {
  id: string;
  canonicalTitle: string;
  originalLanguage?: string | null;
  firstPublicationYear?: number | null;
  description?: string | null;
  status?: string;
};
export type BookPage = {
  items: Book[];
  totalItems: number;
  page: number;
  last: boolean;
};
const baseUrl =
  process.env.EXPO_PUBLIC_API_URL ?? "https://bookrush.jteodoro.tec.br";
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export function authenticatedRequest(
  accessToken: string,
  path: string,
  init?: RequestInit,
) {
  const headers = new Headers(init?.headers);
  headers.set("Authorization", `Bearer ${accessToken}`);
  return fetch(`${baseUrl}${path}`, { ...init, headers });
}

export async function listBooks(
  _accessToken: string,
  query = "",
  signal?: AbortSignal,
): Promise<BookPage> {
  const params = new URLSearchParams({ page: "0", size: "50" });
  if (query.trim()) params.set("q", query.trim());
  const response = await fetch(`${baseUrl}/api/v1/books?${params}`, {
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${_accessToken}`,
    },
    cache: "no-store",
    signal,
  });
  if (!response.ok)
    throw new ApiError(
      response.status,
      response.status === 401
        ? "Sessão expirada. Entre novamente."
        : `Catálogo indisponível (HTTP ${response.status}).`,
    );
  return response.json() as Promise<BookPage>;
}
export async function updateLibrary(
  accessToken: string,
  bookId: string,
  saved: boolean,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(
    `${baseUrl}/api/v1/reader/library/${encodeURIComponent(bookId)}`,
    {
      method: saved ? "PUT" : "DELETE",
      signal,
      headers: { Authorization: `Bearer ${accessToken}` },
    },
  );
  if (!response.ok)
    throw new ApiError(
      response.status,
      `Falha ao atualizar biblioteca (HTTP ${response.status})`,
    );
}
export async function listLibrary(
  accessToken: string,
  signal?: AbortSignal,
): Promise<string[]> {
  const response = await fetch(`${baseUrl}/api/v1/reader/library`, {
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    cache: "no-store",
    signal,
  });
  if (!response.ok)
    throw new ApiError(
      response.status,
      `Falha ao carregar biblioteca (HTTP ${response.status})`,
    );
  const rows = (await response.json()) as Array<{
    book_id?: string;
    bookId?: string;
  }>;
  return rows
    .map((row) => row.book_id ?? row.bookId)
    .filter((id): id is string => Boolean(id));
}
export async function sendBehaviorEvents(
  accessToken: string,
  events: unknown[],
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${baseUrl}/api/v1/behavior/events`, {
    method: "POST",
    signal,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ events }),
  });
  if (!response.ok)
    throw new ApiError(
      response.status,
      `Falha ao enviar telemetria (HTTP ${response.status})`,
    );
}
