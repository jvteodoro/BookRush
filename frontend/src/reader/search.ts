import type { LibraryBook } from './library';

export type SearchResult = { books: LibraryBook[]; totalItems: number; totalPages: number; page: number };
type Fetcher = (url: string, init?: RequestInit) => Promise<Response>;
type CatalogBook = { id: string; canonicalTitle: string; originalTitle?: string; originalLanguage?: string; description?: string; thumbnailUrl?: string };
const snapshots = new WeakMap<Fetcher, Promise<CatalogBook[]>>();
const normalize = (text: string) => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('pt-BR');

async function catalogSnapshot(fetcher: Fetcher): Promise<CatalogBook[]> {
  let snapshot = snapshots.get(fetcher);
  if (!snapshot) {
    snapshot = (async () => {
      const fetchPage = async (page: number) => {
        const response = await fetcher(`/api/v1/books?page=${page}&size=100`, { credentials: 'same-origin' });
        if (!response.ok) throw new Error('Não foi possível buscar os livros. Tente novamente.');
        return response.json() as Promise<{ items: CatalogBook[]; totalPages: number }>;
      };
      const first = await fetchPage(0);
      const pages: CatalogBook[][] = [first.items];
      let cursor = 1;
      await Promise.all(Array.from({ length: Math.min(4, Math.max(0, first.totalPages - 1)) }, async () => {
        while (cursor < first.totalPages) { const page = cursor++; pages[page] = (await fetchPage(page)).items; }
      }));
      return pages.flat();
    })();
    snapshots.set(fetcher, snapshot);
    void snapshot.catch(() => snapshots.delete(fetcher));
  }
  return snapshot;
}

export async function searchCatalog(query: string, page = 0, signal?: AbortSignal, fetcher: Fetcher = fetch): Promise<SearchResult> {
  // The published filtered endpoint currently fails. Search the full public
  // catalog locally, without Bearer or mock data, and reuse it for this session.
  // Loading is shared across keystrokes; cancellation discards only the result.
  const catalog = await catalogSnapshot(fetcher);
  if (signal?.aborted) throw new Error('Busca cancelada.');
  const term = normalize(query.trim());
  const matches = catalog.filter(book => !term || normalize(`${book.canonicalTitle} ${book.originalTitle ?? ''} ${book.description ?? ''}`).includes(term));
  const totalPages = Math.ceil(matches.length / 20);
  const currentPage = Math.min(Math.max(0, page), Math.max(0, totalPages - 1));
  return { books: matches.slice(currentPage * 20, (currentPage + 1) * 20).map(book => ({ id: book.id, title: book.canonicalTitle, author: 'Catálogo BookRush', genre: book.originalLanguage?.toUpperCase() ?? 'Clássico', description: book.description ?? '', coverUrl: book.thumbnailUrl, minutes: 0, progress: 0, available: true })), totalItems: matches.length, totalPages, page: currentPage };
}
