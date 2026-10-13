import type { LibraryBook } from './library';

export type SearchResult = { books: LibraryBook[]; totalItems: number; totalPages: number; page: number };
type Fetcher = (url: string, init?: RequestInit) => Promise<Response>;
type CatalogBook = { id: string; canonicalTitle: string; originalTitle?: string; originalLanguage?: string; description?: string; thumbnailUrl?: string };

export async function searchCatalog(query: string, page = 0, signal?: AbortSignal, fetcher: Fetcher = fetch): Promise<SearchResult> {
  const params = new URLSearchParams({ q: query.trim(), page: String(Math.max(0, page)), size: '20' });
  const response = await fetcher(`/api/v1/books?${params.toString()}`, { credentials: 'same-origin', signal });
  if (!response.ok) throw new Error(response.status === 401 ? 'Sua sessão expirou. Entre novamente.' : 'Não foi possível buscar os livros. Tente novamente.');
  const payload = await response.json() as { items: CatalogBook[]; totalItems: number; totalPages: number; page: number };
  return {
    books: payload.items.map(book => ({ id: book.id, title: book.canonicalTitle, author: 'Catálogo BookRush', genre: book.originalLanguage?.toUpperCase() ?? 'Clássico', description: book.description ?? '', coverUrl: book.thumbnailUrl, minutes: 0, progress: 0, available: true })),
    totalItems: payload.totalItems,
    totalPages: payload.totalPages,
    page: payload.page,
  };
}
