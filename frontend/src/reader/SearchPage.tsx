import { useEffect, useState } from 'react';
import { BookCard } from '../components/book/BookCard';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Icon } from '../components/ui/Icon';
import { CarouselControl } from '../components/ui/CarouselControl';
import type { LibraryBook } from './library';
import { UserSearchResults } from './UserSearchResults';
import { searchCatalog, type SearchResult } from './search';

type Props = { visible: boolean; query: string; onQuery: (value: string) => void; saved: string[]; pending: string[]; message: string; onSave: (id: string) => void; onOpen: (book: LibraryBook) => void; onProfile: (subject: string) => void };
export function SearchPage({ visible, query, onQuery, saved, pending, message, onSave, onOpen, onProfile }: Props) {
  const [kind, setKind] = useState<"books" | "people">("books");
  const [page, setPage] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [data, setData] = useState<SearchResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => { setPage(0); }, [query]);
  useEffect(() => {
    if (!visible || kind !== "books") return;
    const controller = new AbortController();
    setLoading(true); setError('');
    const timer = window.setTimeout(() => {
      void searchCatalog(query, page, controller.signal).then(result => {
        if (!controller.signal.aborted) setData(result);
      }).catch(error => { if (!controller.signal.aborted) setError(error.message); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    }, query.trim() ? 300 : 0);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [query, page, attempt, visible, kind]);
  return <section aria-busy={kind === "books" && loading}>
    <h1>Encontre sua próxima história</h1><p className="br-muted">Busque livros por título ou descrição e pessoas por nome ou @usuário.</p>
    <form className="br-search-field" role="search" onSubmit={event => { event.preventDefault(); setAttempt(current => current + 1); }}>
      <Icon name="search" /><label htmlFor="catalog-search" className="br-sr-only">Buscar livros ou pessoas</label><input id="catalog-search" className="br-search-input" value={query} placeholder={kind === "people" ? "Um nome ou @usuário…" : "Um livro, uma ideia…"} maxLength={kind === "people" ? 100 : undefined} onChange={event => onQuery(event.target.value)} />
      {query && <button type="button" aria-label="Limpar busca" onClick={() => onQuery('')}><Icon name="close" /></button>}
    </form>
    <div role="group" aria-label="Tipo de busca"><Button variant={kind === "books" ? "primary" : "ghost"} aria-pressed={kind === "books"} onClick={() => setKind("books")}>Livros</Button><Button variant={kind === "people" ? "primary" : "ghost"} aria-pressed={kind === "people"} onClick={() => setKind("people")}>Pessoas</Button></div>
    {kind === "people" ? <UserSearchResults key={query} visible={visible} query={query} onProfile={onProfile} requestVersion={attempt} /> : loading ? <p role="status">Buscando livros…</p> : error ? <Card className="br-live-empty"><p role="alert">{error}</p><Button onClick={() => setAttempt(current => current + 1)}>Tentar novamente</Button></Card> : !data?.books.length ? <Card className="br-live-empty"><h2>Nenhum livro encontrado</h2><p>Tente outro título ou palavra.</p><Button variant="ghost" onClick={() => onQuery('')}>Explorar catálogo</Button></Card> : <>
      <div className="br-search-summary"><p role="status">{data.totalItems} {data.totalItems === 1 ? 'livro encontrado' : 'livros encontrados'}</p><span>Ordenados por título</span></div>
      <div className="br-search-results">{data.books.map(book => <Card className="br-search-result" key={book.id}>
        <div className="br-search-result__cover"><BookCard book={book} onOpen={() => onOpen(book)} disabled={pending.includes(book.id)} /></div>
        <div className="br-search-result__copy"><h2>{book.title}</h2><p>{book.author} · {book.genre}</p>{book.description && <p className="br-search-result__description">{book.description}</p>}</div>
        <div className="br-search-result__actions"><Button variant={saved.includes(book.id) ? 'secondary' : 'ghost'} size="sm" disabled={pending.includes(book.id)} aria-pressed={saved.includes(book.id)} aria-label={`${saved.includes(book.id) ? 'Remover' : 'Salvar'} ${book.title} na biblioteca`} onClick={() => onSave(book.id)}><Icon name="bookmark" />{saved.includes(book.id) ? 'Salvo' : 'Salvar'}</Button><Button size="sm" disabled={pending.includes(book.id)} onClick={() => onOpen(book)}>Ler agora</Button></div>
      </Card>)}</div>
      {data.totalPages > 1 && <nav className="br-search-pagination" aria-label="Páginas de resultados"><CarouselControl direction="previous" label="Página anterior" disabled={page === 0} onClick={() => setPage(current => current - 1)} /><span>Página {data.page + 1} de {data.totalPages}</span><CarouselControl direction="next" label="Próxima página" disabled={page >= data.totalPages - 1} onClick={() => setPage(current => current + 1)} /></nav>}
    </>}
    <p role="status" className="br-feed-message">{message}</p>
  </section>;
}
