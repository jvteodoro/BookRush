import { useEffect, useState } from 'react';
import { BookRow } from '../components/book/BookRow';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Chip } from '../components/ui/Chip';
import { loadLibrary, type LibraryData, type LibraryBook } from './library';

type Props = { onDetails?: (book: LibraryBook) => void; visible: boolean; saved: string[]; pending: string[]; message: string; onLoaded: (ids: string[]) => void; onOpen: (book: LibraryBook) => void; onRemove: (id: string) => void; onDiscover: () => void };
export function LibraryPage({ visible, saved, pending, message, onLoaded, onOpen, onRemove, onDiscover, onDetails }: Props) {
  const [data, setData] = useState<LibraryData | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [filter, setFilter] = useState<'saved' | 'reading' | 'recent'>('saved');
  useEffect(() => {
    if (!visible) return;
    const controller = new AbortController();
    setLoading(true); setError('');
    void loadLibrary(controller.signal).then(result => {
      if (controller.signal.aborted) return;
      setData(result); onLoaded(result.saved);
    }).catch(error => { if (!controller.signal.aborted) setError(error.message); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [attempt, onLoaded, visible]);
  const byId = new Map(data?.books.map(book => [book.id, book]));
  const list = (filter === 'recent' ? data?.recent.map(id => byId.get(id)).filter((book): book is LibraryBook => Boolean(book)) : data?.books.filter(book => filter === 'saved' ? saved.includes(book.id) : book.progress > 0 && book.progress < 100)) ?? [];
  return <section aria-busy={loading}>
    <h1>Minha biblioteca</h1><p className="br-muted">Seus livros salvos e leituras em andamento, em um só lugar.</p>
    <div className="br-genres" aria-label="Filtrar biblioteca">{([['saved', 'Salvos'], ['reading', 'Em leitura'], ['recent', 'Recentes']] as const).map(([id, label]) => <Chip key={id} active={filter === id} onClick={() => setFilter(id)}>{label}</Chip>)}</div>
    {loading ? <p role="status">Carregando sua estante…</p> : error ? <Card className="br-live-empty"><p role="alert">{error}</p><Button onClick={() => setAttempt(current => current + 1)}>Tentar novamente</Button></Card> : !list.length ? <Card className="br-live-empty"><h2>Nenhum livro aqui ainda</h2><p>{filter === 'saved' ? 'Salve um livro em Para você para começar sua estante.' : filter === 'reading' ? 'Suas leituras com progresso aparecerão aqui.' : 'Os livros que você abrir aparecerão aqui.'}</p><Button onClick={onDiscover}>Descobrir uma leitura</Button></Card> : <BookRow title={filter === 'saved' ? `${list.length} livros na estante` : filter === 'reading' ? 'Continue de onde parou' : 'Abertos recentemente'} books={list} onOpen={book => { const entry = list.find(item => item.id === book.id); if (entry?.available) onOpen(entry); }} disabledIds={list.filter(book => !book.available || pending.includes(book.id)).map(book => book.id)} renderActions={book => <>{onDetails && list.find(item => item.id === book.id)?.available && <Button variant="ghost" size="sm" onClick={() => onDetails(list.find(item => item.id === book.id)!)}>Sobre e avaliações</Button>}{saved.includes(book.id) ? <Button variant="ghost" size="sm" disabled={pending.includes(book.id)} onClick={() => onRemove(book.id)} aria-label={`Remover ${book.title} da biblioteca`}>Remover da estante</Button> : null}</>} />}
    {!loading && data?.warnings.length ? <div className="br-library-warning"><p role="status">{data.warnings.join(' ')}</p><Button variant="ghost" size="sm" onClick={() => setAttempt(current => current + 1)}>Atualizar estante</Button></div> : null}
    <p role="status" className="br-feed-message">{message}</p>
  </section>;
}
