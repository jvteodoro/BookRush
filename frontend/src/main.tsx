import { StrictMode, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { authClient, type AuthState } from './auth';
import './styles.css';

type Book = { id: string; canonicalTitle: string; originalTitle?: string | null; originalLanguage?: string | null; firstPublicationYear?: number | null; description?: string | null; status?: string };
type BookPage = { items: Book[]; totalItems: number; page: number; last: boolean };
type View = 'feed' | 'library' | 'search';

async function getBooks(query = ''): Promise<BookPage> {
  const params = new URLSearchParams({ page: '0', size: '50' });
  if (query.trim()) params.set('q', query.trim());
  const response = await authClient.fetch(`/api/internal/v1/catalog/books?${params}`);
  if (!response.ok) throw new Error(response.status === 401 ? 'Faça login para acessar o catálogo.' : `Catálogo indisponível (HTTP ${response.status}).`);
  return response.json() as Promise<BookPage>;
}

function App() {
  const [auth, setAuth] = useState<AuthState>({ status: 'loading' });
  const [view, setView] = useState<View>('feed');
  const [books, setBooks] = useState<Book[]>([]);
  const [saved, setSaved] = useState<string[]>(() => JSON.parse(localStorage.getItem('bookrush.saved') ?? '[]') as string[]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<Book | null>(null);

  useEffect(() => {
    let active = true;
    async function restore() {
      try {
        if (window.location.pathname === '/auth/silent-callback') { await authClient.silentCallback(); return; }
        if (window.location.pathname === '/auth/callback') {
          const callback = await authClient.callback();
          window.history.replaceState({}, '', callback.returnTo);
          if (active) setAuth({ status: 'authenticated', user: callback.user });
          return;
        }
        const user = await authClient.currentUser();
        if (active) setAuth(user ? { status: 'authenticated', user } : { status: 'anonymous' });
      } catch (cause) { if (active) setAuth({ status: 'error', error: cause instanceof Error ? cause.message : 'Falha ao restaurar a sessão.' }); }
    }
    void restore();
    return () => { active = false; };
  }, []);

  async function loadBooks(nextQuery = '') {
    setLoading(true); setError('');
    try { const page = await getBooks(nextQuery); setBooks(page.items); }
    catch (cause) { setBooks([]); setError(cause instanceof Error ? cause.message : 'Não foi possível carregar os livros.'); }
    finally { setLoading(false); }
  }
  useEffect(() => { if (auth.status === 'authenticated') void loadBooks(); }, [auth.status]);
  async function login() { setAuth({ status: 'loading' }); try { await authClient.login(window.location.pathname + window.location.search); } catch (cause) { setAuth({ status: 'error', error: cause instanceof Error ? cause.message : 'Não foi possível iniciar o login.' }); } }
  async function logout() { setAuth({ status: 'loading' }); await authClient.logout(); }
  function toggleSaved(id: string) { const next = saved.includes(id) ? saved.filter(item => item !== id) : [...saved, id]; setSaved(next); localStorage.setItem('bookrush.saved', JSON.stringify(next)); }
  const visibleBooks = useMemo(() => view === 'library' ? books.filter(book => saved.includes(book.id)) : books, [books, saved, view]);

  if (auth.status === 'loading') return <div className="splash"><div className="brand-mark">B</div><p>Preparando sua estante…</p></div>;
  if (auth.status !== 'authenticated') return <Landing auth={auth} onLogin={() => void login()} />;
  return <div className="app-shell">
    <header className="topbar"><button className="brand" onClick={() => setView('feed')}><span className="brand-mark">B</span><span>BookRush</span></button><div className="top-actions"><span className="user-name">{auth.user?.profile.preferred_username ?? auth.user?.profile.email ?? 'Leitor'}</span><button className="ghost-button" onClick={() => void logout()}>Sair</button></div></header>
    <main className="content">
      <section className="hero"><div><span className="eyebrow">DESCUBRA SUA PRÓXIMA LEITURA</span><h1>Histórias que<br /><em>ficam</em> com você.</h1><p>Explore o catálogo BookRush e encontre livros para cada momento.</p></div><div className="hero-orbit" aria-hidden="true"><span>✦</span><span>⌁</span><span>✺</span></div></section>
      <nav className="view-tabs" aria-label="Navegação principal"><button className={view === 'feed' ? 'active' : ''} onClick={() => setView('feed')}>Para você</button><button className={view === 'library' ? 'active' : ''} onClick={() => setView('library')}>Minha estante <small>{saved.length}</small></button><button className={view === 'search' ? 'active' : ''} onClick={() => setView('search')}>Buscar</button></nav>
      {view === 'search' && <form className="search-bar" onSubmit={event => { event.preventDefault(); void loadBooks(query); }}><span>⌕</span><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar por título…" /><button type="submit">Buscar</button></form>}
      {error && <div className="notice error">{error} <button onClick={() => void loadBooks(query)}>Tentar novamente</button></div>}
      {loading && <div className="loading-row"><span className="spinner" /> Carregando catálogo…</div>}
      {!loading && !error && visibleBooks.length === 0 && <div className="empty"><span>◌</span><h2>{view === 'library' ? 'Sua estante está vazia' : 'Nenhum livro encontrado'}</h2><p>{view === 'library' ? 'Salve livros para encontrá-los aqui.' : 'O catálogo ainda não possui resultados para esta busca.'}</p></div>}
      <section className="book-grid" aria-label="Livros disponíveis">{visibleBooks.map((book, index) => <BookCard key={book.id} book={book} index={index} isSaved={saved.includes(book.id)} onSave={() => toggleSaved(book.id)} onOpen={() => setSelected(book)} />)}</section>
    </main>
    <footer className="mobile-nav"><button className={view === 'feed' ? 'active' : ''} onClick={() => setView('feed')}>⌂<span>Início</span></button><button className={view === 'search' ? 'active' : ''} onClick={() => setView('search')}>⌕<span>Buscar</span></button><button className={view === 'library' ? 'active' : ''} onClick={() => setView('library')}>▱<span>Estante</span></button></footer>
    {selected && <BookReader book={selected} onClose={() => setSelected(null)} isSaved={saved.includes(selected.id)} onSave={() => toggleSaved(selected.id)} />}
  </div>;
}

function Landing({ auth, onLogin }: { auth: AuthState; onLogin: () => void }) { return <main className="landing"><div className="landing-art"><span className="brand-mark large">B</span><div className="floating-word">READ<br /><em>MORE</em></div></div><span className="eyebrow">UM UNIVERSO DE HISTÓRIAS</span><h1>Seu próximo<br /><em>capítulo</em> começa aqui.</h1><p>Descubra livros, trechos e ideias que combinam com você.</p>{auth.status === 'error' && <div className="notice error">{auth.error}</div>}<button className="primary-button" onClick={onLogin}>Entrar com Keycloak <span>→</span></button><a className="register-link" href={authClient.registrationUrl()}>Ainda não tem conta? <strong>Cadastre-se</strong></a><small>Login seguro · Seus dados ficam protegidos</small></main>; }

function BookCard({ book, index, isSaved, onSave, onOpen }: { book: Book; index: number; isSaved: boolean; onSave: () => void; onOpen: () => void }) { const palettes = ['coral', 'indigo', 'sage', 'ochre', 'plum']; return <article className="book-card"><button className={`cover ${palettes[index % palettes.length]}`} onClick={onOpen} aria-label={`Abrir ${book.canonicalTitle}`}><span className="cover-symbol">{['◒', '✦', '⌁', '❋', '◌'][index % 5]}</span><strong>{book.canonicalTitle}</strong><small>{book.originalLanguage?.toUpperCase() ?? 'BOOKRUSH'}</small></button><div className="book-meta"><div><h3>{book.canonicalTitle}</h3><p>{book.firstPublicationYear ?? 'Edição BookRush'} · {book.originalLanguage ?? 'Idioma não informado'}</p></div><button className={`save-button ${isSaved ? 'saved' : ''}`} onClick={onSave} aria-label={isSaved ? 'Remover da estante' : 'Salvar na estante'}>{isSaved ? '★' : '☆'}</button></div>{book.description && <p className="description">{book.description}</p>}<button className="read-link" onClick={onOpen}>Ver detalhes <span>↗</span></button></article>; }

function BookReader({ book, onClose, isSaved, onSave }: { book: Book; onClose: () => void; isSaved: boolean; onSave: () => void }) { return <div className="reader-backdrop" role="dialog" aria-modal="true"><article className="reader"><button className="close-button" onClick={onClose} aria-label="Fechar">×</button><div className="reader-cover"><span className="cover-symbol">✦</span><strong>{book.canonicalTitle}</strong></div><span className="eyebrow">DETALHES DO LIVRO</span><h2>{book.canonicalTitle}</h2><p className="reader-facts">{book.firstPublicationYear ?? 'Ano não informado'} · {book.originalLanguage ?? 'Idioma não informado'} · {book.status ?? 'Disponível'}</p><p>{book.description || 'Este livro faz parte do catálogo BookRush. Em breve você poderá explorar trechos e acompanhar sua leitura por aqui.'}</p><div className="reader-actions"><button className="primary-button" onClick={onSave}>{isSaved ? '★ Na sua estante' : '☆ Salvar na estante'}</button><button className="ghost-button" onClick={onClose}>Continuar explorando</button></div></article></div>; }

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
