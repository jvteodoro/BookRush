export type BehaviorEvent = {
  eventKey: string;
  eventType: string;
  bookId?: string;
  occurredAt: string;
  payload?: Record<string, unknown>;
};

type Sender = (events: BehaviorEvent[]) => Promise<void>;

const MAX_BATCH = 20;
const MAX_QUEUE = 100;
const MAX_PAYLOAD_KEYS = 12;

function safePayload(
  payload: Record<string, unknown>,
): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(payload)
      .filter(
        ([key, value]) =>
          key.length <= 64 &&
          value !== undefined &&
          (typeof value !== "string" || value.length <= 256),
      )
      .slice(0, MAX_PAYLOAD_KEYS),
  );
}

function eventKey(eventType: string, bookId?: string): string {
  return `${eventType}:${bookId ?? "none"}:${Date.now()}-${Math.random()}`;
}

export function createTelemetry(send: Sender, flushMs = 3000) {
  let queue: BehaviorEvent[] = [];
  let retry = 0;
  let flushing: Promise<void> | undefined;
  let timer: ReturnType<typeof setTimeout> | undefined;

  const schedule = (delay = flushMs) => {
    if (timer !== undefined) return;
    timer = setTimeout(() => {
      timer = undefined;
      void flush();
    }, delay);
  };

  const flush = async (): Promise<void> => {
    if (flushing || !queue.length) return flushing ?? Promise.resolve();
    if (timer !== undefined) {
      clearTimeout(timer);
      timer = undefined;
    }
    const batch = queue.splice(0, MAX_BATCH);
    flushing = send(batch)
      .then(() => {
        retry = 0;
      })
      .catch(() => {
        queue = [...batch, ...queue].slice(-MAX_QUEUE);
        retry = Math.min(retry + 1, 6);
        schedule(Math.min(flushMs * 2 ** retry, 60_000));
      })
      .finally(() => {
        flushing = undefined;
      });
    return flushing;
  };

  const track = (
    eventType: string,
    bookId?: string,
    payload: Record<string, unknown> = {},
  ) => {
    queue.push({
      eventKey: eventKey(eventType, bookId),
      eventType,
      bookId,
      occurredAt: new Date().toISOString(),
      payload: safePayload(payload),
    });
    if (queue.length >= MAX_BATCH) void flush();
    else schedule();
  };

  const impression = (bookId: string, payload: Record<string, unknown> = {}) =>
    track("EXCERPT_IMPRESSION", bookId, payload);

  const dwell = (
    bookId: string,
    startedAt: number,
    payload: Record<string, unknown> = {},
  ) =>
    track("DWELL", bookId, {
      ...payload,
      durationMs: Math.max(0, Math.round(Date.now() - startedAt)),
    });

  return {
    track,
    impression,
    dwell,
    flush,
    pending: () => queue.length,
  };
}
