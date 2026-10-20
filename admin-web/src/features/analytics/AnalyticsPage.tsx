import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { RefreshCw, Search } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { services } from "../../lib/services";
import { AnalyticsJob } from "../../types/domain";

const statusLabel: Record<string, string> = {
  PENDING: "Na fila",
  RUNNING: "Executando",
  COMPLETED: "Concluído",
  COMPLETED_WITH_ERRORS: "Com falhas",
  FAILED: "Falhou",
  CANCELLED: "Cancelado",
};
const formatDate = (value?: string) =>
  value ? new Date(value).toLocaleString() : "—";

export function AnalyticsPage() {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("ALL");
  const [sort, setSort] = useState<"created" | "status" | "progress" | "id">(
    "created",
  );
  const jobs = useQuery({
    queryKey: ["analytics-jobs"],
    queryFn: services.listAnalyticsJobs,
    refetchInterval: 4000,
    refetchIntervalInBackground: true,
  });
  const visibleJobs = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return [...(jobs.data ?? [])]
      .filter((job) => status === "ALL" || job.status === status)
      .filter(
        (job) =>
          !normalized ||
          job.id.toLowerCase().includes(normalized) ||
          job.operationKey?.toLowerCase().includes(normalized),
      )
      .sort((left, right) => {
        if (sort === "status") return left.status.localeCompare(right.status);
        if (sort === "id") return left.id.localeCompare(right.id);
        if (sort === "progress")
          return (
            right.processedItems / Math.max(1, right.totalItems) -
            left.processedItems / Math.max(1, left.totalItems)
          );
        return (
          new Date(right.createdAt ?? 0).getTime() -
          new Date(left.createdAt ?? 0).getTime()
        );
      });
  }, [jobs.data, query, sort, status]);
  return (
    <section className="analytics-console">
      <header className="pagehead analytics-pagehead">
        <div>
          <span className="eyebrow">MODEL OPERATIONS</span>
          <h1>Analysis jobs</h1>
          <p>Execuções persistidas, progresso e falhas operacionais.</p>
        </div>
        <button
          className="button-action compact-action"
          onClick={() => void jobs.refetch()}
          disabled={jobs.isFetching}
        >
          <RefreshCw size={14} /> Atualizar
        </button>
      </header>
      <div className="analytics-toolbar">
        <label className="search-field">
          <Search size={15} />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Filtrar por ID ou operation key"
          />
        </label>
        <select
          value={status}
          onChange={(event) => setStatus(event.target.value)}
        >
          <option value="ALL">Todos os status</option>
          {Object.keys(statusLabel).map((value) => (
            <option key={value} value={value}>
              {statusLabel[value]}
            </option>
          ))}
        </select>
        <select
          value={sort}
          onChange={(event) => setSort(event.target.value as typeof sort)}
        >
          <option value="created">Mais recentes</option>
          <option value="progress">Maior progresso</option>
          <option value="id">ID</option>
          <option value="status">Status</option>
        </select>
        <span className="table-count">{visibleJobs.length} jobs</span>
      </div>
      {jobs.isLoading && (
        <div className="panel compact-panel">Carregando jobs…</div>
      )}
      {jobs.isError && (
        <div className="panel import-error compact-panel">
          Não foi possível consultar os jobs de analytics.
        </div>
      )}
      {!jobs.isLoading && !jobs.isError && (
        <div className="table-shell">
          <table className="admin-table analytics-table">
            <thead>
              <tr>
                <th>Job</th>
                <th>Status</th>
                <th>Progresso</th>
                <th>Estágio</th>
                <th>Criado em</th>
                <th aria-label="Ações" />
              </tr>
            </thead>
            <tbody>
              {visibleJobs.map((job) => (
                <JobRow key={job.id} job={job} />
              ))}
            </tbody>
          </table>
          {visibleJobs.length === 0 && (
            <div className="empty-table">
              Nenhum job corresponde aos filtros.
            </div>
          )}
        </div>
      )}
    </section>
  );
}

function JobRow({ job }: { job: AnalyticsJob }) {
  const progress = Math.round(
    (job.processedItems / Math.max(1, job.totalItems)) * 100,
  );
  return (
    <tr>
      <td>
        <Link className="table-link" to={`/analytics/jobs/${job.id}`}>
          <strong>{job.id.slice(0, 8)}</strong>
          <small>{job.operationKey ?? "sem operation key"}</small>
        </Link>
      </td>
      <td>
        <span className={`status-dot status-${job.status.toLowerCase()}`}>
          {statusLabel[job.status] ?? job.status}
        </span>
      </td>
      <td className="progress-cell">
        <span>
          {job.processedItems}/{job.totalItems}
        </span>
        <div className="mini-progress">
          <i style={{ width: `${progress}%` }} />
        </div>
      </td>
      <td>{job.progressStage ?? "—"}</td>
      <td>{formatDate(job.createdAt)}</td>
      <td>
        <Link className="row-action" to={`/analytics/jobs/${job.id}`}>
          Abrir
        </Link>
      </td>
    </tr>
  );
}
