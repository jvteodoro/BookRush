export type ImportStatus =
  "AVAILABLE" | "QUEUED" | "IMPORTING" | "IMPORTED" | "FAILED";

export interface BookCandidate {
  id: string;
  source: "GUTENBERG" | "OPENLIBRARY" | "MANUAL";
  title: string;
  author: string;
  language: string;
  license: string;
  words: number;
  status: ImportStatus;
  cover?: string;
  description?: string;
  originalTitle?: string;
  firstPublicationYear?: number;
  updatedAt?: string;
  canonicalBookId?: string;
}

export interface AnalyticsMetric {
  key: string;
  label: string;
  value: number;
  unit?: string;
}

export interface AnalyticsRun {
  id: string;
  bookId: string;
  status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED";
  modelVersion: string;
  startedAt: string;
  finishedAt?: string;
  metrics: AnalyticsMetric[];
  operationKey?: string;
  totalItems?: number;
  processedItems?: number;
}

export type AnalyticsJobStatus =
  | "PENDING"
  | "RUNNING"
  | "COMPLETED"
  | "COMPLETED_WITH_ERRORS"
  | "FAILED"
  | "CANCELLED";

export interface AnalyticsJob {
  id: string;
  operation_key?: string;
  status: AnalyticsJobStatus;
  current_stage?: string;
  total_items: number;
  processed_items: number;
  succeeded_items: number;
  failed_items: number;
  cancel_requested?: boolean;
  book_id?: string;
  created_at?: string;
  started_at?: string;
  finished_at?: string;
}

export interface AnalyticsJobItem {
  id: string;
  job_id?: string;
  input_asset_version_id: string;
  book_id?: string;
  status: string;
  attempt_count: number;
  analysis_run_id?: string;
  error_code?: string;
  error_message?: string;
  started_at?: string;
  finished_at?: string;
  progress_stage?: string;
  progress_completed?: number;
  progress_total?: number;
  progress_message?: string;
  progress_updated_at?: string;
}

export interface AnalyticsPhase {
  id: string;
  attempt_count: number;
  phase: string;
  status: string;
  started_at: string;
  finished_at?: string;
  duration_ms?: number;
  error_code?: string;
  metadata?: Record<string, unknown>;
}

export interface AnalyticsItemDetail {
  item: AnalyticsJobItem;
  phases: AnalyticsPhase[];
  statistics: Record<string, number>;
  remainingPhases: string[];
}

export interface FeedWeights {
  hook: number;
  novelty: number;
  affinity: number;
  readability: number;
  diversity: number;
  exploration: number;
}

export interface FeedItem {
  excerptId: string;
  bookId: string;
  bookTitle: string;
  author: string;
  text: string;
  rankScore: number;
  features: Record<string, number>;
}

export type FeedEventType =
  "IMPRESSION" | "DWELL" | "LIKE" | "DISLIKE" | "SAVE" | "OPEN_BOOK" | "SKIP";

export interface FeedEvent {
  eventId?: string;
  sessionId: string;
  userId?: string;
  excerptId: string;
  type: FeedEventType;
  value?: number;
  occurredAt: string;
  parameterVersion: string;
}

export interface TrainingSession {
  id: string;
  userId: string;
  startedAt: string;
  status: "ACTIVE" | "COMPLETED";
  parameterVersion: string;
  weights: FeedWeights;
  eventCount: number;
  population?: "ADMIN_SEED" | "END_USER";
}
