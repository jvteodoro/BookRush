import { useEffect, useRef, useState, type ReactNode } from 'react';
import type { ReaderBook } from './BookCard';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { Icon } from '../ui/Icon';

type Props = { books: ReaderBook[]; saved: string[]; onSave: (id: string) => void; onOpen: (book: ReaderBook) => void; live?: { liked: string[]; pending: string[]; onLike: (id: string) => void; onComments: (id: string) => void; onShare: (id: string) => void; onViewable: (id: string) => void; sidebar: ReactNode; message: string } };
export function ExcerptFeed({ books, saved, onSave, onOpen, live }: Props) {
  const [demoLiked, setLiked] = useState<string[]>([]);
  const liked = live?.liked ?? demoLiked;
  const [commentsFor, setCommentsFor] = useState<string | null>(null);
  const [comments, setComments] = useState<Record<string, string[]>>({});
  const [draft, setDraft] = useState('');
  const [message, setMessage] = useState('');
  const scroll = useRef<HTMLDivElement>(null);
  const onViewable = live?.onViewable;
  useEffect(() => {
    const element = scroll.current;
    if (!element || !onViewable) return;
    const observer = new window.IntersectionObserver(entries => {
      for (const entry of entries) {
        if (entry.isIntersecting && entry.intersectionRatio >= .5) {
          const id = (entry.target as HTMLElement).dataset.bookId;
          if (id) onViewable(id);
          observer.unobserve(entry.target);
        }
      }
    }, { root: element, threshold: .5 });
    element.querySelectorAll('.br-excerpt-card').forEach(card => observer.observe(card));
    return () => observer.disconnect();
  }, [books, onViewable]);
  useEffect(() => {
    const element = scroll.current;
    if (!element) return;
    let gestureActive = false;
    let resetTimer: ReturnType<typeof setTimeout>;
    let animationFrame = 0;
    let moving = false;
    const move = (direction: number) => {
      if (moving) return;
      const cards = Array.from(element.querySelectorAll<HTMLElement>('.br-excerpt-card'));
      if (!cards.length) return;
      const top = (card: HTMLElement) => card.getBoundingClientRect().top - element.getBoundingClientRect().top + element.scrollTop;
      const current = cards.reduce((best, card, index) => Math.abs(top(card) - element.scrollTop) < Math.abs(top(cards[best]) - element.scrollTop) ? index : best, 0);
      const next = cards[Math.min(cards.length - 1, Math.max(0, current + direction))];
      if (!next) return;
      const destination = top(next);
      const start = element.scrollTop;
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || Math.abs(destination - start) < 1) {
        element.scrollTo({ top: destination, behavior: 'instant' });
        return;
      }
      moving = true;
      element.style.scrollSnapType = 'none';
      const startedAt = window.performance.now();
      const animate = (now: number) => {
        const progress = Math.min(1, (now - startedAt) / 420);
        const eased = progress < .5 ? 4 * progress ** 3 : 1 - (-2 * progress + 2) ** 3 / 2;
        element.scrollTop = start + (destination - start) * eased;
        if (progress < 1) animationFrame = window.requestAnimationFrame(animate);
        else { element.style.scrollSnapType = ''; moving = false; }
      };
      animationFrame = window.requestAnimationFrame(animate);
    };
    const wheel = (event: WheelEvent) => {
      if (event.ctrlKey || Math.abs(event.deltaY) <= Math.abs(event.deltaX) || (event.target instanceof Element && event.target.closest('.br-feed-comments'))) return;
      event.preventDefault();
      if (!gestureActive) move(Math.sign(event.deltaY));
      gestureActive = true;
      clearTimeout(resetTimer);
      resetTimer = setTimeout(() => { gestureActive = false; }, 180);
    };
    const key = (event: KeyboardEvent) => {
      if (event.target instanceof Element && event.target.closest('input, textarea, button')) return;
      if (event.key === 'ArrowDown' || event.key === 'PageDown' || event.key === 'ArrowUp' || event.key === 'PageUp') {
        event.preventDefault(); move(event.key === 'ArrowDown' || event.key === 'PageDown' ? 1 : -1);
      }
    };
    element.addEventListener('wheel', wheel, { passive: false });
    element.addEventListener('keydown', key);
    return () => { clearTimeout(resetTimer); window.cancelAnimationFrame(animationFrame); element.style.scrollSnapType = ''; element.removeEventListener('wheel', wheel); element.removeEventListener('keydown', key); };
  }, [books]);
  return <div className={`br-feed-layout${live ? ' br-feed-layout--live' : ''}`}>
    <section className="br-feed-stage" aria-label="Feed Para você">
      <div ref={scroll} className="br-feed-scroll" tabIndex={0} aria-label={`${live ? 'Feed de leitura' : 'Trechos demonstrativos'}; role ou use as setas para ver mais livros`}>
        {books.map((book, index) => <article key={book.id} data-book-id={book.id} className={`br-excerpt-card br-excerpt-card--${index % 3}`} aria-label={book.title}>
          <div className="br-excerpt-copy"><div className="br-excerpt-meta"><span>{book.genre}</span><span>{live ? 'Para você' : 'Prévia'} · {index + 1} de {books.length}</span></div>
            <span className="br-excerpt-quote-mark" aria-hidden="true">“</span><p className="br-excerpt-text">{book.description}</p>
            <div className="br-excerpt-book"><span className="br-excerpt-mini" aria-hidden="true">{book.coverUrl ? <img src={book.coverUrl} alt="" /> : 'B'}</span><div><h2>{book.title}</h2><p>{book.author}</p></div></div>
            <Button disabled={live?.pending.includes(book.id)} onClick={() => onOpen(book)}>Ler agora →</Button>
          </div>
          <div className="br-action-rail">
            <button type="button" className="br-like" disabled={live?.pending.includes(book.id)} aria-pressed={liked.includes(book.id)} aria-label={`${liked.includes(book.id) ? 'Descurtir' : 'Curtir'} ${book.title}`} onClick={() => live ? live.onLike(book.id) : setLiked(current => current.includes(book.id) ? current.filter(id => id !== book.id) : [...current, book.id])}><span className="br-like__bubble"><Icon name="heart" /></span><small>{liked.includes(book.id) ? 'Curtido' : 'Curtir'}</small></button>
            <button type="button" aria-expanded={live ? undefined : commentsFor === book.id} onClick={() => { if (live) { live.onComments(book.id); return; } setCommentsFor(current => current === book.id ? null : book.id); setDraft(''); }}><span><Icon name="comment" /></span><small>Comentários{comments[book.id]?.length ? ` (${comments[book.id].length})` : ''}</small></button>
            <button type="button" disabled={live?.pending.includes(book.id)} aria-pressed={saved.includes(book.id)} aria-label={`${saved.includes(book.id) ? 'Remover' : 'Salvar'} ${book.title} na biblioteca`} onClick={() => onSave(book.id)}><span><Icon name="bookmark" /></span><small>{saved.includes(book.id) ? 'Salvo' : 'Salvar'}</small></button>
            <button type="button" onClick={() => live ? live.onShare(book.id) : setMessage(`Compartilhamento de “${book.title}” será conectado na integração. Esta prévia não cria links públicos.`)}><span><Icon name="share" /></span><small>Compartilhar</small></button>
          </div>
          {commentsFor === book.id && <div className="br-feed-comments"><h3>Comentários da prévia</h3>{(comments[book.id] ?? []).map((comment, commentIndex) => <p key={commentIndex}><strong>Ana: </strong>{comment}</p>)}
            <form onSubmit={event => { event.preventDefault(); if (!draft.trim()) return; setComments(current => ({ ...current, [book.id]: [...(current[book.id] ?? []), draft.trim()] })); setDraft(''); }}><label htmlFor={`comment-${book.id}`}>Seu comentário</label><input id={`comment-${book.id}`} maxLength={500} value={draft} onChange={event => setDraft(event.target.value)} /><Button size="sm" disabled={!draft.trim()} type="submit">Comentar</Button></form>
          </div>}
        </article>)}
      </div>
      {!books.length && <Card className="br-live-empty"><h2>Nenhum livro disponível</h2><p>Seu feed ainda não tem livros para mostrar.</p></Card>}
      <p className="br-feed-message" role="status">{live?.message ?? message}</p>
    </section>
    <aside className="br-feed-side" aria-label="Sua leitura">{live?.sidebar ?? <><Card><h2>12 dias de sequência</h2><p>Mais um capítulo, mais um dia cultivando seu hábito.</p><span className="br-feed-badge">6 min para a meta de hoje</span></Card><Card><h2>Feito para você</h2><p>Histórias para descobrir, salvar e continuar no seu tempo.</p><span className="br-feed-badge">Romance</span> <span className="br-feed-badge">Suspense</span></Card><Card><h2>Sua próxima leitura</h2><p>Gostou de um trecho? Salve o livro na sua biblioteca ou abra os detalhes.</p></Card></>}</aside>
  </div>;
}
