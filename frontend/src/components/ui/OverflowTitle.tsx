import { useLayoutEffect, useRef, useState, type CSSProperties } from 'react';

export function OverflowTitle({ text, className = '', as: Element = 'span' }: { text: string; className?: string; as?: 'span' | 'strong' }) {
  const viewport = useRef<HTMLElement>(null);
  const content = useRef<HTMLSpanElement>(null);
  const [overflow, setOverflow] = useState(0);
  useLayoutEffect(() => {
    const outer = viewport.current;
    const inner = content.current;
    if (!outer || !inner) return;
    const measure = () => setOverflow(Math.max(0, inner.scrollWidth - outer.clientWidth));
    measure();
    const observer = new window.ResizeObserver(measure);
    observer.observe(outer); observer.observe(inner);
    return () => observer.disconnect();
  }, [text]);
  const style = { '--br-title-shift': `${-overflow}px`, '--br-title-duration': `${Math.max(3, overflow / 35 + 2)}s` } as CSSProperties;
  return <Element ref={viewport} title={text} style={style} className={`br-overflow-title${overflow > 1 ? ' br-overflow-title--scrollable' : ''} ${className}`}><span ref={content} className="br-overflow-title__text">{text}</span></Element>;
}
