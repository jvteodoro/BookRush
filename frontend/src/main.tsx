import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { authClient, type AuthState } from './auth';
import './styles.css';

type Book = { id: string; canonicalTitle: string; originalLanguage?: string | null; description?: string | null };
type Page = { items: Book[] };

async function requestCatalog(query = ''): Promise<Page> {
  const params = new URLSearchParams({ page: '0', size: '50' });
  if (query.trim()) params.set('q', query.trim());
  const response = await authClient.fetch(`/api/v1/books?${params}`);
  if (!response.ok) throw new Error(response.status === 401 ? 'Sua sessão expirou. Entre novamente.' : `Catálogo indisponível (HTTP ${response.status}).`);
  return response.json() as Promise<Page>;
}

function App() {
  const [auth, setAuth] = useState<AuthState>({ status: 'loading' });
  const [books, setBooks] = useState<Book[]>([]);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState<Book>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        if (window.location.pathname === '/auth/callback') { const callback = await authClient.callback(); window.history.replaceState({}, '', callback.returnTo); if (active) setAuth({ status: 'authenticated', user: callback.user }); return; }
        if (window.location.pathname === '/auth/silent-callback') { await authClient.silentCallback(); return; }
        const user = await authClient.currentUser(); if (active) setAuth(user ? { status: 'authenticated', user } : { status: 'anonymous' });
      } catch (cause) { if (active) setAuth({ status: 'error', error: cause instanceof Error ? cause.message : 'Falha ao restaurar a sessão.' }); }
    })();
    return () => { active = false; };
  }, []);

  async function load(value = query) { setBusy(true); setError(''); try { setBooks((await requestCatalog(value)).items); } catch (cause) { setError(cause instanceof Error ? cause.message : 'Não foi possível carregar o catálogo.'); } finally { setBusy(false); } }
  useEffect(() => { if (auth.status === 'authenticated') void load(''); }, [auth.status]);
  async function openBook(book: Book) {
    setSelected(book);
    await authClient.fetch(`/api/v1/reader/books/${encodeURIComponent(book.id)}/opened`, { method: 'POST' });
    await authClient.fetch('/api/v1/behavior/events', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ events: [{ eventKey: `BOOK_OPEN:${book.id}:${Date.now()}`, eventType: 'BOOK_OPEN', bookId: book.id, occurredAt: new Date().toISOString(), payload: {} }] }) });
  }
  async function login() { setAuth({ status: 'loading' }); try { await authClient.login('/'); } catch (cause) { setAuth({ status: 'error', error: cause instanceof Error ? cause.message : 'Não foi possível iniciar o login.' }); } }

  if (auth.status === 'authenticated') return <main className="reader-shell"><header className="reader-header"><strong>BOOKRUSH</strong><button onClick={() => void authClient.logout()}>Sair</button></header><section className="reader-hero"><span className="eyebrow">UM UNIVERSO DE HISTÓRIAS</span><h1>Descubra sua próxima leitura.</h1><p>Livros disponíveis no catálogo BookRush.</p><form onSubmit={event => { event.preventDefault(); void load(); }}><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar por título, autor ou assunto" /><button type="submit">Buscar</button></form></section>{error && <p className="notice error">{error}</p>}{busy ? <p>Carregando catálogo…</p> : <section className="book-grid">{books.map(book => <article className="book-card" key={book.id}><span className="book-language">{book.originalLanguage?.toUpperCase() ?? '—'}</span><h2>{book.canonicalTitle}</h2><p>{book.description ?? 'Descrição ainda não disponível.'}</p><button onClick={() => void openBook(book)}>Abrir livro</button></article>)}{!books.length && !busy && <p>Nenhum livro encontrado.</p>}</section>}{selected && <div className="reader-modal" role="dialog" aria-modal="true"><button className="close" onClick={() => setSelected(undefined)} aria-label="Fechar">×</button><span className="eyebrow">LEITURA</span><h2>{selected.canonicalTitle}</h2><p>{selected.description ?? 'O texto reader-ready será carregado pela API de conteúdo.'}</p><button onClick={() => setSelected(undefined)}>Voltar ao catálogo</button></div>}</main>;
  if (auth.status === 'loading') return <div className="splash"><div className="brand-mark">B</div><p>Preparando seu feed…</p></div>;
  return <main className="landing"><div className="landing-art"><span className="brand-mark large">B</span><div className="floating-word">READ<br /><em>MORE</em></div></div><span className="eyebrow">UM UNIVERSO DE HISTÓRIAS</span><h1>Seu próximo<br /><em>capítulo</em> começa aqui.</h1><p>Descubra livros reais do catálogo BookRush.</p>{auth.status === 'error' && <div className="notice error">{auth.error}</div>}<button className="primary-button" onClick={() => void login()}>Entrar com Keycloak <span>→</span></button><a className="register-link" href={authClient.registrationUrl()}>Ainda não tem conta? <strong>Cadastre-se</strong></a></main>;
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
