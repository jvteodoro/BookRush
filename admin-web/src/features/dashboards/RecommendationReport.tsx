import { lazy, Suspense } from "react";
import type { ReportingResponse } from "../../lib/services";
import { EmptyReportState } from "./ReportingStates";

const ProfileQualityReport = lazy(() => import("./ProfileQualityReport"));

export default function RecommendationReport({
  data,
  profileQuality,
  quality,
  position,
  positionFunnel,
}: {
  data: ReportingResponse;
  profileQuality: ReportingResponse;
  quality: ReportingResponse;
  position: ReportingResponse;
  positionFunnel: ReportingResponse;
}) {
  if (!data.series.length && !profileQuality.series.length)
    return <EmptyReportState data={data} />;
  return (
    <div className="dashboard-scroll-table">
      <table className="dashboard-table">
        <caption>
          FD-05/08/09 · ledger por estratégia e caminho; comparação
          observacional, sem inferência causal; plataforma {data.population};
          versão {data.definitionVersion}
        </caption>
        <thead>
          <tr>
            <th>Dia</th>
            <th>Modelo</th>
            <th>Estratégia</th>
            <th>Caminho</th>
            <th>Plataforma</th>
            <th>Requests</th>
            <th>Candidatos recuperados (pool limitado)</th>
            <th>Com score Analytics</th>
            <th>Selecionados pelo ranking</th>
            <th>Itens</th>
            <th>Impressões</th>
            <th>Viewable</th>
            <th>Repetidas (30d)</th>
            <th>Taxa de repetição</th>
            <th>Livros únicos viewable</th>
            <th>Livros</th>
            <th>P50 rank</th>
            <th>P90 rank</th>
          </tr>
        </thead>
        <tbody>
          {data.series.slice(-60).map((row, index) => (
            <tr
              key={`${row.day}-${row.strategy}-${row.model_version}-${row.feed_path}-${row.platform}-${index}`}
            >
              <td>{row.day ?? "—"}</td>
              <td>{row.model_version ?? "—"}</td>
              <td>{row.strategy ?? "—"}</td>
              <td>{row.feed_path ?? "—"}</td>
              <td>{row.platform ?? "UNKNOWN"}</td>
              <td>{row.request_count ?? "—"}</td>
              <td>{row.candidate_count ?? "—"}</td>
              <td>{row.scored_candidate_count ?? "—"}</td>
              <td>{row.selected_count ?? "—"}</td>
              <td>{row.item_count ?? "—"}</td>
              <td>{row.impression_count ?? "—"}</td>
              <td>{row.viewable_count ?? "—"}</td>
              <td>{row.repeat_viewable_count ?? "—"}</td>
              <td>
                {row.viewable_count
                  ? `${(((row.repeat_viewable_count ?? 0) / row.viewable_count) * 100).toFixed(1)}%`
                  : "—"}
              </td>
              <td>{row.viewable_unique_books ?? "—"}</td>
              <td>{row.unique_books ?? "—"}</td>
              <td>{row.p50_rank == null ? "—" : Math.round(row.p50_rank)}</td>
              <td>{row.p90_rank == null ? "—" : Math.round(row.p90_rank)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="dashboard-note">
        Candidatos recuperados são o pool limitado entregue ao ranking por
        request; não representam o total de livros elegíveis do catálogo. A
        cobertura do universo elegível não está disponível nesta fonte.
      </p>
      {profileQuality.series.length > 0 && (
        <Suspense
          fallback={<p role="status">Carregando qualidade do perfil…</p>}
        >
          <ProfileQualityReport data={profileQuality} />
        </Suspense>
      )}
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
      {positionFunnel.series.length > 0 && (
        <table className="dashboard-table">
          <caption>
            FD-09 · CTR viewable por posição; coortes maduras de 7 dias;
            população {positionFunnel.population}; versão{" "}
            {positionFunnel.definitionVersion}
          </caption>
          <thead>
            <tr>
              <th>Dia</th>
              <th>Estratégia</th>
              <th>Modelo</th>
              <th>Bucket</th>
              <th>Impressões</th>
              <th>Viewable</th>
              <th>Elegíveis (7d)</th>
              <th>Aberturas atribuídas</th>
              <th>CTR (7d)</th>
            </tr>
          </thead>
          <tbody>
            {positionFunnel.series.slice(-60).map((row, index) => {
              const eligible = row.eligible_viewable_count ?? 0;
              const opened = row.mature_attributed_open_count ?? 0;
              return (
                <tr
                  key={`${row.day}-${row.strategy}-${row.model_version}-${row.rank_bucket}-${index}`}
                >
                  <td>{row.day ?? "—"}</td>
                  <td>{row.strategy ?? "—"}</td>
                  <td>{row.model_version ?? "—"}</td>
                  <td>{row.rank_bucket ?? "—"}</td>
                  <td>{row.impression_count ?? "—"}</td>
                  <td>{row.viewable_count ?? "—"}</td>
                  <td>{row.eligible_viewable_count ?? "—"}</td>
                  <td>{row.attributed_open_count ?? "—"}</td>
                  <td>
                    {eligible
                      ? `${((opened / eligible) * 100).toFixed(1)}%`
                      : "—"}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
      {position.series.length > 0 && (
        <table className="dashboard-table">
          <caption>
            FD-09 · Outcomes atribuídos por posição; sem inferência causal
          </caption>
          <thead>
            <tr>
              <th>Dia</th>
              <th>Estratégia</th>
              <th>Bucket</th>
              <th>Outcome</th>
              <th>Atribuídos</th>
              <th>Usuários</th>
              <th>Livros</th>
            </tr>
          </thead>
          <tbody>
            {position.series.slice(-60).map((row, index) => (
              <tr
                key={`${row.day}-${row.strategy}-${row.rank_bucket}-${row.outcome_type}-${index}`}
              >
                <td>{row.day ?? "—"}</td>
                <td>{row.strategy ?? "—"}</td>
                <td>{row.rank_bucket ?? "—"}</td>
                <td>{row.outcome_type ?? "—"}</td>
                <td>{row.attributed_count ?? "—"}</td>
                <td>{row.unique_subjects ?? "—"}</td>
                <td>{row.unique_books ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
