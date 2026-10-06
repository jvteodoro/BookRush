import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { authClient, type AuthState } from './auth';
import { createTelemetry } from './telemetry';
import { HttpFetcher, Manifest, Publication } from '@readium/shared';
import { WebPubNavigator } from '@readium/navigator';
import './styles.css';

type ReadiumBridge = {
  mount: (bookId: string, container: HTMLElement, onLocatorChanged?: (progression: number) => void) => Promise<void>;
  unmount: () => Promise<void>;
};

let readiumNavigator: any;
const readiumBridge: ReadiumBridge = {
  async mount(bookId, container, onLocatorChanged) {
    await this.unmount();
    const response = await authClient.fetch(`/api/v1/content/books/${encodeURIComponent(bookId)}/publication.json`);
    if (!response.ok) throw new Error(`Publicação indisponível (HTTP ${response.status}).`);
    const raw = await response.json();
    const manifest = Manifest.deserialize(raw);
    if (!manifest || !manifest.readingOrder?.items?.length) throw new Error('Este livro ainda não possui conteúdo reader-ready.');
    // Readium does not provide an HTTP fetcher by default. Without an
    // authenticated fetcher it falls back to EmptyFetcher and fails as soon
    // as it tries to load the first chapter. Route every publication resource
    // through the same PKCE-aware client used by the rest of the application.
    const fetcher = new HttpFetcher(
      (input, init) => authClient.fetch(String(input), { ...init, cache: 'no-store' }),
      window.location.origin,
    );
    const publication = new Publication({ manifest, fetcher });
    readiumNavigator = new WebPubNavigator(container, publication, {
      frameLoaded: () => undefined,
      positionChanged: (locator: any) => onLocatorChanged?.(locator.locations?.totalProgression ?? locator.locations?.progression ?? 0),
      timelineItemChanged: () => undefined,
      tap: () => false, click: () => false, zoom: () => undefined, scroll: () => undefined,
      customEvent: () => undefined, handleLocator: () => false, textSelected: () => undefined,
      contentProtection: () => undefined, contextMenu: () => undefined, peripheral: () => undefined,
    } as any);
    await readiumNavigator.load();
  },
  async unmount() {
    if (readiumNavigator) {
      await readiumNavigator.destroy();
      readiumNavigator = undefined;
    }
  },
};

type CatalogBook = { id: string; canonicalTitle: string; originalLanguage?: string | null; description?: string | null; thumbnailUrl?: string | null };
type FeedExcerpt = { id: string; text: string; sourceAssetVersionId: string; textSha256: string; startCodepoint: number; endCodepoint: number; generationMethod: string; generatorVersion: string; rank?: { version?: string; score?: number | null } | null };

type PageLoopBook = {
  id: string; title: string; author: string; genre: string; keywords: string[];
  match: number; a: string; b: string; quote: string; excerpt?: FeedExcerpt | null; likes: number; comments: number;
  shares: number; pages: number; progress: number;
  thumbnailUrl?: string | null;
  recommendationRequestId?: string; impressionId?: string; modelVersion?: string; rank?: number;
};

type RecommendationItem = {
  book?: CatalogBook;
  impressionId?: string;
  recommendationRequestId?: string;
  modelVersion?: string;
  rank?: number;
  excerpt?: FeedExcerpt | null;
};

async function loadCatalog(): Promise<CatalogBook[]> {
  // O catálogo de leitura possui uma rota pública própria; a rota internal é
  // reservada para operadores e devolve 401 ao leitor autenticado.
  // Esta é uma leitura pública. Não anexe um token possivelmente expirado:
  // um Bearer inválido transforma uma leitura pública em 401 no resource server.
  const response = await fetch('/api/v1/books?page=0&size=50', { credentials: 'same-origin' });
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

async function loadRecent(): Promise<string[]> {
  const response = await authClient.fetch('/api/v1/reader/recent');
  if (!response.ok) return [];
  const rows = await response.json() as Array<{ book_id?: string; bookId?: string }>;
  return rows.map(row => row.book_id ?? row.bookId).filter((id): id is string => Boolean(id));
}

async function loadRecommendationFeed(): Promise<RecommendationItem[]> {
  const response = await authClient.fetch('/api/v1/reader/feed?size=50');
  if (!response.ok) return [];
  const payload = await response.json() as { items?: RecommendationItem[] };
  return payload.items ?? [];
}

async function loadSocialCounts(bookId: string): Promise<{ likes: number; comments: number; liked: boolean }> {
  const [likesResponse, commentsResponse] = await Promise.all([
    authClient.fetch(`/api/v1/social/books/${encodeURIComponent(bookId)}/likes`),
    authClient.fetch(`/api/v1/social/books/${encodeURIComponent(bookId)}/comments`),
  ]);
  let likes = 0;
  let liked = false;
  if (likesResponse.ok) {
    const payload = await likesResponse.json() as { count?: number; liked?: boolean };
    likes = Number(payload.count ?? 0);
    liked = payload.liked === true;
  }
  const commentsPayload = commentsResponse.ok ? await commentsResponse.json() : [];
  const comments = Array.isArray(commentsPayload) ? commentsPayload.length : 0;
  return { likes, comments, liked };
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
        const telemetry = createTelemetry(async events => {
          const response = await authClient.fetch('/api/v1/behavior/events', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ events }),
          });
          if (!response.ok) throw new Error('telemetry rejected');
        });
        const catalog = await loadCatalog();
        const recommendations = await loadRecommendationFeed();
        if (!active) return;
        const byId = new Map(catalog.map(book => [book.id, book]));
        const ordered = recommendations.map(item => item.book).filter((book): book is CatalogBook => Boolean(book && byId.has(book.id)));
        const source = ordered.length ? ordered : catalog;
        const palettes = [['#82d4a4', '#213e35'], ['#74b9ff', '#202b52'], ['#f0a8bd', '#5a2337'], ['#c7d87a', '#26311f'], ['#d2aa6d', '#4a2e1f']];
        const books: PageLoopBook[] = source.map((book, index) => {
          const recommendation = recommendations.find(item => item.book?.id === book.id);
          return {
          id: book.id, title: book.canonicalTitle, author: 'Catálogo BookRush', genre: book.originalLanguage?.toUpperCase() ?? 'Clássico', keywords: [], match: Math.max(70, 96 - index * 2), a: palettes[index % palettes.length][0], b: palettes[index % palettes.length][1], quote: recommendation?.excerpt?.text?.trim() || book.description || `Descubra ${book.canonicalTitle} no catálogo BookRush.`, excerpt: recommendation?.excerpt ?? null, likes: 0, comments: 0, shares: 0, pages: 0, progress: 0, thumbnailUrl: book.thumbnailUrl,
          recommendationRequestId: recommendation?.recommendationRequestId, impressionId: recommendation?.impressionId, modelVersion: recommendation?.modelVersion, rank: recommendation?.rank,
        };
        });
        const socialCounts = await Promise.all(books.map(book => loadSocialCounts(book.id).catch(() => ({ likes: 0, comments: 0, liked: false }))));
        books.forEach((book, index) => Object.assign(book, socialCounts[index]));
        (window as Window & { __BOOKRUSH_LIKES__?: Record<string, boolean> }).__BOOKRUSH_LIKES__ = Object.fromEntries(books.map((book, index) => [book.id, socialCounts[index].liked]));
        const saved = new Set(await loadLibrary());
        const recent = await loadRecent();
        let submissions: unknown[] = [];
        try {
          const response = await authClient.fetch('/api/v1/publisher/submissions');
          if (response.ok) submissions = await response.json() as unknown[];
        } catch {
          // Publisher access is optional for reader accounts; the portal shows
          // an empty state instead of preventing the reader feed from loading.
        }
        (window as Window & { __BOOKRUSH_BOOKS__?: PageLoopBook[]; __BOOKRUSH_API__?: Record<string, unknown> }).__BOOKRUSH_BOOKS__ = books;
        (window as Window & { __BOOKRUSH_RECENT__?: string[] }).__BOOKRUSH_RECENT__ = recent;
        (window as Window & { __BOOKRUSH_READIUM__?: ReadiumBridge }).__BOOKRUSH_READIUM__ = readiumBridge;
        (window as Window & { __BOOKRUSH_PUBLISHER_SUBMISSIONS__?: unknown[] }).__BOOKRUSH_PUBLISHER_SUBMISSIONS__ = submissions;
        try {
          const profileResponse = await authClient.fetch('/api/v1/profile');
          (window as Window & { __BOOKRUSH_PROFILE__?: unknown }).__BOOKRUSH_PROFILE__ = profileResponse.ok ? await profileResponse.json() : null;
        } catch { (window as Window & { __BOOKRUSH_PROFILE__?: unknown }).__BOOKRUSH_PROFILE__ = null; }
        try {
          const [usersResponse, reportsResponse] = await Promise.all([
            authClient.fetch('/api/v1/admin/users'), authClient.fetch('/api/v1/admin/reports'),
          ]);
          (window as Window & { __BOOKRUSH_ADMIN_USERS__?: unknown[] }).__BOOKRUSH_ADMIN_USERS__ = usersResponse.ok ? await usersResponse.json() as unknown[] : [];
          (window as Window & { __BOOKRUSH_ADMIN_REPORTS__?: unknown[] }).__BOOKRUSH_ADMIN_REPORTS__ = reportsResponse.ok ? await reportsResponse.json() as unknown[] : [];
        } catch {
          (window as Window & { __BOOKRUSH_ADMIN_USERS__?: unknown[] }).__BOOKRUSH_ADMIN_USERS__ = [];
          (window as Window & { __BOOKRUSH_ADMIN_REPORTS__?: unknown[] }).__BOOKRUSH_ADMIN_REPORTS__ = [];
        }
        try {
          const streakResponse = await authClient.fetch('/api/v1/reader/streak');
          (window as Window & { __BOOKRUSH_STREAK__?: unknown }).__BOOKRUSH_STREAK__ = streakResponse.ok ? await streakResponse.json() : null;
        } catch { (window as Window & { __BOOKRUSH_STREAK__?: unknown }).__BOOKRUSH_STREAK__ = null; }
        (window as Window & { __BOOKRUSH_API__?: Record<string, unknown> }).__BOOKRUSH_API__ = {
          ...(window as Window & { __BOOKRUSH_API__?: Record<string, unknown> }).__BOOKRUSH_API__,
          saved,
          save: async (id: string, enabled: boolean) => {
            const response = await authClient.fetch(`/api/v1/reader/library/${encodeURIComponent(id)}`, { method: enabled ? 'PUT' : 'DELETE' });
            if (!response.ok) throw new Error('Não foi possível atualizar sua biblioteca.');
            enabled ? saved.add(id) : saved.delete(id);
          },
          like: async (id: string, enabled: boolean) => {
            const response = await authClient.fetch(`/api/v1/social/books/${encodeURIComponent(id)}/like`, { method: enabled ? 'PUT' : 'DELETE' });
            if (!response.ok) throw new Error('Não foi possível atualizar a curtida.');
          },
          share: async (id: string) => {
            const response = await authClient.fetch(`/api/v1/social/books/${encodeURIComponent(id)}/share`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ channel: 'copy-link' }) });
            if (!response.ok) throw new Error('Não foi possível registrar o compartilhamento.');
          },
          comments: async (id: string) => {
            const response = await authClient.fetch(`/api/v1/social/books/${encodeURIComponent(id)}/comments`);
            return response.ok ? response.json() : [];
          },
          comment: async (id: string, text: string) => {
            const response = await authClient.fetch(`/api/v1/social/books/${encodeURIComponent(id)}/comments`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ body: text }) });
            if (!response.ok) throw new Error('Não foi possível publicar o comentário.');
          },
          refreshCounts: async (id: string) => {
            const counts = await loadSocialCounts(id);
            const book = books.find(item => item.id === id);
            if (book) Object.assign(book, counts);
            const likes = (window as Window & { __BOOKRUSH_LIKES__?: Record<string, boolean> }).__BOOKRUSH_LIKES__ ?? {};
            likes[id] = counts.liked;
            (window as Window & { __BOOKRUSH_LIKES__?: Record<string, boolean> }).__BOOKRUSH_LIKES__ = likes;
            return counts;
          },
          profile: async () => {
            const response = await authClient.fetch('/api/v1/profile');
            return response.ok ? response.json() : null;
          },
          updateProfile: async (profile: { displayName?: string; bio?: string; isPublic?: boolean }) => {
            const response = await authClient.fetch('/api/v1/profile', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(profile) });
            if (!response.ok) throw new Error('Não foi possível salvar o perfil.');
            return response.json();
          },
          follow: async (subject: string, enabled: boolean) => {
            const response = await authClient.fetch(`/api/v1/social/users/${encodeURIComponent(subject)}/follow`, { method: enabled ? 'PUT' : 'DELETE' });
            if (!response.ok) throw new Error('Não foi possível atualizar o acompanhamento.');
          },
          adminUsers: async () => {
            const response = await authClient.fetch('/api/v1/admin/users');
            if (!response.ok) throw new Error('Não foi possível carregar os usuários.');
            return response.json();
          },
          adminReports: async () => {
            const response = await authClient.fetch('/api/v1/admin/reports');
            if (!response.ok) throw new Error('Não foi possível carregar as denúncias.');
            return response.json();
          },
          adminAudit: async (action: string, target: string) => {
            const response = await authClient.fetch('/api/v1/admin/audit', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ action, target }) });
            if (!response.ok) throw new Error('Não foi possível registrar a ação administrativa.');
            return response.json();
          },
          adminModerate: async (target: string, decision: string) => {
            const response = await authClient.fetch('/api/v1/admin/moderation', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ target, decision }) });
            if (!response.ok) throw new Error('Não foi possível registrar a decisão de moderação.');
            return response.json();
          },
          publisherSubmissions: async () => {
            const response = await authClient.fetch('/api/v1/publisher/submissions');
            if (!response.ok) throw new Error('Não foi possível carregar suas publicações.');
            return response.json();
          },
          createSubmission: async (title: string) => {
            const response = await authClient.fetch('/api/v1/publisher/submissions', {
              method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title }),
            });
            if (!response.ok) throw new Error('Não foi possível salvar o rascunho.');
            return response.json();
          },
          submitSubmission: async (id: string) => {
            const response = await authClient.fetch(`/api/v1/publisher/submissions/${encodeURIComponent(id)}/submit`, { method: 'POST' });
            if (!response.ok) throw new Error('Não foi possível enviar a publicação para revisão.');
            return response.json();
          },
          publisherMetrics: async (id: string) => {
            const response = await authClient.fetch(`/api/v1/publisher/submissions/${encodeURIComponent(id)}/metrics`);
            if (!response.ok) throw new Error('Não foi possível carregar as métricas.');
            return response.json();
          },
          uploadSubmissionFile: async (submissionId: string, file: File) => {
            const request = await authClient.fetch(`/api/v1/publisher/submissions/${encodeURIComponent(submissionId)}/upload`, {
              method: 'POST', headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ filename: file.name, contentType: file.type || 'application/octet-stream' }),
            });
            if (!request.ok) throw new Error('Não foi possível reservar o upload.');
            const reservation = await request.json() as { uploadId: string; url: string };
            const put = await fetch(reservation.url, { method: 'PUT', headers: { 'Content-Type': file.type || 'application/octet-stream' }, body: file });
            if (!put.ok) throw new Error('O storage recusou o upload.');
            const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
            const sha256 = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
            const complete = await authClient.fetch(`/api/v1/publisher/submissions/${encodeURIComponent(submissionId)}/upload/${encodeURIComponent(reservation.uploadId)}/finalize`, {
              method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sha256 }),
            });
            if (!complete.ok) throw new Error('Não foi possível finalizar o upload.');
            return complete.json();
          },
          progress: async (id: string, percent: number, positionCodepoint: number) => {
            await authClient.fetch(`/api/v1/reader/books/${encodeURIComponent(id)}/progress`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ percent, positionCodepoint }) });
          },
          bookmark: async (id: string, positionCodepoint: number) => {
            await authClient.fetch(`/api/v1/reader/books/${encodeURIComponent(id)}/bookmarks`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ positionCodepoint }) });
          },
          open: async (id: string) => {
            await authClient.fetch(`/api/v1/reader/books/${encodeURIComponent(id)}/opened`, { method: 'POST' });
            await authClient.fetch('/api/v1/behavior/events', {
              method: 'POST', headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ events: [{ eventKey: `BOOK_OPEN:${id}:${Date.now()}`, eventType: 'BOOK_OPEN', bookId: id, occurredAt: new Date().toISOString(), payload: {} }] }),
            });
          },
          chapters: async (id: string) => {
            const response = await authClient.fetch(`/api/v1/content/books/${encodeURIComponent(id)}/chapters`);
            return response.ok ? response.json() : [];
          },
          viewable: async (id: string) => {
            const item = recommendations.find(candidate => candidate.book?.id === id);
            if (item?.impressionId) await authClient.fetch(`/api/v1/recommendations/impressions/${item.impressionId}/viewable`, { method: 'POST' });
          },
          trackExcerpt: (eventType: string, bookId: string, excerpt?: FeedExcerpt | null, payload: Record<string, unknown> = {}) => {
            telemetry.track(eventType, bookId, { ...payload, excerptId: excerpt?.id, sourceAssetVersionId: excerpt?.sourceAssetVersionId });
          },
        };
        document.body.dataset.mode = 'web'; document.body.dataset.start = 'feed';
        if (document.querySelector('script[data-bookrush-pageloop]')) return;
        const style = document.createElement('link'); style.id = 'bookrush-pageloop-style'; style.rel = 'stylesheet'; style.href = '/pageloop.css?v=20261002-readium-kindle-7'; document.head.appendChild(style);
        const thumbnailStyle = document.createElement('link'); thumbnailStyle.id = 'bookrush-thumbnail-style'; thumbnailStyle.rel = 'stylesheet'; thumbnailStyle.href = '/thumbnails.css?v=20261006-gutenberg'; document.head.appendChild(thumbnailStyle);
        const script = document.createElement('script'); script.dataset.bookrushPageloop = 'true'; script.src = '/pageloop.js?v=20261002-readium-kindle-7'; script.async = true; document.body.appendChild(script);
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
