import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { authClient, type AuthState } from './auth';
import './styles.css';

type Status = { service: string; status: string; database: string };

function App() {
  const [auth, setAuth] = useState<AuthState>({ status: 'loading' });
  const [result, setResult] = useState<Status | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function checkConnection() {
    setLoading(true);
    setError('');
    setResult(null);
    try {
      const response = await authClient.fetch('/api/status', { signal: AbortSignal.timeout(10000), cache: 'no-store' });
      if (!response.ok) throw new Error(`API indisponível (HTTP ${response.status}).`);
      const data: Status = await response.json();
      if (data.status !== 'ok' || data.database !== 'up' || typeof data.service !== 'string') {
        throw new Error('A API retornou um estado inesperado.');
      }
      setResult(data);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível conectar à API.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void checkConnection(); }, []);

  useEffect(() => {
    let active = true;
    async function restore() {
      try {
        if (window.location.pathname === '/auth/silent-callback') {
          await authClient.silentCallback();
          return;
        }
        if (window.location.pathname === '/auth/callback') {
          const callback = await authClient.callback();
          window.history.replaceState({}, '', callback.returnTo);
          if (active) setAuth({ status: 'authenticated', user: callback.user });
          return;
        }
        const user = await authClient.currentUser();
        if (active) setAuth(user ? { status: 'authenticated', user } : { status: 'anonymous' });
      } catch (cause) {
        if (active) setAuth({ status: 'error', error: cause instanceof Error ? cause.message : 'Falha ao restaurar a sessão.' });
      }
    }
    void restore();
    return () => { active = false; };
  }, []);

  async function login() {
    setAuth({ status: 'loading' });
    try { await authClient.login(window.location.pathname + window.location.search); }
    catch (cause) { setAuth({ status: 'error', error: cause instanceof Error ? cause.message : 'Não foi possível iniciar o login.' }); }
  }

  async function logout() {
    setAuth({ status: 'loading' });
    try { await authClient.logout(); }
    catch (cause) { setAuth({ status: 'error', error: cause instanceof Error ? cause.message : 'Não foi possível sair.' }); }
  }

  return <main>
    <span className="eyebrow">AMBIENTE DE TESTE</span>
    <h1>BookRush</h1>
    <p>O início da sua próxima leitura.</p>
    <section aria-live="polite">
      <h2>Login</h2>
      {auth.status === 'loading' && <p>Verificando sessão…</p>}
      {auth.status === 'anonymous' && <button onClick={() => void login()}>Entrar com Keycloak</button>}
      {auth.status === 'authenticated' && <><p className="success">Sessão autenticada.</p><button onClick={() => void logout()}>Sair</button></>}
      {auth.status === 'error' && <><p className="error">{auth.error}</p><button onClick={() => void login()}>Tentar novamente</button></>}
    </section>
    <section aria-live="polite" aria-busy={loading}>
      <h2>Conexão dos serviços</h2>
      <dl>
        <div><dt>Frontend React</dt><dd>Operacional</dd></div>
        <div><dt>API Java</dt><dd>{loading ? 'Verificando…' : result ? 'Conectada' : 'Indisponível'}</dd></div>
        <div><dt>PostgreSQL</dt><dd>{loading ? 'Verificando…' : result ? 'Conectado' : 'Não confirmado'}</dd></div>
      </dl>
      {result && <p className="success">React → Nginx → {result.service} → PostgreSQL: conexão confirmada.</p>}
      {error && <p className="error">{error} Verifique os containers e tente novamente.</p>}
      <button onClick={() => void checkConnection()} disabled={loading}>
        {loading ? 'Testando…' : 'Testar conexão novamente'}
      </button>
    </section>
  </main>;
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
