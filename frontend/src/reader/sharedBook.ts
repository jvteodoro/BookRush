import { apiRoutes } from "../app/apiRoutes";
import { bookMetadata } from "./bookMetadata";
import { categoryLabel } from "./bookCategories";
import type { CatalogBook } from "../app/types";
import type { DetailsBook } from "./BookDetailsDrawer";

export function sharedBookPath(pathname: string): string | null {
  if (!/^\/book(?:\/|$)/.test(pathname)) return null;
  const match = pathname.match(/^\/book\/([^/]+)\/?$/);
  if (!match) return "";
  try {
    return decodeURIComponent(match[1]);
  } catch {
    return "";
  }
}

export async function loadSharedBook(
  id: string,
  signal?: AbortSignal,
  fetcher: typeof fetch = fetch,
): Promise<DetailsBook> {
  if (
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)
  )
    throw new Error("Link de livro inválido.");
  const response = await fetcher(apiRoutes.catalog.book(id), {
    credentials: "same-origin",
    signal,
  });
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: "Link de livro inválido.",
      401: "Sua sessão expirou. Entre novamente.",
      403: "Você não tem permissão para acessar este livro.",
      404: "Livro não encontrado ou indisponível.",
    };
    throw new Error(
      messages[response.status] ??
        "Não foi possível carregar o livro. Tente novamente.",
    );
  }
  const book = (await response.json()) as CatalogBook;
  if (!book.canonicalTitle || book.id?.toLowerCase() !== id.toLowerCase())
    throw new Error("Livro indisponível. Tente novamente.");
  return {
    id: book.id,
    title: book.canonicalTitle,
    ...bookMetadata(book),
    genre: categoryLabel(book.categories),
    categories: book.categories ?? [],
    language: book.originalLanguage,
    description: book.description ?? "",
    coverUrl: book.thumbnailUrl ?? undefined,
  };
}
