import { createHttp } from "../../lib/http";
import { config } from "../../lib/config";

export type Submission = {
  id: string;
  title: string;
  status: string;
  catalog_book_id?: string | null;
};
export const metricLabels = {
  impressions: "Impressões",
  opens: "Aberturas",
  reads: "Leituras",
  likes: "Curtidas",
  shares: "Compartilhamentos",
  comments: "Comentários",
} as const;
export type Metric = keyof typeof metricLabels;
export type MetricRow = { day: string } & Record<Metric, number>;
export type PublicationMetrics = {
  status: "AVAILABLE" | "NOT_LINKED";
  days: number;
  items: MetricRow[];
};
const http = createHttp(config.apis.admin, () => undefined);
export const publisherApi = {
  async submissions(signal?: AbortSignal): Promise<Submission[]> {
    return (await http.get("/api/v1/publisher/submissions", { signal })).data;
  },
  async metrics(
    id: string,
    days: number,
    signal?: AbortSignal,
  ): Promise<PublicationMetrics> {
    return (
      await http.get(
        `/api/v1/publisher/submissions/${encodeURIComponent(id)}/metrics`,
        { params: { days }, signal },
      )
    ).data;
  },
};
export function metricTotals(rows: MetricRow[]): Record<Metric, number> {
  const totals = {
    impressions: 0,
    opens: 0,
    reads: 0,
    likes: 0,
    shares: 0,
    comments: 0,
  };
  for (const row of rows)
    for (const key of Object.keys(metricLabels) as Metric[]) {
      const value = Number(row[key]);
      if (!Number.isFinite(value) || value < 0)
        throw new Error("Estatísticas inválidas recebidas.");
      totals[key] += value;
    }
  return totals;
}
