import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Activity, CheckCircle2, Eye, Play, RefreshCw } from 'lucide-react';
import { services } from '../../lib/services';
import { AnalyticsRun } from '../../types/domain';

const terminal = new Set(['SUCCEEDED', 'FAILED']);
const statusLabel: Record<string, string> = { PENDING: 'Na fila', RUNNING: 'Processando', SUCCEEDED: 'Concluída', FAILED: 'Falhou' };

export function AnalyticsPage() {
  const qc = useQueryClient();
  const books = useQuery({ queryKey: ['books'], queryFn: () => services.listBooks() });
  const runs = useQuery({ queryKey: ['analytics'], queryFn: services.listAnalytics });
  const [localRuns, setLocalRuns] = useState<AnalyticsRun[]>([]);
  const [selectedRunId, setSelectedRunId] = useState<string>();
  const run = useMutation({
    mutationFn: ({ bookId, canonicalBookId }: { bookId: string; canonicalBookId?: string }) => services.runAnalytics(bookId, canonicalBookId),
    onSuccess: created => { setLocalRuns(current => [created, ...current.filter(item => item.id !== created.id)]); void qc.invalidateQueries({ queryKey: ['analytics'] }); },
  });
  const displayedRuns = useMemo(() => [...localRuns, ...(runs.data ?? [])].filter((item, index, all) => all.findIndex(other => other.id === item.id) === index), [localRuns, runs.data]);
  const latestByBook = useMemo(() => new Map(displayedRuns.map(item => [item.bookId, item])), [displayedRuns]);
  const runForBook = (bookId: string, canonicalBookId?: string) => latestByBook.get(canonicalBookId ?? bookId);
  const titleForRun = (bookId: string) => books.data?.find(book => book.canonicalBookId === bookId || book.id === bookId)?.title ?? `Obra ${bookId}`;

  useEffect(() => {
    const active = localRuns.filter(item => !terminal.has(item.status));
    if (!active.length) return;
    const timer = window.setInterval(() => {
      void Promise.all(active.map(async item => {
        const latest = await services.getAnalyticsJob(item.id);
        const normalized: AnalyticsRun = {
          ...item,
          status: latest.status === 'COMPLETED' ? 'SUCCEEDED' : latest.status === 'FAILED' || latest.status === 'COMPLETED_WITH_ERRORS' ? 'FAILED' : latest.status === 'RUNNING' ? 'RUNNING' : 'PENDING',
          totalItems: latest.total_items ?? latest.totalItems,
          processedItems: latest.processed_items ?? latest.processedItems ?? latest.succeeded_items,
          finishedAt: latest.finished_at ?? latest.finishedAt,
        };
        setLocalRuns(current => current.map(existing => existing.id === item.id ? normalized : existing));
        if (terminal.has(normalized.status)) void qc.invalidateQueries({ queryKey: ['analytics'] });
      })).catch(() => undefined);
    }, 2000);
    return () => window.clearInterval(timer);
  }, [localRuns, qc]);

  const scrollToRun = (id: string) => { setSelectedRunId(id); window.requestAnimationFrame(() => document.getElementById(`analytics-run-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })); };
  const importedBooks = books.data?.filter(book => book.status === 'IMPORTED') ?? [];

  return <section>
    <header className="pagehead"><div><span className="eyebrow">MODEL OPERATIONS</span><h1>Analytics</h1><p>Consulte métricas já persistidas por versão textual e acompanhe novas execuções sem perder o histórico.</p></div><button className="runrow button-action" onClick={() => void runs.refetch()} disabled={runs.isFetching}><RefreshCw size={15}/>{runs.isFetching ? 'Atualizando…' : 'Atualizar métricas'}</button></header>
    <div className="panel"><div className="panelhead"><div><h2>Obras importadas</h2><span>Uma execução concluída pode ser consultada sem rodar a pipeline novamente.</span></div><span>{importedBooks.length} disponíveis</span></div><div className="runrow analytics-books">{importedBooks.length === 0 && <p>Nenhuma obra importada possui analytics disponível ainda.</p>}{importedBooks.map(book => { const existing = runForBook(book.id, book.canonicalBookId); const running = existing && !terminal.has(existing.status); return <div className="analytics-book" key={book.id}><div><strong>{book.title}</strong><small>{existing ? `Última execução: ${statusLabel[existing.status] ?? existing.status}` : 'Ainda não executada'}</small></div>{existing ? <><button onClick={() => scrollToRun(existing.id)}><Eye size={15}/>Ver métricas</button><button disabled={run.isPending || running} onClick={() => run.mutate({ bookId: book.id, canonicalBookId: book.canonicalBookId })}><Play size={15}/>{running ? 'Processando…' : 'Reprocessar'}</button></> : <button disabled={run.isPending} onClick={() => run.mutate({ bookId: book.id, canonicalBookId: book.canonicalBookId })}><Play size={15}/>Executar analytics</button>}</div>; })}</div>{run.isError && <p className="error-text">{run.error instanceof Error ? run.error.message : 'Não foi possível iniciar o analytics.'}</p>}</div>
    {runs.isLoading && <div className="panel">Consultando execuções persistidas…</div>}
    {runs.isError && <div className="panel import-error"><h2>Métricas indisponíveis</h2><p>{runs.error instanceof Error ? runs.error.message : 'Não foi possível consultar o serviço de analytics.'}</p></div>}
    {!runs.isLoading && !runs.isError && displayedRuns.length === 0 && <div className="panel"><h2>Nenhuma execução encontrada</h2><p>Importe uma obra e execute o analytics para gerar métricas persistidas.</p></div>}
    <div className="runs">{displayedRuns.map(item => <article className={`run ${selectedRunId === item.id ? 'run-selected' : ''}`} id={`analytics-run-${item.id}`} key={item.id}><div className="runhead"><div><Activity size={18}/><div><strong>{titleForRun(item.bookId)}</strong><span>{item.modelVersion} · {statusLabel[item.status] ?? item.status}</span></div></div><time>{new Date(item.startedAt).toLocaleString()}</time></div>{item.status === 'SUCCEEDED' && <div className="run-success"><CheckCircle2 size={15}/> Métricas persistidas e disponíveis para consulta</div>}{item.status === 'RUNNING' && <div className="progress"><span style={{ width: `${item.totalItems ? Math.min(100, Math.round(((item.processedItems ?? 0) / item.totalItems) * 100)) : 15}%` }}/></div>}<div className="metrics">{item.metrics.map(metric => <div key={metric.key}><span>{metric.label}</span><strong>{Number.isInteger(metric.value) ? metric.value : metric.value.toFixed(3)}{metric.unit ? ` ${metric.unit}` : ''}</strong></div>)}{item.totalItems !== undefined && <div><span>Itens processados</span><strong>{item.processedItems ?? 0}/{item.totalItems}</strong></div>}</div></article>)}</div>
  </section>;
}
