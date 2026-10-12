import { useEffect, useId, useRef, useState } from 'react';
import { BookCard, type ReaderBook } from './BookCard';
import { CarouselControl } from '../ui/CarouselControl';

export function BookRow({ title, books, onOpen }: { title: string; books: ReaderBook[]; onOpen: (book: ReaderBook) => void }) {
  const id = useId();
  const track = useRef<HTMLDivElement>(null);
  const [edges, setEdges] = useState({ previous: false, next: false });
  useEffect(() => {
    const element = track.current;
    if (!element) { setEdges({ previous: false, next: false }); return; }
    const update = () => setEdges({ previous: element.scrollLeft > 2, next: element.scrollLeft + element.clientWidth < element.scrollWidth - 2 });
    update();
    element.addEventListener('scroll', update, { passive: true });
    const observer = new window.ResizeObserver(update);
    observer.observe(element);
    return () => { element.removeEventListener('scroll', update); observer.disconnect(); };
  }, [books]);
  const scroll = (direction: number) => {
    const element = track.current;
    element?.scrollBy({ left: direction * element.clientWidth * .8, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
  };
  return <section className="br-book-row" aria-labelledby={id}>
    <div className="br-book-row__heading"><h2 id={id}>{title}</h2></div>
    {books.length ? <div className="br-book-row__carousel">
      <div ref={track} className="br-book-row__track">{books.map(book => <BookCard key={book.id} book={book} onOpen={onOpen} />)}</div>
      {edges.previous && <div className="br-book-row__edge br-book-row__edge--previous br-book-row__edge--visible"><CarouselControl direction="previous" label={`Rolar ${title} para a esquerda`} onClick={() => scroll(-1)} /></div>}
      {edges.next && <div className="br-book-row__edge br-book-row__edge--next br-book-row__edge--visible"><CarouselControl direction="next" label={`Rolar ${title} para a direita`} onClick={() => scroll(1)} /></div>}
    </div> : <p className="br-empty">Nenhum livro nesta seleção por enquanto.</p>}
  </section>;
}
