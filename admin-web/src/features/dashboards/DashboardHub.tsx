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
import { services, ReportingResponse } from "../../lib/services";

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
  if (status === "UNTRUSTED") return "UNTRUSTED — população ainda não classificada";
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
            <Tooltip contentStyle={{ background: "#111828", border: "1px solid #2a3a55" }} />
            <Line type="monotone" dataKey="activeUsers" name="Leitores ativos" stroke="#8cf0d2" dot={false} connectNulls={false} />
            <Line type="monotone" dataKey="weeklyActiveUsers" name="WAU" stroke="#bb9cff" dot={false} connectNulls={false} />
            <Line type="monotone" dataKey="monthlyActiveUsers" name="MAU" stroke="#ff9f7f" dot={false} connectNulls={false} />
            <Line type="monotone" dataKey="sessions" name="Sessões" stroke="#7fa8ff" dot={false} connectNulls={false} />
            <Line type="monotone" dataKey="meaningfulReads" name="Leituras significativas" stroke="#f6c76d" dot={false} connectNulls={false} />
          </LineChart>
        </ResponsiveContainer>
      </div>
      <table className="dashboard-table">
        <caption>Valores reais do snapshot; população {data.population}</caption>
        <thead><tr><th>Dia</th><th>DAU</th><th>WAU</th><th>MAU</th><th>Novos</th><th>Recorrentes</th><th>Sessões</th><th>Leituras ≥60s</th></tr></thead>
        <tbody>{data.series.slice(-14).map((row) => <tr key={row.day}>
          <td>{row.day}</td><td>{row.active_users ?? "—"}</td><td>{row.weekly_active_users ?? "—"}</td><td>{row.monthly_active_users ?? "—"}</td>
          <td>{row.newly_active_users ?? "—"}</td><td>{row.returning_users ?? "—"}</td><td>{row.sessions ?? "—"}</td><td>{row.meaningful_reads ?? "—"}</td>
        </tr>)}</tbody>
      </table>
    </>
  );
}

function FeedReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length) return <div className="dashboard-state">NO_DATA</div>;
  return <div className="dashboard-scroll-table"><table className="dashboard-table">
    <caption>Entrega e conversão atribuída; população {data.population}; versão {data.definitionVersion}</caption>
    <thead><tr><th>Dia</th><th>Estratégia</th><th>Servidos</th><th>Viewable</th><th>Aberturas</th><th>Read 10%</th><th>Read 50%</th><th>Conclusões</th></tr></thead>
    <tbody>{data.series.slice(-50).map((row, index) => <tr key={`${row.day}-${row.strategy}-${row.model_version}-${index}`}>
      <td>{row.day ?? "—"}</td><td>{row.strategy ?? "—"}</td><td>{row.served_count ?? "—"}</td>
      <td>{row.viewable_count ?? "—"}</td><td>{row.attributed_open_count ?? "—"}</td>
      <td>{row.attributed_read_10_count ?? "—"}</td><td>{row.attributed_read_50_count ?? "—"}</td>
      <td>{row.attributed_complete_count ?? "—"}</td>
    </tr>)}</tbody>
  </table></div>;
}

function PipelineReport({ data }: { data: ReportingResponse }) {
  if (!data.series.length) return <div className="dashboard-state">NO_DATA</div>;
  return <div className="dashboard-scroll-table"><table className="dashboard-table">
    <caption>Snapshots reais de jobs e fases; estado de população {data.population}</caption>
    <thead><tr><th>Coletado</th><th>Fase</th><th>Estado</th><th>Itens</th><th>Falhas</th><th>Throughput/h</th><th>Fila mais antiga</th></tr></thead>
    <tbody>{data.series.slice(-50).map((row, index) => <tr key={`${row.collected_at}-${row.phase}-${row.job_status}-${index}`}>
      <td>{row.collected_at ? new Date(row.collected_at).toLocaleString() : "—"}</td>
      <td>{row.phase ?? "—"}</td><td>{row.job_status ?? "—"}</td><td>{row.status_count ?? "—"}</td>
      <td>{row.failure_count ?? "—"}</td><td>{row.throughput ?? "—"}</td>
      <td>{row.oldest_pending_age_seconds == null ? "—" : `${row.oldest_pending_age_seconds}s`}</td>
    </tr>)}</tbody>
  </table></div>;
}

function QualityReport({ attribution, reconciliation }: {
  attribution: ReportingResponse;
  reconciliation: ReportingResponse;
}) {
  return <div className="dashboard-quality-grid">
    <div>
      <strong>DQ-03 · Atribuição</strong>
      {!attribution.series.length ? <div className="dashboard-state">NO_DATA</div> : <table className="dashboard-table">
        <thead><tr><th>Dia</th><th>Estado</th><th>Outcomes</th><th>Eventos únicos</th></tr></thead>
        <tbody>{attribution.series.slice(-30).map((row, index) => <tr key={`${row.day}-${row.attribution_status}-${index}`}>
          <td>{row.day ?? "—"}</td><td>{row.attribution_status ?? "—"}</td><td>{row.outcome_count ?? "—"}</td><td>{row.unique_events ?? "—"}</td>
        </tr>)}</tbody>
      </table>}
    </div>
    <div>
      <strong>DQ-04 · Reconciliação de impressões</strong>
      {!reconciliation.series.length ? <div className="dashboard-state">NO_DATA</div> : <table className="dashboard-table">
        <thead><tr><th>Dia</th><th>Ledger</th><th>Viewable</th><th>Eventos</th><th>Duplicados</th><th>Sem match</th></tr></thead>
        <tbody>{reconciliation.series.slice(-30).map((row, index) => <tr key={`${row.day}-${index}`}>
          <td>{row.day ?? "—"}</td><td>{row.ledger_impression_count ?? "—"}</td><td>{row.ledger_viewable_count ?? "—"}</td>
          <td>{row.behavior_impression_event_count ?? "—"}</td><td>{row.duplicate_event_count ?? "—"}</td><td>{row.mismatched_impression_count ?? "—"}</td>
        </tr>)}</tbody>
      </table>}
    </div>
  </div>;
}

function SocialReport({ social, publisher }: {
  social: ReportingResponse;
  publisher: ReportingResponse;
}) {
  return <div className="dashboard-quality-grid">
    <div>
      <strong>SO-01/SO-03 · Interações e moderação</strong>
      {!social.series.length ? <div className="dashboard-state">NO_DATA</div> : <table className="dashboard-table">
        <thead><tr><th>Dia</th><th>Likes</th><th>Comentários</th><th>Shares</th><th>Atores</th><th>Reports abertos</th><th>Backlog moderação</th></tr></thead>
        <tbody>{social.series.slice(-30).map((row, index) => <tr key={`${row.day}-${index}`}>
          <td>{row.day ?? "—"}</td><td>{row.like_count ?? "—"}</td><td>{row.comment_count ?? "—"}</td><td>{row.share_count ?? "—"}</td>
          <td>{row.unique_actors ?? "—"}</td><td>{row.reports_open ?? "—"}</td><td>{row.moderation_backlog ?? "—"}</td>
        </tr>)}</tbody>
      </table>}
    </div>
    <div>
      <strong>SO-05/SO-06 · Publicação</strong>
      {!publisher.series.length ? <div className="dashboard-state">NO_DATA</div> : <table className="dashboard-table">
        <thead><tr><th>Dia</th><th>Obras</th><th>Impressões</th><th>Aberturas</th><th>Leituras</th><th>Likes</th><th>Shares</th></tr></thead>
        <tbody>{publisher.series.slice(-30).map((row, index) => <tr key={`${row.day}-${index}`}>
          <td>{row.day ?? "—"}</td><td>{row.published_work_count ?? "—"}</td><td>{row.impressions ?? "—"}</td>
          <td>{row.opens ?? "—"}</td><td>{row.reads ?? "—"}</td><td>{row.likes ?? "—"}</td><td>{row.shares ?? "—"}</td>
        </tr>)}</tbody>
      </table>}
    </div>
  </div>;
}

export function DashboardHub() {
  const [params, setParams] = useSearchParams();
  const active = params.get("area") ?? "executive";
  const days = Number(params.get("days") ?? "30");
  const [report, setReport] = useState<ReportingResponse | null>(null);
  const [feedReport, setFeedReport] = useState<ReportingResponse | null>(null);
  const [pipelineReport, setPipelineReport] = useState<ReportingResponse | null>(null);
  const [attributionReport, setAttributionReport] = useState<ReportingResponse | null>(null);
  const [reconciliationReport, setReconciliationReport] = useState<ReportingResponse | null>(null);
  const [socialReport, setSocialReport] = useState<ReportingResponse | null>(null);
  const [publisherReport, setPublisherReport] = useState<ReportingResponse | null>(null);
  const [health, setHealth] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const area = areas.some(([key]) => key === active) ? active : "executive";

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [readerDay, projectionHealth, feedDaily, pipeline, attribution, reconciliation, social, publisher] = await Promise.all([
        services.reportingReaderDay(Math.min(Math.max(days, 1), 366)),
        services.reportingHealth(),
        services.reportingFeedDaily(Math.min(Math.max(days, 1), 366)),
        services.reportingPipeline(Math.min(Math.max(days, 1), 366)),
        services.reportingAttributionQuality(Math.min(Math.max(days, 1), 366)),
        services.reportingReconciliationQuality(Math.min(Math.max(days, 1), 366)),
        services.reportingSocialDaily(Math.min(Math.max(days, 1), 366)),
        services.reportingPublisherDaily(Math.min(Math.max(days, 1), 366)),
      ]);
      setReport(readerDay);
      setHealth(projectionHealth);
      setFeedReport(feedDaily);
      setPipelineReport(pipeline);
      setAttributionReport(attribution);
      setReconciliationReport(reconciliation);
      setSocialReport(social);
      setPublisherReport(publisher);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Falha ao consultar reporting.");
    } finally {
      setLoading(false);
    }
  }, [days]);

  useEffect(() => { void load(); }, [load]);

  const currentLabel = useMemo(
    () => areas.find(([key]) => key === area)?.[1] ?? "Executive",
    [area],
  );
  const displayedReport = area === "feed" ? feedReport : area === "pipelines" ? pipelineReport : area === "quality" ? attributionReport : area === "social" ? socialReport : report;

  return (
    <section className="dashboard-hub">
      <header className="pagehead">
        <div><span className="eyebrow">OBSERVABILITY · DASHBOARD-RESPONSE-V1</span>
          <h1>Dashboards administrativos</h1>
          <p>Saúde operacional e indicadores de negócio com estado explícito de fonte, frescor e confiança.</p>
        </div>
        <button className="button secondary" type="button" onClick={() => void load()} disabled={loading}>
          <RefreshCw size={15} /> Atualizar
        </button>
      </header>
      <nav className="dashboard-tabs" aria-label="Áreas dos dashboards">
        {areas.map(([key, label]) => <button key={key} type="button" className={key === area ? "active" : ""}
          onClick={() => setParams({ area: key, days: String(days) })}>{label}</button>)}
      </nav>
      <div className="dashboard-filters">
        <label>Período
          <select value={days} onChange={(event) => setParams({ area, days: event.target.value })}>
            <option value="7">7 dias</option><option value="30">30 dias</option><option value="90">90 dias</option><option value="366">366 dias</option>
          </select>
        </label>
        <span>Fonte: reporting · UTC · sem dados mockados</span>
      </div>
      {error && <div className="dashboard-alert error" role="alert"><ShieldAlert size={18} />{error}</div>}
      {loading ? <div className="dashboard-state">Carregando snapshots…</div> : <>
        <div className="dashboard-heading"><div><span className="eyebrow">ÁREA</span><h2>{currentLabel}</h2></div>
          <span className={`data-badge ${displayedReport?.status === "UNTRUSTED" ? "warning" : ""}`}>{statusText(displayedReport?.status ?? "NO_DATA")}</span></div>
        {area === "executive" || area === "growth" ? <div className="dashboard-card wide">
          <div className="dashboard-card-heading"><div><strong>{area === "growth" ? "GR-01..GR-07 · Atividade e leitura" : "EX-01/04 · Atividade geral"}</strong><p>Agregado diário do behavior-service; ausência de população classificada impede interpretação como produto.</p></div><AlertTriangle size={18} /></div>
          {report && <ReadingChart data={report} />}
        </div> : area === "feed" && feedReport ? <div className="dashboard-card wide">
          <div className="dashboard-card-heading"><div><strong>FD-01..FD-05 · Entrega e conversão atribuída</strong><p>Somente outcomes com vínculo válido ao ledger e status ATTRIBUTED; não é inferência causal.</p></div><AlertTriangle size={18} /></div>
          {feedReport.status === "UNTRUSTED" && <div className="dashboard-alert warning"><AlertTriangle size={18} /><span>{statusText(feedReport.status)} · definição {feedReport.definitionVersion}</span></div>}
          <FeedReport data={feedReport} />
        </div> : area === "pipelines" && pipelineReport ? <div className="dashboard-card wide">
          <div className="dashboard-card-heading"><div><strong>PL-03..05 · Jobs, itens e fases de analytics</strong><p>Snapshots persistidos pelo reporting; throughput considera itens concluídos na última hora de cada coleta.</p></div><AlertTriangle size={18} /></div>
          <PipelineReport data={pipelineReport} />
        </div> : area === "quality" && attributionReport && reconciliationReport ? <div className="dashboard-card wide">
          <div className="dashboard-card-heading"><div><strong>DQ-03/04 · Qualidade dos eventos e reconciliação</strong><p>Contagens persistidas para auditoria; duplicidades não são apagadas e qualquer mismatch permanece visível.</p></div><AlertTriangle size={18} /></div>
          {(attributionReport.status === "UNTRUSTED" || reconciliationReport.status === "UNTRUSTED") && <div className="dashboard-alert warning"><AlertTriangle size={18} /><span>{statusText(attributionReport.status)} · população e integridade devem ser interpretadas com a definição de cálculo.</span></div>}
          <QualityReport attribution={attributionReport} reconciliation={reconciliationReport} />
        </div> : area === "social" && socialReport && publisherReport ? <div className="dashboard-card wide">
          <div className="dashboard-card-heading"><div><strong>SO-01/SO-03/SO-05/SO-06 · Social e Publisher</strong><p>Agregados reais dos bounded contexts; acesso por obra/editora ainda não é exposto neste snapshot global.</p></div><AlertTriangle size={18} /></div>
          <SocialReport social={socialReport} publisher={publisherReport} />
        </div> : <div className="dashboard-card dashboard-gap">
          <div className="dashboard-card-heading"><div><strong>{unavailablePanels[area][0]}</strong><p>Esta área está conectada ao hub, mas as fontes específicas ainda não foram materializadas.</p></div><AlertTriangle size={18} /></div>
          <div className="dashboard-alert warning"><AlertTriangle size={18} /><span><strong>NOT_INSTRUMENTED</strong><br />{unavailablePanels[area][1]}. Não exibimos gráficos vazios nem zeros.</span></div>
        </div>}
        <div className="dashboard-card"><div className="dashboard-card-heading"><div><strong>Freshness e lineage</strong><p>Estado da projeção usada por esta área.</p></div></div>
          {health.length ? <div className="projection-list">{health.map((item) => <div key={String(item.projection_name)}><span>{String(item.projection_name)}</span><b>{String(item.refresh_status)} · {String(item.definition_version)}</b></div>)}</div> : <div className="dashboard-state">NO_DATA</div>}
        </div>
      </>}
    </section>
  );
}
