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

export function publicationFileType(file: Pick<File, "name" | "size">): string {
  if (file.size === 0) throw new Error("O arquivo está vazio.");
  if (/\.epub$/i.test(file.name)) return "application/epub+zip";
  if (/\.pdf$/i.test(file.name)) return "application/pdf";
  throw new Error("Escolha um livro em EPUB ou PDF.");
}

export const publishingApi = {
  async create(title: string): Promise<Submission> {
    const value = title.trim();
    if (!value || value.length > 300)
      throw new Error("Informe um título com até 300 caracteres.");
    return (await http.post("/api/v1/publisher/submissions", { title: value }))
      .data;
  },
  async upload(id: string, file: File): Promise<void> {
    const contentType = publicationFileType(file);
    const digest = await crypto.subtle.digest(
      "SHA-256",
      await file.arrayBuffer(),
    );
    const sha256 = Array.from(new Uint8Array(digest), (byte) =>
      byte.toString(16).padStart(2, "0"),
    ).join("");
    const path = `/api/v1/publisher/submissions/${encodeURIComponent(id)}/upload`;
    const { data } = await http.post<{ uploadId: string; url: string }>(path, {
      filename: file.name,
      contentType,
    });
    const result = await fetch(data.url, {
      method: "PUT",
      credentials: "omit",
      headers: { "Content-Type": contentType },
      body: file,
    });
    if (!result.ok)
      throw new Error("Não foi possível enviar o arquivo. Tente novamente.");
    await http.post(`${path}/${encodeURIComponent(data.uploadId)}/finalize`, {
      sha256,
    });
  },
  async attest(id: string): Promise<void> {
    await http.post(
      `/api/v1/publisher/submissions/${encodeURIComponent(id)}/rights`,
      {
        rightType: "DISTRIBUTION",
        territory: "GLOBAL",
        evidenceReference:
          "Publisher explicitly affirmed distribution rights in the publishing portal",
      },
    );
  },
  async reviewQueue(): Promise<{ submission_id: string; title: string }[]> {
    return (await http.get("/api/v1/publisher/review")).data;
  },
  async review(id: string, status: string): Promise<void> {
    await http.post(
      `/api/v1/publisher/submissions/${encodeURIComponent(id)}/review`,
      { status, reasonCode: "EDITORIAL_REVIEW" },
    );
  },
  async withdraw(id: string): Promise<void> {
    await http.post(
      `/api/v1/publisher/submissions/${encodeURIComponent(id)}/withdraw`,
    );
  },
  async submit(id: string): Promise<{ id: string; status: string }> {
    return (
      await http.post(
        `/api/v1/publisher/submissions/${encodeURIComponent(id)}/submit`,
      )
    ).data;
  },
};
