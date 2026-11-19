import { config } from "../../../lib/config";
import { DashboardAreaPage } from "./DashboardAreaPage";
import type { DashboardData } from "./DashboardAreaPage";

const technicalDashboards = [
  ["Service Detail", "bookrush-service-detail"],
  ["PostgreSQL", "bookrush-postgresql"],
  ["JVM & Containers", "bookrush-jvm-containers"],
  ["Gateway & Keycloak", "bookrush-gateway-keycloak"],
  ["Storage & Redis", "bookrush-storage-redis"],
  ["Logs & Traces", "bookrush-logs-traces"],
];

export function Operations({ data }: { data: DashboardData }) {
  return <>
    <DashboardAreaPage title="Operations" question="Quais componentes ou jornadas exigem investigação agora?" period="Saúde atual · Grafana nas últimas 6 horas" data={data} cards={[{ label: "Componentes disponíveis", source: "health", field: "healthy_count", unit: "projeções" }, { label: "Componentes degradados", source: "health", field: "unhealthy_count", unit: "projeções" }, { label: "Incidentes ativos", source: "health", field: "incident_count", unit: "incidentes" }]} charts={[{ title: "Estado das projeções", source: "health", fields: [["record_count", "Registros"]], kind: "bar" }]} details={["health"]} />
    <nav className="dashboard-v2-grafana-links" aria-label="Dashboards técnicos no Grafana">
      <h2>Investigar componentes no Grafana</h2>
      <div className="dashboard-v2-grafana-grid">{technicalDashboards.map(([label, uid]) => <a className="dashboard-card" key={uid} href={`${config.observability.grafanaUrl}/d/${uid}`} target="_blank" rel="noreferrer">{label} ↗</a>)}</div>
    </nav>
  </>;
}
