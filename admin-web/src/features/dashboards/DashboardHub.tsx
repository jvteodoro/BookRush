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
            <Line type="monotone" dataKey="sessions" name="Sessões" stroke="#7fa8ff" dot={false} connectNulls={false} />
            <Line type="monotone" dataKey="meaningfulReads" name="Leituras significativas" stroke="#f6c76d" dot={false} connectNulls={false} />
          </LineChart>
        </ResponsiveContainer>
      </div>
      <table className="dashboard-table">
        <caption>Valores reais do snapshot; população {data.population}</caption>
        <thead><tr><th>Dia</th><th>Ativos</th><th>Sessões</th><th>Leituras ≥60s</th></tr></thead>
        <tbody>{data.series.slice(-14).map((row) => <tr key={row.day}>
          <td>{row.day}</td><td>{row.active_users ?? "—"}</td><td>{row.sessions ?? "—"}</td><td>{row.meaningful_reads ?? "—"}</td>
        </tr>)}</tbody>
      </table>
    </>
  );
}

export function DashboardHub() {
  const [params, setParams] = useSearchParams();
  const active = params.get("area") ?? "executive";
  const days = Number(params.get("days") ?? "30");
  const [report, setReport] = useState<ReportingResponse | null>(null);
  const [health, setHealth] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const area = areas.some(([key]) => key === active) ? active : "executive";

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [readerDay, projectionHealth] = await Promise.all([
        services.reportingReaderDay(Math.min(Math.max(days, 1), 366)),
        services.reportingHealth(),
      ]);
      setReport(readerDay);
      setHealth(projectionHealth);
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
          <span className={`data-badge ${report?.status === "UNTRUSTED" ? "warning" : ""}`}>{statusText(report?.status ?? "NO_DATA")}</span></div>
        {area === "executive" || area === "growth" ? <div className="dashboard-card wide">
          <div className="dashboard-card-heading"><div><strong>{area === "growth" ? "GR-01..GR-07 · Atividade e leitura" : "EX-01/04 · Atividade geral"}</strong><p>Agregado diário do behavior-service; ausência de população classificada impede interpretação como produto.</p></div><AlertTriangle size={18} /></div>
          {report && <ReadingChart data={report} />}
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
