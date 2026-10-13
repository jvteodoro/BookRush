import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Button } from './Button';
import { Icon } from './Icon';

export function Drawer({ title, subtitle, children, footer, onClose }: { title: string; subtitle?: string; children: ReactNode; footer?: ReactNode; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const element = dialog.current;
    element?.showModal();
    return () => { if (element?.open) element.close(); };
  }, []);
  return createPortal(<dialog ref={dialog} className="br-drawer" aria-labelledby={titleId} onCancel={event => { event.preventDefault(); onClose(); }} onClick={event => {
    if (event.target !== event.currentTarget) return;
    const bounds = event.currentTarget.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) onClose();
  }}>
    <header className="br-drawer__header"><div><h2 id={titleId}>{title}</h2>{subtitle && <p>{subtitle}</p>}</div><Button variant="ghost" size="sm" aria-label={`Fechar ${title.toLowerCase()}`} onClick={onClose}><Icon name="close" /></Button></header>
    <div className="br-drawer__body br-scroll-area">{children}</div>
    {footer && <footer className="br-drawer__footer">{footer}</footer>}
  </dialog>, document.body);
}
