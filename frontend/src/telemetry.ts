export type BehaviorEvent = { eventKey: string; eventType: string; bookId?: string; occurredAt: string; payload?: Record<string, unknown> };
type Sender = (events: BehaviorEvent[]) => Promise<void>;

const MAX_BATCH = 20;
const MAX_PAYLOAD_KEYS = 12;

function safePayload(payload: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(payload).filter(([key, value]) => key.length <= 64 && value !== undefined && (typeof value !== 'string' || value.length <= 256)).slice(0, MAX_PAYLOAD_KEYS));
}

export function createTelemetry(send: Sender, flushMs = 3000) {
  let queue: BehaviorEvent[] = [];
  let timer: number | undefined;
  let retry = 0;
  let flushing: Promise<void> | undefined;
  const flush = async (): Promise<void> => {
    if (flushing || !queue.length) return flushing ?? Promise.resolve();
    const batch = queue.splice(0, MAX_BATCH);
    flushing = send(batch).then(() => { retry = 0; }).catch(() => {
      queue = [...batch, ...queue].slice(-100);
      retry = Math.min(retry + 1, 6);
      schedule(Math.min(flushMs * 2 ** retry, 60_000));
    }).finally(() => { flushing = undefined; });
    return flushing;
  };
  const schedule = (delay = flushMs) => { if (timer === undefined) timer = window.setTimeout(() => { timer = undefined; void flush(); }, delay); };
  const track = (eventType: string, bookId?: string, payload: Record<string, unknown> = {}) => {
    const event: BehaviorEvent = { eventKey: `${eventType}:${bookId ?? 'none'}:${crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`}`, eventType, bookId, occurredAt: new Date().toISOString(), payload: safePayload(payload) };
    queue.push(event); if (queue.length >= MAX_BATCH) void flush(); else schedule();
  };
  const observeImpression = (element: Element, bookId: string, threshold = 0.5) => {
    if (!('IntersectionObserver' in window)) { track('EXCERPT_IMPRESSION', bookId, { threshold }); return () => undefined; }
    let sent = false;
    const observer = new IntersectionObserver(entries => { if (!sent && entries.some(entry => entry.isIntersecting && entry.intersectionRatio >= threshold)) { sent = true; track('EXCERPT_IMPRESSION', bookId, { threshold }); observer.disconnect(); } }, { threshold });
    observer.observe(element); return () => observer.disconnect();
  };
  const dwell = (bookId: string, startedAt: number, payload: Record<string, unknown> = {}) => track('DWELL', bookId, { ...payload, durationMs: Math.max(0, Math.round(performance.now() - startedAt)) });
  return { track, observeImpression, dwell, flush, pending: () => queue.length };
}
