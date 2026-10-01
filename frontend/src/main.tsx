import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { authClient, type AuthState } from './auth';
import './styles.css';

type CatalogBook = { id: string; canonicalTitle: string; originalLanguage?: string | null; description?: string | null };

type PageLoopBook = {
  id: string; title: string; author: string; genre: string; keywords: string[];
  match: number; a: string; b: string; quote: string; likes: number; comments: number;
  shares: number; pages: number; progress: number;
};

async function loadCatalog(): Promise<CatalogBook[]> {
  const response = await authClient.fetch('/api/internal/v1/catalog/books?page=0&size=50');
  if (!response.ok) throw new Error(response.status === 401 ? 'Sua sessão expirou. Entre novamente.' : `Catálogo indisponível (HTTP ${response.status}).`);
  const page = await response.json() as { items: CatalogBook[] };
  return page.items;
}

async function loadLibrary(): Promise<string[]> {
  const response = await authClient.fetch('/api/v1/reader/library');
  if (!response.ok) return [];
  const rows = await response.json() as Array<{ book_id?: string; bookId?: string }>;
  return rows.map(row => row.book_id ?? row.bookId).filter((id): id is string => Boolean(id));
}

function App() {
  const [auth, setAuth] = useState<AuthState>({ status: 'loading' });
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    async function restore() {
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
      } catch (cause) { if (active) setAuth({ status: 'error', error: cause instanceof Error ? cause.message : 'Falha ao restaurar a sessão.' }); }
    }
    void restore();
    return () => { active = false; };
  }, []);
  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    let active = true;
    async function startPrototype() {
      try {
        const catalog = await loadCatalog();
        if (!active) return;
        const palettes = [['#82d4a4', '#213e35'], ['#74b9ff', '#202b52'], ['#f0a8bd', '#5a2337'], ['#c7d87a', '#26311f'], ['#d2aa6d', '#4a2e1f']];
        const books: PageLoopBook[] = catalog.map((book, index) => ({
          id: book.id, title: book.canonicalTitle, author: 'Catálogo BookRush', genre: book.originalLanguage?.toUpperCase() ?? 'Clássico', keywords: [], match: Math.max(70, 96 - index * 2), a: palettes[index % palettes.length][0], b: palettes[index % palettes.length][1], quote: book.description || `Descubra ${book.canonicalTitle} no catálogo BookRush.`, likes: 0, comments: 0, shares: 0, pages: 0, progress: 0,
        }));
        const saved = new Set(await loadLibrary());
        (window as Window & { __BOOKRUSH_BOOKS__?: PageLoopBook[]; __BOOKRUSH_API__?: Record<string, unknown> }).__BOOKRUSH_BOOKS__ = books;
        (window as Window & { __BOOKRUSH_API__?: Record<string, unknown> }).__BOOKRUSH_API__ = {
          ...(window as Window & { __BOOKRUSH_API__?: Record<string, unknown> }).__BOOKRUSH_API__,
          saved,
          save: async (id: string, enabled: boolean) => {
            const response = await authClient.fetch(`/api/v1/reader/library/${encodeURIComponent(id)}`, { method: enabled ? 'PUT' : 'DELETE' });
            if (!response.ok) throw new Error('Não foi possível atualizar sua biblioteca.');
            enabled ? saved.add(id) : saved.delete(id);
          },
          open: async (id: string) => {
            await authClient.fetch(`/api/v1/reader/books/${encodeURIComponent(id)}/opened`, { method: 'POST' });
            await authClient.fetch('/api/v1/behavior/events', {
              method: 'POST', headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ events: [{ eventKey: `BOOK_OPEN:${id}:${Date.now()}`, eventType: 'BOOK_OPEN', bookId: id, occurredAt: new Date().toISOString(), payload: {} }] }),
            });
          },
        };
        document.body.dataset.mode = 'web'; document.body.dataset.start = 'feed';
        if (document.querySelector('script[data-bookrush-pageloop]')) return;
        const style = document.createElement('link'); style.id = 'bookrush-pageloop-style'; style.rel = 'stylesheet'; style.href = '/pageloop.css'; document.head.appendChild(style);
        const script = document.createElement('script'); script.dataset.bookrushPageloop = 'true'; script.src = '/pageloop.js'; script.async = true; document.body.appendChild(script);
      } catch (cause) { if (active) setError(cause instanceof Error ? cause.message : 'Não foi possível carregar o catálogo.'); }
    }
    void startPrototype();
    return () => { active = false; };
  }, [auth.status]);
  async function login() { setAuth({ status: 'loading' }); try { await authClient.login('/'); } catch (cause) { setAuth({ status: 'error', error: cause instanceof Error ? cause.message : 'Não foi possível iniciar o login.' }); } }
  if (auth.status === 'authenticated') return error ? <main className="landing"><span className="eyebrow">BOOKRUSH</span><h1>Não conseguimos abrir seu feed.</h1><p>{error}</p><button className="primary-button" onClick={() => window.location.reload()}>Tentar novamente</button></main> : <div id="app" />;
  if (auth.status === 'loading') return <div className="splash"><div className="brand-mark">B</div><p>Preparando seu feed…</p></div>;
  return <main className="landing"><div className="landing-art"><span className="brand-mark large">B</span><div className="floating-word">READ<br /><em>MORE</em></div></div><span className="eyebrow">UM UNIVERSO DE HISTÓRIAS</span><h1>Seu próximo<br /><em>capítulo</em> começa aqui.</h1><p>Descubra trechos, livros e leitores que combinam com você.</p>{auth.status === 'error' && <div className="notice error">{auth.error}</div>}<button className="primary-button" onClick={() => void login()}>Entrar com Keycloak <span>→</span></button><a className="register-link" href={authClient.registrationUrl()}>Ainda não tem conta? <strong>Cadastre-se</strong></a><small>Login seguro · Seus dados ficam protegidos</small></main>;
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
