import { bookMetadata } from "./bookMetadata";
import { categoryLabel } from "./bookCategories";
import type { CatalogBook } from "../app/types";
import { authClient } from "../auth";
import type { ReaderBook } from "../components/book/BookCard";

export type LibraryBook = ReaderBook & { available: boolean };
export type LibraryData = {
  books: LibraryBook[];
  saved: string[];
  recent: string[];
  warnings: string[];
};
type Fetcher = (url: string, init?: RequestInit) => Promise<Response>;
type Row = { book_id?: string; bookId?: string };
const ids = (rows: Row[]) => [
  ...new Set(
    rows
      .map((row) => row.book_id ?? row.bookId)
      .filter((id): id is string => Boolean(id)),
  ),
];

export async function loadLibrary(
  signal?: AbortSignal,
  fetcher: Fetcher = authClient.fetch.bind(authClient),
  catalogFetcher: Fetcher = fetch,
): Promise<LibraryData> {
  const savedResponse = await fetcher(apiRoutes.reader.library(), { signal });
  if (!savedResponse.ok)
    throw new Error(
      savedResponse.status === 401
        ? "Sua sessão expirou. Entre novamente."
        : "Não foi possível carregar sua biblioteca.",
    );
  const saved = ids(await savedResponse.json());
  const warnings: string[] = [];
  let recent: string[] = [];
  try {
    const response = await fetcher(apiRoutes.reader.recent(), { signal });
    if (!response.ok) throw new Error();
    recent = ids(await response.json());
  } catch {
    if (signal?.aborted) throw new Error("Carregamento cancelado.");
    warnings.push("Não foi possível carregar as leituras recentes.");
  }
  const allIds = [...new Set([...saved, ...recent])];
  const books: LibraryBook[] = new Array(allIds.length);
  let cursor = 0;
  // Saved books can be outside the current recommendation page. Resolve each
  // by ID, with bounded concurrency, rather than filtering the feed catalog.
  await Promise.all(
    Array.from({ length: Math.min(6, allIds.length) }, async () => {
      while (cursor < allIds.length) {
        const index = cursor++;
        const id = allIds[index];
        try {
          // Public bibliographic metadata is independent of published reading
          // content. Resolve the exact ID, as search/shared details do, without
          // forwarding the product bearer to the catalog's platform realm.
          const response = await catalogFetcher(apiRoutes.catalog.book(id), {
            credentials: "same-origin",
            signal,
          });
          if (!response.ok) throw new Error();
          const book = (await response.json()) as CatalogBook;
          if (!book.canonicalTitle) throw new Error();
          let progress = 0;
          try {
            const response = await fetcher(apiRoutes.reader.progress(id), {
              signal,
            });
            if (!response.ok) throw new Error();
            const rows = (await response.json()) as Array<{ percent?: number }>;
            progress = Math.min(
              100,
              Math.max(0, Number(rows[0]?.percent) || 0),
            );
          } catch {
            warnings.push(
              "Alguns progressos de leitura não puderam ser consultados.",
            );
          }
          books[index] = {
            id,
            title: book.canonicalTitle,
            ...bookMetadata(book),
            genre: categoryLabel(book.categories),
            categories: book.categories ?? [],
            language: book.originalLanguage,
            description: book.description ?? "",
            coverUrl: book.thumbnailUrl ?? undefined,
            minutes: 0,
            progress,
            available: true,
          };
        } catch {
          if (signal?.aborted) throw new Error("Carregamento cancelado.");
          books[index] = {
            id,
            title: "Livro indisponível",
            author: "Metadados indisponíveis",
            genre: "",
            description: "",
            minutes: 0,
            progress: 0,
            available: false,
          };
          warnings.push(
            "Alguns livros estão indisponíveis. Você ainda pode removê-los da estante.",
          );
        }
      }
    }),
  );
  if (signal?.aborted) throw new Error("Carregamento cancelado.");
  return { books, saved, recent, warnings: [...new Set(warnings)] };
}
import { apiRoutes } from "../app/apiRoutes";
