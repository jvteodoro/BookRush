import { useMemo } from "react";
import { AlertTriangle, CircleHelp } from "lucide-react";
import {
  Bar, BarChart, CartesianGrid, Funnel, FunnelChart, Line, LineChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import type { ReportingResponse, ReportingSeries } from "../../../lib/services";

export type DashboardData = Record<string, ReportingResponse | Record<string, unknown>[]>;
export type MetricCard = { label: string; source: string; field: string; unit: string; aggregate?: "sum" | "first"; previousField?: string; numeratorField?: string; denominatorField?: string; format?: "percent" };
export type ChartSpec = { title: string; source: string; fields: [string, string][]; kind?: "line" | "funnel" | "bar" | "heatmap"; subtitle?: string };
type Props = { title: string; question: string; period: string; data: DashboardData; cards: MetricCard[]; charts: ChartSpec[]; details: string[] };
type Insight = { key: string; problem: string; severity: "CRITICAL" | "WARNING" | "INFO"; evidence: string; comparison: string; sample: number | string; href: string };
const isReport = (value: DashboardData[string]): value is ReportingResponse => !Array.isArray(value);
const display = (value: unknown) => typeof value === "number" ? new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(value) : value == null ? "—" : String(value);
const total = (rows: ReportingSeries[], field: string) => {
  const values = rows.map(row => row[field]).filter((value): value is number => typeof value === "number");
  return values.length === rows.length && values.length ? values.reduce((sum, value) => sum + value, 0) : null;
};
const periodRate = (rows: ReportingSeries[], numerator: string, denominator: string, from: number, to: number) => {
  const group = new Map<string, ReportingSeries[]>();
  for (const row of rows) if (row.day) group.set(row.day, [...(group.get(row.day) ?? []), row]);
  const days = [...group.keys()].sort();
  const selected = days.slice(from, to).flatMap(day => group.get(day) ?? []);
  const n = total(selected, numerator); const d = total(selected, denominator);
  return { rate: n != null && d != null && d > 0 ? n / d : null, sample: d ?? 0, numerator: n ?? 0 };
};

function detectInsights(data: DashboardData): Insight[] {
  const found: Insight[] = [];
  for (const [name, value] of Object.entries(data)) {
    if (!isReport(value)) {
      if (name === "health") {
        const failed = value.filter(row => !["SUCCESS", "OK", "HEALTHY", "AVAILABLE"].includes(String(row.refresh_status ?? "").toUpperCase()));
        if (failed.length) found.push({ key: "projection-health", problem: `${failed.length} projeção(ões) indisponível(is) ou com erro.`, severity: "CRITICAL", evidence: `${failed.length} de ${value.length} projeções fora de SUCCESS`, comparison: "Estado atual; atividade de negócio é avaliada separadamente", sample: failed.slice(0, 3).map(row => row.projection_name).join(", "), href: "#dashboard-details" });
      }
      continue;
    }
    if (value.status === "ERROR" || value.status === "STALE" || value.status === "UNKNOWN_FRESHNESS") {
      found.push({ key: name, problem: value.status === "ERROR" ? `A fonte ${name} está indisponível.` : `A atualização de ${name} exige investigação.`, severity: value.status === "ERROR" ? "CRITICAL" : "WARNING", evidence: `${value.status}; ${value.series.length} linhas no período`, comparison: "Comparação bloqueada por confiabilidade insuficiente", sample: value.series.length, href: "#dashboard-details" });
    } else if (value.status === "NO_DATA") {
      found.push({ key: name, problem: `A fonte ${name} não tem dados neste período.`, severity: "INFO", evidence: "Nenhuma linha retornada; zero não foi presumido", comparison: "Sem base para comparação", sample: 0, href: "#dashboard-details" });
    }
  }
  const feed = data.feed;
  if (feed && isReport(feed) && feed.status === "OK" && feed.series.length) {
    const rows = feed.series;
    const n = new Set(rows.map(row => row.day).filter(Boolean)).size;
    if (n >= 28) {
      const before = periodRate(rows, "mature_attributed_open_count", "eligible_viewable_count", Math.max(0, n - 28), n - 14);
      const current = periodRate(rows, "mature_attributed_open_count", "eligible_viewable_count", n - 14, n);
      if (before.rate != null && current.rate != null && before.sample >= 50 && current.sample >= 50 && current.rate < before.rate * 0.85)
        found.push({ key: "conversion-drop", problem: "A conversão de impressão viewable em abertura caiu.", severity: "WARNING", evidence: `${(before.rate * 100).toFixed(1)}% → ${(current.rate * 100).toFixed(1)}%; amostras ${before.sample} e ${current.sample}`, comparison: "14 dias atuais contra 14 dias anteriores; coortes maduras de 7 dias", sample: current.sample, href: "#dashboard-details" });
    }
  }
  const daily = data.daily;
  if (daily && isReport(daily) && daily.status === "OK" && daily.series.length) {
    const rows = daily.series; const n = new Set(rows.map(row => row.day).filter(Boolean)).size;
    if (n >= 28) {
      const previous = periodRate(rows, "repeat_viewable_count", "viewable_count", Math.max(0, n - 28), n - 14);
      const current = periodRate(rows, "repeat_viewable_count", "viewable_count", n - 14, n);
      if (previous.rate != null && current.rate != null && previous.sample >= 50 && current.sample >= 50 && current.rate - previous.rate >= 0.10)
        found.push({ key: "repetition-rise", problem: "A proporção de impressões repetidas aumentou.", severity: "WARNING", evidence: `${(previous.rate * 100).toFixed(1)}% → ${(current.rate * 100).toFixed(1)}%; amostras ${previous.sample} e ${current.sample}`, comparison: "14 dias atuais contra 14 dias anteriores", sample: current.sample, href: "#dashboard-details" });
      const paths = rows.filter(row => /fallback|degrad/i.test(String(row.feed_path ?? "")));
      const currentDays = [...new Set(rows.map(row => row.day).filter((day): day is string => typeof day === "string"))].sort().slice(n - 14);
      const previousDays = [...new Set(rows.map(row => row.day).filter((day): day is string => typeof day === "string"))].sort().slice(Math.max(0, n - 28), n - 14);
      const previousFallbackCount = total(paths.filter(row => previousDays.includes(String(row.day))), "request_count");
      const prevTotal = total(rows.filter(row => previousDays.includes(String(row.day))), "request_count");
      const currentTotal = total(rows.filter(row => currentDays.includes(String(row.day))), "request_count");
      const currentFallbackCount = total(paths.filter(row => currentDays.includes(String(row.day))), "request_count");
      const previousRate = previousFallbackCount != null && prevTotal != null && prevTotal > 0 ? previousFallbackCount / prevTotal : null;
      const currentRate = currentFallbackCount != null && currentTotal != null && currentTotal > 0 ? currentFallbackCount / currentTotal : null;
      if (previousRate != null && currentRate != null && prevTotal != null && currentTotal != null && prevTotal >= 100 && currentTotal >= 100 && currentRate - previousRate >= 0.10)
        found.push({ key: "fallback-rise", problem: "A participação de fallback técnico aumentou.", severity: "WARNING", evidence: `${(previousRate * 100).toFixed(1)}% → ${(currentRate * 100).toFixed(1)}%; ${previousFallbackCount}/${prevTotal} → ${currentFallbackCount}/${currentTotal} requests`, comparison: "14 dias atuais contra os 14 dias anteriores", sample: currentTotal, href: "#dashboard-details" });
    }
  }
  const queue = data.queue;
  if (queue && isReport(queue) && queue.status === "OK" && queue.series.length >= 2) {
    const rows = [...queue.series].sort((a, b) => String(a.collected_at ?? "").localeCompare(String(b.collected_at ?? "")));
    const firstAt = rows[0].collected_at; const lastAt = rows[rows.length - 1]?.collected_at;
    const first = total(rows.filter(row => row.collected_at === firstAt), "task_count");
    const last = total(rows.filter(row => row.collected_at === lastAt), "task_count");
    if (first != null && last != null && last > first)
      found.push({ key: "backlog-growing", problem: "O backlog da fila cresceu no período observado.", severity: "WARNING", evidence: `${first} → ${last} itens pendentes`, comparison: `${firstAt ?? "snapshot inicial"} contra ${lastAt ?? "snapshot atual"}`, sample: last, href: "#dashboard-details" });
  }
  const phases = data.phases;
  if (phases && isReport(phases) && phases.status === "OK" && phases.series.length) {
    const stuck = total(phases.series, "stuck_count");
    if (stuck != null && stuck > 0)
      found.push({ key: "workers-no-progress", problem: "Há jobs marcados como travados ou com lease expirado.", severity: "CRITICAL", evidence: `${stuck} itens com stuck_count na fonte consolidada`, comparison: "Estado do período; a regra usa heartbeat/lease quando disponível", sample: stuck, href: "#dashboard-details" });
  }
  return found;
}

export function DashboardAreaPage({ title, question, period, data, cards, charts, details }: Props) {
  const insights = useMemo(() => detectInsights(data), [data]);
  return <main className="dashboard-hub dashboard-v2">
    <header className="pagehead dashboard-v2-heading"><div><span className="eyebrow">CENTRO DE CONTROLE · {period}</span><h1>{title}</h1><p>{question}</p></div></header>
    <section aria-label="Indicadores principais" className="dashboard-v2-kpis">
      {cards.map(card => {
        const report = data[card.source]; const rows: ReportingSeries[] = report ? (isReport(report) ? report.series : report as ReportingSeries[]) : [];
        const healthRows = card.source === "health" && Array.isArray(rows) ? rows : [];
        const healthState = (row: ReportingSeries) => String(row.refresh_status ?? "").toUpperCase();
        const metricValue = (sourceRows: ReportingSeries[]) => card.numeratorField && card.denominatorField
          ? (() => { const numerator = total(sourceRows, card.numeratorField!); const denominator = total(sourceRows, card.denominatorField!); return numerator != null && denominator != null && denominator > 0 ? numerator / denominator : null; })()
          : card.aggregate === "first" ? sourceRows[0]?.[card.field] : total(sourceRows, card.field);
        const raw = healthRows.length && card.field === "healthy_count" ? healthRows.filter(row => ["SUCCESS", "OK", "HEALTHY", "AVAILABLE"].includes(healthState(row))).length
          : healthRows.length && card.field === "stale_count" ? healthRows.filter(row => ["STALE", "DELAYED"].includes(healthState(row))).length
          : healthRows.length && card.field === "unhealthy_count" ? healthRows.filter(row => !["SUCCESS", "OK", "HEALTHY", "AVAILABLE"].includes(healthState(row))).length
          : healthRows.length && card.field === "incident_count" ? healthRows.filter(row => healthState(row) === "ERROR").length
          : metricValue(rows);
        const previousRaw = card.previousField && rows[0] ? rows[0][card.previousField] : null;
        const dateKeys = [...new Set(rows.map(row => row.day).filter((day): day is string => typeof day === "string"))].sort();
        const cut = Math.floor(dateKeys.length / 2);
        const periodSum = (keys: string[]) => total(rows.filter(row => keys.includes(String(row.day))), card.field);
        const previousByPeriod = !card.previousField && dateKeys.length >= 4 ? (card.numeratorField && card.denominatorField ? metricValue(rows.filter(row => dateKeys.slice(0, cut).includes(String(row.day)))) : periodSum(dateKeys.slice(0, cut))) : null;
        const currentByPeriod = !card.previousField && dateKeys.length >= 4 ? (card.numeratorField && card.denominatorField ? metricValue(rows.filter(row => dateKeys.slice(cut).includes(String(row.day)))) : periodSum(dateKeys.slice(cut))) : null;
        const previous = typeof previousRaw === "number" ? previousRaw : previousByPeriod;
        const current = currentByPeriod ?? raw;
        const value = card.unit === "minutos" && typeof current === "number" ? current / 60 : current;
        const previousValue = card.unit === "minutos" && typeof previous === "number" ? previous / 60 : previous;
        const shownValue = card.format === "percent" && typeof value === "number" ? `${(value * 100).toFixed(1)}%` : display(value);
        const shownPrevious = card.format === "percent" && typeof previousValue === "number" ? `${(previousValue * 100).toFixed(1)}%` : display(previousValue);
        const comparison = typeof value === "number" && typeof previousValue === "number"
          ? `${shownPrevious} → ${shownValue} vs. período anterior${previousValue ? ` (${((value - previousValue) / Math.abs(previousValue) * 100).toFixed(1)}%)` : " (base anterior zero)"}`
          : "comparação anterior indisponível";
        return <article className="dashboard-v2-kpi" key={card.label}><span>{card.label}</span><strong>{shownValue}<small>{card.unit}</small></strong><footer>{period} · {comparison} · referência não definida</footer></article>;
      })}
    </section>
    <section className="dashboard-v2-insights" aria-label="Problemas e oportunidades"><h2>O que exige atenção?</h2>
      {insights.length ? insights.map(item => <article className={`dashboard-v2-insight ${item.severity.toLowerCase()}`} key={item.key}><AlertTriangle size={18}/><div><strong>{item.problem}</strong><p>Severidade: {item.severity} · Evidência: {item.evidence} · Comparação: {item.comparison} · Amostra: {item.sample}</p></div><a href={item.href}>Investigar</a></article>) : <p className="dashboard-v2-empty"><CircleHelp size={17}/> Dados insuficientes ou sem alerta determinístico neste período.</p>}
    </section>
    <section className="dashboard-v2-charts" aria-label="Gráficos explicativos">
      {charts.map(chart => {
        const source = data[chart.source]; const rows: ReportingSeries[] = source ? (isReport(source) ? [...source.series].reverse().filter(row => row.day) : [...source as ReportingSeries[]].reverse()) : [];
        const heatRows = source && isReport(source) && chart.kind === "heatmap" ? source.series.filter(row => row.mature) : [];
        const barRows = chart.kind === "bar" && source ? (isReport(source) ? source.series.filter(row => row.aggregation_level === "BOOK").reduce<ReportingSeries[]>((acc,row) => acc.some(item => item.aggregation_key === row.aggregation_key) ? acc : [...acc,row], []).sort((a,b) => Number(a.entity_rank ?? 999)-Number(b.entity_rank ?? 999)).slice(0,10) : rows) : [];
        const funnelRows = source && isReport(source) && chart.kind === "funnel" ? chart.fields.map(([field, label]) => ({ name: label, value: total(source.series, field) })).filter(row => row.value != null) : [];
        return <article className="dashboard-card dashboard-v2-chart" key={chart.title}><div className="dashboard-card-heading"><div><strong>{chart.title}</strong><p>{chart.subtitle ?? (chart.kind === "funnel" ? "Impressões únicas em coorte madura; attribution válida" : chart.kind === "heatmap" ? "Coortes de leitores com sessão significativa ≥60s" : rows.length ? "Série da projeção selecionada" : "Série indisponível nesta fonte")}</p></div></div>
          {chart.kind === "funnel" && funnelRows.length ? <><ResponsiveContainer width="100%" height={280}><FunnelChart><Tooltip/><Funnel dataKey="value" data={funnelRows} isAnimationActive={false} fill="#35b99b"/></FunnelChart></ResponsiveContainer><ol className="dashboard-v2-funnel-loss">{funnelRows.map((row,index) => {const prev=funnelRows[index-1]; const lost=prev?.value != null && row.value != null ? prev.value-row.value : null; const rate=prev?.value ? lost! / prev.value : null; return <li key={row.name}><span>{row.name}: {display(row.value)}</span>{prev && <small>Perda {display(lost)} ({rate == null ? "—" : `${(rate*100).toFixed(1)}%`})</small>}</li>;})}</ol></> : chart.kind === "funnel" ? <p className="dashboard-v2-no-data">A fonte ainda não fornece valores maduros completos para esta etapa; nenhuma etapa foi estimada.</p> : chart.kind === "heatmap" && heatRows.length ? <div className="dashboard-v2-heatmap" role="table" aria-label="Retenção por coorte">{heatRows.map((row,i) => { const rate=Number(row.cohort_users)>0 ? Number(row.retained_users)/Number(row.cohort_users) : 0; return <div role="row" key={`${row.cohort_day}-${row.offset_day}-${i}`} title={`${row.cohort_day} D${row.offset_day}: ${row.retained_users}/${row.cohort_users}`} style={{backgroundColor:`rgba(53,185,155,${Math.max(.08,rate)})`}}><span>{row.cohort_day} · D{row.offset_day}</span><strong>{row.mature ? `${(rate*100).toFixed(1)}%` : "Imatura"}</strong></div>;})}</div> : chart.kind === "heatmap" ? <p className="dashboard-v2-no-data">Sem coortes maduras disponíveis.</p> : chart.kind === "bar" && chart.source === "health" && barRows.length ? <div className="dashboard-v2-health-list">{barRows.map(row => { const status=String(row.refresh_status ?? "UNKNOWN").toUpperCase(); return <div key={String(row.projection_name)}><span><strong>{String(row.projection_name ?? "Projeção")}</strong><small className={status === "ERROR" ? "error" : status === "SUCCESS" || status === "AVAILABLE" ? "healthy" : "stale"}>{status}</small></span><small>{display(row.record_count)} registros · watermark {display(row.source_watermark)}</small></div>; })}</div> : chart.kind === "bar" && barRows.length ? <ResponsiveContainer width="100%" height={280}><BarChart data={barRows} layout="vertical" margin={{left:24,right:24}}><CartesianGrid stroke="#26334a" strokeDasharray="3 3"/><XAxis type="number" dataKey={chart.fields[0]?.[0]} stroke="#8290a8"/><YAxis type="category" dataKey={barRows[0]?.book_title ? "book_title" : "projection_name"} width={150} stroke="#8290a8"/><Tooltip/><Bar dataKey={chart.fields[0]?.[0]} name={chart.fields[0]?.[1]} fill="#36b99b"/></BarChart></ResponsiveContainer> : chart.kind === "bar" ? <p className="dashboard-v2-no-data">Sem livros com amostra suficiente; ausência não representa zero.</p> : rows.length ? <ResponsiveContainer width="100%" height={240}><LineChart data={rows}><CartesianGrid stroke="#26334a" strokeDasharray="3 3"/><XAxis dataKey="day" stroke="#8290a8"/><YAxis stroke="#8290a8"/><Tooltip/><>{chart.fields.map(([field,label],i)=><Line key={field} type="monotone" dataKey={field} name={label} stroke={["#36c9a5","#8a7dff","#e6a84b"][i%3]} dot={false} connectNulls={false}/>)}</></LineChart></ResponsiveContainer> : <p className="dashboard-v2-no-data">Sem dados para plotar; ausência de dados não equivale a zero.</p>}
        </article>;
      })}
    </section>
    <details className="dashboard-card dashboard-v2-details" id="dashboard-details"><summary>Detalhes sob demanda</summary>{details.map(key=>{const value=data[key];if(!value)return null;const rows=isReport(value)?value.series:value;return <section key={key}><h3>{key}</h3><pre>{JSON.stringify(rows,null,2)}</pre></section>;})}</details>
  </main>;
}
