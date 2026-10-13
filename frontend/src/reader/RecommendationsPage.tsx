import type { ReaderBook } from '../components/book/BookCard';
import { BookRow } from '../components/book/BookRow';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Icon } from '../components/ui/Icon';

type Props = { books: ReaderBook[]; saved: string[]; pending: string[]; message: string; onSave: (id: string) => void; onOpen: (book: ReaderBook) => void; onLucky: () => void; onSearch: () => void };
export function RecommendationsPage({ books, saved, pending, message, onSave, onOpen, onLucky, onSearch }: Props) {
  const renderActions = (book: ReaderBook) => <Button variant={saved.includes(book.id) ? 'secondary' : 'ghost'} size="sm" disabled={pending.includes(book.id)} aria-pressed={saved.includes(book.id)} aria-label={`${saved.includes(book.id) ? 'Remover' : 'Salvar'} ${book.title} na biblioteca`} onClick={() => onSave(book.id)}><Icon name="bookmark" />{saved.includes(book.id) ? 'Salvo' : 'Salvar'}</Button>;
  return <section>
    <div className="br-recommendations-heading"><div><span className="br-kicker">NOVAS DESCOBERTAS</span><h1>Descubra sua próxima leitura</h1><p className="br-muted">Histórias para explorar, salvar e ler no seu tempo.</p></div><Button variant="secondary" onClick={onLucky} disabled={!books.length}><Icon name="sparkle" />Estou com sorte</Button></div>
    <Card className="br-recommendations-hero"><div><span className="br-kicker">UM NOVO CAPÍTULO</span><h2>Deixe uma boa história encontrar você.</h2><p>Explore os livros da sua seleção e guarde suas próximas leituras na biblioteca.</p><Button variant="ghost" size="sm" onClick={onSearch}>Buscar uma história <Icon name="arrow" /></Button></div><div className="br-recommendations-orbit" aria-hidden="true"><Icon name="sparkle" width={52} height={52} /><strong>{books.length}</strong><span>livros para descobrir</span></div></Card>
    {books.length ? <><BookRow title="Escolhidos para você" books={books.slice(0, 5)} onOpen={onOpen} disabledIds={pending} renderActions={renderActions} />{books.length > 5 && <BookRow title="Mais histórias para explorar" books={books.slice(5)} onOpen={onOpen} disabledIds={pending} renderActions={renderActions} />}</> : <Card className="br-live-empty"><h2>Nenhuma recomendação disponível por enquanto</h2><p>Você ainda pode explorar os livros do catálogo.</p><Button onClick={onSearch}>Ir para Buscar</Button></Card>}
    <p className="br-feed-message" role="status">{message}</p>
  </section>;
}
