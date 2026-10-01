export type ImportStatus = 'AVAILABLE' | 'QUEUED' | 'IMPORTING' | 'IMPORTED' | 'FAILED';

export interface BookCandidate {
  id: string;
  source: 'GUTENBERG' | 'OPENLIBRARY' | 'MANUAL';
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
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  modelVersion: string;
  startedAt: string;
  finishedAt?: string;
  metrics: AnalyticsMetric[];
  operationKey?: string;
  totalItems?: number;
  processedItems?: number;
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

export type FeedEventType = 'IMPRESSION' | 'DWELL' | 'LIKE' | 'DISLIKE' | 'SAVE' | 'OPEN_BOOK' | 'SKIP';

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
  status: 'ACTIVE' | 'COMPLETED';
  parameterVersion: string;
  weights: FeedWeights;
  eventCount: number;
  population?: 'ADMIN_SEED' | 'END_USER';
}
