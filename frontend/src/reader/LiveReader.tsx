import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppShell, type ReaderTab } from '../components/layout/AppShell';
import { ExcerptFeed } from '../components/book/ExcerptFeed';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { authClient } from '../auth';

type SourceBook = { id: string; title: string; author: string; genre: string; quote: string; progress: number; thumbnailUrl?: string | null; excerpt?: unknown; impressionId?: string };
type Runtime = {
  __BOOKRUSH_BOOKS__: SourceBook[];
  __BOOKRUSH_LIKES__: Record<string, boolean>;
  __BOOKRUSH_STREAK__?: { currentDays?: number };
  __BOOKRUSH_API__: { saved: Set<string>; save: (id: string, enabled: boolean) => Promise<void>; like: (id: string, enabled: boolean) => Promise<void>; viewable: (id: string) => Promise<void>; trackExcerpt: (type: string, id: string, excerpt: unknown) => void };
  __BOOKRUSH_LEGACY__: { navigate: (route: string, query?: string) => void; openBook: (id: string) => Promise<void>; openComments: (id: string) => Promise<void>; openShare: (id: string) => void };
};
const runtime = () => window as unknown as Runtime;

export function LiveReader({ visible, userName, onLegacy }: { visible: boolean; userName: string; onLegacy: () => void }) {
  const [tab, setTab] = useState<ReaderTab>('feed');
  const [query, setQuery] = useState('');
  const [saved, setSaved] = useState(() => [...runtime().__BOOKRUSH_API__.saved]);
  const [liked, setLiked] = useState(() => Object.keys(runtime().__BOOKRUSH_LIKES__).filter(id => runtime().__BOOKRUSH_LIKES__[id]));
  const [pending, setPending] = useState<string[]>([]);
  const [message, setMessage] = useState('');
  const locks = useRef(new Set<string>());
  const viewed = useRef(new Set<string>());
  const books = useMemo(() => runtime().__BOOKRUSH_BOOKS__.map(book => ({ id: book.id, title: book.title, author: book.author, genre: book.genre, progress: book.progress, minutes: 0, description: book.quote, coverUrl: book.thumbnailUrl ?? undefined })), []);
  useEffect(() => {
    if (!visible) return;
    setTab('feed');
    setLiked(Object.keys(runtime().__BOOKRUSH_LIKES__).filter(id => runtime().__BOOKRUSH_LIKES__[id]));
    let active = true;
    void authClient.fetch('/api/v1/reader/library').then(async response => {
      if (!response.ok) throw new Error('Não foi possível atualizar sua biblioteca.');
      const rows = await response.json() as Array<{ book_id?: string; bookId?: string }>;
      if (active) {
        const ids = rows.map(row => row.book_id ?? row.bookId).filter((id): id is string => Boolean(id));
        runtime().__BOOKRUSH_API__.saved.clear();
        ids.forEach(id => runtime().__BOOKRUSH_API__.saved.add(id));
        setSaved(ids);
      }
    }).catch(error => { if (active) setMessage(error.message); });
    return () => { active = false; };
  }, [visible]);
  const action = async (id: string, operation: () => Promise<void>) => {
    if (locks.current.has(id)) return;
    locks.current.add(id); setPending(current => [...current, id]); setMessage('');
    try { await operation(); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível concluir a ação.'); }
    finally { locks.current.delete(id); setPending(current => current.filter(item => item !== id)); }
  };
  const save = (id: string) => void action(id, async () => {
    const enabled = !saved.includes(id);
    await runtime().__BOOKRUSH_API__.save(id, enabled);
    setSaved(current => enabled ? [...current, id] : current.filter(item => item !== id));
    setMessage(enabled ? 'Livro salvo na sua biblioteca.' : 'Livro removido da biblioteca.');
  });
  const like = (id: string) => void action(id, async () => {
    const enabled = !liked.includes(id);
    await runtime().__BOOKRUSH_API__.like(id, enabled);
    runtime().__BOOKRUSH_LIKES__[id] = enabled;
    setLiked(current => enabled ? [...current, id] : current.filter(item => item !== id));
  });
  const onViewable = useCallback((id: string) => {
    if (viewed.current.has(id)) return;
    const book = runtime().__BOOKRUSH_BOOKS__.find(item => item.id === id);
    if (!book) return;
    viewed.current.add(id);
    if (book.excerpt) runtime().__BOOKRUSH_API__.trackExcerpt('EXCERPT_IMPRESSION', id, book.excerpt);
    if (book.impressionId) void runtime().__BOOKRUSH_API__.viewable(id).catch(() => { viewed.current.delete(id); });
  }, []);
  const navigate = (tab: ReaderTab) => {
    if (tab === 'feed' || tab === 'prototype') { setTab(tab); return; }
    runtime().__BOOKRUSH_LEGACY__.navigate(tab, query); onLegacy();
  };
  const streak = runtime().__BOOKRUSH_STREAK__?.currentDays;
  return <AppShell active={tab} query={query} onSearch={setQuery} onNavigate={navigate} userName={userName}>
    {tab === 'prototype' ? <Card className="br-profile-card"><h1>Menu do protótipo</h1><p>Escolha uma área do BookRush.</p><div className="br-preview__row"><Button onClick={() => navigate('feed')}>Para você</Button><Button variant="ghost" onClick={() => navigate('admin')}>Administração</Button><Button variant="ghost" onClick={() => navigate('publisher')}>Publicador</Button></div></Card> : <ExcerptFeed books={books} saved={saved} onSave={save} onOpen={book => void action(book.id, async () => { await runtime().__BOOKRUSH_LEGACY__.openBook(book.id); onLegacy(); })} live={{ liked, pending, message, onLike: like, onComments: id => void action(id, () => runtime().__BOOKRUSH_LEGACY__.openComments(id)), onShare: id => runtime().__BOOKRUSH_LEGACY__.openShare(id), onViewable, sidebar: <>
      <Card><h2>{typeof streak === 'number' ? `${streak} dias de sequência` : 'Sua leitura'}</h2><p>Continue seu hábito, uma história de cada vez.</p></Card>
      <Card><h2>Feito para você</h2><p>Descubra histórias do seu catálogo e salve as que quiser ler depois.</p></Card>
      <Card><h2>Sua biblioteca</h2><p>{saved.length} {saved.length === 1 ? 'livro salvo' : 'livros salvos'} para sua próxima leitura.</p></Card>
    </> }} />}
  </AppShell>;
}
