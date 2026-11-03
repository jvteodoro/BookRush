import { authClient } from "../auth";
import { apiRoutes } from "../app/apiRoutes";

type Fetcher = (url: string, init?: RequestInit) => Promise<Response>;
const authenticated: Fetcher = (url, init) => authClient.fetch(url, init);
export const bookOpenedEvent = "bookrush:book-opened";

export async function loadSeenBooks(
  bookIds: string[],
  signal?: AbortSignal,
  fetcher: Fetcher = authenticated,
): Promise<Set<string>> {
  const ids = [...new Set(bookIds)];
  const seen = new Set<string>();
  for (let start = 0; start < ids.length; start += 100) {
    const batch = ids.slice(start, start + 100);
    const response = await fetcher(apiRoutes.reader.seen(batch), { signal });
    if (!response.ok)
      throw new Error(
        response.status === 401
          ? "Sua sessão expirou. Entre novamente."
          : response.status === 403
            ? "Você não tem permissão para consultar os livros vistos."
            : "Não foi possível consultar os livros vistos. Tente novamente.",
      );
    const payload = (await response.json()) as {
      items: Array<{ bookId: string }>;
    };
    const requested = new Set(batch);
    payload.items.forEach((item) => {
      if (requested.has(item.bookId)) seen.add(item.bookId);
    });
  }
  return seen;
}

/** Call only after the reading content has loaded successfully. */
export async function recordOpenedBook(
  bookId: string,
  headers: Record<string, string> = {},
  fetcher: Fetcher = authenticated,
) {
  const response = await fetcher(apiRoutes.reader.opened(bookId), {
    method: "POST",
    headers,
  });
  if (!response.ok)
    throw new Error(
      "O livro abriu, mas não foi possível registrar seu histórico. Tente abri-lo novamente.",
    );
  window.dispatchEvent(new CustomEvent(bookOpenedEvent, { detail: bookId }));
}
