import { StrictMode, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { authClient, type AuthState } from './auth';
import './styles.css';

type Book = {
  id: string;
  canonicalTitle: string;
  originalLanguage?: string | null;
  description?: string | null;
};
type Page = { items: Book[] };

const palettes = [
  ['coral', '#c9654e'], ['indigo', '#384e75'], ['sage', '#66816e'],
  ['ochre', '#b28c48'], ['plum', '#7d5265'],
] as const;

async function requestCatalog(query = ''): Promise<Book[]> {
  const params = new URLSearchParams({ page: '0', size: '50' });
  if (query.trim()) params.set('q', query.trim());
  const response = await authClient.fetch(`/api/v1/books?${params}`);
  if (!response.ok) throw new Error(response.status === 401
    ? 'Sua sessão expirou. Entre novamente.' : `Catálogo indisponível (HTTP ${response.status}).`);
  return (await response.json() as Page).items;
}

async function requestLibrary(): Promise<string[]> {
  const response = await authClient.fetch('/api/v1/reader/library');
  if (!response.ok) return [];
  const rows = await response.json() as Array<{ book_id: string }>;
  return rows.map(row => row.book_id);
}

function App() {
  const [auth, setAuth] = useState<AuthState>({ status: 'loading' });
  const [books, setBooks] = useState<Book[]>([]);
  const [library, setLibrary] = useState<string[]>([]);
  const [query, setQuery] = useState('');
  const [view, setView] = useState<'discover' | 'library'>('discover');
  const [selected, setSelected] = useState<Book>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        if (window.location.pathname === '/auth/callback') {
          const callback = await authClient.callback();
          window.history.replaceState({}, '', callback.returnTo);
          if (active) setAuth({ status: 'authenticated', user: callback.user });
          return;
        }
        if (window.location.pathname === '/auth/silent-callback') { await authClient.silentCallback(); return; }
        const user = await authClient.currentUser();
        if (active) setAuth(user ? { status: 'authenticated', user } : { status: 'anonymous' });
      } catch (cause) {
        if (active) setAuth({ status: 'error', error: cause instanceof Error ? cause.message : 'Falha ao restaurar a sessão.' });
      }
    })();
    return () => { active = false; };
  }, []);

  async function load(value = query) {
    setBusy(true); setError('');
    try { setBooks(await requestCatalog(value)); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Não foi possível carregar o catálogo.'); }
    finally { setBusy(false); }
  }

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    void Promise.all([load(''), requestLibrary().then(setLibrary)]);
  }, [auth.status]);

  const visibleBooks = useMemo(() => view === 'library'
    ? books.filter(book => library.includes(book.id)) : books, [books, library, view]);

  async function toggleLibrary(book: Book) {
    const saved = library.includes(book.id);
    const response = await authClient.fetch(`/api/v1/reader/library/${encodeURIComponent(book.id)}`, { method: saved ? 'DELETE' : 'PUT' });
    if (response.ok) setLibrary(current => saved ? current.filter(id => id !== book.id) : [...current, book.id]);
  }

  async function openBook(book: Book) {
    setSelected(book);
    await authClient.fetch(`/api/v1/reader/books/${encodeURIComponent(book.id)}/opened`, { method: 'POST' });
    await authClient.fetch('/api/v1/behavior/events', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ events: [{ eventKey: `BOOK_OPEN:${book.id}:${Date.now()}`, eventType: 'BOOK_OPEN', bookId: book.id, occurredAt: new Date().toISOString(), payload: {} }] }),
    });
  }

  async function login() {
    setAuth({ status: 'loading' });
    try { await authClient.login('/'); }
    catch (cause) { setAuth({ status: 'error', error: cause instanceof Error ? cause.message : 'Não foi possível iniciar o login.' }); }
  }

  if (auth.status === 'authenticated') return <div className="app-shell">
    <header className="topbar">
      <button className="brand" onClick={() => { setView('discover'); void load(''); }}><span className="brand-mark">B</span> BOOKRUSH</button>
      <div className="top-actions"><span className="user-name">{auth.user?.profile.preferred_username ?? auth.user?.profile.email ?? 'Leitor'}</span><button className="ghost-button" onClick={() => void authClient.logout()}>Sair</button></div>
    </header>
    <main className="content">
      <section className="hero"><div><span className="eyebrow">UM UNIVERSO DE HISTÓRIAS</span><h1>Descubra sua<br /><em>próxima leitura.</em></h1><p>Livros reais do catálogo BookRush, selecionados para você.</p></div><div className="hero-orbit"><span>✦</span><span>☼</span><span>◌</span></div></section>
      <nav className="view-tabs"><button className={view === 'discover' ? 'active' : ''} onClick={() => setView('discover')}>Explorar <small>{books.length}</small></button><button className={view === 'library' ? 'active' : ''} onClick={() => setView('library')}>Minha biblioteca <small>{library.length}</small></button></nav>
      <form className="search-bar" onSubmit={event => { event.preventDefault(); void load(); }}><span>⌕</span><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar por título, autor ou assunto" /><button type="submit">Buscar</button></form>
      {error && <div className="notice error">{error}<button onClick={() => void load()}>Tentar novamente</button></div>}
      {busy ? <div className="loading-row"><span className="spinner" /> Carregando catálogo…</div> : <section className="book-grid">{visibleBooks.map((book, index) => { const [tone, color] = palettes[index % palettes.length]; return <article className="book-card" key={book.id}><button className={`cover ${tone}`} style={{ background: color }} onClick={() => void openBook(book)}><span className="cover-symbol">✦</span><strong>{book.canonicalTitle}</strong><small>{book.originalLanguage?.toUpperCase() ?? 'BOOKRUSH'}</small></button><div className="book-meta"><div><h3>{book.canonicalTitle}</h3><p>{book.originalLanguage?.toUpperCase() ?? 'Catálogo BookRush'}</p></div><button className={`save-button ${library.includes(book.id) ? 'saved' : ''}`} onClick={() => void toggleLibrary(book)} aria-label={library.includes(book.id) ? 'Remover da biblioteca' : 'Salvar na biblioteca'}>{library.includes(book.id) ? '♥' : '♡'}</button></div><p className="description">{book.description ?? 'Descrição ainda não disponível.'}</p><button className="read-link" onClick={() => void openBook(book)}>Abrir livro <span>→</span></button></article>; })}{!visibleBooks.length && <div className="empty"><span>◌</span><h2>{view === 'library' ? 'Sua biblioteca está vazia' : 'Nenhum livro encontrado'}</h2><p>{view === 'library' ? 'Salve livros para encontrá-los aqui.' : 'Tente outro termo de busca.'}</p></div>}</section>}
    </main>
    <nav className="mobile-nav"><button className={view === 'discover' ? 'active' : ''} onClick={() => setView('discover')}>⌂<span>Explorar</span></button><button className={view === 'library' ? 'active' : ''} onClick={() => setView('library')}>♡<span>Biblioteca</span></button></nav>
    {selected && <div className="reader-backdrop" role="dialog" aria-modal="true"><article className="reader"><button className="close-button" onClick={() => setSelected(undefined)} aria-label="Fechar">×</button><div className="reader-cover"><span className="eyebrow">LEITURA BOOKRUSH</span><strong>{selected.canonicalTitle}</strong><small>{selected.originalLanguage?.toUpperCase() ?? 'CATÁLOGO'}</small></div><span className="reader-facts">Texto reader-ready</span><h2>{selected.canonicalTitle}</h2><p>{selected.description ?? 'O conteúdo deste livro será carregado pela API de conteúdo.'}</p><div className="reader-actions"><button className="primary-button" onClick={() => setSelected(undefined)}>Começar leitura →</button><button className="ghost-button" onClick={() => void toggleLibrary(selected)}>{library.includes(selected.id) ? 'Remover da biblioteca' : 'Salvar na biblioteca'}</button></div></article></div>}
  </div>;
  if (auth.status === 'loading') return <div className="splash"><div className="brand-mark">B</div><p>Preparando seu feed…</p></div>;
  return <main className="landing"><div className="landing-art"><span className="brand-mark large">B</span><div className="floating-word">READ<br /><em>MORE</em></div></div><span className="eyebrow">UM UNIVERSO DE HISTÓRIAS</span><h1>Seu próximo<br /><em>capítulo</em> começa aqui.</h1><p>Descubra livros reais do catálogo BookRush.</p>{auth.status === 'error' && <div className="notice error">{auth.error}</div>}<button className="primary-button" onClick={() => void login()}>Entrar com Keycloak <span>→</span></button><a className="register-link" href={authClient.registrationUrl()}>Ainda não tem conta? <strong>Cadastre-se</strong></a></main>;
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
