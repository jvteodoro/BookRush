import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle, RefreshCw, ShieldAlert } from "lucide-react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useSearchParams } from "react-router-dom";
import { config } from "../../lib/config";
import {
  services,
  ExecutivePeriod,
  ExecutiveSummary,
  ReportingResponse,
} from "../../lib/services";

const areas = [
  ["executive", "Executive"],
  ["operations", "Operations"],
  ["pipelines", "Pipelines & Content"],
  ["growth", "Growth & Reading"],
  ["feed", "Feed & Recommendations"],
  ["social", "Social & Publisher"],
  ["quality", "Data Quality & Governance"],
] as const;

const unavailablePanels: Record<string, string[]> = {
  operations: ["OP-01..OP-12", "Prometheus/Grafana runtime query"],
  pipelines: ["PL-01..PL-11", "Ingestion and analytics projections"],
  feed: ["FD-01..FD-13", "Ledger attribution and experiment assignment"],
  social: ["SO-01..SO-07", "Social/publisher reporting projection"],
  quality: ["DQ-01..DQ-08", "Event registry and projection freshness"],
};

function statusText(status: string) {
  if (status === "UNTRUSTED")
    return "UNTRUSTED — população ainda não classificada";
  if (status === "NO_DATA") return "NO_DATA — não há snapshot no período";
  return status;
}

function ReadingChart({ data }: { data: ReportingResponse }) {
  const chartData = data.series.map((row) => ({
    day: row.day,
    activeUsers: row.active_users ?? null,
    weeklyActiveUsers: row.weekly_active_users ?? null,
    monthlyActiveUsers: row.monthly_active_users ?? null,
    sessions: row.sessions ?? null,
    meaningfulReads: row.meaningful_reads ?? null,
  }));
  if (!chartData.length) return <div className="dashboard-state">NO_DATA</div>;
  return (
    <>
      <div className="dashboard-chart" aria-label="Atividade diária de leitura">
        <ResponsiveContainer width="100%" height={250}>
          <LineChart data={chartData} margin={{ left: 8, right: 16, top: 8 }}>
            <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
            <XAxis dataKey="day" stroke="#8290a8" tick={{ fontSize: 11 }} />
            <YAxis stroke="#8290a8" tick={{ fontSize: 11 }} />
            <Tooltip
              contentStyle={{
                background: "#111828",
                border: "1px solid #2a3a55",
              }}
            />
            <Line
              type="monotone"
              dataKey="activeUsers"
              name="Leitores ativos"
              stroke="#8cf0d2"
              dot={false}
              connectNulls={false}
            />
            <Line
              type="monotone"
              dataKey="weeklyActiveUsers"
              name="WAU"
              stroke="#bb9cff"
              dot={false}
              connectNulls={false}
            />
            <Line
              type="monotone"
              dataKey="monthlyActiveUsers"
              name="MAU"
              stroke="#ff9f7f"
              dot={false}
              connectNulls={false}
            />
            <Line
              type="monotone"
              dataKey="sessions"
              name="Sessões"
              stroke="#7fa8ff"
              dot={false}
              connectNulls={false}
            />
            <Line
              type="monotone"
              dataKey="meaningfulReads"
              name="Leituras significativas"
              stroke="#f6c76d"
              dot={false}
              connectNulls={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
      <table className="dashboard-table">
        <caption>
          Valores reais do snapshot; população {data.population}
        </caption>
        <thead>
          <tr>
            <th>Dia</th>
            <th>DAU</th>
            <th>WAU</th>
            <th>MAU</th>
            <th>Novos</th>
            <th>Recorrentes</th>
            <th>Sessões</th>
            <th>Leituras ≥60s</th>
          </tr>
        </thead>
        <tbody>
          {data.series.slice(-14).map((row) => (
            <tr key={row.day}>
              <td>{row.day}</td>
              <td>{row.active_users ?? "—"}</td>
              <td>{row.weekly_active_users ?? "—"}</td>
              <td>{row.monthly_active_users ?? "—"}</td>
              <td>{row.newly_active_users ?? "—"}</td>
              <td>{row.returning_users ?? "—"}</td>
              <td>{row.sessions ?? "—"}</td>
              <td>{row.meaningful_reads ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

function ExecutiveSummaryReport({ data }: { data: ExecutiveSummary }) {
  const current = data.current;
  const previous = data.previous;
  const delta = (key: keyof ExecutivePeriod) => {
    const now = current[key];
    const before = previous[key];
    if (typeof now !== "number" || typeof before !== "number") return "—";
    return `${now - before >= 0 ? "+" : ""}${now - before}`;
  };
  const stages = [
    ["Servidos", current.served, delta("served")],
    ["Viewable", current.viewable, delta("viewable")],
    ["Abertos", current.opened, delta("opened")],
    ["Read ≥10%", current.meaningful, delta("meaningful")],
    ["Concluídos", current.complete, delta("complete")],
  ];
  return (
    <>
      <div className="dashboard-quality-grid">
        <div>
          <strong>EX-01/04/06 · KPIs do período</strong>
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>KPI</th>
                <th>Atual</th>
                <th>Anterior</th>
                <th>Delta</th>
              </tr>
            </thead>
            <tbody>
              {[
                [
                  "Pico de leitores",
                  current.daily_peak_users,
                  previous.daily_peak_users,
                  delta("daily_peak_users"),
                ],
                [
                  "Sessões",
                  current.sessions,
                  previous.sessions,
                  delta("sessions"),
                ],
                [
                  "Minutos ativos",
                  current.active_seconds == null
                    ? null
                    : Math.round(current.active_seconds / 60),
                  previous.active_seconds == null
                    ? null
                    : Math.round(previous.active_seconds / 60),
                  delta("active_seconds"),
                ],
                [
                  "Leituras significativas",
                  current.meaningful_reads,
                  previous.meaningful_reads,
                  delta("meaningful_reads"),
                ],
              ].map(([label, now, before, change]) => (
                <tr key={String(label)}>
                  <td>{label}</td>
                  <td>{now ?? "—"}</td>
                  <td>{before ?? "—"}</td>
                  <td>{change}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div>
          <strong>EX-02 · Funil atribuído</strong>
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Etapa</th>
                <th>Atual</th>
                <th>Delta</th>
              </tr>
            </thead>
            <tbody>
              {stages.map(([label, value, change]) => (
                <tr key={String(label)}>
                  <td>{label}</td>
                  <td>{value ?? "—"}</td>
                  <td>{change}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      <p className="dashboard-caption">
        Comparação observacional entre janelas de {data.days} dias; valores sem
        snapshot permanecem como — e não representam zero.
      </p>
    </>
  );
}

function OperationsReport({ health }: { health: Record<string, unknown>[] }) {
  const grafanaUrl = `${config.observability.grafanaUrl}${config.observability.operationsPath}`;
  return (
    <div className="dashboard-card wide">
      <div className="dashboard-card-heading">
        <div>
          <strong>OP-01..OP-12 · Saúde operacional</strong>
          <p>
            Métricas de infraestrutura, HTTP, JVM, PostgreSQL, tracing e alertas
            permanecem no Grafana autenticado; o Admin Web exibe a saúde das
            projeções sem transportar credenciais.
          </p>
        </div>
        <a
          className="button secondary"
          href={grafanaUrl}
          target="_blank"
          rel="noreferrer"
        >
          Abrir Grafana
        </a>
      </div>
      {!health.length ? (
        <div className="dashboard-state">NO_DATA</div>
      ) : (
        <div className="dashboard-scroll-table">
          <table className="dashboard-table">
            <caption>
              Reporting projection health · fonte administrativa
            </caption>
            <thead>
              <tr>
                <th>Projeção</th>
                <th>Status</th>
                <th>Última atualização</th>
                <th>Registros</th>
                <th>Versão</th>
              </tr>
            </thead>
            <tbody>
              {health.map((row, index) => (
                <tr key={`${String(row.projection_name)}-${index}`}>
                  <td>{String(row.projection_name ?? "—")}</td>
                  <td>{String(row.refresh_status ?? "—")}</td>
                  <td>{String(row.last_successful_refresh ?? "—")}</td>
                  <td>{String(row.record_count ?? "—")}</td>
                  <td>{String(row.definition_version ?? "—")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function ReadingEngagementReport({
  reading,
  retention,
}: {
  reading: ReportingResponse;
  retention: ReportingResponse;
}) {
  return (
    <div className="dashboard-quality-grid">
      <div>
        <strong>GR-04/05/07 · Sessões, duração e biblioteca</strong>
        {!reading.series.length ? (
          <div className="dashboard-state">NO_DATA</div>
        ) : (
          <div className="dashboard-scroll-table">
            <table className="dashboard-table">
              <thead>
                <tr>
                  <th>Dia</th>
                  <th>Leitores</th>
                  <th>Sessões</th>
                  <th>Minutos ativos</th>
                  <th>P50 sessão</th>
                  <th>P90 sessão</th>
                  <th>Obras abertas</th>
                  <th>Salvas</th>
                </tr>
              </thead>
              <tbody>
                {reading.series.slice(-30).map((row, index) => (
                  <tr key={`${row.day}-${index}`}>
                    <td>{row.day ?? "—"}</td>
                    <td>{row.unique_readers ?? "—"}</td>
                    <td>{row.sessions ?? "—"}</td>
                    <td>
                      {row.active_seconds == null
                        ? "—"
                        : Math.round(row.active_seconds / 60)}
                    </td>
                    <td>
                      {row.p50_session_seconds == null
                        ? "—"
                        : `${Math.round(row.p50_session_seconds)}s`}
                    </td>
                    <td>
                      {row.p90_session_seconds == null
                        ? "—"
                        : `${Math.round(row.p90_session_seconds)}s`}
                    </td>
                    <td>{row.books_opened ?? "—"}</td>
                    <td>{row.saved_books ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <div>
        <strong>GR-03 · Retenção por coorte</strong>
        {!retention.series.length ? (
          <div className="dashboard-state">NO_DATA</div>
        ) : (
          <div className="dashboard-scroll-table">
            <table className="dashboard-table">
              <thead>
                <tr>
                  <th>Coorte</th>
                  <th>Dia</th>
                  <th>n</th>
                  <th>Retidos</th>
                  <th>Taxa</th>
                  <th>Madura</th>
                </tr>
              </thead>
              <tbody>
                {retention.series.slice(-40).map((row, index) => (
                  <tr key={`${row.cohort_day}-${row.offset_day}-${index}`}>
                    <td>{row.cohort_day ?? "—"}</td>
                    <td>D{row.offset_day ?? "—"}</td>
                    <td>{row.cohort_users ?? "—"}</td>
                    <td>{row.retained_users ?? "—"}</td>
                    <td>
                      {row.mature && row.cohort_users
                        ? `${(((row.retained_users ?? 0) / row.cohort_users) * 100).toFixed(1)}%`
                        : "—"}
                    </td>
                    <td>{row.mature ? "SIM" : "NÃO"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function FeedReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length)
    return <div className="dashboard-state">NO_DATA</div>;
  return (
    <div className="dashboard-scroll-table">
      <table className="dashboard-table">
        <caption>
          Entrega e conversão atribuída; população {data.population}; versão{" "}
          {data.definitionVersion}
        </caption>
        <thead>
          <tr>
            <th>Dia</th>
            <th>Estratégia</th>
            <th>Servidos</th>
            <th>Viewable</th>
            <th>Aberturas</th>
            <th>Read 10%</th>
            <th>Read 50%</th>
            <th>Conclusões</th>
          </tr>
        </thead>
        <tbody>
          {data.series.slice(-50).map((row, index) => (
            <tr
              key={`${row.day}-${row.strategy}-${row.model_version}-${index}`}
            >
              <td>{row.day ?? "—"}</td>
              <td>{row.strategy ?? "—"}</td>
              <td>{row.served_count ?? "—"}</td>
              <td>{row.viewable_count ?? "—"}</td>
              <td>{row.attributed_open_count ?? "—"}</td>
              <td>{row.attributed_read_10_count ?? "—"}</td>
              <td>{row.attributed_read_50_count ?? "—"}</td>
              <td>{row.attributed_complete_count ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function RecommendationReport({
  data,
  quality,
}: {
  data: ReportingResponse;
  quality: ReportingResponse;
}) {
  if (!data.series.length)
    return <div className="dashboard-state">NO_DATA</div>;
  return (
    <div className="dashboard-scroll-table">
      <table className="dashboard-table">
        <caption>
          FD-05/08/09 · ledger por estratégia e caminho; comparação
          observacional, sem inferência causal
        </caption>
        <thead>
          <tr>
            <th>Dia</th>
            <th>Modelo</th>
            <th>Estratégia</th>
            <th>Caminho</th>
            <th>Requests</th>
            <th>Itens</th>
            <th>Impressões</th>
            <th>Viewable</th>
            <th>Livros</th>
            <th>P50 rank</th>
            <th>P90 rank</th>
          </tr>
        </thead>
        <tbody>
          {data.series.slice(-60).map((row, index) => (
            <tr
              key={`${row.day}-${row.strategy}-${row.model_version}-${row.feed_path}-${index}`}
            >
              <td>{row.day ?? "—"}</td>
              <td>{row.model_version ?? "—"}</td>
              <td>{row.strategy ?? "—"}</td>
              <td>{row.feed_path ?? "—"}</td>
              <td>{row.request_count ?? "—"}</td>
              <td>{row.item_count ?? "—"}</td>
              <td>{row.impression_count ?? "—"}</td>
              <td>{row.viewable_count ?? "—"}</td>
              <td>{row.unique_books ?? "—"}</td>
              <td>{row.p50_rank == null ? "—" : Math.round(row.p50_rank)}</td>
              <td>{row.p90_rank == null ? "—" : Math.round(row.p90_rank)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {quality.series.length > 0 && (
        <table className="dashboard-table">
          <caption>
            Outcomes atribuídos ao item recomendado; sem inferência causal
          </caption>
          <thead>
            <tr>
              <th>Dia</th>
              <th>Estratégia</th>
              <th>Outcome</th>
              <th>Atribuídos</th>
              <th>Usuários</th>
              <th>Livros</th>
              <th>P50 rank</th>
            </tr>
          </thead>
          <tbody>
            {quality.series.slice(-60).map((row, index) => (
              <tr
                key={`${row.day}-${row.strategy}-${row.outcome_type}-${index}`}
              >
                <td>{row.day ?? "—"}</td>
                <td>{row.strategy ?? "—"}</td>
                <td>{row.outcome_type ?? "—"}</td>
                <td>{row.attributed_count ?? "—"}</td>
                <td>{row.unique_subjects ?? "—"}</td>
                <td>{row.unique_books ?? "—"}</td>
                <td>{row.p50_rank == null ? "—" : Math.round(row.p50_rank)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function PipelineReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length)
    return <div className="dashboard-state">NO_DATA</div>;
  return (
    <div className="dashboard-scroll-table">
      <table className="dashboard-table">
        <caption>
          Snapshots reais de jobs e fases; estado de população {data.population}
        </caption>
        <thead>
          <tr>
            <th>Coletado</th>
            <th>Fase</th>
            <th>Estado</th>
            <th>Itens</th>
            <th>Falhas</th>
            <th>Throughput/h</th>
            <th>Fila mais antiga</th>
          </tr>
        </thead>
        <tbody>
          {data.series.slice(-50).map((row, index) => (
            <tr
              key={`${row.collected_at}-${row.phase}-${row.job_status}-${index}`}
            >
              <td>
                {row.collected_at
                  ? new Date(row.collected_at).toLocaleString()
                  : "—"}
              </td>
              <td>{row.phase ?? "—"}</td>
              <td>{row.job_status ?? "—"}</td>
              <td>{row.status_count ?? "—"}</td>
              <td>{row.failure_count ?? "—"}</td>
              <td>{row.throughput ?? "—"}</td>
              <td>
                {row.oldest_pending_age_seconds == null
                  ? "—"
                  : `${row.oldest_pending_age_seconds}s`}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PipelinePhaseReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length)
    return <div className="dashboard-state">NO_DATA</div>;
  return (
    <div className="dashboard-scroll-table">
      <table className="dashboard-table">
        <caption>
          PL-04/05/11 · percentis reais de duração, falhas e stuck leases;
          `NONE` significa ausência de código de erro
        </caption>
        <thead>
          <tr>
            <th>Dia</th>
            <th>Fase</th>
            <th>Estado</th>
            <th>Erro</th>
            <th>Tentativas</th>
            <th>Concluídos</th>
            <th>Falhas</th>
            <th>Stuck</th>
            <th>P50 ms</th>
            <th>P95 ms</th>
            <th>P99 ms</th>
          </tr>
        </thead>
        <tbody>
          {data.series.slice(-60).map((row, index) => (
            <tr
              key={`${row.day}-${row.phase}-${row.phase_status}-${row.error_code}-${index}`}
            >
              <td>{row.day ?? "—"}</td>
              <td>{row.phase ?? "—"}</td>
              <td>{row.phase_status ?? "—"}</td>
              <td>{row.error_code ?? "—"}</td>
              <td>{row.attempts ?? "—"}</td>
              <td>{row.completed_count ?? "—"}</td>
              <td>{row.failed_count ?? "—"}</td>
              <td>{row.stuck_count ?? "—"}</td>
              <td>
                {row.p50_duration_ms == null
                  ? "—"
                  : Math.round(row.p50_duration_ms)}
              </td>
              <td>
                {row.p95_duration_ms == null
                  ? "—"
                  : Math.round(row.p95_duration_ms)}
              </td>
              <td>
                {row.p99_duration_ms == null
                  ? "—"
                  : Math.round(row.p99_duration_ms)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function SupplyReport({
  supply,
  ingestion,
}: {
  supply: ReportingResponse;
  ingestion: ReportingResponse;
}) {
  return (
    <div className="dashboard-quality-grid">
      <div>
        <strong>PL-01/02 · Oferta analítica disponível</strong>
        {!supply.series.length ? (
          <div className="dashboard-state">NO_DATA</div>
        ) : (
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Coletado</th>
                <th>Livros ativos</th>
                <th>Assets disponíveis</th>
                <th>Assets analisados</th>
                <th>Excerpts elegíveis</th>
                <th>Excerpts rankeados</th>
              </tr>
            </thead>
            <tbody>
              {supply.series.slice(-30).map((row, index) => (
                <tr key={`${row.collected_at}-${index}`}>
                  <td>
                    {row.collected_at
                      ? new Date(row.collected_at).toLocaleString()
                      : "—"}
                  </td>
                  <td>{row.active_books ?? "—"}</td>
                  <td>{row.available_asset_versions ?? "—"}</td>
                  <td>{row.analyzed_asset_versions ?? "—"}</td>
                  <td>{row.eligible_excerpts ?? "—"}</td>
                  <td>{row.ranked_excerpts ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <div>
        <strong>PL-06/07 · Ingestão por estado</strong>
        {!ingestion.series.length ? (
          <div className="dashboard-state">NO_DATA</div>
        ) : (
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Dia</th>
                <th>Jobs</th>
                <th>Estado job</th>
                <th>Estado item</th>
                <th>Descobertos</th>
                <th>Processados</th>
                <th>Falhas</th>
              </tr>
            </thead>
            <tbody>
              {ingestion.series.slice(-40).map((row, index) => (
                <tr
                  key={`${row.day}-${row.job_status}-${row.item_status}-${index}`}
                >
                  <td>{row.day ?? "—"}</td>
                  <td>{row.job_count ?? "—"}</td>
                  <td>{row.job_status ?? "—"}</td>
                  <td>{row.item_status ?? "—"}</td>
                  <td>{row.discovered_items ?? "—"}</td>
                  <td>{row.processed_items ?? "—"}</td>
                  <td>{row.failed_items ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function QualityReport({
  attribution,
  reconciliation,
  eventQuality,
  eventSchema,
}: {
  attribution: ReportingResponse;
  reconciliation: ReportingResponse;
  eventQuality: ReportingResponse;
  eventSchema: ReportingResponse;
}) {
  return (
    <div className="dashboard-quality-grid">
      <div>
        <strong>DQ-03 · Atribuição</strong>
        {!attribution.series.length ? (
          <div className="dashboard-state">NO_DATA</div>
        ) : (
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Dia</th>
                <th>Estado</th>
                <th>Outcomes</th>
                <th>Eventos únicos</th>
              </tr>
            </thead>
            <tbody>
              {attribution.series.slice(-30).map((row, index) => (
                <tr key={`${row.day}-${row.attribution_status}-${index}`}>
                  <td>{row.day ?? "—"}</td>
                  <td>{row.attribution_status ?? "—"}</td>
                  <td>{row.outcome_count ?? "—"}</td>
                  <td>{row.unique_events ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <div>
        <strong>DQ-04 · Reconciliação de impressões</strong>
        {!reconciliation.series.length ? (
          <div className="dashboard-state">NO_DATA</div>
        ) : (
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Dia</th>
                <th>Ledger</th>
                <th>Viewable</th>
                <th>Eventos</th>
                <th>Duplicados</th>
                <th>Sem match</th>
              </tr>
            </thead>
            <tbody>
              {reconciliation.series.slice(-30).map((row, index) => (
                <tr key={`${row.day}-${index}`}>
                  <td>{row.day ?? "—"}</td>
                  <td>{row.ledger_impression_count ?? "—"}</td>
                  <td>{row.ledger_viewable_count ?? "—"}</td>
                  <td>{row.behavior_impression_event_count ?? "—"}</td>
                  <td>{row.duplicate_event_count ?? "—"}</td>
                  <td>{row.mismatched_impression_count ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <div>
        <strong>DQ-01/02 · Eventos e outbox</strong>
        {!eventQuality.series.length ? (
          <div className="dashboard-state">NO_DATA</div>
        ) : (
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Dia</th>
                <th>Aceitos</th>
                <th>Rejeitados</th>
                <th>Duplicados</th>
                <th>Pending outbox</th>
                <th>Idade</th>
                <th>Tentativas</th>
              </tr>
            </thead>
            <tbody>
              {eventQuality.series.slice(-30).map((row, index) => (
                <tr key={`${row.day}-${index}`}>
                  <td>{row.day ?? "—"}</td>
                  <td>{row.accepted_events ?? "—"}</td>
                  <td>{row.rejected_events ?? "—"}</td>
                  <td>{row.duplicate_rejections ?? "—"}</td>
                  <td>{row.outbox_pending ?? "—"}</td>
                  <td>
                    {row.outbox_oldest_pending_age_seconds == null
                      ? "—"
                      : `${row.outbox_oldest_pending_age_seconds}s`}
                  </td>
                  <td>{row.outbox_failed_attempts ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <div>
        <strong>DQ-06 · Schema e telemetria</strong>
        {!eventSchema.series.length ? (
          <div className="dashboard-state">NO_DATA</div>
        ) : (
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Dia</th>
                <th>Tipo</th>
                <th>Registro</th>
                <th>Schema</th>
                <th>Cliente</th>
                <th>Motivo</th>
                <th>n</th>
                <th>Desconhecidos</th>
              </tr>
            </thead>
            <tbody>
              {eventSchema.series.slice(-40).map((row, index) => (
                <tr
                  key={`${row.day}-${row.record_kind}-${row.event_type}-${index}`}
                >
                  <td>{row.day ?? "—"}</td>
                  <td>{row.event_type ?? "—"}</td>
                  <td>{row.record_kind ?? "—"}</td>
                  <td>{row.schema_version ?? "—"}</td>
                  <td>{row.client_version ?? "—"}</td>
                  <td>{row.reject_reason ?? "—"}</td>
                  <td>{row.record_count ?? "—"}</td>
                  <td>{row.unknown_event_count ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function BookEngagementReport({ data }: { data: ReportingResponse }) {
  const books = data.series.filter((row) => row.aggregation_level === "BOOK");
  const languages = data.series.filter(
    (row) => row.aggregation_level === "LANGUAGE",
  );
  if (!data.series.length) {
    return (
      <div className="dashboard-state">
        {data.population === "SUPPRESSED_BELOW_MIN_SAMPLE"
          ? "INSUFFICIENT_SAMPLE"
          : "NO_DATA"}
      </div>
    );
  }
  return (
    <div className="dashboard-quality-grid">
      <div>
        <strong>GR-10/SO-02 · Top obras</strong>
        <div className="dashboard-scroll-table">
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Dia</th>
                <th>Obra</th>
                <th>Idioma</th>
                <th>Leitores</th>
                <th>Minutos</th>
                <th>Aberturas</th>
                <th>Conclusões</th>
                <th>Interações</th>
              </tr>
            </thead>
            <tbody>
              {books.slice(0, 50).map((row, index) => (
                <tr key={`${row.day}-${row.book_id}-${index}`}>
                  <td>{row.day ?? "—"}</td>
                  <td>{row.book_title ?? "—"}</td>
                  <td>{row.language ?? "—"}</td>
                  <td>{row.unique_readers ?? "—"}</td>
                  <td>{row.active_minutes ?? "—"}</td>
                  <td>{row.opens ?? "—"}</td>
                  <td>{row.completions ?? "—"}</td>
                  <td>{row.interactions ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      <div>
        <strong>GR-10 · Idiomas</strong>
        <div className="dashboard-scroll-table">
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Dia</th>
                <th>Idioma</th>
                <th>Leitores</th>
                <th>Minutos</th>
                <th>Conclusões</th>
              </tr>
            </thead>
            <tbody>
              {languages.slice(0, 30).map((row, index) => (
                <tr key={`${row.day}-${row.language}-${index}`}>
                  <td>{row.day ?? "—"}</td>
                  <td>{row.language ?? "—"}</td>
                  <td>{row.unique_readers ?? "—"}</td>
                  <td>{row.active_minutes ?? "—"}</td>
                  <td>{row.completions ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function SocialReport({
  social,
  publisher,
  moderation,
  workflow,
  rights,
}: {
  social: ReportingResponse;
  publisher: ReportingResponse;
  moderation: ReportingResponse;
  workflow: ReportingResponse;
  rights: ReportingResponse;
}) {
  return (
    <>
      <div className="dashboard-quality-grid">
        <div>
          <strong>SO-01/SO-03 · Interações e moderação</strong>
          {!social.series.length ? (
            <div className="dashboard-state">NO_DATA</div>
          ) : (
            <table className="dashboard-table">
              <thead>
                <tr>
                  <th>Dia</th>
                  <th>Likes</th>
                  <th>Comentários</th>
                  <th>Shares</th>
                  <th>Atores</th>
                  <th>Reports abertos</th>
                  <th>Backlog moderação</th>
                </tr>
              </thead>
              <tbody>
                {social.series.slice(-30).map((row, index) => (
                  <tr key={`${row.day}-${index}`}>
                    <td>{row.day ?? "—"}</td>
                    <td>{row.like_count ?? "—"}</td>
                    <td>{row.comment_count ?? "—"}</td>
                    <td>{row.share_count ?? "—"}</td>
                    <td>{row.unique_actors ?? "—"}</td>
                    <td>{row.reports_open ?? "—"}</td>
                    <td>{row.moderation_backlog ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        <div>
          <strong>SO-05/SO-06 · Publicação</strong>
          {!publisher.series.length ? (
            <div className="dashboard-state">NO_DATA</div>
          ) : (
            <table className="dashboard-table">
              <thead>
                <tr>
                  <th>Dia</th>
                  <th>Obras</th>
                  <th>Impressões</th>
                  <th>Aberturas</th>
                  <th>Leituras</th>
                  <th>Likes</th>
                  <th>Shares</th>
                </tr>
              </thead>
              <tbody>
                {publisher.series.slice(-30).map((row, index) => (
                  <tr key={`${row.day}-${index}`}>
                    <td>{row.day ?? "—"}</td>
                    <td>{row.published_work_count ?? "—"}</td>
                    <td>{row.impressions ?? "—"}</td>
                    <td>{row.opens ?? "—"}</td>
                    <td>{row.reads ?? "—"}</td>
                    <td>{row.likes ?? "—"}</td>
                    <td>{row.shares ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
      <div className="dashboard-quality-grid">
        <div>
          <strong>SO-04 · Resolução de moderação</strong>
          {!moderation.series.length ? (
            <div className="dashboard-state">NO_DATA</div>
          ) : (
            <table className="dashboard-table">
              <thead>
                <tr>
                  <th>Dia</th>
                  <th>Abertos</th>
                  <th>Em análise</th>
                  <th>Resolvidos</th>
                  <th>Descartados</th>
                  <th>P50 resolução</th>
                  <th>P95 resolução</th>
                </tr>
              </thead>
              <tbody>
                {moderation.series.slice(-30).map((row, index) => (
                  <tr key={`${row.day}-${index}`}>
                    <td>{row.day ?? "—"}</td>
                    <td>{row.open_count ?? "—"}</td>
                    <td>{row.in_review_count ?? "—"}</td>
                    <td>{row.resolved_count ?? "—"}</td>
                    <td>{row.dismissed_count ?? "—"}</td>
                    <td>
                      {row.p50_resolution_seconds == null
                        ? "—"
                        : `${Math.round(row.p50_resolution_seconds)}s`}
                    </td>
                    <td>
                      {row.p95_resolution_seconds == null
                        ? "—"
                        : `${Math.round(row.p95_resolution_seconds)}s`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        <div>
          <strong>SO-05/07 · Funil editorial e direitos</strong>
          {!workflow.series.length && !rights.series.length ? (
            <div className="dashboard-state">NO_DATA</div>
          ) : (
            <table className="dashboard-table">
              <thead>
                <tr>
                  <th>Dia</th>
                  <th>Submissões</th>
                  <th>Aprovadas</th>
                  <th>Publicadas</th>
                  <th>Direitos</th>
                  <th>Ação/status</th>
                  <th>Decisões</th>
                </tr>
              </thead>
              <tbody>
                {workflow.series.slice(-30).map((row, index) => (
                  <tr key={`w-${row.day}-${index}`}>
                    <td>{row.day ?? "—"}</td>
                    <td>{row.submissions ?? "—"}</td>
                    <td>{row.approved_reviews ?? "—"}</td>
                    <td>{row.published_works ?? "—"}</td>
                    <td>{row.rights_attestations ?? "—"}</td>
                    <td>—</td>
                    <td>—</td>
                  </tr>
                ))}
                {rights.series.slice(-20).map((row, index) => (
                  <tr
                    key={`r-${row.day}-${row.action}-${row.distribution_status}-${index}`}
                  >
                    <td>{row.day ?? "—"}</td>
                    <td>—</td>
                    <td>—</td>
                    <td>—</td>
                    <td>—</td>
                    <td>
                      {row.action ?? "—"}/{row.distribution_status ?? "—"}
                    </td>
                    <td>{row.decision_count ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
  );
}

export function DashboardHub() {
  const [params, setParams] = useSearchParams();
  const active = params.get("area") ?? "executive";
  const days = Number(params.get("days") ?? "30");
  const [report, setReport] = useState<ReportingResponse | null>(null);
  const [executiveSummaryReport, setExecutiveSummaryReport] =
    useState<ExecutiveSummary | null>(null);
  const [feedReport, setFeedReport] = useState<ReportingResponse | null>(null);
  const [recommendationReport, setRecommendationReport] =
    useState<ReportingResponse | null>(null);
  const [recommendationQualityReport, setRecommendationQualityReport] =
    useState<ReportingResponse | null>(null);
  const [pipelineReport, setPipelineReport] =
    useState<ReportingResponse | null>(null);
  const [pipelinePhaseReport, setPipelinePhaseReport] =
    useState<ReportingResponse | null>(null);
  const [attributionReport, setAttributionReport] =
    useState<ReportingResponse | null>(null);
  const [reconciliationReport, setReconciliationReport] =
    useState<ReportingResponse | null>(null);
  const [eventQualityReport, setEventQualityReport] =
    useState<ReportingResponse | null>(null);
  const [eventSchemaReport, setEventSchemaReport] =
    useState<ReportingResponse | null>(null);
  const [socialReport, setSocialReport] = useState<ReportingResponse | null>(
    null,
  );
  const [publisherReport, setPublisherReport] =
    useState<ReportingResponse | null>(null);
  const [moderationReport, setModerationReport] =
    useState<ReportingResponse | null>(null);
  const [workflowReport, setWorkflowReport] =
    useState<ReportingResponse | null>(null);
  const [rightsReport, setRightsReport] = useState<ReportingResponse | null>(
    null,
  );
  const [supplyReport, setSupplyReport] = useState<ReportingResponse | null>(
    null,
  );
  const [ingestionReport, setIngestionReport] =
    useState<ReportingResponse | null>(null);
  const [readingReport, setReadingReport] = useState<ReportingResponse | null>(
    null,
  );
  const [retentionReport, setRetentionReport] =
    useState<ReportingResponse | null>(null);
  const [bookEngagementReport, setBookEngagementReport] =
    useState<ReportingResponse | null>(null);
  const [health, setHealth] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const area = areas.some(([key]) => key === active) ? active : "executive";

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [
        executiveSummary,
        readerDay,
        projectionHealth,
        feedDaily,
        recommendationDaily,
        recommendationQualityDaily,
        bookEngagement,
        pipeline,
        pipelinePhases,
        attribution,
        reconciliation,
        eventQuality,
        eventSchema,
        social,
        publisher,
        moderation,
        workflow,
        rights,
        supply,
        ingestion,
        reading,
        retention,
      ] = await Promise.all([
        services.reportingExecutiveSummary(Math.min(Math.max(days, 1), 366)),
        services.reportingReaderDay(Math.min(Math.max(days, 1), 366)),
        services.reportingHealth(),
        services.reportingFeedDaily(Math.min(Math.max(days, 1), 366)),
        services.reportingRecommendationDaily(Math.min(Math.max(days, 1), 366)),
        services.reportingRecommendationQualityDaily(
          Math.min(Math.max(days, 1), 366),
        ),
        services.reportingBookEngagement(Math.min(Math.max(days, 1), 366)),
        services.reportingPipeline(Math.min(Math.max(days, 1), 366)),
        services.reportingPipelinePhases(Math.min(Math.max(days, 1), 366)),
        services.reportingAttributionQuality(Math.min(Math.max(days, 1), 366)),
        services.reportingReconciliationQuality(
          Math.min(Math.max(days, 1), 366),
        ),
        services.reportingEventQuality(Math.min(Math.max(days, 1), 366)),
        services.reportingEventSchemaQuality(Math.min(Math.max(days, 1), 366)),
        services.reportingSocialDaily(Math.min(Math.max(days, 1), 366)),
        services.reportingPublisherDaily(Math.min(Math.max(days, 1), 366)),
        services.reportingModerationDaily(Math.min(Math.max(days, 1), 366)),
        services.reportingPublisherWorkflowDaily(
          Math.min(Math.max(days, 1), 366),
        ),
        services.reportingRightsDaily(Math.min(Math.max(days, 1), 366)),
        services.reportingContentSupply(Math.min(Math.max(days, 1), 366)),
        services.reportingIngestionDaily(Math.min(Math.max(days, 1), 366)),
        services.reportingReadingDaily(Math.min(Math.max(days, 1), 366)),
        services.reportingRetentionCohort(366),
      ]);
      setReport(readerDay);
      setExecutiveSummaryReport(executiveSummary);
      setHealth(projectionHealth);
      setFeedReport(feedDaily);
      setRecommendationReport(recommendationDaily);
      setRecommendationQualityReport(recommendationQualityDaily);
      setBookEngagementReport(bookEngagement);
      setPipelineReport(pipeline);
      setPipelinePhaseReport(pipelinePhases);
      setAttributionReport(attribution);
      setReconciliationReport(reconciliation);
      setEventQualityReport(eventQuality);
      setEventSchemaReport(eventSchema);
      setSocialReport(social);
      setPublisherReport(publisher);
      setModerationReport(moderation);
      setWorkflowReport(workflow);
      setRightsReport(rights);
      setSupplyReport(supply);
      setIngestionReport(ingestion);
      setReadingReport(reading);
      setRetentionReport(retention);
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Falha ao consultar reporting.",
      );
    } finally {
      setLoading(false);
    }
  }, [days]);

  useEffect(() => {
    void load();
  }, [load]);

  const currentLabel = useMemo(
    () => areas.find(([key]) => key === area)?.[1] ?? "Executive",
    [area],
  );
  const displayedReport =
    area === "feed"
      ? feedReport
      : area === "pipelines"
        ? pipelineReport
        : area === "quality"
          ? attributionReport
          : area === "social"
            ? socialReport
            : report;

  return (
    <section className="dashboard-hub">
      <header className="pagehead">
        <div>
          <span className="eyebrow">OBSERVABILITY · DASHBOARD-RESPONSE-V1</span>
          <h1>Dashboards administrativos</h1>
          <p>
            Saúde operacional e indicadores de negócio com estado explícito de
            fonte, frescor e confiança.
          </p>
        </div>
        <button
          className="button secondary"
          type="button"
          onClick={() => void load()}
          disabled={loading}
        >
          <RefreshCw size={15} /> Atualizar
        </button>
      </header>
      <nav className="dashboard-tabs" aria-label="Áreas dos dashboards">
        {areas.map(([key, label]) => (
          <button
            key={key}
            type="button"
            className={key === area ? "active" : ""}
            onClick={() => setParams({ area: key, days: String(days) })}
          >
            {label}
          </button>
        ))}
      </nav>
      <div className="dashboard-filters">
        <label>
          Período
          <select
            value={days}
            onChange={(event) => setParams({ area, days: event.target.value })}
          >
            <option value="7">7 dias</option>
            <option value="30">30 dias</option>
            <option value="90">90 dias</option>
            <option value="366">366 dias</option>
          </select>
        </label>
        <span>Fonte: reporting · UTC · sem dados mockados</span>
      </div>
      {error && (
        <div className="dashboard-alert error" role="alert">
          <ShieldAlert size={18} />
          {error}
        </div>
      )}
      {loading ? (
        <div className="dashboard-state">Carregando snapshots…</div>
      ) : (
        <>
          <div className="dashboard-heading">
            <div>
              <span className="eyebrow">ÁREA</span>
              <h2>{currentLabel}</h2>
            </div>
            <span
              className={`data-badge ${displayedReport?.status === "UNTRUSTED" ? "warning" : ""}`}
            >
              {statusText(displayedReport?.status ?? "NO_DATA")}
            </span>
          </div>
          {area === "operations" ? (
            <OperationsReport health={health} />
          ) : area === "executive" && executiveSummaryReport ? (
            <div className="dashboard-card wide">
              <div className="dashboard-card-heading">
                <div>
                  <strong>EX-01/04 · Atividade geral</strong>
                  <p>
                    Agregado diário do behavior-service; ausência de população
                    classificada impede interpretação como produto.
                  </p>
                </div>
                <AlertTriangle size={18} />
              </div>
              {report && <ReadingChart data={report} />}
              <ExecutiveSummaryReport data={executiveSummaryReport} />
            </div>
          ) : area === "growth" &&
            report &&
            readingReport &&
            retentionReport ? (
            <div className="dashboard-card wide">
              <div className="dashboard-card-heading">
                <div>
                  <strong>GR-01..07 · Crescimento, leitura e retenção</strong>
                  <p>
                    Atividade, sessões, biblioteca e coortes; retenção só é
                    calculada para janelas maduras.
                  </p>
                </div>
                <AlertTriangle size={18} />
              </div>
              <ReadingChart data={report} />
              <ReadingEngagementReport
                reading={readingReport}
                retention={retentionReport}
              />
              {bookEngagementReport && (
                <BookEngagementReport data={bookEngagementReport} />
              )}
            </div>
          ) : area === "feed" && feedReport ? (
            <div className="dashboard-card wide">
              <div className="dashboard-card-heading">
                <div>
                  <strong>FD-01..FD-05 · Entrega e conversão atribuída</strong>
                  <p>
                    Somente outcomes com vínculo válido ao ledger e status
                    ATTRIBUTED; não é inferência causal.
                  </p>
                </div>
                <AlertTriangle size={18} />
              </div>
              {feedReport.status === "UNTRUSTED" && (
                <div className="dashboard-alert warning">
                  <AlertTriangle size={18} />
                  <span>
                    {statusText(feedReport.status)} · definição{" "}
                    {feedReport.definitionVersion}
                  </span>
                </div>
              )}
              <FeedReport data={feedReport} />
              {recommendationReport && recommendationQualityReport && (
                <RecommendationReport
                  data={recommendationReport}
                  quality={recommendationQualityReport}
                />
              )}
            </div>
          ) : area === "pipelines" &&
            pipelineReport &&
            supplyReport &&
            ingestionReport ? (
            <div className="dashboard-card wide">
              <div className="dashboard-card-heading">
                <div>
                  <strong>PL-03..05 · Jobs, itens e fases de analytics</strong>
                  <p>
                    Snapshots persistidos pelo reporting; throughput considera
                    itens concluídos na última hora de cada coleta.
                  </p>
                </div>
                <AlertTriangle size={18} />
              </div>
              <PipelineReport data={pipelineReport} />
              {pipelinePhaseReport && (
                <PipelinePhaseReport data={pipelinePhaseReport} />
              )}
              <SupplyReport supply={supplyReport} ingestion={ingestionReport} />
            </div>
          ) : area === "quality" &&
            attributionReport &&
            reconciliationReport &&
            eventQualityReport &&
            eventSchemaReport ? (
            <div className="dashboard-card wide">
              <div className="dashboard-card-heading">
                <div>
                  <strong>
                    DQ-03/04 · Qualidade dos eventos e reconciliação
                  </strong>
                  <p>
                    Contagens persistidas para auditoria; duplicidades não são
                    apagadas e qualquer mismatch permanece visível.
                  </p>
                </div>
                <AlertTriangle size={18} />
              </div>
              {(attributionReport.status === "UNTRUSTED" ||
                reconciliationReport.status === "UNTRUSTED") && (
                <div className="dashboard-alert warning">
                  <AlertTriangle size={18} />
                  <span>
                    {statusText(attributionReport.status)} · população e
                    integridade devem ser interpretadas com a definição de
                    cálculo.
                  </span>
                </div>
              )}
              <QualityReport
                attribution={attributionReport}
                reconciliation={reconciliationReport}
                eventQuality={eventQualityReport}
                eventSchema={eventSchemaReport}
              />
            </div>
          ) : area === "social" &&
            socialReport &&
            publisherReport &&
            moderationReport &&
            workflowReport &&
            rightsReport ? (
            <div className="dashboard-card wide">
              <div className="dashboard-card-heading">
                <div>
                  <strong>SO-01/SO-03/SO-05/SO-06 · Social e Publisher</strong>
                  <p>
                    Agregados reais dos bounded contexts; acesso por
                    obra/editora ainda não é exposto neste snapshot global.
                  </p>
                </div>
                <AlertTriangle size={18} />
              </div>
              <SocialReport
                social={socialReport}
                publisher={publisherReport}
                moderation={moderationReport}
                workflow={workflowReport}
                rights={rightsReport}
              />
              {bookEngagementReport && (
                <BookEngagementReport data={bookEngagementReport} />
              )}
            </div>
          ) : (
            <div className="dashboard-card dashboard-gap">
              <div className="dashboard-card-heading">
                <div>
                  <strong>{unavailablePanels[area][0]}</strong>
                  <p>
                    Esta área está conectada ao hub, mas as fontes específicas
                    ainda não foram materializadas.
                  </p>
                </div>
                <AlertTriangle size={18} />
              </div>
              <div className="dashboard-alert warning">
                <AlertTriangle size={18} />
                <span>
                  <strong>NOT_INSTRUMENTED</strong>
                  <br />
                  {unavailablePanels[area][1]}. Não exibimos gráficos vazios nem
                  zeros.
                </span>
              </div>
            </div>
          )}
          <div className="dashboard-card">
            <div className="dashboard-card-heading">
              <div>
                <strong>Freshness e lineage</strong>
                <p>Estado da projeção usada por esta área.</p>
              </div>
            </div>
            {health.length ? (
              <div className="projection-list">
                {health.map((item) => (
                  <div key={String(item.projection_name)}>
                    <span>{String(item.projection_name)}</span>
                    <b>
                      {String(item.refresh_status)} ·{" "}
                      {String(item.definition_version)}
                    </b>
                  </div>
                ))}
              </div>
            ) : (
              <div className="dashboard-state">NO_DATA</div>
            )}
          </div>
        </>
      )}
    </section>
  );
}
