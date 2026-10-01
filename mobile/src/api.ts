export type Book = { id: string; canonicalTitle: string; originalLanguage?: string | null; firstPublicationYear?: number | null; description?: string | null; status?: string };
export type BookPage = { items: Book[]; totalItems: number; page: number; last: boolean };
const baseUrl = process.env.EXPO_PUBLIC_API_URL ?? 'https://bookrush.jteodoro.tec.br';
export async function listBooks(_accessToken: string, query = ''): Promise<BookPage> {
  const params = new URLSearchParams({ page: '0', size: '50' });
  if (query.trim()) params.set('q', query.trim());
  const response = await fetch(`${baseUrl}/api/v1/books?${params}`, { headers: { Accept: 'application/json' }, cache: 'no-store' });
  if (!response.ok) throw new Error(response.status === 401 ? 'Sessão expirada. Entre novamente.' : `Catálogo indisponível (HTTP ${response.status}).`);
  return response.json() as Promise<BookPage>;
}
