import { Link, useParams } from "react-router-dom";
import {
  ArrowLeft,
  CheckCircle2,
  Circle,
  LoaderCircle,
  XCircle,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { services } from "../../lib/services";

const formatDate = (value?: string) =>
  value ? new Date(value).toLocaleString() : "—";
const phaseLabel = (phase: string) =>
  phase
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/(^|\s)\S/g, (letter: string) => letter.toUpperCase());

export function AnalyticsItemPage() {
  const { jobId = "", itemId = "" } = useParams();
  const detail = useQuery({
    queryKey: ["analytics-item", jobId, itemId],
    queryFn: () => services.getAnalyticsItemDetail(jobId, itemId),
    refetchInterval: 3000,
  });
  if (detail.isLoading)
    return <section className="panel">Carregando detalhe do item…</section>;
  if (detail.isError || !detail.data)
    return (
      <section className="panel import-error">
        Não foi possível consultar o item.
      </section>
    );
  const { item, phases, statistics, remainingPhases } = detail.data;
  return (
    <section className="analytics-console">
      <div className="analytics-breadcrumb">
        <Link to={`/analytics/jobs/${jobId}`}>
          <ArrowLeft size={14} /> Job {jobId.slice(0, 8)}
        </Link>
        <span>/ item {itemId.slice(0, 8)}</span>
      </div>
      <header className="pagehead analytics-pagehead">
        <div>
          <span className="eyebrow">ITEM TRACE</span>
          <h1>{item.book_id ?? item.input_asset_version_id}</h1>
          <p>
            {item.status} · tentativa {item.attempt_count} · fase atual:{" "}
            {item.progress_stage ?? "—"}
          </p>
        </div>
      </header>
      <div className="analytics-summary-strip">
        <b>{statistics.excerpts ?? 0}</b>
        <span>excerpts</span>
        <b>{statistics.features ?? 0}</b>
        <span>features</span>
        <b>{statistics.embeddings ?? 0}</b>
        <span>embeddings</span>
        <span>Início: {formatDate(item.started_at)}</span>
        <span>Atualização: {formatDate(item.progress_updated_at)}</span>
      </div>
      <div className="detail-grid">
        <div className="panel compact-panel">
          <div className="panelhead">
            <div>
              <h2>Fases</h2>
              <span>Histórico por tentativa e duração persistida.</span>
            </div>
          </div>
          <div className="phase-list">
            {phases.map((phase) => (
              <div className="phase-row" key={phase.id}>
                <PhaseIcon status={phase.status} />
                <div>
                  <strong>{phaseLabel(phase.phase)}</strong>
                  <small>
                    {phase.status} · início {formatDate(phase.started_at)} · fim{" "}
                    {formatDate(phase.finished_at)}
                  </small>
                </div>
                <b>
                  {phase.duration_ms !== undefined
                    ? `${phase.duration_ms} ms`
                    : "em curso"}
                </b>
              </div>
            ))}
            {phases.length === 0 && (
              <p className="muted">Nenhuma fase registrada.</p>
            )}
          </div>
        </div>
        <div className="panel compact-panel">
          <h2>A fazer</h2>
          <div className="remaining-list">
            {remainingPhases.map((phase) => (
              <span key={phase}>
                <Circle size={13} /> {phaseLabel(phase)}
              </span>
            ))}
            {remainingPhases.length === 0 && (
              <span className="success-text">
                <CheckCircle2 size={14} /> Todas as fases concluídas
              </span>
            )}
          </div>
          {item.error_message && (
            <p className="error-text">
              {item.error_code}: {item.error_message}
            </p>
          )}
        </div>
      </div>
    </section>
  );
}

function PhaseIcon({ status }: { status: string }) {
  if (status === "COMPLETED")
    return <CheckCircle2 className="phase-complete" size={16} />;
  if (status === "FAILED")
    return <XCircle className="phase-failed" size={16} />;
  return <LoaderCircle className="phase-running" size={16} />;
}
