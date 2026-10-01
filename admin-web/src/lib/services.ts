import { config } from './config';
import { createHttp } from './http';
import { analytics as mockAnalytics, books, defaultWeights, feedItems } from '../mocks/data';
import { AnalyticsRun, BookCandidate, FeedEvent, FeedWeights, TrainingSession } from '../types/domain';

export type BookCatalogPage = { items: BookCandidate[]; page: number; totalItems?: number; hasNext: boolean };
export type ImportJob = { id?: string; status: string; itemsDiscovered?: number; itemsProcessed?: number; itemsSucceeded?: number; itemsFailed?: number; errorMessage?: string; createdAt?: string; startedAt?: string; finishedAt?: string };
export type AnnotationCampaign = { id:string; code:string; name:string; status:string; target_annotations_per_item:number; protocol_version?:string; sampling_config_hash?:string };

const sleep = (ms = 250) => new Promise(resolve => setTimeout(resolve, ms));
let mutableBooks = [...books];
let mutableRuns = [...mockAnalytics];
const sessions: TrainingSession[] = [];
const catalogHttp = createHttp(config.apis.catalog, () => undefined);
const ingestionHttp = createHttp(config.apis.ingestion, () => undefined);
const analyticsHttp = createHttp(config.apis.analytics, () => undefined);
const feedHttp = createHttp(config.apis.feed, () => undefined);

function idempotencyKey(prefix: string, id: string) { return `${prefix}-${id}-${crypto.randomUUID()}`; }
function problem(error: unknown, fallback: string) {
  if (error && typeof error === 'object' && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string; detail?: string } } }).response;
    return new Error(response?.data?.message ?? response?.data?.detail ?? fallback);
  }
  return error instanceof Error ? error : new Error(fallback);
}
function mapBook(raw: Record<string, unknown>): BookCandidate {
  const status = String(raw.status ?? 'AVAILABLE');
  return { id: String(raw.id), source: 'MANUAL', title: String(raw.canonicalTitle ?? raw.title ?? 'Untitled'), author: String(raw.author ?? 'Unknown author'), language: String(raw.originalLanguage ?? raw.language ?? 'und'), license: String(raw.license ?? 'Provenance in catalog'), words: Number(raw.words ?? 0), status: status === 'ACTIVE' ? 'IMPORTED' : status === 'AVAILABLE' ? 'AVAILABLE' : status as BookCandidate['status'], description: raw.description ? String(raw.description) : undefined, originalTitle: raw.originalTitle ? String(raw.originalTitle) : undefined, firstPublicationYear: raw.firstPublicationYear ? Number(raw.firstPublicationYear) : undefined, updatedAt: raw.updatedAt ? String(raw.updatedAt) : undefined };
}

export const services = {
  async listBookPage(query = '', page = 0, size = 50): Promise<BookCatalogPage> {
    if (config.useMocks) { await sleep(); const items = structuredClone(mutableBooks.filter(book => `${book.title} ${book.author}`.toLowerCase().includes(query.toLowerCase()))); return { items, page, totalItems: items.length, hasNext: false }; }
    try {
      const { data } = await ingestionHttp.get('/api/admin/v1/ingestion/gutenberg/catalog', { params: { q: query || undefined, page, size } });
      const items = (data.items ?? []).map((item: Record<string, unknown>): BookCandidate => ({
        id: String(item.externalId), source: 'GUTENBERG', title: String(item.title ?? 'Untitled'), author: String(item.author ?? 'Unknown author'),
        language: String(item.language ?? 'und'), license: 'Consultar direitos/proveniência', words: 0, status: 'AVAILABLE',
        description: Array.isArray(item.subjects) ? item.subjects.slice(0, 3).join(' · ') : undefined,
      }));
      let statuses: Record<string, Record<string, unknown>> = {};
      if (items.length) {
        try {
          const response = await ingestionHttp.get<Record<string, Record<string, unknown>>>('/api/admin/v1/ingestion/gutenberg/catalog/status', { params: { ids: items.map((item: BookCandidate) => item.id).join(',') } });
          statuses = response.data ?? {};
        } catch { /* status enrichment is best effort */ }
      }
      const enriched = items.map((item: BookCandidate) => {
        const stored = statuses[item.id];
        if (!stored) return item;
        const status = String(stored.status ?? '');
        return { ...item, canonicalBookId: stored.book_id ? String(stored.book_id) : undefined, status: ['SUCCEEDED', 'NOOP', 'SKIPPED'].includes(status) ? 'IMPORTED' : status === 'RUNNING' ? 'IMPORTING' : status === 'PENDING' ? 'QUEUED' : status === 'FAILED' || status === 'CANCELLED' ? 'FAILED' : item.status };
      });
      return { items: enriched, page: Number(data.page ?? page), totalItems: Number(data.totalItems ?? 0), hasNext: Boolean(data.next) };
    } catch (error) { throw problem(error, 'Não foi possível consultar o catálogo Gutenberg.'); }
  },
  async listBooks(query = '', page = 0, size = 50): Promise<BookCandidate[]> { return (await this.listBookPage(query, page, size)).items; },
  async getBook(bookId: string) { if (config.useMocks) return mutableBooks.find(book => book.id === bookId); try { const { data } = await catalogHttp.get(`/internal/v1/catalog/books/${bookId}`); return mapBook(data); } catch (error) { throw problem(error, 'Não foi possível consultar a obra.'); } },
  async importBook(bookId: string): Promise<ImportJob> {
    if (config.useMocks) { await sleep(500); mutableBooks = mutableBooks.map(book => book.id === bookId ? { ...book, status: 'IMPORTED' } : book); return { status: 'IMPORTED' }; }
    try {
      const operationKey = idempotencyKey('admin-import', bookId);
      const { data } = await ingestionHttp.post('/api/admin/v1/ingestion/run', {
        source: 'GUTENBERG',
        externalIds: [bookId],
        languages: [],
        maxItems: 1,
        dryRun: false,
        processAssets: true,
      }, { headers: { 'Idempotency-Key': operationKey } });
      return { id: data.jobId ?? data.id, status: data.status ?? 'QUEUED' };
    } catch (error) { throw problem(error, 'A importação foi rejeitada.'); }
  },
  async getImportJob(jobId: string): Promise<ImportJob> { if (config.useMocks) return { id: jobId, status: 'COMPLETED', itemsDiscovered: 1, itemsProcessed: 1, itemsSucceeded: 1, itemsFailed: 0 }; try { const { data } = await ingestionHttp.get(`/api/admin/v1/ingestion/jobs/${jobId}`); return { id: String(data.id ?? jobId), status: String(data.status ?? 'UNKNOWN'), itemsDiscovered: Number(data.items_discovered ?? data.itemsDiscovered ?? 0), itemsProcessed: Number(data.items_processed ?? data.itemsProcessed ?? 0), itemsSucceeded: Number(data.items_succeeded ?? data.itemsSucceeded ?? 0), itemsFailed: Number(data.items_failed ?? data.itemsFailed ?? 0), errorMessage: data.error_message ?? data.errorMessage, createdAt: data.created_at ?? data.createdAt, startedAt: data.started_at ?? data.startedAt, finishedAt: data.finished_at ?? data.finishedAt }; } catch (error) { throw problem(error, 'Não foi possível acompanhar a importação.'); } },
  async listAnalytics(): Promise<AnalyticsRun[]> {
    if (config.useMocks) { await sleep(); return structuredClone(mutableRuns); }
    try {
      const { data } = await analyticsHttp.get('/api/internal/v1/content-analytics/runs', { params: { limit: 100 } });
      return (data.items ?? []).map((raw: Record<string, unknown>): AnalyticsRun => ({
        id: String(raw.id), bookId: String(raw.book_id ?? raw.bookId), status: (String(raw.status ?? 'FAILED') === 'COMPLETED' ? 'SUCCEEDED' : String(raw.status ?? 'FAILED') === 'COMPLETED_WITH_ERRORS' ? 'FAILED' : String(raw.status ?? 'FAILED')) as AnalyticsRun['status'],
        modelVersion: String(raw.model_version ?? raw.modelVersion ?? 'unknown'), startedAt: String(raw.started_at ?? raw.startedAt ?? raw.created_at ?? new Date().toISOString()),
        finishedAt: raw.finished_at ?? raw.finishedAt ? String(raw.finished_at ?? raw.finishedAt) : undefined,
        metrics: Array.isArray(raw.metrics) ? raw.metrics.map((metric: Record<string, unknown>) => ({ key: String(metric.key), label: String(metric.label ?? metric.key), value: Number(metric.value ?? 0), unit: metric.unit ? String(metric.unit) : undefined })) : [],
      }));
    } catch (error) { throw problem(error, 'Não foi possível consultar as métricas persistidas.'); }
  },
  async runAnalytics(bookId: string, canonicalBookId?: string): Promise<AnalyticsRun> {
    if (config.useMocks) { await sleep(600); const run: AnalyticsRun = { id: `run-${Date.now()}`, bookId, status: 'SUCCEEDED', modelVersion: 'analytics-2.0.0', startedAt: new Date().toISOString(), finishedAt: new Date().toISOString(), metrics: [{ key: 'excerpt_candidates', label: 'Candidate excerpts', value: 100 }] }; mutableRuns = [run, ...mutableRuns]; return run; }
    try {
      const resolvedBookId = canonicalBookId ?? bookId;
      const { data: input } = await analyticsHttp.get(`/api/internal/v1/content-analytics/books/${resolvedBookId}/input`);
      const version = (input.items ?? [])[0];
      if (!version?.version_id) throw new Error('A obra não possui versão textual normalizada disponível para analytics.');
      const operationKey = `admin-analytics-${resolvedBookId}-${version.version_id}-${Date.now()}`;
      const { data } = await analyticsHttp.post('/api/admin/v1/content-analytics/jobs', { operationKey, inputAssetVersionIds: [version.version_id], configuration: { requestedBy: 'admin-web' } }, { headers: { 'Idempotency-Key': operationKey } });
      return { id: String(data.id), bookId: resolvedBookId, status: data.status ?? 'PENDING', modelVersion: 'analytics-v2', startedAt: new Date().toISOString(), metrics: [], totalItems: data.totalItems };
    } catch (error) { throw problem(error, 'O job de analytics foi rejeitado.'); }
  },
  async getAnalyticsJob(jobId: string) { if (config.useMocks) return { id: jobId, status: 'COMPLETED', processedItems: 1, succeededItems: 1, failedItems: 0 }; try { const { data } = await analyticsHttp.get(`/api/admin/v1/content-analytics/jobs/${jobId}`); return data; } catch (error) { throw problem(error, 'Não foi possível consultar o job de analytics.'); } },
  async createSession(userId: string, weights: FeedWeights = defaultWeights): Promise<TrainingSession> {
    if (config.useMocks) { const session = { id: `sess-${Date.now()}`, userId, startedAt: new Date().toISOString(), status: 'ACTIVE' as const, parameterVersion: `pv-${Date.now()}`, weights: { ...weights }, eventCount: 0, population: 'ADMIN_SEED' as const }; sessions.unshift(session); return session; }
    try { const { data } = await feedHttp.post('/admin/training-sessions', { population: 'ADMIN_SEED', weights }); return data; } catch (error) { throw problem(error, 'Não foi possível criar a sessão de treinamento.'); }
  },
  async getFeed(sessionId?: string) { if (config.useMocks) { await sleep(120); return structuredClone(feedItems); } try { const { data } = await feedHttp.get('/admin/feed', { params: { sessionId } }); return (data.items ?? data).map((item: Record<string, unknown>) => ({ ...item, rankScore: Number(item.rankScore ?? item.rank_score ?? item.score ?? item.finalScore ?? item.final_score ?? 0), features: item.features ?? item.scoreDecomposition ?? item.score_decomposition ?? {} })); } catch (error) { throw problem(error, 'Não foi possível carregar o feed.'); } },
  async emitEvent(event: FeedEvent) { const payload = { ...event, eventId: event.eventId ?? crypto.randomUUID(), userId: undefined }; if (config.useMocks) { const session = sessions.find(item => item.id === event.sessionId); if (session) session.eventCount += 1; return; } try { await feedHttp.post('/admin/events', payload, { headers: { 'Idempotency-Key': payload.eventId } }); } catch (error) { throw problem(error, 'Não foi possível registrar o evento.'); } },
  async updateWeights(sessionId: string, weights: FeedWeights) { if (config.useMocks) { const session = sessions.find(item => item.id === sessionId); if (!session) throw new Error('Sessão não encontrada'); session.weights = { ...weights }; session.parameterVersion = `pv-${Date.now()}`; return { ...session }; } try { const { data } = await feedHttp.put(`/admin/training-sessions/${sessionId}/parameters`, { weights }); return data; } catch (error) { throw problem(error, 'Não foi possível versionar os parâmetros.'); } },
  async promoteBaseline(sessionId: string, parameterVersion: string) { if (config.useMocks) return { sessionId, parameterVersion, status: 'PROMOTED', auditId: `audit-${Date.now()}` }; try { const { data } = await feedHttp.post(`/admin/training-sessions/${sessionId}/promotions`, { parameterVersion }); return data; } catch (error) { throw problem(error, 'Promoção não autorizada ou indisponível.'); } },
  async listSessions() { if (config.useMocks) return structuredClone(sessions); const { data } = await feedHttp.get('/admin/training-sessions'); return data; },
  async listAnnotationCampaigns(): Promise<AnnotationCampaign[]> { const { data } = await analyticsHttp.get('/api/admin/v1/annotation/campaigns'); return data.items ?? []; },
  async createAnnotationCampaign(input: {code:string;name:string;languagePolicy:Record<string,unknown>;samplingConfig:Record<string,unknown>}) { const { data } = await analyticsHttp.post('/api/admin/v1/annotation/campaigns', { ...input, languagePolicy: input.languagePolicy, samplingConfig: input.samplingConfig, protocolVersion:'annotation-protocol-v1', targetAnnotationsPerItem:3 }); return data; },
  async transitionAnnotationCampaign(id:string, action:string) { const { data } = await analyticsHttp.post(`/api/admin/v1/annotation/campaigns/${id}/${action}`); return data; },
  async sampleAnnotationCampaign(id:string) { const { data } = await analyticsHttp.post(`/api/admin/v1/annotation/campaigns/${id}/sample`, { items: 100, seed: 1337 }); return data; },
  async annotationMetrics(id:string) { const { data } = await analyticsHttp.get(`/api/admin/v1/annotation/campaigns/${id}/metrics`); return data; },
  async annotationQueue(id:string) { const { data } = await analyticsHttp.get(`/api/admin/v1/annotation/campaigns/${id}/adjudication-queue`); return data; },
  async publishAnnotationDataset(id:string) { const { data } = await analyticsHttp.post(`/api/admin/v1/annotation/campaigns/${id}/datasets`); return data; },
  async exportAnnotationDataset(id:string,version:number) { const { data } = await analyticsHttp.get(`/api/admin/v1/annotation/campaigns/${id}/datasets/${version}/export`); return data; },
  async claimAnnotationItem(id:string) { const { data } = await analyticsHttp.post(`/api/admin/v1/annotation/campaigns/${id}/claim-next`); if (!data?.campaignItemId) return data; const item = await analyticsHttp.get(`/api/admin/v1/annotation/items/${data.campaignItemId}`); return { ...data, ...item.data }; },
  async rateFirstScreen(itemId:string,hook:number) { const { data } = await analyticsHttp.post(`/api/admin/v1/annotation/items/${itemId}/first-screen`,{hook}); return data; },
  async lockAnnotation(itemId:string,dimensions:Record<string,number>,confidence:number) { const { data } = await analyticsHttp.post(`/api/admin/v1/annotation/items/${itemId}/lock`,{dimensions,confidence,failureTags:[]}); return data; },
  async revealAnnotationContext(itemId:string) { const { data } = await analyticsHttp.get(`/api/admin/v1/annotation/items/${itemId}/context`); return data; },
  async submitAnnotation(itemId:string,contextRequired:boolean,severity:number) { const { data } = await analyticsHttp.post(`/api/admin/v1/annotation/items/${itemId}/submit`,{contextRequired,severity}); return data; },
  async adjudicateAnnotation(itemId:string,dimensionCode:string,ordinalValue:number,rationale:string) { const { data } = await analyticsHttp.post(`/api/admin/v1/annotation/items/${itemId}/adjudicate`,{dimensionCode,ordinalValue,rationale}); return data; },
};
