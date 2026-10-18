import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, ChevronLeft, ChevronRight, Search } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { services } from "../../lib/services";

const formatDate = (value?: string) =>
  value ? new Date(value).toLocaleString() : "—";

export function AnalyticsJobPage() {
  const { jobId = "" } = useParams();
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("ALL");
  const [sort, setSort] = useState("created");
  const [page, setPage] = useState(0);
  const job = useQuery({
    queryKey: ["analytics-job", jobId],
    queryFn: () => services.getAnalyticsJob(jobId),
    refetchInterval: 4000,
  });
  const items = useQuery({
    queryKey: ["analytics-job-items", jobId, page, status],
    queryFn: () =>
      services.getAnalyticsJobItems(jobId, {
        limit: 100,
        offset: page * 100,
        status: status === "ALL" ? undefined : status,
      }),
    refetchInterval: 4000,
  });
  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return [...(items.data?.items ?? [])]
      .filter(
        (item) =>
          !needle ||
          item.id.toLowerCase().includes(needle) ||
          item.input_asset_version_id.toLowerCase().includes(needle) ||
          item.book_id?.toLowerCase().includes(needle),
      )
      .sort((a, b) =>
        sort === "status"
          ? a.status.localeCompare(b.status)
          : sort === "id"
            ? a.id.localeCompare(b.id)
            : sort === "attempts"
              ? b.attempt_count - a.attempt_count
              : new Date(b.started_at ?? 0).getTime() -
                new Date(a.started_at ?? 0).getTime(),
      );
  }, [items.data, query, sort]);
  const jobData = job.data as Record<string, unknown> | undefined;
  return (
    <section className="analytics-console">
      <div className="analytics-breadcrumb">
        <Link to="/analytics">
          <ArrowLeft size={14} /> Jobs
        </Link>
        <span>/ {jobId.slice(0, 8)}</span>
      </div>
      <header className="pagehead analytics-pagehead">
        <div>
          <span className="eyebrow">JOB DETAIL</span>
          <h1>{jobId}</h1>
          <p>
            {String(jobData?.status ?? "Carregando")} ·{" "}
            {jobData?.current_stage
              ? String(jobData.current_stage)
              : "sem estágio"}
          </p>
        </div>
      </header>
      <div className="analytics-summary-strip">
        <b>
          {Number(jobData?.processed_items ?? 0)}/
          {Number(jobData?.total_items ?? 0)}
        </b>
        <span>processados</span>
        <b>{Number(jobData?.succeeded_items ?? 0)}</b>
        <span>sucesso</span>
        <b>{Number(jobData?.failed_items ?? 0)}</b>
        <span>falhas</span>
        <span>
          Início: {formatDate(jobData?.started_at as string | undefined)}
        </span>
      </div>
      <div className="analytics-toolbar">
        <label className="search-field">
          <Search size={15} />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Filtrar item, asset ou livro"
          />
        </label>
        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
        >
          <option value="ALL">Todos</option>
          <option value="PENDING">Pendentes</option>
          <option value="RUNNING">Em execução</option>
          <option value="COMPLETED">Concluídos</option>
          <option value="FAILED">Falhos</option>
        </select>
        <select value={sort} onChange={(event) => setSort(event.target.value)}>
          <option value="created">Mais recentes</option>
          <option value="attempts">Tentativas</option>
          <option value="status">Status</option>
          <option value="id">ID</option>
        </select>
        <span className="table-count">{visible.length} itens nesta página</span>
      </div>
      <div className="table-shell">
        <table className="admin-table analytics-table">
          <thead>
            <tr>
              <th>Item / livro</th>
              <th>Status</th>
              <th>Fase</th>
              <th>Tentativas</th>
              <th>Início</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {visible.map((item) => (
              <tr key={item.id}>
                <td>
                  <Link
                    className="table-link"
                    to={`/analytics/jobs/${jobId}/items/${item.id}`}
                  >
                    <strong>{item.id.slice(0, 8)}</strong>
                    <small>{item.book_id ?? item.input_asset_version_id}</small>
                  </Link>
                </td>
                <td>
                  <span
                    className={`status-dot status-${item.status.toLowerCase()}`}
                  >
                    {item.status}
                  </span>
                </td>
                <td>{item.progress_stage ?? "—"}</td>
                <td>{item.attempt_count}</td>
                <td>{formatDate(item.started_at)}</td>
                <td>
                  <Link
                    className="row-action"
                    to={`/analytics/jobs/${jobId}/items/${item.id}`}
                  >
                    Detalhes
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {visible.length === 0 && (
          <div className="empty-table">
            Nenhum item corresponde aos filtros.
          </div>
        )}
      </div>
      <div className="table-pagination">
        <button
          className="button-action compact-action"
          disabled={page === 0}
          onClick={() => setPage((value) => value - 1)}
        >
          <ChevronLeft size={14} /> Anterior
        </button>
        <span>Página {page + 1}</span>
        <button
          className="button-action compact-action"
          disabled={!items.data?.hasMore}
          onClick={() => setPage((value) => value + 1)}
        >
          Próxima <ChevronRight size={14} />
        </button>
      </div>
    </section>
  );
}
