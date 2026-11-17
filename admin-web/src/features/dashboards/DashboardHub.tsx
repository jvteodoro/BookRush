import {
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useState,
} from "react";
import { AlertTriangle, Download, RefreshCw, ShieldAlert } from "lucide-react";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
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
  ReportingExportDataset,
  ReportingSeries,
} from "../../lib/services";
import { useAuth } from "../../auth/useAuth";
import { EmptyReportState } from "./ReportingStates";
import { statusText } from "./reportingStatus";

const RecommendationReport = lazy(() => import("./RecommendationReport"));

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

async function safeReport(
  metricId: string,
  request: Promise<ReportingResponse>,
): Promise<ReportingResponse> {
  try {
    return await request;
  } catch {
    return {
      schemaVersion: "dashboard-response-v1",
      metricId,
      definitionVersion: "unavailable",
      status: "ERROR",
      unit: "aggregate",
      population: "UNKNOWN",
      warnings: [
        "Fonte indisponível; atualize o painel ou consulte operações.",
      ],
      series: [],
    };
  }
}

async function safeExecutiveSummary(
  request: Promise<ExecutiveSummary>,
  days: number,
): Promise<ExecutiveSummary> {
  try {
    return await request;
  } catch {
    return {
      schemaVersion: "dashboard-response-v1",
      metricId: "executive_summary",
      definitionVersion: "unavailable",
      status: "ERROR",
      unit: "period_aggregate",
      population: "UNKNOWN",
      warnings: [
        "Fonte indisponível; atualize o painel ou consulte operações.",
      ],
      series: [],
      current: { label: "current", has_data: false },
      previous: { label: "previous", has_data: false },
      days,
    };
  }
}

async function safeHealth(
  request: Promise<Record<string, unknown>[]>,
): Promise<Record<string, unknown>[]> {
  try {
    return await request;
  } catch {
    return [
      {
        projection_name: "reporting-health",
        refresh_status: "ERROR",
        last_error_code: "health_unavailable",
      },
    ];
  }
}

function ReadingChart({ data }: { data: ReportingResponse }) {
  const platforms = [
    ...new Set(data.series.map((row) => row.platform ?? "UNKNOWN")),
  ];
  if (!data.series.length) return <EmptyReportState data={data} />;
  return (
    <>
      <div className="dashboard-platform-charts">
        {platforms.map((platform) => {
          const chartData = data.series
            .filter((row) => (row.platform ?? "UNKNOWN") === platform)
            .map((row) => ({
              day: row.day,
              activeUsers: row.active_users ?? null,
              weeklyActiveUsers: row.weekly_active_users ?? null,
              monthlyActiveUsers: row.monthly_active_users ?? null,
              sessions: row.sessions ?? null,
              activeMinutes:
                row.active_seconds == null ? null : row.active_seconds / 60,
              meaningfulReads: row.meaningful_reads ?? null,
            }));
          return (
            <section className="dashboard-platform-chart" key={platform}>
              <h3>
                {platform === "UNKNOWN" ? "Plataforma não informada" : platform}
              </h3>
              <div aria-label={`Atividade diária de leitura: ${platform}`}>
                <ResponsiveContainer width="100%" height={250}>
                  <LineChart
                    data={chartData}
                    margin={{ left: 8, right: 16, top: 8 }}
                  >
                    <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                    <XAxis
                      dataKey="day"
                      stroke="#8290a8"
                      tick={{ fontSize: 11 }}
                    />
                    <YAxis stroke="#8290a8" tick={{ fontSize: 11 }} />
                    <YAxis
                      yAxisId="minutes"
                      orientation="right"
                      stroke="#8290a8"
                      tick={{ fontSize: 11 }}
                    />
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
                      dataKey="activeMinutes"
                      name="Minutos ativos"
                      stroke="#ff9f7f"
                      yAxisId="minutes"
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
            </section>
          );
        })}
      </div>
      <table className="dashboard-table">
        <caption>
          Valores reais do snapshot; população {data.population}
        </caption>
        <thead>
          <tr>
            <th>Dia</th>
            <th>Plataforma</th>
            <th>DAU</th>
            <th>WAU</th>
            <th>MAU</th>
            <th>Novos</th>
            <th>Recorrentes</th>
            <th>Sessões</th>
            <th>Minutos ativos</th>
            <th>Leituras ≥60s</th>
          </tr>
        </thead>
        <tbody>
          {data.series.slice(-42).map((row) => (
            <tr key={`${row.day}-${row.platform ?? "UNKNOWN"}`}>
              <td>{row.day}</td>
              <td>{row.platform ?? "UNKNOWN"}</td>
              <td>{row.active_users ?? "—"}</td>
              <td>{row.weekly_active_users ?? "—"}</td>
              <td>{row.monthly_active_users ?? "—"}</td>
              <td>{row.newly_active_users ?? "—"}</td>
              <td>{row.returning_users ?? "—"}</td>
              <td>{row.sessions ?? "—"}</td>
              <td>
                {row.active_seconds == null
                  ? "—"
                  : Math.round(row.active_seconds / 60)}
              </td>
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
  const summaryMetrics = [
    {
      label: "Pico de leitores",
      current: current.daily_peak_users,
      previous: previous.daily_peak_users,
    },
    {
      label: "Sessões",
      current: current.sessions,
      previous: previous.sessions,
    },
    {
      label: "Minutos ativos",
      current:
        current.active_seconds == null
          ? null
          : Math.round(current.active_seconds / 60),
      previous:
        previous.active_seconds == null
          ? null
          : Math.round(previous.active_seconds / 60),
    },
    {
      label: "Leituras significativas",
      current: current.meaningful_reads,
      previous: previous.meaningful_reads,
    },
  ];
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
          <div className="executive-delta-grid">
            {summaryMetrics.map(({ label, current: now, previous: before }) => {
              const absolute =
                typeof now === "number" && typeof before === "number"
                  ? now - before
                  : null;
              const relative =
                absolute == null || before == null || before === 0
                  ? null
                  : (absolute / Math.abs(before)) * 100;
              return (
                <article className="executive-delta-card" key={label}>
                  <span>{label}</span>
                  <strong>{now ?? "—"}</strong>
                  <small>Anterior: {before ?? "—"}</small>
                  <b
                    className={
                      absolute == null
                        ? ""
                        : absolute > 0
                          ? "positive"
                          : absolute < 0
                            ? "negative"
                            : "neutral"
                    }
                    aria-label={
                      absolute == null
                        ? "Variação absoluta indisponível"
                        : `Variação absoluta ${absolute >= 0 ? "mais" : "menos"} ${Math.abs(absolute)}`
                    }
                  >
                    {absolute == null
                      ? "Variação: —"
                      : `Variação: ${absolute > 0 ? "+" : ""}${absolute}`}
                  </b>
                  <small>
                    {relative == null
                      ? before === 0 && typeof now === "number"
                        ? "Percentual indisponível (base anterior 0)"
                        : "Percentual indisponível"
                      : `${relative > 0 ? "+" : ""}${relative.toFixed(1)}%`}
                  </small>
                </article>
              );
            })}
          </div>
        </div>
        <div>
          <strong>EX-02 · Funil atribuído</strong>
          <div
            className="executive-funnel"
            aria-label="Funil de conversão atribuído"
          >
            {stages.map(([label, value]) => {
              const amount = typeof value === "number" ? value : null;
              const served =
                typeof current.served === "number" ? current.served : null;
              const width =
                amount == null || !served || served <= 0
                  ? 0
                  : Math.min(100, Math.max(0, (amount / served) * 100));
              return (
                <div className="executive-funnel-stage" key={String(label)}>
                  <div>
                    <span>{label}</span>
                    <b>{amount ?? "—"}</b>
                  </div>
                  <div
                    className="executive-funnel-track"
                    role="img"
                    aria-label={
                      amount == null || served == null
                        ? `${String(label)}: sem dados suficientes`
                        : `${String(label)}: ${width.toFixed(1)}% de servidos`
                    }
                  >
                    <span style={{ width: `${width}%` }} />
                  </div>
                  <small>
                    {amount == null || !served
                      ? "Taxa indisponível"
                      : `${width.toFixed(1)}% dos servidos`}
                  </small>
                </div>
              );
            })}
          </div>
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

function OperationsReport({
  health,
  releases,
}: {
  health: Record<string, unknown>[];
  releases: ReportingResponse;
}) {
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
        <div className="dashboard-state">NO_DATA · projection health</div>
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
      <div className="dashboard-scroll-table">
        <strong>OP-10 · Builds, deploys e releases</strong>
        {!releases.series.length ? (
          <EmptyReportState data={releases} />
        ) : (
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Data</th>
                <th>Job / build</th>
                <th>Evento</th>
                <th>Status</th>
                <th>Serviço</th>
                <th>Branch / commit</th>
                <th>Imagem / digest</th>
                <th>Duração</th>
              </tr>
            </thead>
            <tbody>
              {releases.series.slice(0, 100).map((row, index) => (
                <tr
                  key={
                    row.event_id ??
                    `${row.job_name}-${row.build_number}-${index}`
                  }
                >
                  <td>{row.occurred_at ?? "—"}</td>
                  <td>
                    {row.build_url?.startsWith("https://") ? (
                      <a href={row.build_url} target="_blank" rel="noreferrer">
                        {row.job_name ?? "Jenkins"} #{row.build_number ?? "—"}
                      </a>
                    ) : (
                      `${row.job_name ?? "Jenkins"} #${row.build_number ?? "—"}`
                    )}
                  </td>
                  <td>{row.event_type ?? "—"}</td>
                  <td>{row.status ?? "—"}</td>
                  <td>{row.service_name ?? "—"}</td>
                  <td>
                    {row.branch ?? "—"} · {row.git_commit?.slice(0, 12) ?? "—"}
                  </td>
                  <td>
                    {row.image_ref ?? "—"} · {row.image_digest ?? "—"}
                  </td>
                  <td>
                    {row.duration_ms == null
                      ? "—"
                      : `${(row.duration_ms / 1000).toFixed(1)}s`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="dashboard-caption">
          Fonte: eventos S2S do Jenkins persistidos pelo admin-service. Ausência
          de eventos é mostrada como NO_DATA; build tags não substituem digests.
        </p>
      </div>
    </div>
  );
}

function ProductHealthReport({
  health,
  feed,
  eventQuality,
}: {
  health: Record<string, unknown>[];
  feed: ReportingResponse;
  eventQuality: ReportingResponse;
}) {
  const requiredProjections = [
    "reader_day",
    "feed_daily",
    "event_quality_daily",
  ];
  const projectionRows = requiredProjections.map((name) =>
    health.find((row) => row.projection_name === name),
  );
  const refreshTimes = projectionRows
    .map((row) => {
      const value = row?.last_successful_refresh;
      return typeof value === "string" ? Date.parse(value) : Number.NaN;
    })
    .filter(Number.isFinite);
  const refreshAgeSeconds = refreshTimes.length
    ? Math.max(0, Math.round((Date.now() - Math.min(...refreshTimes)) / 1000))
    : null;
  const hasHealthError =
    health.some(
      (row) =>
        row.projection_name === "reporting-health" &&
        row.refresh_status === "ERROR",
    ) ||
    projectionRows.some(
      (row) => row != null && row.refresh_status !== "SUCCESS",
    );
  const freshnessStatus = hasHealthError
    ? "ERROR"
    : projectionRows.some((row) => !row)
      ? "NOT_INSTRUMENTED"
      : refreshAgeSeconds == null
        ? "NO_DATA"
        : refreshAgeSeconds > 900
          ? "STALE"
          : "FRESH";
  const feedServed = feed.series.reduce(
    (total, row) =>
      total + (typeof row.served_count === "number" ? row.served_count : 0),
    0,
  );
  const feedViewable = feed.series.reduce(
    (total, row) =>
      total + (typeof row.viewable_count === "number" ? row.viewable_count : 0),
    0,
  );
  const latestEventQuality =
    eventQuality.series[eventQuality.series.length - 1];
  const rejectedEvents = latestEventQuality?.rejected_events;
  const acceptedEvents = latestEventQuality?.accepted_events;
  const grafanaUrl = `${config.observability.grafanaUrl}${config.observability.operationsPath}`;

  return (
    <div className="dashboard-product-health">
      <article className="dashboard-health-tile">
        <span>Snapshot de atribuição do feed</span>
        <strong>{statusText(feed.status)}</strong>
        {feed.series.length ? (
          <small>
            {feedServed} servidos · {feedViewable} viewable na janela
          </small>
        ) : (
          <small>Sem contagens disponíveis para a janela selecionada.</small>
        )}
      </article>
      <article className="dashboard-health-tile">
        <span>Refresh das projeções</span>
        <strong>{freshnessStatus}</strong>
        <small>
          {refreshAgeSeconds == null
            ? "Sem timestamp verificável."
            : `Mais antiga atualizada há ${refreshAgeSeconds}s; limite operacional: 900s.`}
        </small>
        <small>
          Watermark mais recente:{" "}
          {String(
            (() => {
              const watermarks = projectionRows
                .map((row) => row?.source_watermark)
                .filter((value): value is string => typeof value === "string")
                .sort();
              return watermarks[watermarks.length - 1] ?? "—";
            })(),
          )}
        </small>
      </article>
      <article className="dashboard-health-tile">
        <span>Eventos rejeitados · dia mais recente</span>
        <strong>
          {typeof rejectedEvents === "number"
            ? rejectedEvents
            : statusText(eventQuality.status)}
        </strong>
        <small>
          {typeof rejectedEvents === "number" &&
          typeof acceptedEvents === "number"
            ? `${acceptedEvents} aceitos no mesmo dia; motivos disponíveis em Data Quality.`
            : "Sem contagem diária disponível."}
        </small>
      </article>
      <article className="dashboard-health-tile">
        <span>SLO e alertas de runtime</span>
        <strong>GRAFANA</strong>
        <small>
          Consultar disponibilidade e alertas com autenticação operacional.
        </small>
        <a href={grafanaUrl} target="_blank" rel="noreferrer">
          Abrir painel de operações
        </a>
      </article>
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
  const cohortOffsets = [0, 1, 7, 30];
  const retentionRows = new Map(
    retention.series.map((row) => [`${row.cohort_day}:${row.offset_day}`, row]),
  );
  const cohortDays = [
    ...new Set(retention.series.map((row) => String(row.cohort_day))),
  ].slice(-30);
  return (
    <div className="dashboard-quality-grid">
      <div>
        <strong>GR-04/05/07 · Sessões, duração e biblioteca</strong>
        {!reading.series.length ? (
          <EmptyReportState data={reading} />
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
        {reading.series.length > 0 && (
          <figure className="dashboard-chart">
            <figcaption>
              GR-05 · Sessões por duração: &lt;1 min, 1–5 min, 5–15 min e 15+
              min. Sessões são contadas uma vez no dia em que começaram; valores
              acima de 24 h são limitados a 24 h.
            </figcaption>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={reading.series.slice(-30)}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="day" />
                <YAxis allowDecimals={false} />
                <Tooltip />
                <Legend />
                <Bar
                  dataKey="session_under_1m"
                  name="&lt;1 min"
                  stackId="duration"
                  fill="#94a3b8"
                />
                <Bar
                  dataKey="session_1m_5m"
                  name="1–5 min"
                  stackId="duration"
                  fill="#38bdf8"
                />
                <Bar
                  dataKey="session_5m_15m"
                  name="5–15 min"
                  stackId="duration"
                  fill="#6366f1"
                />
                <Bar
                  dataKey="session_15m_plus"
                  name="15+ min"
                  stackId="duration"
                  fill="#f59e0b"
                />
              </BarChart>
            </ResponsiveContainer>
          </figure>
        )}
      </div>
      <div>
        <strong>GR-03 · Retenção por coorte</strong>
        {!retention.series.length ? (
          <EmptyReportState data={retention} />
        ) : (
          <>
            <div className="dashboard-scroll-table">
              <table className="dashboard-retention-heatmap">
                <caption>
                  GR-03 · Retenção percentual; coortes imaturas não recebem
                  taxa. Cada célula inclui retained/n.
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Coorte</th>
                    {cohortOffsets.map((offset) => (
                      <th scope="col" key={offset}>
                        D{offset}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {cohortDays.map((cohortDay) => (
                    <tr key={cohortDay}>
                      <th scope="row">{cohortDay}</th>
                      {cohortOffsets.map((offset) => {
                        const row = retentionRows.get(`${cohortDay}:${offset}`);
                        const mature = row?.mature === true;
                        const cohortSize = row?.cohort_users;
                        const retained = row?.retained_users;
                        const rate =
                          mature &&
                          typeof cohortSize === "number" &&
                          cohortSize > 0 &&
                          typeof retained === "number"
                            ? (retained / cohortSize) * 100
                            : null;
                        const cellLabel =
                          rate == null
                            ? row
                              ? `D${offset}: coorte imatura ou denominador indisponível`
                              : `D${offset}: sem snapshot`
                            : `D${offset}: ${rate.toFixed(1)}%, ${retained} retidos de ${cohortSize}`;
                        return (
                          <td
                            key={offset}
                            aria-label={cellLabel}
                            title={cellLabel}
                            className={
                              rate == null ? "retention-immature" : undefined
                            }
                            style={
                              rate == null
                                ? undefined
                                : {
                                    backgroundColor: `rgba(140, 240, 210, ${0.52 + (rate / 100) * 0.35})`,
                                  }
                            }
                          >
                            {rate == null
                              ? row
                                ? "IMATURA"
                                : "—"
                              : `${rate.toFixed(1)}% (${retained}/${cohortSize})`}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
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
          </>
        )}
      </div>
    </div>
  );
}

function ReadingProgressReport({ data }: { data: ReportingResponse }) {
  const funnelStages = [
    [
      "Iniciaram",
      data.series.reduce(
        (sum, row) => sum + Number(row.reader_book_pairs_started ?? 0),
        0,
      ),
    ],
    [
      "≥10%",
      data.series.reduce(
        (sum, row) => sum + Number(row.reader_book_pairs_10 ?? 0),
        0,
      ),
    ],
    [
      "≥50%",
      data.series.reduce(
        (sum, row) => sum + Number(row.reader_book_pairs_50 ?? 0),
        0,
      ),
    ],
    [
      "100%",
      data.series.reduce(
        (sum, row) => sum + Number(row.reader_book_pairs_100 ?? 0),
        0,
      ),
    ],
  ] as const;
  const maximum = Math.max(0, ...funnelStages.map(([, value]) => value));
  return (
    <div>
      <strong>GR-06 · Funil por coorte de início</strong>
      {!data.series.length ? (
        <EmptyReportState data={data} />
      ) : (
        <>
          <div
            className="executive-funnel"
            role="img"
            aria-label="Pares por etapa da coorte de leitura"
          >
            {funnelStages.map(([label, value]) => (
              <div className="executive-funnel-stage" key={label}>
                <div>
                  <span>{label}</span>
                  <b>{value}</b>
                </div>
                <div
                  className="executive-funnel-track"
                  role="img"
                  aria-label={`${label}: ${value} pares leitor/obra`}
                >
                  <span
                    style={{
                      width: `${maximum > 0 ? (value / maximum) * 100 : 0}%`,
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
          <p className="dashboard-caption">
            Coortes agrupadas pelo primeiro início de sessão ou primeiro
            progresso observado. Etapas contam pares distintos que já chegaram
            ao marco até o refresh; coortes recentes continuam em progresso e os
            valores não são taxas de conversão.
          </p>
          <div className="dashboard-scroll-table">
            <table className="dashboard-table">
              <thead>
                <tr>
                  <th>Coorte de início</th>
                  <th>Iniciaram</th>
                  <th>≥10%</th>
                  <th>≥50%</th>
                  <th>100%</th>
                </tr>
              </thead>
              <tbody>
                {data.series.slice(-30).map((row, index) => (
                  <tr key={`progress-${row.day}-${index}`}>
                    <td>{row.day ?? "—"}</td>
                    <td>{row.reader_book_pairs_started ?? 0}</td>
                    <td>{row.reader_book_pairs_10 ?? 0}</td>
                    <td>{row.reader_book_pairs_50 ?? 0}</td>
                    <td>{row.reader_book_pairs_100 ?? 0}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <strong>GR-07 · Ações de biblioteca observadas</strong>
          <div className="dashboard-scroll-table">
            <table className="dashboard-table">
              <thead>
                <tr>
                  <th>Dia</th>
                  <th>Salvamentos</th>
                  <th>Remoções</th>
                  <th>Leitores que salvaram</th>
                  <th>Leitores que removeram</th>
                </tr>
              </thead>
              <tbody>
                {data.series.slice(-30).map((row, index) => (
                  <tr key={`library-${row.day}-${index}`}>
                    <td>{row.day ?? "—"}</td>
                    <td>{row.save_actions ?? 0}</td>
                    <td>{row.unsave_actions ?? 0}</td>
                    <td>{row.unique_savers ?? 0}</td>
                    <td>{row.unique_unsavers ?? 0}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}

function LibraryStateReport({ data }: { data: ReportingResponse }) {
  const row = data.series[0];
  return (
    <div className="dashboard-scroll-table">
      <strong>GR-07 · Estado atual da biblioteca</strong>
      <p>
        Snapshot corrente de saves. A fonte não conserva histórico de mudanças;
        subject keys não são uma contagem deduplicada entre issuers.
      </p>
      {!row ? (
        <EmptyReportState data={data} />
      ) : (
        <table className="dashboard-table">
          <caption>Atualizado em {String(row.refreshed_at ?? "—")}</caption>
          <thead>
            <tr>
              <th scope="col">Pares salvos</th>
              <th scope="col">Obras distintas</th>
              <th scope="col">Subject keys com saves</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{row.saved_book_pairs ?? "—"}</td>
              <td>{row.saved_books ?? "—"}</td>
              <td>{row.saved_subject_keys ?? "—"}</td>
            </tr>
          </tbody>
        </table>
      )}
    </div>
  );
}

function ReadingHabitsReport({ data }: { data: ReportingResponse }) {
  return (
    <div>
      <strong>GR-08 · Hábitos e streaks de leitura</strong>
      {!data.series.length ? (
        <EmptyReportState data={data} />
      ) : (
        <div className="dashboard-scroll-table">
          <table className="dashboard-table">
            <caption>
              Streak é a sequência consecutiva de dias ativos que termina no dia
              da linha; contagens são mutuamente exclusivas e agregadas.
            </caption>
            <thead>
              <tr>
                <th>Dia</th>
                <th>Leitores ativos</th>
                <th>Minutos</th>
                <th>Livros concluídos</th>
                <th>1 dia</th>
                <th>2–6 dias</th>
                <th>7–29 dias</th>
                <th>30+ dias</th>
              </tr>
            </thead>
            <tbody>
              {data.series.slice(-30).map((row, index) => (
                <tr key={`${row.day}-${index}`}>
                  <td>{row.day ?? "—"}</td>
                  <td>{row.active_readers ?? "—"}</td>
                  <td>{row.reading_minutes ?? "—"}</td>
                  <td>{row.completed_books ?? "—"}</td>
                  <td>{row.streak_1_day_users ?? "—"}</td>
                  <td>{row.streak_2_6_day_users ?? "—"}</td>
                  <td>{row.streak_7_29_day_users ?? "—"}</td>
                  <td>{row.streak_30_plus_day_users ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function FeedReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length) return <EmptyReportState data={data} />;
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
            <th>Viewable elegíveis (7d)</th>
            <th>Aberturas</th>
            <th>CTR viewable (7d)</th>
            <th>Read 10%</th>
            <th>Read 50%</th>
            <th>Conclusões</th>
            <th>Visitas com dwell</th>
            <th>Dwell (s)</th>
            <th>Skips explícitos</th>
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
              <td>{row.eligible_viewable_count ?? "—"}</td>
              <td>{row.attributed_open_count ?? "—"}</td>
              <td>
                {row.eligible_viewable_count
                  ? `${(((row.mature_attributed_open_count ?? 0) / row.eligible_viewable_count) * 100).toFixed(1)}%`
                  : "—"}
              </td>
              <td>{row.attributed_read_10_count ?? "—"}</td>
              <td>{row.attributed_read_50_count ?? "—"}</td>
              <td>{row.attributed_complete_count ?? "—"}</td>
              <td>{row.dwell_observations ?? "—"}</td>
              <td>{row.dwell_seconds ?? "—"}</td>
              <td>{row.skipped_visits ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ExcerptPerformanceReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length) return <EmptyReportState data={data} />;
  return (
    <div className="dashboard-scroll-table">
      <table className="dashboard-table">
        <caption>
          FD-13 · desempenho agregado por excerpt; mínimo de 20 impressões, sem
          texto do trecho e sem inferência causal
        </caption>
        <thead>
          <tr>
            <th>Dia</th>
            <th>Livro</th>
            <th>Idioma</th>
            <th>Modelo</th>
            <th>Elegível</th>
            <th>Impressões</th>
            <th>Viewable</th>
            <th>Viewable elegíveis (7d)</th>
            <th>Aberturas atribuídas</th>
            <th>CTR viewable (7d)</th>
            <th>Leituras</th>
            <th>Conclusões</th>
            <th>Usuários</th>
          </tr>
        </thead>
        <tbody>
          {data.series.slice(-100).map((row, index) => {
            const impressions = row.impressions ?? 0;
            const viewable = row.viewable_count ?? 0;
            const eligibleViewable = row.eligible_viewable_count ?? 0;
            const matureOpened = row.mature_attributed_open_count ?? 0;
            return (
              <tr
                key={`${row.day}-${row.excerpt_id}-${row.model_version}-${index}`}
              >
                <td>{row.day ?? "—"}</td>
                <td>{row.book_title ?? row.book_id ?? "—"}</td>
                <td>{row.language ?? "—"}</td>
                <td>{row.model_version ?? "—"}</td>
                <td>
                  {row.eligible == null ? "—" : row.eligible ? "SIM" : "NÃO"}
                </td>
                <td>{impressions || "—"}</td>
                <td>{viewable || "—"}</td>
                <td>{row.eligible_viewable_count ?? "—"}</td>
                <td>{row.attributed_open_count ?? "—"}</td>
                <td>
                  {eligibleViewable
                    ? `${((matureOpened / eligibleViewable) * 100).toFixed(1)}%`
                    : "—"}
                </td>
                <td>{row.attributed_read_count ?? "—"}</td>
                <td>{row.attributed_complete_count ?? "—"}</td>
                <td>{row.unique_subjects ?? "—"}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function ExperimentReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length) return <EmptyReportState data={data} />;
  return (
    <div className="dashboard-scroll-table">
      <table className="dashboard-table">
        <caption>
          FD-10 · assignments persistidos e guardrails observacionais; sem
          declaração de efeito causal
        </caption>
        <thead>
          <tr>
            <th>Dia</th>
            <th>Experimento</th>
            <th>Variante</th>
            <th>Assignment n</th>
            <th>Requests</th>
            <th>Impressões</th>
            <th>Viewable</th>
            <th>Viewable elegíveis (7d)</th>
            <th>Aberturas atribuídas</th>
            <th>CTR viewable (7d)</th>
            <th>Leituras</th>
            <th>Conclusões</th>
          </tr>
        </thead>
        <tbody>
          {data.series.slice(-60).map((row, index) => {
            const viewable = row.viewable_count ?? 0;
            const eligibleViewable = row.eligible_viewable_count ?? 0;
            const matureOpened = row.mature_attributed_open_count ?? 0;
            return (
              <tr
                key={`${row.day}-${row.experiment_id}-${row.variant}-${index}`}
              >
                <td>{row.day ?? "—"}</td>
                <td>{row.experiment_id ?? "—"}</td>
                <td>{row.variant ?? "—"}</td>
                <td>{row.assigned_subjects ?? "—"}</td>
                <td>{row.request_count ?? "—"}</td>
                <td>{row.impression_count ?? "—"}</td>
                <td>{viewable || "—"}</td>
                <td>{row.eligible_viewable_count ?? "—"}</td>
                <td>{row.attributed_open_count ?? "—"}</td>
                <td>
                  {eligibleViewable
                    ? `${((matureOpened / eligibleViewable) * 100).toFixed(1)}%`
                    : "—"}
                </td>
                <td>{row.attributed_read_count ?? "—"}</td>
                <td>{row.attributed_complete_count ?? "—"}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function PipelineReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length) return <EmptyReportState data={data} />;
  const itemRows = data.series.filter((row) => row.phase === "ITEM");
  const itemStatuses = [
    ...new Set(itemRows.map((row) => String(row.job_status))),
  ].map((status, index) => ({ status, chartKey: `status_${index}` }));
  const itemSnapshots = new Map<string, Map<string, ReportingSeries>>();
  itemRows.forEach((row) => {
    const timestamp = String(row.collected_at);
    const snapshot = itemSnapshots.get(timestamp) ?? new Map();
    snapshot.set(String(row.job_status), row);
    itemSnapshots.set(timestamp, snapshot);
  });
  const itemChartData = [...itemSnapshots.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([timestamp, snapshot]) => {
      const point: Record<string, string | number | null> = {
        collected_at: new Date(timestamp).toLocaleTimeString(),
      };
      let throughput = 0;
      itemStatuses.forEach(({ status, chartKey }) => {
        const row = snapshot.get(status);
        point[chartKey] =
          typeof row?.status_count === "number" ? row.status_count : 0;
        if (typeof row?.throughput === "number") throughput += row.throughput;
      });
      point.throughput = throughput;
      return point;
    });
  const statusColors = ["#f6c76d", "#8cf0d2", "#ff9f7f", "#bb9cff", "#7fa8ff"];
  return (
    <>
      {itemChartData.length > 0 && (
        <>
          <div
            className="dashboard-chart"
            aria-label="Itens de analytics por estado ao longo das coletas"
          >
            <ResponsiveContainer width="100%" height={280}>
              <AreaChart data={itemChartData}>
                <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                <XAxis
                  dataKey="collected_at"
                  stroke="#8290a8"
                  tick={{ fontSize: 10 }}
                  interval="preserveStartEnd"
                />
                <YAxis stroke="#8290a8" tick={{ fontSize: 11 }} />
                <Tooltip
                  contentStyle={{
                    background: "#111828",
                    border: "1px solid #2a3a55",
                  }}
                />
                <Legend />
                {itemStatuses.map(({ status, chartKey }, index) => (
                  <Area
                    key={status}
                    type="monotone"
                    dataKey={chartKey}
                    name={status}
                    stackId="items"
                    stroke={statusColors[index % statusColors.length]}
                    fill={statusColors[index % statusColors.length]}
                  />
                ))}
              </AreaChart>
            </ResponsiveContainer>
          </div>
          <div
            className="dashboard-chart"
            aria-label="Throughput de itens completados por hora"
          >
            <ResponsiveContainer width="100%" height={220}>
              <LineChart data={itemChartData}>
                <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                <XAxis
                  dataKey="collected_at"
                  stroke="#8290a8"
                  tick={{ fontSize: 10 }}
                  interval="preserveStartEnd"
                />
                <YAxis stroke="#8290a8" tick={{ fontSize: 11 }} />
                <Tooltip
                  contentStyle={{
                    background: "#111828",
                    border: "1px solid #2a3a55",
                  }}
                />
                <Line
                  type="monotone"
                  dataKey="throughput"
                  name="Itens concluídos/h"
                  stroke="#8cf0d2"
                  dot={false}
                  connectNulls={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </>
      )}
      <div className="dashboard-scroll-table">
        <table className="dashboard-table">
          <caption>
            Snapshots reais de jobs e fases; estado de população{" "}
            {data.population}
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
    </>
  );
}

function IngestionQueueReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length) return <EmptyReportState data={data} />;
  const statuses = [
    ...new Set(data.series.map((row) => String(row.queue_status))),
  ];
  const snapshots = new Map<string, Map<string, ReportingSeries>>();
  data.series.forEach((row) => {
    const timestamp = String(row.collected_at);
    const snapshot =
      snapshots.get(timestamp) ?? new Map<string, ReportingSeries>();
    snapshot.set(String(row.queue_status), row);
    snapshots.set(timestamp, snapshot);
  });
  const statusKeys = statuses.map((status, index) => ({
    status,
    chartKey: `queue_${index}`,
  }));
  const chartData = [...snapshots.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([timestamp, snapshot]) => {
      const point: Record<string, string | number | null> = {
        collected_at: new Date(timestamp).toLocaleString(),
      };
      let oldestAge: number | null = null;
      statusKeys.forEach(({ status, chartKey }) => {
        const row = snapshot.get(status);
        point[chartKey] = row?.task_count ?? 0;
        if (
          (status === "PENDING" || status === "RETRY_WAIT") &&
          typeof row?.oldest_task_age_seconds === "number"
        ) {
          oldestAge = Math.max(oldestAge ?? 0, row.oldest_task_age_seconds);
        }
      });
      point.oldest_age_seconds = oldestAge;
      return point;
    });
  const colors: Record<string, string> = {
    PENDING: "#f6c76d",
    RETRY_WAIT: "#ff9f7f",
    RUNNING: "#8cf0d2",
    FAILED: "#ff6f7d",
    DONE: "#7fa8ff",
    CANCELLED: "#8290a8",
    OTHER: "#bb9cff",
  };
  const orderedSnapshots = [...snapshots.entries()].sort(([left], [right]) =>
    left.localeCompare(right),
  );
  const latest = orderedSnapshots[orderedSnapshots.length - 1]?.[1];
  const latestExpiredLeases = latest?.get("RUNNING")?.expired_lease_count;
  return (
    <section
      className="dashboard-product-health-section"
      aria-label="Fila de ingestão PL-02"
    >
      <div className="dashboard-card-heading">
        <div>
          <strong>PL-02 · Fila de ingestão</strong>
          <p>
            Estados de tarefas duráveis; idade pendente medida desde a criação.
            Última coleta: {latestExpiredLeases ?? 0} leases expirados.
          </p>
        </div>
      </div>
      <div className="dashboard-platform-charts">
        <div
          className="dashboard-platform-chart"
          aria-label="Tarefas de ingestão por estado ao longo das coletas"
        >
          <h3>Contagem por estado</h3>
          <ResponsiveContainer width="100%" height={280}>
            <AreaChart data={chartData}>
              <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
              <XAxis
                dataKey="collected_at"
                stroke="#8290a8"
                tick={{ fontSize: 10 }}
                interval="preserveStartEnd"
              />
              <YAxis stroke="#8290a8" allowDecimals={false} />
              <Tooltip
                contentStyle={{
                  background: "#111828",
                  border: "1px solid #2a3a55",
                }}
              />
              <Legend />
              {statusKeys.map(({ status, chartKey }) => (
                <Area
                  key={status}
                  type="monotone"
                  dataKey={chartKey}
                  name={status}
                  stackId="queue"
                  stroke={colors[status] ?? colors.OTHER}
                  fill={colors[status] ?? colors.OTHER}
                />
              ))}
            </AreaChart>
          </ResponsiveContainer>
        </div>
        <div
          className="dashboard-platform-chart"
          aria-label="Idade máxima das tarefas pendentes e retry wait"
        >
          <h3>Idade mais antiga na fila</h3>
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={chartData}>
              <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
              <XAxis
                dataKey="collected_at"
                stroke="#8290a8"
                tick={{ fontSize: 10 }}
                interval="preserveStartEnd"
              />
              <YAxis stroke="#8290a8" />
              <Tooltip
                contentStyle={{
                  background: "#111828",
                  border: "1px solid #2a3a55",
                }}
              />
              <Line
                type="monotone"
                dataKey="oldest_age_seconds"
                name="Idade (s)"
                stroke="#ff9f7f"
                dot={false}
                connectNulls={false}
              />
            </LineChart>
          </ResponsiveContainer>
          <p className="dashboard-caption">
            Lease expirado: RUNNING com lease_until anterior à coleta.
          </p>
        </div>
      </div>
      <div className="dashboard-scroll-table">
        <table className="dashboard-table">
          <caption>
            Snapshots da fila; ausência de status numa coleta representa
            contagem zero daquele estado.
          </caption>
          <thead>
            <tr>
              <th>Coletado</th>
              <th>Estado</th>
              <th>Tarefas</th>
              <th>Idade mais antiga (s)</th>
              <th>Leases expirados</th>
            </tr>
          </thead>
          <tbody>
            {data.series.slice(-50).map((row, index) => (
              <tr key={`${row.collected_at}-${row.queue_status}-${index}`}>
                <td>
                  {row.collected_at
                    ? new Date(row.collected_at).toLocaleString()
                    : "—"}
                </td>
                <td>{row.queue_status ?? "—"}</td>
                <td>{row.task_count ?? "—"}</td>
                <td>
                  {row.oldest_task_age_seconds == null
                    ? "—"
                    : row.oldest_task_age_seconds}
                </td>
                <td>{row.expired_lease_count ?? 0}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function PipelinePhaseReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length) return <EmptyReportState data={data} />;
  const failuresByCode = new Map<string, number>();
  const stuckByPhase = new Map<string, number>();
  data.series.forEach((row) => {
    if (row.phase_status === "FAILED" && row.error_code !== "NONE") {
      const code = String(row.error_code ?? "UNKNOWN");
      failuresByCode.set(
        code,
        (failuresByCode.get(code) ?? 0) + (row.failed_count ?? 0),
      );
    }
    if (row.phase_status === "RUNNING" && (row.stuck_count ?? 0) > 0) {
      const phase = String(row.phase ?? "UNKNOWN");
      stuckByPhase.set(
        phase,
        (stuckByPhase.get(phase) ?? 0) + (row.stuck_count ?? 0),
      );
    }
  });
  const errorChartData = [...failuresByCode.entries()]
    .map(([error_code, failed_count]) => ({ error_code, failed_count }))
    .sort((left, right) => right.failed_count - left.failed_count);
  const stuckChartData = [...stuckByPhase.entries()]
    .map(([phase, stuck_count]) => ({ phase, stuck_count }))
    .sort((left, right) => right.stuck_count - left.stuck_count);
  const durationRows = data.series.filter(
    (row) =>
      row.phase_status === "COMPLETED" &&
      row.error_code === "NONE" &&
      typeof row.p95_duration_ms === "number",
  );
  const phases = [...new Set(durationRows.map((row) => String(row.phase)))].map(
    (phase, index) => ({ phase, chartKey: `phase_${index}` }),
  );
  const durationByDay = new Map(
    durationRows.map((row) => [`${row.day}:${row.phase}`, row]),
  );
  const durationDays = [
    ...new Set(durationRows.map((row) => String(row.day))),
  ].sort();
  const durationValues = durationRows
    .map((row) => row.p95_duration_ms)
    .filter((value): value is number => typeof value === "number");
  const minDuration = Math.min(...durationValues);
  const maxDuration = Math.max(...durationValues);
  const durationChartData = durationDays.map((day) => {
    const point: Record<string, string | number | null> = { day };
    phases.forEach(({ phase, chartKey }) => {
      const row = durationByDay.get(`${day}:${phase}`);
      point[chartKey] =
        typeof row?.p95_duration_ms === "number" ? row.p95_duration_ms : null;
    });
    return point;
  });
  const phaseColors = ["#8cf0d2", "#bb9cff", "#ff9f7f", "#7fa8ff", "#f6c76d"];
  return (
    <>
      {(errorChartData.length > 0 || stuckChartData.length > 0) && (
        <div className="dashboard-platform-charts">
          {errorChartData.length > 0 && (
            <div
              className="dashboard-platform-chart"
              aria-label="Tentativas de analytics com falha por código de erro"
            >
              <h3>PL-05 · Falhas por código</h3>
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={errorChartData}>
                  <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                  <XAxis
                    dataKey="error_code"
                    stroke="#8290a8"
                    tick={{ fontSize: 10 }}
                    interval="preserveStartEnd"
                  />
                  <YAxis stroke="#8290a8" allowDecimals={false} />
                  <Tooltip
                    contentStyle={{
                      background: "#111828",
                      border: "1px solid #2a3a55",
                    }}
                  />
                  <Bar
                    dataKey="failed_count"
                    name="Tentativas falhas"
                    fill="#ff9f7f"
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
          {stuckChartData.length > 0 && (
            <div
              className="dashboard-platform-chart"
              aria-label="Leases de analytics parados por fase"
            >
              <h3>PL-05 · Leases parados por fase</h3>
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={stuckChartData}>
                  <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                  <XAxis
                    dataKey="phase"
                    stroke="#8290a8"
                    tick={{ fontSize: 10 }}
                  />
                  <YAxis stroke="#8290a8" allowDecimals={false} />
                  <Tooltip
                    contentStyle={{
                      background: "#111828",
                      border: "1px solid #2a3a55",
                    }}
                  />
                  <Bar
                    dataKey="stuck_count"
                    name="Leases > 15 min"
                    fill="#f6c76d"
                  />
                </BarChart>
              </ResponsiveContainer>
              <p className="dashboard-caption">
                Lease RUNNING com mais de 15 minutos; agregado no período
                selecionado.
              </p>
            </div>
          )}
        </div>
      )}
      {durationChartData.length > 0 && (
        <>
          <div className="dashboard-scroll-table">
            <table className="dashboard-retention-heatmap">
              <caption>
                PL-04 · P95 (ms) por dia/fase; apenas tentativas COMPLETED sem
                erro.
              </caption>
              <thead>
                <tr>
                  <th scope="col">Dia</th>
                  {phases.map(({ phase }) => (
                    <th scope="col" key={phase}>
                      {phase}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {durationDays.map((day) => (
                  <tr key={day}>
                    <th scope="row">{day}</th>
                    {phases.map(({ phase }) => {
                      const value = durationByDay.get(
                        `${day}:${phase}`,
                      )?.p95_duration_ms;
                      const normalized =
                        typeof value !== "number" || maxDuration === minDuration
                          ? 0.5
                          : (value - minDuration) / (maxDuration - minDuration);
                      const label =
                        typeof value === "number"
                          ? `${day}, ${phase}: P95 ${Math.round(value)} ms`
                          : `${day}, ${phase}: sem amostra concluída`;
                      return (
                        <td
                          key={phase}
                          aria-label={label}
                          title={label}
                          className={
                            typeof value === "number"
                              ? undefined
                              : "retention-immature"
                          }
                          style={
                            typeof value === "number"
                              ? {
                                  backgroundColor: `rgba(255, 159, 127, ${0.52 + normalized * 0.35})`,
                                }
                              : undefined
                          }
                        >
                          {typeof value === "number" ? Math.round(value) : "—"}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div
            className="dashboard-chart"
            aria-label="P95 de duração por fase para tentativas concluídas"
          >
            <ResponsiveContainer width="100%" height={280}>
              <LineChart data={durationChartData}>
                <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                <XAxis dataKey="day" stroke="#8290a8" tick={{ fontSize: 11 }} />
                <YAxis stroke="#8290a8" tick={{ fontSize: 11 }} />
                <Tooltip
                  contentStyle={{
                    background: "#111828",
                    border: "1px solid #2a3a55",
                  }}
                />
                <Legend />
                {phases.map(({ phase, chartKey }, index) => (
                  <Line
                    key={phase}
                    type="monotone"
                    dataKey={chartKey}
                    name={`${phase} P95 (ms)`}
                    stroke={phaseColors[index % phaseColors.length]}
                    dot={false}
                    connectNulls={false}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
            <p className="dashboard-caption">
              Somente tentativas COMPLETED sem error_code; percentis de falhas e
              retries permanecem na tabela.
            </p>
          </div>
        </>
      )}
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
    </>
  );
}

function EditorialQualityReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length) return <EmptyReportState data={data} />;
  return (
    <div className="dashboard-scroll-table">
      <table className="dashboard-table">
        <caption>
          PL-09 · fila editorial, qualidade de anotação e tempos observados; sem
          texto ou identidade de annotator
        </caption>
        <thead>
          <tr>
            <th>Dia</th>
            <th>Campanhas</th>
            <th>Amostrados</th>
            <th>Pendentes</th>
            <th>Em progresso</th>
            <th>Completos</th>
            <th>Submissões</th>
            <th>Annotators</th>
            <th>Discordâncias</th>
            <th>Adjudicações</th>
            <th>P50 ms</th>
            <th>P95 ms</th>
          </tr>
        </thead>
        <tbody>
          {data.series.slice(-60).map((row, index) => (
            <tr key={`${row.day}-${index}`}>
              <td>{row.day ?? "—"}</td>
              <td>{row.campaigns ?? "—"}</td>
              <td>{row.sampled_items ?? "—"}</td>
              <td>{row.pending_items ?? "—"}</td>
              <td>{row.in_progress_items ?? "—"}</td>
              <td>{row.completed_items ?? "—"}</td>
              <td>{row.submitted_annotations ?? "—"}</td>
              <td>{row.unique_annotators ?? "—"}</td>
              <td>{row.disputed_items ?? "—"}</td>
              <td>{row.adjudications ?? "—"}</td>
              <td>
                {row.p50_elapsed_ms == null
                  ? "—"
                  : Math.round(row.p50_elapsed_ms)}
              </td>
              <td>
                {row.p95_elapsed_ms == null
                  ? "—"
                  : Math.round(row.p95_elapsed_ms)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function IngestionDailyReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length) return <EmptyReportState data={data} />;
  const statuses = [
    ...new Set(
      data.series
        .map((row) => String(row.item_status ?? "NO_ITEMS"))
        .filter((status) => status !== "NO_ITEMS"),
    ),
  ];
  const totals = new Map<string, Map<string, number>>();
  data.series.forEach((row) => {
    if (!row.day || !row.item_status) return;
    const dayTotals = totals.get(row.day) ?? new Map<string, number>();
    dayTotals.set(
      row.item_status,
      (dayTotals.get(row.item_status) ?? 0) + (row.item_count ?? 0),
    );
    totals.set(row.day, dayTotals);
  });
  const chartData = [...totals.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([day, dayTotals]) => {
      const point: Record<string, string | number> = { day };
      statuses.forEach((status) => {
        point[status] = dayTotals.get(status) ?? 0;
      });
      return point;
    });
  const colors: Record<string, string> = {
    PENDING: "#f6c76d",
    RUNNING: "#8cf0d2",
    SUCCEEDED: "#7fa8ff",
    FAILED: "#ff6f7d",
    CANCELLED: "#8290a8",
    SKIPPED: "#bb9cff",
  };
  return (
    <section aria-label="PL-01 ingestão diária por estado">
      <h3>PL-01 · Itens de ingestão</h3>
      <p className="dashboard-caption">
        Jobs agrupados pelo dia de criação; séries contam itens pelo estado
        atual na projeção. {statusText(data.status)} · população{" "}
        {data.population}
        {data.freshnessSeconds == null
          ? ""
          : ` · atualizado há ${Math.round(data.freshnessSeconds)} s`}
      </p>
      <div
        className="dashboard-chart"
        aria-label="Itens de ingestão por estado atual e dia de criação do job"
      >
        <ResponsiveContainer width="100%" height={260}>
          <AreaChart data={chartData}>
            <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
            <XAxis
              dataKey="day"
              stroke="#8290a8"
              tick={{ fontSize: 10 }}
              interval="preserveStartEnd"
            />
            <YAxis stroke="#8290a8" allowDecimals={false} />
            <Tooltip
              contentStyle={{
                background: "#111828",
                border: "1px solid #2a3a55",
              }}
            />
            <Legend />
            {statuses.map((status) => (
              <Area
                key={status}
                type="monotone"
                dataKey={status}
                name={status}
                stackId="ingestion"
                stroke={colors[status] ?? "#bb9cff"}
                fill={colors[status] ?? "#bb9cff"}
              />
            ))}
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <div className="dashboard-scroll-table">
        <table className="dashboard-table">
          <caption>
            Itens de ingestão por dia do job, estado atual do job e estado atual
            do item; retries são medidos separadamente como tentativas.
          </caption>
          <thead>
            <tr>
              <th>Dia do job</th>
              <th>Estado do job</th>
              <th>Estado do item</th>
              <th>Itens</th>
            </tr>
          </thead>
          <tbody>
            {data.series.slice(-60).map((row, index) => (
              <tr
                key={`${row.day}-${row.job_status}-${row.item_status}-${index}`}
              >
                <td>{row.day ?? "—"}</td>
                <td>{row.job_status ?? "—"}</td>
                <td>{row.item_status ?? "NO_ITEMS"}</td>
                <td>{row.item_count ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function SupplyReport({
  supply,
  coverage,
  rankScores,
  excerptProduction,
  importQuality,
  backlog,
  ingestion,
}: {
  supply: ReportingResponse;
  coverage?: ReportingResponse;
  rankScores?: ReportingResponse;
  excerptProduction?: ReportingResponse;
  importQuality?: ReportingResponse;
  backlog?: ReportingResponse;
  ingestion?: ReportingResponse;
}) {
  const latest = supply.series[supply.series.length - 1];
  const readinessRate = (count: unknown, denominator: unknown) =>
    typeof count === "number" &&
    typeof denominator === "number" &&
    denominator > 0
      ? `${((count / denominator) * 100).toFixed(1)}%`
      : "—";
  const snapshots = supply.series.slice(-30);
  return (
    <div className="dashboard-quality-grid">
      <div>
        <strong>EX-05 · Conteúdo disponível</strong>
        {!supply.series.length ? (
          <EmptyReportState data={supply} />
        ) : (
          <>
            <div className="dashboard-supply-kpis">
              <div>
                <span>Ativos</span>
                <b>{latest?.active_books ?? "—"}</b>
              </div>
              <div>
                <span>Reader-ready</span>
                <b>{latest?.reader_ready_books ?? "—"}</b>
                <small>
                  {readinessRate(
                    latest?.reader_ready_books,
                    latest?.active_books,
                  )}{" "}
                  dos ativos
                </small>
              </div>
              <div>
                <span>Excerpt-ready</span>
                <b>{latest?.excerpt_ready_books ?? "—"}</b>
                <small>
                  {readinessRate(
                    latest?.excerpt_ready_books,
                    latest?.reader_ready_books,
                  )}{" "}
                  dos reader-ready
                </small>
              </div>
            </div>
            <div
              className="dashboard-chart"
              aria-label="Livros ativos, reader-ready e excerpt-ready ao longo das coletas"
            >
              <ResponsiveContainer width="100%" height={260}>
                <BarChart
                  data={snapshots.map((row) => ({
                    ...row,
                    collected_at: row.collected_at
                      ? new Date(row.collected_at).toLocaleTimeString()
                      : "—",
                  }))}
                >
                  <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                  <XAxis
                    dataKey="collected_at"
                    stroke="#8290a8"
                    tick={{ fontSize: 10 }}
                    interval="preserveStartEnd"
                  />
                  <YAxis stroke="#8290a8" tick={{ fontSize: 11 }} />
                  <Tooltip
                    contentStyle={{
                      background: "#111828",
                      border: "1px solid #2a3a55",
                    }}
                  />
                  <Legend />
                  <Bar
                    dataKey="active_books"
                    name="Livros ativos"
                    fill="#7fa8ff"
                  />
                  <Bar
                    dataKey="reader_ready_books"
                    name="Reader-ready"
                    fill="#8cf0d2"
                  />
                  <Bar
                    dataKey="excerpt_ready_books"
                    name="Excerpt-ready"
                    fill="#bb9cff"
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <p className="dashboard-caption">
              Contagens são etapas aninhadas, não segmentos aditivos. Snapshot
              antigo sem a definição de readiness aparece como —.
            </p>
            <div className="dashboard-scroll-table">
              <table className="dashboard-table">
                <thead>
                  <tr>
                    <th>Coletado</th>
                    <th>Ativos</th>
                    <th>Reader-ready</th>
                    <th>Excerpt-ready</th>
                    <th>Assets disponíveis</th>
                    <th>Assets analisados</th>
                    <th>Excerpts elegíveis</th>
                    <th>Excerpts rankeados</th>
                  </tr>
                </thead>
                <tbody>
                  {snapshots.map((row, index) => (
                    <tr key={`${row.collected_at}-${index}`}>
                      <td>
                        {row.collected_at
                          ? new Date(row.collected_at).toLocaleString()
                          : "—"}
                      </td>
                      <td>{row.active_books ?? "—"}</td>
                      <td>{row.reader_ready_books ?? "—"}</td>
                      <td>{row.excerpt_ready_books ?? "—"}</td>
                      <td>{row.available_asset_versions ?? "—"}</td>
                      <td>{row.analyzed_asset_versions ?? "—"}</td>
                      <td>{row.eligible_excerpts ?? "—"}</td>
                      <td>{row.ranked_excerpts ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
      {coverage && <ContentCoverageReport data={coverage} />}
      {rankScores && <ContentRankScoresReport data={rankScores} />}
      {excerptProduction && (
        <ExcerptProductionReport data={excerptProduction} />
      )}
      {importQuality && <ImportQualityReport data={importQuality} />}
      {backlog && <BacklogReport data={backlog} />}
      {ingestion && (
        <div>
          <IngestionDailyReport data={ingestion} />
        </div>
      )}
    </div>
  );
}

function ContentCoverageReport({ data }: { data: ReportingResponse }) {
  const latest = new Map<string, ReportingSeries>();
  data.series.forEach((row) =>
    latest.set(`${row.language}-${row.rights_status}`, row),
  );
  const rows = [...latest.values()];
  return (
    <section>
      <strong>PL-08 · Cobertura do corpus por idioma e direitos</strong>
      {!rows.length ? (
        <EmptyReportState data={data} />
      ) : (
        <div className="dashboard-scroll-table">
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Idioma</th>
                <th>Direitos</th>
                <th>Livros</th>
                <th>Com asset</th>
                <th>Disponível</th>
                <th>Reader-ready</th>
                <th>Analisado</th>
                <th>Excerpt-ready</th>
                <th>Ranked</th>
                <th>Excerpts elegíveis</th>
                <th>Ranked</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr key={`${row.language}-${row.rights_status}-${index}`}>
                  <td>{row.language}</td>
                  <td>{row.rights_status}</td>
                  <td>{row.active_books}</td>
                  <td>{row.books_with_active_assets}</td>
                  <td>{row.available_asset_books}</td>
                  <td>{row.reader_ready_books}</td>
                  <td>{row.analyzed_books}</td>
                  <td>{row.eligible_excerpt_books}</td>
                  <td>{row.ranked_books}</td>
                  <td>{row.eligible_excerpts}</td>
                  <td>{row.ranked_excerpts}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="dashboard-caption">
            Contagens por etapa; use a etapa anterior como denominador. Dados
            agregados sem IDs de obras. População ainda não classificada.
          </p>
        </div>
      )}
    </section>
  );
}

function ContentRankScoresReport({ data }: { data: ReportingResponse }) {
  const latest = new Map<string, ReportingSeries>();
  data.series.forEach((row) => {
    const key = [
      row.language,
      row.rights_status,
      row.ranker_code,
      row.ranker_version,
      row.configuration_hash,
      row.score_decile,
    ].join("|");
    latest.set(key, row);
  });
  const rows = [...latest.values()];
  return (
    <section>
      <strong>PL-08 · Distribuição de scores por ranker</strong>
      {!rows.length ? (
        <EmptyReportState data={data} />
      ) : (
        <>
          <div
            className="dashboard-chart"
            aria-label="Distribuição de excerpts por decis de score, separada por ranker e configuração"
          >
            <ResponsiveContainer width="100%" height={260}>
              <BarChart
                data={rows.map((row) => ({
                  ...row,
                  bucket: `${row.language}/${row.rights_status}/${row.ranker_code}@${row.ranker_version} · D${(row.score_decile ?? 0) + 1}`,
                }))}
              >
                <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                <XAxis
                  dataKey="bucket"
                  stroke="#8290a8"
                  tick={{ fontSize: 9 }}
                  interval="preserveStartEnd"
                />
                <YAxis stroke="#8290a8" tick={{ fontSize: 11 }} />
                <Tooltip
                  contentStyle={{
                    background: "#111828",
                    border: "1px solid #2a3a55",
                  }}
                />
                <Bar dataKey="excerpt_count" name="Excerpts" fill="#bb9cff" />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <div className="dashboard-scroll-table">
            <table className="dashboard-table">
              <thead>
                <tr>
                  <th>Idioma</th>
                  <th>Direitos</th>
                  <th>Ranker</th>
                  <th>Versão</th>
                  <th>Configuração</th>
                  <th>Decil</th>
                  <th>Score min</th>
                  <th>Score max</th>
                  <th>Excerpts</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row, index) => (
                  <tr
                    key={`${row.configuration_hash}-${row.score_decile}-${index}`}
                  >
                    <td>{row.language}</td>
                    <td>{row.rights_status}</td>
                    <td>{row.ranker_code}</td>
                    <td>{row.ranker_version}</td>
                    <td>{row.configuration_hash?.slice(0, 10) ?? "—"}</td>
                    <td>{(row.score_decile ?? 0) + 1}</td>
                    <td>{row.score_min?.toPrecision(4) ?? "—"}</td>
                    <td>{row.score_max?.toPrecision(4) ?? "—"}</td>
                    <td>{row.excerpt_count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="dashboard-caption">
            Decis calculados por ranker, versão e hash de configuração. Escalas
            diferentes não são combinadas; a contagem representa excerpts.
          </p>
        </>
      )}
    </section>
  );
}

function ExcerptProductionReport({ data }: { data: ReportingResponse }) {
  const latest = new Map<string, ReportingSeries>();
  data.series.forEach((row) => {
    latest.set(
      [
        row.language,
        row.rights_status,
        row.generation_method,
        row.outcome,
        row.reason_bucket,
      ].join("|"),
      row,
    );
  });
  const rows = [...latest.values()];
  return (
    <section>
      <strong>PL-07 · Produção e elegibilidade de excerpts</strong>
      {!rows.length ? (
        <EmptyReportState data={data} />
      ) : (
        <>
          <div
            className="dashboard-chart"
            aria-label="Contagem de excerpts por estado de produção"
          >
            <ResponsiveContainer width="100%" height={250}>
              <BarChart
                data={rows.map((row) => ({
                  ...row,
                  label: `${row.language}/${row.rights_status} · ${row.outcome}`,
                }))}
              >
                <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                <XAxis
                  dataKey="label"
                  stroke="#8290a8"
                  tick={{ fontSize: 9 }}
                  interval="preserveStartEnd"
                />
                <YAxis stroke="#8290a8" tick={{ fontSize: 11 }} />
                <Tooltip
                  contentStyle={{
                    background: "#111828",
                    border: "1px solid #2a3a55",
                  }}
                />
                <Bar dataKey="excerpt_count" name="Excerpts" fill="#8cf0d2" />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <div className="dashboard-scroll-table">
            <table className="dashboard-table">
              <thead>
                <tr>
                  <th>Idioma</th>
                  <th>Direitos</th>
                  <th>Método</th>
                  <th>Resultado</th>
                  <th>Motivo</th>
                  <th>Excerpts</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row, index) => (
                  <tr
                    key={`${row.language}-${row.outcome}-${row.reason_bucket}-${index}`}
                  >
                    <td>{row.language}</td>
                    <td>{row.rights_status}</td>
                    <td>{row.generation_method}</td>
                    <td>{row.outcome}</td>
                    <td>{row.reason_bucket}</td>
                    <td>{row.excerpt_count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="dashboard-caption">
            Estados agregados sem texto/IDs. A fonte não registra aprovação nem
            publicação editorial; esses estados não são inferidos.
          </p>
        </>
      )}
    </section>
  );
}

function ImportQualityReport({ data }: { data: ReportingResponse }) {
  const latest = new Map<string, ReportingSeries>();
  data.series.forEach((row) =>
    latest.set(
      [
        row.day,
        row.source_code,
        row.item_status,
        row.error_bucket,
        row.rights_outcome,
        row.asset_outcome,
      ].join("|"),
      row,
    ),
  );
  const rows = [...latest.values()];
  return (
    <section>
      <strong>PL-10 · Qualidade de importação</strong>
      {!rows.length ? (
        <EmptyReportState data={data} />
      ) : (
        <>
          <div
            className="dashboard-chart"
            aria-label="Tentativas e retries de importação"
          >
            <ResponsiveContainer width="100%" height={240}>
              <BarChart
                data={rows.slice(-60).map((row) => ({
                  ...row,
                  label: `${row.day}/${row.source_code}/${row.item_status}`,
                }))}
              >
                <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                <XAxis
                  dataKey="label"
                  stroke="#8290a8"
                  tick={{ fontSize: 9 }}
                  interval="preserveStartEnd"
                />
                <YAxis stroke="#8290a8" tick={{ fontSize: 11 }} />
                <Tooltip
                  contentStyle={{
                    background: "#111828",
                    border: "1px solid #2a3a55",
                  }}
                />
                <Legend />
                <Bar dataKey="item_count" name="Tentativas" fill="#7fa8ff" />
                <Bar
                  dataKey="retry_attempt_count"
                  name="Retries"
                  fill="#f2c879"
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <div className="dashboard-scroll-table">
            <table className="dashboard-table">
              <thead>
                <tr>
                  <th>Dia</th>
                  <th>Origem</th>
                  <th>Estado</th>
                  <th>Erro</th>
                  <th>Direitos atuais</th>
                  <th>Asset</th>
                  <th>Tentativas</th>
                  <th>Retries</th>
                </tr>
              </thead>
              <tbody>
                {rows.slice(-100).map((row, index) => (
                  <tr
                    key={`${row.day}-${row.source_code}-${row.item_status}-${index}`}
                  >
                    <td>{row.day}</td>
                    <td>{row.source_code}</td>
                    <td>{row.item_status}</td>
                    <td>{row.error_bucket}</td>
                    <td>{row.rights_outcome}</td>
                    <td>{row.asset_outcome}</td>
                    <td>{row.item_count}</td>
                    <td>{row.retry_attempt_count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="dashboard-caption">
            Retries são tentativas com attempt_number &gt; 1. Repetição não é
            tratada como duplicidade. Direitos refletem a decisão vigente no
            snapshot.
          </p>
        </>
      )}
    </section>
  );
}

function BacklogReport({ data }: { data: ReportingResponse }) {
  const latest = new Map<string, ReportingSeries>();
  data.series.forEach((row) =>
    latest.set(`${row.source_type}-${row.queue_name}`, row),
  );
  const rows = [...latest.values()];
  return (
    <section>
      <strong>PL-11 · Backlog e frescor operacional</strong>
      {!rows.length ? (
        <EmptyReportState data={data} />
      ) : (
        <div className="dashboard-scroll-table">
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Fonte</th>
                <th>Fila/projeção</th>
                <th>Pendentes</th>
                <th>Mais antigo (min)</th>
                <th>Falhas</th>
                <th>Presos &gt;15 min</th>
                <th>Frescor (min)</th>
                <th>Refresh</th>
                <th>Registros</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr key={`${row.source_type}-${row.queue_name}-${index}`}>
                  <td>{row.source_type}</td>
                  <td>{row.queue_name}</td>
                  <td>{row.pending_count}</td>
                  <td>
                    {row.oldest_pending_age_seconds == null
                      ? "—"
                      : Math.floor(row.oldest_pending_age_seconds / 60)}
                  </td>
                  <td>{row.failed_count}</td>
                  <td>{row.stuck_count}</td>
                  <td>
                    {row.freshness_age_seconds == null
                      ? "—"
                      : Math.floor(row.freshness_age_seconds / 60)}
                  </td>
                  <td>{row.refresh_status}</td>
                  <td>{row.record_count}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="dashboard-caption">
            RUNNING acima de 15 minutos é um sinal operacional. Não há SLA de
            publicação aprovado; freshness mostra a idade do último refresh.
          </p>
        </div>
      )}
    </section>
  );
}

function QualityReport({
  attribution,
  reconciliation,
  eventQuality,
  eventSchema,
  accessAudit,
}: {
  attribution: ReportingResponse;
  reconciliation: ReportingResponse;
  eventQuality: ReportingResponse;
  eventSchema: ReportingResponse;
  accessAudit: ReportingResponse;
}) {
  return (
    <div className="dashboard-quality-grid">
      <div>
        <strong>DQ-03 · Atribuição</strong>
        {!attribution.series.length ? (
          <EmptyReportState data={attribution} />
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
          <EmptyReportState data={reconciliation} />
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
          <EmptyReportState data={eventQuality} />
        ) : (
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Dia</th>
                <th>Aceitos</th>
                <th>Rejeitados</th>
                <th>Classificados</th>
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
                  <td>{row.classified_events ?? "—"}</td>
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
          <EmptyReportState data={eventSchema} />
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
      <div>
        <strong>DQ-08 · Auditoria de acesso agregada</strong>
        {!accessAudit.series.length ? (
          <EmptyReportState data={accessAudit} />
        ) : (
          <table className="dashboard-table">
            <thead>
              <tr>
                <th>Dia</th>
                <th>Ação</th>
                <th>Eventos</th>
                <th>Atores únicos</th>
                <th>Alvos únicos</th>
              </tr>
            </thead>
            <tbody>
              {accessAudit.series.slice(-40).map((row, index) => (
                <tr key={`${row.day}-${row.action}-${index}`}>
                  <td>{row.day ?? "—"}</td>
                  <td>{row.action ?? "—"}</td>
                  <td>{row.event_count ?? "—"}</td>
                  <td>{row.unique_actors ?? "—"}</td>
                  <td>{row.unique_targets ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function BookEngagementTrendCharts({ data }: { data: ReportingResponse }) {
  const chartRows = data.series.filter(
    (row) =>
      row.aggregation_level === "BOOK" || row.aggregation_level === "LANGUAGE",
  );
  if (!chartRows.length) return <EmptyReportState data={data} />;

  return (
    <div className="dashboard-engagement-trends">
      {(["BOOK", "LANGUAGE"] as const).map((level) => {
        const rows = chartRows.filter((row) => row.aggregation_level === level);
        if (!rows.length) return null;
        const entityMap = new Map<
          string,
          { aggregationKey: string; label: string; chartKey: string }
        >();
        rows.forEach((row) => {
          const aggregationKey = String(row.aggregation_key);
          if (!entityMap.has(aggregationKey)) {
            entityMap.set(aggregationKey, {
              aggregationKey,
              label: String(row.book_title ?? row.language ?? aggregationKey),
              chartKey: `entity_${entityMap.size}`,
            });
          }
        });
        const entities = [...entityMap.values()];
        const days = [...new Set(rows.map((row) => String(row.day)))].sort();
        const dailyData = days.map((day) => {
          const point: Record<string, string | number | null> = { day };
          entities.forEach((entity) => {
            const row = rows.find(
              (candidate) =>
                candidate.day === day &&
                candidate.aggregation_key === entity.aggregationKey,
            );
            point[entity.chartKey] =
              typeof row?.active_minutes === "number"
                ? row.active_minutes
                : null;
          });
          return point;
        });
        const palette = ["#8cf0d2", "#bb9cff", "#ff9f7f", "#7fa8ff", "#f6c76d"];

        return (
          <section className="dashboard-engagement-trend" key={level}>
            <h4>
              {level === "BOOK"
                ? "Obras por minutos ativos"
                : "Idiomas por minutos ativos"}
            </h4>
            <div
              aria-label={`${level === "BOOK" ? "Tendência diária das principais obras" : "Tendência diária por idioma"}`}
            >
              <ResponsiveContainer width="100%" height={280}>
                <LineChart
                  data={dailyData}
                  margin={{ left: 8, right: 16, top: 8 }}
                >
                  <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                  <XAxis
                    dataKey="day"
                    stroke="#8290a8"
                    tick={{ fontSize: 11 }}
                  />
                  <YAxis stroke="#8290a8" tick={{ fontSize: 11 }} />
                  <Tooltip
                    contentStyle={{
                      background: "#111828",
                      border: "1px solid #2a3a55",
                    }}
                  />
                  <Legend />
                  {entities.map((entity, index) => (
                    <Line
                      key={entity.aggregationKey}
                      type="monotone"
                      dataKey={entity.chartKey}
                      name={entity.label}
                      stroke={palette[index % palette.length]}
                      dot={false}
                      connectNulls={false}
                    />
                  ))}
                </LineChart>
              </ResponsiveContainer>
            </div>
          </section>
        );
      })}
      <div className="dashboard-scroll-table">
        <table className="dashboard-table">
          <caption>
            Pontos diários da tendência; dias abaixo de minReaders são omitidos.
          </caption>
          <thead>
            <tr>
              <th>Dia</th>
              <th>Dimensão</th>
              <th>Obra / idioma</th>
              <th>Leitores</th>
              <th>Minutos ativos</th>
              <th>Conclusões</th>
            </tr>
          </thead>
          <tbody>
            {chartRows.map((row, index) => (
              <tr
                key={`${row.day}-${row.aggregation_level}-${row.aggregation_key}-${index}`}
              >
                <td>{row.day ?? "—"}</td>
                <td>{row.aggregation_level}</td>
                <td>{row.book_title ?? row.language ?? "—"}</td>
                <td>{row.unique_readers ?? "—"}</td>
                <td>{row.active_minutes ?? "—"}</td>
                <td>{row.completions ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function BookEngagementReport({
  data,
  trends,
}: {
  data: ReportingResponse;
  trends: ReportingResponse;
}) {
  const books = data.series.filter((row) => row.aggregation_level === "BOOK");
  const languages = data.series.filter(
    (row) => row.aggregation_level === "LANGUAGE",
  );
  if (!data.series.length && !trends.series.length) {
    return (
      <div className="dashboard-state">
        {data.population === "SUPPRESSED_BELOW_MIN_SAMPLE" ||
        trends.population === "SUPPRESSED_BELOW_MIN_SAMPLE"
          ? "INSUFFICIENT_SAMPLE"
          : statusText(trends.status)}
      </div>
    );
  }
  return (
    <>
      <div className="dashboard-card wide">
        <div className="dashboard-card-heading">
          <div>
            <strong>GR-10 · Tendências de leitura</strong>
            <p>
              Top obras e idiomas por minutos ativos nos dias elegíveis; cada
              ponto cumpre o limiar mínimo de leitores.
            </p>
          </div>
        </div>
        <BookEngagementTrendCharts data={trends} />
      </div>
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
    </>
  );
}

function SocialReport({
  social,
  publisher,
  moderation,
  workflowFunnel,
  rights,
}: {
  social: ReportingResponse;
  publisher: ReportingResponse;
  moderation: ReportingResponse;
  workflowFunnel: ReportingResponse;
  rights: ReportingResponse;
}) {
  const rightsChartData = Object.values(
    rights.series.reduce<Record<string, Record<string, string | number>>>(
      (days, row) => {
        const day = row.day ?? "unknown";
        const entry = (days[day] ??= { day });
        const state = (row.status ?? "UNKNOWN").toLowerCase();
        entry[state] = Number(entry[state] ?? 0) + Number(row.item_count ?? 0);
        return days;
      },
      {},
    ),
  ).slice(-30);
  const rightsStates = [
    ["active", "Ativas", "#55d6be"],
    ["expired", "Expiradas", "#ff9b71"],
    ["not_yet_valid", "Ainda não vigentes", "#bb9cff"],
    ["revoked", "Revogadas", "#ff6b7d"],
    ["rejected", "Rejeitadas", "#ff6b7d"],
    ["review_required", "Revisão necessária", "#ffd166"],
    ["unknown", "Desconhecidas", "#8a95a8"],
    ["attested", "Atestações", "#7fa8ff"],
  ] as const;
  const submittedCount = workflowFunnel.series.reduce(
    (sum, row) => sum + Number(row.submissions ?? 0),
    0,
  );
  const funnelStages = [
    [
      "Submetidas",
      workflowFunnel.series.reduce(
        (sum, row) => sum + Number(row.submissions ?? 0),
        0,
      ),
    ],
    [
      "Revisadas",
      workflowFunnel.series.reduce(
        (sum, row) => sum + Number(row.reviewed_submissions ?? 0),
        0,
      ),
    ],
    [
      "Aprovadas",
      workflowFunnel.series.reduce(
        (sum, row) => sum + Number(row.approved_submissions ?? 0),
        0,
      ),
    ],
    [
      "Publicadas",
      workflowFunnel.series.reduce(
        (sum, row) => sum + Number(row.published_submissions ?? 0),
        0,
      ),
    ],
    [
      "Ativas",
      workflowFunnel.series.reduce(
        (sum, row) => sum + Number(row.active_published_submissions ?? 0),
        0,
      ),
    ],
  ] as const;
  return (
    <>
      <div className="dashboard-quality-grid">
        <div>
          <strong>SO-01/SO-03 · Interações e moderação</strong>
          {!social.series.length ? (
            <EmptyReportState data={social} />
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
          <strong>SO-01 · Eventos de interação</strong>
          {!social.series.length ? (
            <EmptyReportState data={social} />
          ) : (
            <>
              <div
                className="dashboard-chart"
                role="img"
                aria-label="Eventos sociais aceitos por dia, separados por tipo de ação"
              >
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={social.series.slice(-30)}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="day" />
                    <YAxis allowDecimals={false} />
                    <Tooltip />
                    <Legend />
                    <Bar
                      dataKey="behavior_like_events"
                      name="Likes"
                      fill="#55d6be"
                    />
                    <Bar
                      dataKey="behavior_unlike_events"
                      name="Unlikes"
                      fill="#ff6b7d"
                    />
                    <Bar
                      dataKey="behavior_save_events"
                      name="Saves"
                      fill="#7fa8ff"
                    />
                    <Bar
                      dataKey="behavior_unsave_events"
                      name="Unsaves"
                      fill="#bb9cff"
                    />
                    <Bar
                      dataKey="behavior_share_events"
                      name="Shares"
                      fill="#ffd166"
                    />
                    <Bar
                      dataKey="behavior_comment_events"
                      name="Comentários"
                      fill="#ff9b71"
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <div className="dashboard-scroll-table">
                <table className="dashboard-table">
                  <thead>
                    <tr>
                      <th>Dia</th>
                      <th>Likes</th>
                      <th>Unlikes</th>
                      <th>Saves</th>
                      <th>Unsaves</th>
                      <th>Shares</th>
                      <th>Comentários</th>
                      <th>Atores distintos</th>
                      <th>Obras distintas</th>
                    </tr>
                  </thead>
                  <tbody>
                    {social.series.slice(-30).map((row, index) => (
                      <tr key={`events-${row.day}-${index}`}>
                        <td>{row.day ?? "—"}</td>
                        <td>{row.behavior_like_events ?? 0}</td>
                        <td>{row.behavior_unlike_events ?? 0}</td>
                        <td>{row.behavior_save_events ?? 0}</td>
                        <td>{row.behavior_unsave_events ?? 0}</td>
                        <td>{row.behavior_share_events ?? 0}</td>
                        <td>{row.behavior_comment_events ?? 0}</td>
                        <td>{row.behavior_unique_actors ?? 0}</td>
                        <td>{row.behavior_distinct_books ?? 0}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
          <p className="dashboard-caption">
            Eventos aceitos pelo serviço de behavior; cada ação é contada uma
            vez pela chave única do evento. Atores e obras são distintos no dia.
            Os totais não são somados aos contadores de atividade social acima.
          </p>
        </div>
        <div>
          <strong>SO-05/SO-06 · Publicação</strong>
          {!publisher.series.length ? (
            <EmptyReportState data={publisher} />
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
            <EmptyReportState data={moderation} />
          ) : (
            <>
              <div
                className="dashboard-chart"
                aria-label="Estados diários de moderação"
              >
                <ResponsiveContainer width="100%" height={270}>
                  <BarChart data={moderation.series.slice(-30)}>
                    <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                    <XAxis
                      dataKey="day"
                      stroke="#8290a8"
                      tick={{ fontSize: 11 }}
                    />
                    <YAxis stroke="#8290a8" tick={{ fontSize: 11 }} />
                    <Tooltip
                      contentStyle={{
                        background: "#111828",
                        border: "1px solid #2a3a55",
                      }}
                    />
                    <Legend />
                    <Bar
                      dataKey="open_count"
                      name="Abertos"
                      stackId="reports"
                      fill="#ff9f7f"
                    />
                    <Bar
                      dataKey="in_review_count"
                      name="Em análise"
                      stackId="reports"
                      fill="#f6c76d"
                    />
                    <Bar
                      dataKey="resolved_count"
                      name="Resolvidos"
                      stackId="reports"
                      fill="#8cf0d2"
                    />
                    <Bar
                      dataKey="dismissed_count"
                      name="Descartados"
                      stackId="reports"
                      fill="#7fa8ff"
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <div
                className="dashboard-chart"
                aria-label="Duração de resolução e idade dos casos abertos"
              >
                <ResponsiveContainer width="100%" height={250}>
                  <LineChart data={moderation.series.slice(-30)}>
                    <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                    <XAxis
                      dataKey="day"
                      stroke="#8290a8"
                      tick={{ fontSize: 11 }}
                    />
                    <YAxis stroke="#8290a8" tick={{ fontSize: 11 }} />
                    <Tooltip
                      contentStyle={{
                        background: "#111828",
                        border: "1px solid #2a3a55",
                      }}
                    />
                    <Legend />
                    <Line
                      type="monotone"
                      dataKey="p50_resolution_seconds"
                      name="P50 resolução (s)"
                      stroke="#8cf0d2"
                      dot={false}
                      connectNulls={false}
                    />
                    <Line
                      type="monotone"
                      dataKey="p95_resolution_seconds"
                      name="P95 resolução (s)"
                      stroke="#bb9cff"
                      dot={false}
                      connectNulls={false}
                    />
                    <Line
                      type="monotone"
                      dataKey="oldest_open_age_seconds"
                      name="Caso aberto mais antigo (s)"
                      stroke="#ff9f7f"
                      dot={false}
                      connectNulls={false}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
              <div
                className="dashboard-chart"
                aria-label="Distribuição dos casos de moderação abertos por idade"
              >
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={moderation.series.slice(-30)}>
                    <CartesianGrid stroke="#26334a" strokeDasharray="3 3" />
                    <XAxis
                      dataKey="day"
                      stroke="#8290a8"
                      tick={{ fontSize: 11 }}
                    />
                    <YAxis
                      allowDecimals={false}
                      stroke="#8290a8"
                      tick={{ fontSize: 11 }}
                    />
                    <Tooltip
                      contentStyle={{
                        background: "#111828",
                        border: "1px solid #2a3a55",
                      }}
                    />
                    <Legend />
                    <Bar
                      dataKey="open_under_1_day"
                      name="Menos de 1 dia"
                      stackId="age"
                      fill="#8cf0d2"
                    />
                    <Bar
                      dataKey="open_1_to_3_days"
                      name="1 a 3 dias"
                      stackId="age"
                      fill="#7fa8ff"
                    />
                    <Bar
                      dataKey="open_3_to_7_days"
                      name="3 a 7 dias"
                      stackId="age"
                      fill="#ffcf70"
                    />
                    <Bar
                      dataKey="open_7_days_or_more"
                      name="7 dias ou mais"
                      stackId="age"
                      fill="#ff7f8b"
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <p className="dashboard-caption">
                Estados são contados por dia de criação; percentis usam casos
                resolvidos. A linha de idade mostra o caso aberto mais antigo;
                barras mostram casos ainda abertos/em análise por faixa de idade
                em cada coorte de criação, no momento da atualização.
              </p>
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
            </>
          )}
        </div>
        <div>
          <strong>SO-05 · Funil por coorte de submissão</strong>
          {!workflowFunnel.series.length ? (
            <EmptyReportState data={workflowFunnel} />
          ) : (
            <>
              <div
                className="executive-funnel"
                aria-label="Submissões distintas que alcançaram cada etapa editorial"
              >
                {funnelStages.map(([label, value]) => {
                  const width = submittedCount
                    ? Math.min(100, Math.max(0, (value / submittedCount) * 100))
                    : 0;
                  return (
                    <div className="executive-funnel-stage" key={label}>
                      <div>
                        <span>{label}</span>
                        <b>{value}</b>
                      </div>
                      <div
                        className="executive-funnel-track"
                        role="img"
                        aria-label={`${label}: ${value} submissões`}
                      >
                        <span style={{ width: `${width}%` }} />
                      </div>
                    </div>
                  );
                })}
              </div>
              <p className="dashboard-caption">
                Cada etapa conta submissões distintas da coorte selecionada que
                já a alcançaram no último refresh. Coortes recentes podem seguir
                em análise; barras não representam taxas nem SLA.
              </p>
              <div className="dashboard-scroll-table">
                <table className="dashboard-table">
                  <thead>
                    <tr>
                      <th>Coorte (submissão)</th>
                      <th>Submetidas</th>
                      <th>Revisadas</th>
                      <th>Aprovadas</th>
                      <th>Publicadas</th>
                      <th>Ativas</th>
                      <th>Com direitos</th>
                    </tr>
                  </thead>
                  <tbody>
                    {workflowFunnel.series.slice(-30).map((row, index) => (
                      <tr key={`f-${row.cohort_day}-${index}`}>
                        <td>{row.cohort_day ?? "—"}</td>
                        <td>{row.submissions ?? "—"}</td>
                        <td>{row.reviewed_submissions ?? "—"}</td>
                        <td>{row.approved_submissions ?? "—"}</td>
                        <td>{row.published_submissions ?? "—"}</td>
                        <td>{row.active_published_submissions ?? "—"}</td>
                        <td>{row.rights_attested_submissions ?? "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
          <strong>SO-07 · Direitos e atestações</strong>
          {!rights.series.length ? (
            <EmptyReportState data={rights} />
          ) : (
            <>
              <div
                className="dashboard-chart"
                role="img"
                aria-label="Decisões de direitos e atestações por dia e estado"
              >
                <ResponsiveContainer width="100%" height={240}>
                  <BarChart data={rightsChartData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="day" />
                    <YAxis allowDecimals={false} />
                    <Tooltip />
                    <Legend />
                    {rightsStates.map(([key, label, color]) => (
                      <Bar
                        key={key}
                        dataKey={key}
                        name={label}
                        stackId="rights"
                        fill={color}
                      />
                    ))}
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <div className="dashboard-scroll-table">
                <table className="dashboard-table">
                  <thead>
                    <tr>
                      <th>Dia</th>
                      <th>Fonte</th>
                      <th>Direito</th>
                      <th>Território</th>
                      <th>Estado registrado</th>
                      <th>Itens</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rights.series.slice(-20).map((row, index) => (
                      <tr
                        key={`r-${row.day}-${row.source_type}-${row.right_type}-${row.territory}-${row.status}-${index}`}
                      >
                        <td>{row.day ?? "—"}</td>
                        <td>{row.source_type ?? "—"}</td>
                        <td>{row.right_type ?? "—"}</td>
                        <td>{row.territory ?? "—"}</td>
                        <td>{row.status ?? "—"}</td>
                        <td>{row.item_count ?? "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}

export function DashboardHub() {
  const auth = useAuth();
  const [params, setParams] = useSearchParams();
  const active = params.get("area") ?? "executive";
  const days = Number(params.get("days") ?? "30");
  const [report, setReport] = useState<ReportingResponse | null>(null);
  const [executiveSummaryReport, setExecutiveSummaryReport] =
    useState<ExecutiveSummary | null>(null);
  const [feedReport, setFeedReport] = useState<ReportingResponse | null>(null);
  const [recommendationReport, setRecommendationReport] =
    useState<ReportingResponse | null>(null);
  const [
    recommendationProfileQualityReport,
    setRecommendationProfileQualityReport,
  ] = useState<ReportingResponse | null>(null);
  const [recommendationQualityReport, setRecommendationQualityReport] =
    useState<ReportingResponse | null>(null);
  const [recommendationPositionReport, setRecommendationPositionReport] =
    useState<ReportingResponse | null>(null);
  const [
    recommendationPositionFunnelReport,
    setRecommendationPositionFunnelReport,
  ] = useState<ReportingResponse | null>(null);
  const [excerptPerformanceReport, setExcerptPerformanceReport] =
    useState<ReportingResponse | null>(null);
  const [experimentReport, setExperimentReport] =
    useState<ReportingResponse | null>(null);
  const [pipelineReport, setPipelineReport] =
    useState<ReportingResponse | null>(null);
  const [pipelinePhaseReport, setPipelinePhaseReport] =
    useState<ReportingResponse | null>(null);
  const [editorialQualityReport, setEditorialQualityReport] =
    useState<ReportingResponse | null>(null);
  const [attributionReport, setAttributionReport] =
    useState<ReportingResponse | null>(null);
  const [reconciliationReport, setReconciliationReport] =
    useState<ReportingResponse | null>(null);
  const [eventQualityReport, setEventQualityReport] =
    useState<ReportingResponse | null>(null);
  const [eventSchemaReport, setEventSchemaReport] =
    useState<ReportingResponse | null>(null);
  const [accessAuditReport, setAccessAuditReport] =
    useState<ReportingResponse | null>(null);
  const [socialReport, setSocialReport] = useState<ReportingResponse | null>(
    null,
  );
  const [publisherReport, setPublisherReport] =
    useState<ReportingResponse | null>(null);
  const [moderationReport, setModerationReport] =
    useState<ReportingResponse | null>(null);
  const [workflowFunnelReport, setWorkflowFunnelReport] =
    useState<ReportingResponse | null>(null);
  const [rightsReport, setRightsReport] = useState<ReportingResponse | null>(
    null,
  );
  const [supplyReport, setSupplyReport] = useState<ReportingResponse | null>(
    null,
  );
  const [contentCoverageReport, setContentCoverageReport] =
    useState<ReportingResponse | null>(null);
  const [contentRankScoresReport, setContentRankScoresReport] =
    useState<ReportingResponse | null>(null);
  const [excerptProductionReport, setExcerptProductionReport] =
    useState<ReportingResponse | null>(null);
  const [importQualityReport, setImportQualityReport] =
    useState<ReportingResponse | null>(null);
  const [backlogReport, setBacklogReport] = useState<ReportingResponse | null>(
    null,
  );
  const [ingestionReport, setIngestionReport] =
    useState<ReportingResponse | null>(null);
  const [ingestionQueueReport, setIngestionQueueReport] =
    useState<ReportingResponse | null>(null);
  const [readingReport, setReadingReport] = useState<ReportingResponse | null>(
    null,
  );
  const [retentionReport, setRetentionReport] =
    useState<ReportingResponse | null>(null);
  const [readingProgressReport, setReadingProgressReport] =
    useState<ReportingResponse | null>(null);
  const [libraryStateReport, setLibraryStateReport] =
    useState<ReportingResponse | null>(null);
  const [readingHabitsReport, setReadingHabitsReport] =
    useState<ReportingResponse | null>(null);
  const [bookEngagementReport, setBookEngagementReport] =
    useState<ReportingResponse | null>(null);
  const [bookEngagementTrendsReport, setBookEngagementTrendsReport] =
    useState<ReportingResponse | null>(null);
  const [health, setHealth] = useState<Record<string, unknown>[]>([]);
  const [releaseEventsReport, setReleaseEventsReport] =
    useState<ReportingResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [failedMetrics, setFailedMetrics] = useState<string[]>([]);
  const [exportDataset, setExportDataset] =
    useState<ReportingExportDataset>("feed-daily");
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const canExport = auth.roles.some((role) =>
    [
      "platform-admins",
      "operators",
      "platform-admin",
      "platform-operator",
      "OPERATOR",
    ].includes(role),
  );
  const area = areas.some(([key]) => key === active) ? active : "executive";

  const exportReporting = async () => {
    setExportError(null);
    setExporting(true);
    try {
      const blob = await services.exportReporting(exportDataset, days);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `bookrush-${exportDataset}-${days}d.csv`;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (cause) {
      setExportError(
        cause instanceof Error
          ? cause.message
          : "Não foi possível exportar este relatório.",
      );
    } finally {
      setExporting(false);
    }
  };

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
        recommendationProfileQualityDaily,
        recommendationQualityDaily,
        recommendationPositionDaily,
        recommendationPositionFunnelDaily,
        excerptPerformance,
        experimentDaily,
        bookEngagement,
        bookEngagementTrends,
        pipeline,
        pipelinePhases,
        editorialQuality,
        attribution,
        reconciliation,
        eventQuality,
        eventSchema,
        accessAudit,
        social,
        publisher,
        moderation,
        workflowFunnel,
        rights,
        supply,
        contentCoverage,
        contentRankScores,
        excerptProduction,
        importQuality,
        backlog,
        ingestion,
        ingestionQueue,
        reading,
        retention,
        readingProgress,
        readingHabits,
        libraryState,
        releaseEvents,
      ] = await Promise.all([
        safeExecutiveSummary(
          services.reportingExecutiveSummary(Math.min(Math.max(days, 1), 366)),
          days,
        ),
        safeReport(
          "reader_day",
          services.reportingReaderDay(Math.min(Math.max(days, 1), 366)),
        ),
        safeHealth(services.reportingHealth()),
        safeReport(
          "feed_daily",
          services.reportingFeedDaily(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "recommendation_daily",
          services.reportingRecommendationDaily(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "recommendation_profile_quality_daily",
          services.reportingRecommendationProfileQualityDaily(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "recommendation_quality_daily",
          services.reportingRecommendationQualityDaily(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "recommendation_position_daily",
          services.reportingRecommendationPositionDaily(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "recommendation_position_funnel_daily",
          services.reportingRecommendationPositionFunnelDaily(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "excerpt_performance",
          services.reportingExcerptPerformance(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "experiment_daily",
          services.reportingExperimentDaily(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "book_engagement",
          services.reportingBookEngagement(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "book_engagement_trends",
          services.reportingBookEngagementTrends(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "pipeline_snapshot",
          services.reportingPipeline(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "pipeline_phase_daily",
          services.reportingPipelinePhases(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "editorial_quality_daily",
          services.reportingEditorialQualityDaily(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "attribution_daily",
          services.reportingAttributionQuality(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "reconciliation_daily",
          services.reportingReconciliationQuality(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "event_quality_daily",
          services.reportingEventQuality(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "event_schema_daily",
          services.reportingEventSchemaQuality(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "access_audit_daily",
          services.reportingAccessAudit(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "social_daily",
          services.reportingSocialDaily(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "publisher_daily",
          services.reportingPublisherDaily(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "moderation_daily",
          services.reportingModerationDaily(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "editorial_submission_funnel_daily",
          services.reportingEditorialSubmissionFunnelDaily(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "rights_status_daily",
          services.reportingRightsStatusDaily(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "content_supply_snapshot",
          services.reportingContentSupply(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "content_coverage_snapshot",
          services.reportingContentCoverage(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "content_rank_score_snapshot",
          services.reportingContentRankScores(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "excerpt_production_snapshot",
          services.reportingExcerptProduction(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "import_quality_daily",
          services.reportingImportQualityDaily(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "backlog_snapshot",
          services.reportingBacklog(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "ingestion_daily",
          services.reportingIngestionDaily(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "ingestion_queue_snapshot",
          services.reportingIngestionQueue(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport(
          "reading_daily",
          services.reportingReadingDaily(Math.min(Math.max(days, 1), 366)),
        ),
        safeReport("retention_cohort", services.reportingRetentionCohort(366)),
        safeReport(
          "reading_progress_daily",
          services.reportingReadingProgressDaily(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport(
          "reading_habits_daily",
          services.reportingReadingHabitsDaily(
            Math.min(Math.max(days, 1), 366),
          ),
        ),
        safeReport("library_state_snapshot", services.reportingLibraryState()),
        safeReport("release_events", services.reportingReleaseEvents(30, 100)),
      ]);
      setReport(readerDay);
      setExecutiveSummaryReport(executiveSummary);
      setHealth(projectionHealth);
      setReleaseEventsReport(releaseEvents);
      setFeedReport(feedDaily);
      setRecommendationReport(recommendationDaily);
      setRecommendationProfileQualityReport(recommendationProfileQualityDaily);
      setRecommendationQualityReport(recommendationQualityDaily);
      setRecommendationPositionReport(recommendationPositionDaily);
      setRecommendationPositionFunnelReport(recommendationPositionFunnelDaily);
      setExcerptPerformanceReport(excerptPerformance);
      setExperimentReport(experimentDaily);
      setBookEngagementReport(bookEngagement);
      setBookEngagementTrendsReport(bookEngagementTrends);
      setPipelineReport(pipeline);
      setPipelinePhaseReport(pipelinePhases);
      setEditorialQualityReport(editorialQuality);
      setAttributionReport(attribution);
      setReconciliationReport(reconciliation);
      setEventQualityReport(eventQuality);
      setEventSchemaReport(eventSchema);
      setAccessAuditReport(accessAudit);
      setSocialReport(social);
      setPublisherReport(publisher);
      setModerationReport(moderation);
      setWorkflowFunnelReport(workflowFunnel);
      setRightsReport(rights);
      setSupplyReport(supply);
      setContentCoverageReport(contentCoverage);
      setContentRankScoresReport(contentRankScores);
      setExcerptProductionReport(excerptProduction);
      setImportQualityReport(importQuality);
      setBacklogReport(backlog);
      setIngestionReport(ingestion);
      setIngestionQueueReport(ingestionQueue);
      setReadingReport(reading);
      setRetentionReport(retention);
      setReadingProgressReport(readingProgress);
      setReadingHabitsReport(readingHabits);
      setLibraryStateReport(libraryState);
      setFailedMetrics(
        [
          executiveSummary,
          readerDay,
          feedDaily,
          recommendationDaily,
          recommendationProfileQualityDaily,
          recommendationQualityDaily,
          recommendationPositionDaily,
          recommendationPositionFunnelDaily,
          excerptPerformance,
          experimentDaily,
          bookEngagement,
          pipeline,
          pipelinePhases,
          editorialQuality,
          attribution,
          reconciliation,
          eventQuality,
          eventSchema,
          accessAudit,
          social,
          publisher,
          moderation,
          workflowFunnel,
          rights,
          supply,
          contentCoverage,
          contentRankScores,
          excerptProduction,
          importQuality,
          backlog,
          ingestion,
          ingestionQueue,
          reading,
          retention,
          readingProgress,
          readingHabits,
          libraryState,
          releaseEvents,
        ]
          .filter((item) => item.status === "ERROR")
          .map((item) => item.metricId),
      );
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
    area === "executive"
      ? executiveSummaryReport
      : area === "feed"
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
        {canExport && (
          <>
            <label>
              Exportar
              <select
                value={exportDataset}
                onChange={(event) =>
                  setExportDataset(event.target.value as ReportingExportDataset)
                }
              >
                <option value="feed-daily">Feed diário</option>
                <option value="recommendation-daily">Recomendações</option>
                <option value="recommendation-quality">
                  Outcomes atribuídos
                </option>
                <option value="recommendation-profile-quality">
                  Qualidade de perfil
                </option>
                <option value="reader-day">Atividade de leitores</option>
                <option value="moderation-daily">Moderação</option>
                <option value="social-daily">Interações sociais</option>
                <option value="book-engagement">Engajamento por obra</option>
                <option value="rights-status-daily">Direitos</option>
              </select>
            </label>
            <button
              className="button secondary"
              type="button"
              onClick={() => void exportReporting()}
              disabled={exporting || loading}
            >
              <Download size={15} /> {exporting ? "Exportando…" : "Baixar CSV"}
            </button>
          </>
        )}
        <span>Fonte: reporting · UTC · sem dados mockados</span>
      </div>
      {exportError && (
        <div className="dashboard-alert error" role="alert">
          <ShieldAlert size={18} /> {exportError}
        </div>
      )}
      {error && (
        <div className="dashboard-alert error" role="alert">
          <ShieldAlert size={18} />
          {error}
        </div>
      )}
      {failedMetrics.length > 0 && (
        <div className="dashboard-alert warning" role="status">
          <AlertTriangle size={18} />
          <span>
            Algumas fontes falharam ({failedMetrics.join(", ")}); os demais
            painéis continuam disponíveis. Atualize para tentar novamente.
          </span>
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
              className={`data-badge ${displayedReport?.status !== "OK" ? "warning" : ""}`}
            >
              {statusText(displayedReport?.status ?? "NO_DATA")}
            </span>
          </div>
          {area === "operations" ? (
            releaseEventsReport ? (
              <OperationsReport
                health={health}
                releases={releaseEventsReport}
              />
            ) : (
              <div className="dashboard-state">NO_DATA</div>
            )
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
              <div className="dashboard-product-health-section">
                <strong>EX-03 · Saúde do produto</strong>
                {feedReport && eventQualityReport ? (
                  <ProductHealthReport
                    health={health}
                    feed={feedReport}
                    eventQuality={eventQualityReport}
                  />
                ) : (
                  <div className="dashboard-state">NO_DATA</div>
                )}
              </div>
              {report && <ReadingChart data={report} />}
              <ExecutiveSummaryReport data={executiveSummaryReport} />
              {supplyReport && (
                <SupplyReport
                  supply={supplyReport}
                  coverage={contentCoverageReport ?? undefined}
                  rankScores={contentRankScoresReport ?? undefined}
                  excerptProduction={excerptProductionReport ?? undefined}
                  importQuality={importQualityReport ?? undefined}
                  backlog={backlogReport ?? undefined}
                />
              )}
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
              {readingProgressReport && (
                <ReadingProgressReport data={readingProgressReport} />
              )}
              {libraryStateReport && (
                <LibraryStateReport data={libraryStateReport} />
              )}
              {readingHabitsReport && (
                <ReadingHabitsReport data={readingHabitsReport} />
              )}
              {bookEngagementReport && bookEngagementTrendsReport && (
                <BookEngagementReport
                  data={bookEngagementReport}
                  trends={bookEngagementTrendsReport}
                />
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
              {recommendationReport &&
                recommendationProfileQualityReport &&
                recommendationQualityReport &&
                recommendationPositionReport &&
                recommendationPositionFunnelReport && (
                  <Suspense
                    fallback={
                      <p role="status">Carregando painéis de recomendação…</p>
                    }
                  >
                    <RecommendationReport
                      data={recommendationReport}
                      profileQuality={recommendationProfileQualityReport}
                      quality={recommendationQualityReport}
                      position={recommendationPositionReport}
                      positionFunnel={recommendationPositionFunnelReport}
                    />
                  </Suspense>
                )}
              {excerptPerformanceReport && (
                <ExcerptPerformanceReport data={excerptPerformanceReport} />
              )}
              {experimentReport && <ExperimentReport data={experimentReport} />}
            </div>
          ) : area === "pipelines" &&
            pipelineReport &&
            supplyReport &&
            ingestionReport ? (
            <div className="dashboard-card wide">
              <div className="dashboard-card-heading">
                <div>
                  <strong>
                    PL-01..05/09 · Ingestão, analytics e editorial
                  </strong>
                  <p>
                    Snapshots persistidos pelo reporting; throughput considera
                    itens concluídos na última hora de cada coleta.
                  </p>
                </div>
                <AlertTriangle size={18} />
              </div>
              <PipelineReport data={pipelineReport} />
              {ingestionQueueReport && (
                <IngestionQueueReport data={ingestionQueueReport} />
              )}
              {pipelinePhaseReport && (
                <PipelinePhaseReport data={pipelinePhaseReport} />
              )}
              {editorialQualityReport && (
                <EditorialQualityReport data={editorialQualityReport} />
              )}
              <SupplyReport
                supply={supplyReport}
                coverage={contentCoverageReport ?? undefined}
                rankScores={contentRankScoresReport ?? undefined}
                excerptProduction={excerptProductionReport ?? undefined}
                importQuality={importQualityReport ?? undefined}
                backlog={backlogReport ?? undefined}
                ingestion={ingestionReport}
              />
            </div>
          ) : area === "quality" &&
            attributionReport &&
            reconciliationReport &&
            eventQualityReport &&
            eventSchemaReport &&
            accessAuditReport ? (
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
                accessAudit={accessAuditReport}
              />
            </div>
          ) : area === "social" &&
            socialReport &&
            publisherReport &&
            moderationReport &&
            workflowFunnelReport &&
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
                workflowFunnel={workflowFunnelReport}
                rights={rightsReport}
              />
              {bookEngagementReport && bookEngagementTrendsReport && (
                <BookEngagementReport
                  data={bookEngagementReport}
                  trends={bookEngagementTrendsReport}
                />
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
