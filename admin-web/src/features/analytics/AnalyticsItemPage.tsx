import { useState } from "react";
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

const formatDuration = (duration?: number | null) => {
  if (typeof duration !== "number" || !Number.isFinite(duration)) {
    return "em curso";
  }
  if (duration < 1000) return `${Math.round(duration)} ms`;

  const totalSeconds = Math.round(duration / 1000);
  if (totalSeconds < 60) return `${totalSeconds} s`;

  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes < 60) return seconds ? `${minutes}m ${seconds}s` : `${minutes}m`;

  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  if (hours < 24) {
    return remainingMinutes ? `${hours}h ${remainingMinutes}m` : `${hours}h`;
  }

  const days = Math.floor(hours / 24);
  const remainingHours = hours % 24;
  return remainingHours ? `${days}d ${remainingHours}h` : `${days}d`;
};

export function AnalyticsItemPage() {
  const { jobId = "", itemId = "" } = useParams();
  const [selectedAttempt, setSelectedAttempt] = useState<number | null>(null);
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
  const phasesByAttempt = phases.reduce<Map<number, typeof phases>>(
    (groups, phase) => {
      const attempt = phase.attempt_count || 1;
      const group = groups.get(attempt) ?? [];
      group.push(phase);
      groups.set(attempt, group);
      return groups;
    },
    new Map(),
  );
  const attempts = [...phasesByAttempt.entries()].sort(
    ([left], [right]) => right - left,
  );
  const activeAttempt =
    selectedAttempt ?? item.attempt_count ?? attempts[0]?.[0] ?? 1;
  const selectedPhases = phasesByAttempt.get(activeAttempt) ?? [];
  const hasHistoricalFailure = attempts.some(
    ([attempt, attemptPhases]) =>
      attempt < item.attempt_count &&
      attemptPhases.some((phase) => phase.status === "FAILED"),
  );
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
          {hasHistoricalFailure && (
            <div className="phase-history-note">
              Falhas de tentativas anteriores são mantidas para auditoria e não
              representam o estado da tentativa atual.
            </div>
          )}
          <div className="phase-tabs" role="tablist" aria-label="Tentativas">
            {attempts.map(([attempt, attemptPhases]) => {
              const failed = attemptPhases.some(
                (phase) => phase.status === "FAILED",
              );
              return (
                <button
                  className={`phase-tab ${
                    activeAttempt === attempt ? "active" : ""
                  }`}
                  key={attempt}
                  onClick={() => setSelectedAttempt(attempt)}
                  role="tab"
                  aria-selected={activeAttempt === attempt}
                  type="button"
                >
                  <span>Tentativa {attempt}</span>
                  <small>
                    {attempt === item.attempt_count ? "atual" : "histórico"}
                    {failed ? " · falhou" : ""}
                  </small>
                </button>
              );
            })}
          </div>
          <div className="phase-list" role="tabpanel">
            {selectedPhases.map((phase) => (
              <div className="phase-row" key={phase.id}>
                <PhaseIcon status={phase.status} />
                <div>
                  <strong>{phaseLabel(phase.phase)}</strong>
                  <small>
                    {phase.status} · início {formatDate(phase.started_at)} · fim{" "}
                    {formatDate(phase.finished_at)}
                  </small>
                </div>
                <b>{formatDuration(phase.duration_ms)}</b>
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
