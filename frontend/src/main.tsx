import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

type Status = { service: string; status: string; database: string };

function App() {
  const [result, setResult] = useState<Status | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function checkConnection() {
    setLoading(true);
    setError('');
    setResult(null);
    try {
      const response = await fetch('/api/status', { signal: AbortSignal.timeout(10000), cache: 'no-store' });
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

  return <main>
    <span className="eyebrow">AMBIENTE DE TESTE</span>
    <h1>BookRush</h1>
    <p>O início da sua próxima leitura.</p>
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
