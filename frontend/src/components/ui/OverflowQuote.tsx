import { useLayoutEffect, useRef, useState, type CSSProperties } from 'react';
import { quoteText } from './quoteText';

export function OverflowQuote({ text }: { text: string }) {
  const displayText = quoteText(text);
  const viewport = useRef<HTMLDivElement>(null);
  const measure = useRef<HTMLParagraphElement>(null);
  const [layout, setLayout] = useState({ overflow: 0, lines: 4 });
  useLayoutEffect(() => {
    const outer = viewport.current;
    const inner = measure.current;
    if (!outer || !inner) return;
    const update = () => {
      const lineHeight = Number.parseFloat(window.getComputedStyle(inner).lineHeight) || 32;
      setLayout({ overflow: Math.max(0, inner.scrollHeight - outer.clientHeight), lines: Math.max(1, Math.floor(outer.clientHeight / lineHeight)) });
    };
    update();
    const observer = new window.ResizeObserver(update);
    observer.observe(outer); observer.observe(inner);
    return () => observer.disconnect();
  }, [text]);
  const style = { '--br-quote-lines': layout.lines } as CSSProperties;
  return <div ref={viewport} className={`br-excerpt-text br-quote-window br-scroll-area${layout.overflow > 1 ? ' br-quote-window--overflow' : ''}`} style={style} tabIndex={layout.overflow > 1 ? 0 : undefined} aria-label={text} onMouseLeave={event => { if (!event.currentTarget.matches(':focus')) event.currentTarget.scrollTop = 0; }} onBlur={event => { event.currentTarget.scrollTop = 0; }}>
    <p className="br-quote-text" aria-hidden="true">{displayText}</p><p ref={measure} className="br-quote-measure" aria-hidden="true">{displayText}</p>
  </div>;
}
