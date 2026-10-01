export type BehaviorEvent = { eventKey: string; eventType: string; bookId?: string; occurredAt: string; payload?: Record<string, unknown> };
export function createTelemetry(send: (events: BehaviorEvent[]) => Promise<void>, flushMs = 3000) {
  let queue: BehaviorEvent[] = []; let timer: number | undefined;
  const flush = async () => { const batch = queue.splice(0); if (!batch.length) return; try { await send(batch); } catch { queue.unshift(...batch); } };
  const schedule = () => { if (timer === undefined) timer = window.setTimeout(() => { timer=undefined; void flush(); }, flushMs); };
  return { track(eventType: string, bookId?: string, payload: Record<string, unknown> = {}) { queue.push({eventKey:`${eventType}:${bookId ?? 'none'}:${Date.now()}:${Math.random()}`,eventType,bookId,occurredAt:new Date().toISOString(),payload}); if(queue.length>=20) void flush(); else schedule(); }, flush };
}
