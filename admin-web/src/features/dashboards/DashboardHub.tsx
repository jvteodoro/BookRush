import { useQuery } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { services } from "../../lib/services";
import { AnalyticsFactory } from "./pages/AnalyticsFactory";
import { BusinessOverview } from "./pages/BusinessOverview";
import { ContentIntelligence } from "./pages/ContentIntelligence";
import { DataQuality } from "./pages/DataQuality";
import { Operations } from "./pages/Operations";
import { RecommendationIntelligence } from "./pages/RecommendationIntelligence";
import type { DashboardData } from "./pages/DashboardAreaPage";

const areas = [["business", "Business Overview"], ["operations", "Operations"], ["recommendations", "Recommendation Intelligence"], ["content", "Content Intelligence"], ["factory", "Analytics Factory"], ["quality", "Data Quality"]] as const;
const daysByArea: Record<string, number> = { business: 7, operations: 1, recommendations: 30, content: 30, factory: 1, quality: 30 };
async function fetchArea(area: string, population = "PRODUCT"): Promise<DashboardData> {
  const days = daysByArea[area] ?? 7;
  switch (area) {
    case "business": {
      const [readers, reading, retention, books, funnel] = await Promise.all([services.reportingWeeklySignificantReaders(population), services.reportingReadingDaily(7, population), services.reportingRetentionCohort(366, population), services.reportingBookEngagementTrends(7, 1, 10, population), services.reportingFeedDaily(30, population)]);
      return { readers, reading, retention, books, funnel };
    }
    case "operations": return { health: await services.reportingHealth(), runtime: [], journeys: [] };
    case "recommendations": {
      const [feed, daily, quality, position, funnel, excerpts] = await Promise.all([services.reportingFeedDaily(days), services.reportingRecommendationDaily(days), services.reportingRecommendationQualityDaily(days), services.reportingRecommendationPositionDaily(days), services.reportingRecommendationPositionFunnelDaily(days), services.reportingExcerptPerformance(days)]);
      return { feed, daily, quality, position, funnel, excerpts };
    }
    case "content": {
      const [supply, rights, coverage, rank, excerpts] = await Promise.all([services.reportingContentSupply(days), services.reportingRightsStatusDaily(days), services.reportingContentCoverage(days), services.reportingContentRankScores(days), services.reportingExcerptProduction(days)]);
      return { supply, rights, coverage, rank, excerpts };
    }
    case "factory": {
      const [backlog, queue, phases, ingestion, jobs] = await Promise.all([services.reportingBacklog(days), services.reportingIngestionQueue(days), services.reportingPipelinePhases(days), services.reportingIngestionDaily(days), services.reportingPipeline(days)]);
      return { backlog, queue, phases, ingestion, jobs };
    }
    default: {
      const [health, events, attribution, reconciliation, audit] = await Promise.all([services.reportingHealth(), services.reportingEventQuality(days), services.reportingAttributionQuality(days), services.reportingReconciliationQuality(days), services.reportingAccessAudit(days)]);
      return { health, events, attribution, reconciliation, audit };
    }
  }
}
export function DashboardHub() {
  const [params, setParams] = useSearchParams();
  const requested = params.get("area") ?? "business";
  const area = areas.some(([key]) => key === requested) ? requested : "business";
  const population = params.get("population") ?? "PRODUCT";
  const query = useQuery({ queryKey: ["dashboard-area", area, population], queryFn: () => fetchArea(area, population), staleTime: 15_000 });
  const pageProps = { data: query.data ?? {} };
  return <section className="dashboard-hub dashboard-v2-shell">
    <header className="pagehead"><div><span className="eyebrow">BOOKRUSH · CENTRO DE CONTROLE</span><h1>Dashboards administrativos</h1><p>Indicadores de negócio, recomendações, conteúdo, processamento e qualidade de dados.</p></div><button className="button secondary" type="button" onClick={() => void query.refetch()} disabled={query.isFetching}><RefreshCw size={15}/> Atualizar</button></header>
    <nav className="dashboard-tabs" aria-label="Áreas dos dashboards">{areas.map(([key, label]) => <button key={key} type="button" className={key === area ? "active" : ""} aria-current={key === area ? "page" : undefined} onClick={() => setParams(key === "business" ? { area: key, population } : { area: key })}>{label}</button>)}</nav>
    {area === "business" && <label className="dashboard-v2-population">População <select value={population} onChange={event => setParams({ area, population: event.target.value })}><option value="PRODUCT">PRODUCT</option><option value="ADMIN_SEED">ADMIN_SEED</option><option value="TEST">TEST</option><option value="UNKNOWN">UNKNOWN</option></select></label>}
    {query.isPending ? <div className="dashboard-state" role="status">Carregando esta área…</div> : query.isError ? <div className="dashboard-state" role="alert">Não foi possível carregar os dados desta área. {query.error instanceof Error ? query.error.message : "Tente novamente."}</div> : <>
      {area === "business" && <BusinessOverview {...pageProps}/>}{area === "operations" && <Operations {...pageProps}/>}{area === "recommendations" && <RecommendationIntelligence {...pageProps}/>}{area === "content" && <ContentIntelligence {...pageProps}/>}{area === "factory" && <AnalyticsFactory {...pageProps}/>}{area === "quality" && <DataQuality {...pageProps}/>}
    </>}
  </section>;
}
