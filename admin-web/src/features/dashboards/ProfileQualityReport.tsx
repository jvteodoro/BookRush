import type { ReportingResponse } from "../../lib/services";

export default function ProfileQualityReport({
  data,
}: {
  data: ReportingResponse;
}) {
  if (!data.series.length) return null;
  return (
    <table className="dashboard-table">
      <caption>
        FD-12 · buckets de confiança/tamanho do perfil, candidatos com exposição
        recente e requests cold-start; somente contagens agregadas; população{" "}
        {data.population}; versão {data.definitionVersion}
      </caption>
      <thead>
        <tr>
          <th>Dia</th>
          <th>Estratégia</th>
          <th>Caminho</th>
          <th>Plataforma</th>
          <th>Confiança</th>
          <th>Tamanho do perfil</th>
          <th>Cobertura de fadiga</th>
          <th>Cold-start</th>
          <th>Requests</th>
        </tr>
      </thead>
      <tbody>
        {data.series.slice(-60).map((row, index) => (
          <tr
            key={`${row.day}-${row.strategy}-${row.profile_size_bucket}-${row.fatigue_coverage_bucket}-${row.platform}-${index}`}
          >
            <td>{row.day ?? "—"}</td>
            <td>{row.strategy ?? "—"}</td>
            <td>{row.feed_path ?? "—"}</td>
            <td>{row.platform ?? "UNKNOWN"}</td>
            <td>{row.confidence_bucket ?? "—"}</td>
            <td>{row.profile_size_bucket ?? "—"}</td>
            <td>{row.fatigue_coverage_bucket ?? "—"}</td>
            <td>
              {row.cold_start == null ? "—" : row.cold_start ? "Sim" : "Não"}
            </td>
            <td>{row.request_count ?? "—"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
