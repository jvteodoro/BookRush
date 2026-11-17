import { DashboardAreaPage } from "./DashboardAreaPage";
import type { DashboardData } from "./DashboardAreaPage";
export function BusinessOverview({ data }: { data: DashboardData }) { return <DashboardAreaPage title="Business Overview" question="A descoberta está se convertendo em leitura significativa?" period="Últimos 7 dias · população selecionável" data={data} cards={[
 {label:"Leitores engajados",source:"readers",field:"weekly_significant_readers",unit:"leitores únicos",aggregate:"first",previousField:"previous_week_significant_readers"},{label:"Minutos ativos",source:"readers",field:"weekly_active_seconds",unit:"minutos",previousField:"previous_week_active_seconds"},{label:"Sessões",source:"readers",field:"weekly_sessions",unit:"sessões",previousField:"previous_week_sessions"},{label:"Livros iniciados",source:"readers",field:"weekly_books_started",unit:"pares leitor-livro únicos",previousField:"previous_week_books_started"},
]} charts={[
 {title:"Funil de leitura significativa",source:"funnel",kind:"funnel",subtitle:"Exposições maduras dos últimos 30 dias; cada outcome teve 7 dias para ocorrer",fields:[["mature_served_count","Excerpts servidos"],["eligible_viewable_count","Viewable"],["mature_attributed_open_count","Livros abertos"],["mature_attributed_significant_read_count","Leitura ≥60s"],["mature_attributed_complete_count","Conclusão"]]},
 {title:"Leitores ativos ao longo do tempo",source:"reading",fields:[["unique_readers","Leitores únicos"]]},
 {title:"Retenção por coorte",source:"retention",kind:"heatmap",fields:[["retained_users","Leitores retidos"]]},
 {title:"Livros com mais tempo ativo",source:"books",kind:"bar",fields:[["period_active_minutes","Minutos ativos"]]},
]} details={["readers","reading","retention","books","funnel"]}/>; }
