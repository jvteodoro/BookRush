import { ProgressBar } from '../ui/ProgressBar';

export type ReaderBook = { id: string; title: string; author: string; genre: string; progress: number; minutes: number; description: string; coverUrl?: string };
export function BookCard({ book, onOpen }: { book: ReaderBook; onOpen: (book: ReaderBook) => void }) {
  const palette = [...book.id].reduce((sum, character) => sum + character.charCodeAt(0), 0) % 3;
  return <button type="button" className="br-book-card" onClick={() => onOpen(book)} aria-label={`Abrir ${book.title}, de ${book.author}`}>
    <div className={`br-book-cover br-book-cover--${palette}`}>
      {book.coverUrl ? <img src={book.coverUrl} alt="" loading="lazy" /> : <><span className="br-book-cover__edition">BOOKRUSH · CLÁSSICOS</span><span className="br-book-cover__title">{book.title}</span><span className="br-book-cover__author">{book.author}</span></>}
    </div>
    <div className="br-book-card__meta"><strong>{book.title}</strong><span>{book.author}</span>
      {book.progress > 0 ? <><ProgressBar value={book.progress} label={`Progresso em ${book.title}`} /><small>{book.progress}% concluído</small></> : <small>{book.minutes} min de leitura</small>}
    </div>
  </button>;
}
