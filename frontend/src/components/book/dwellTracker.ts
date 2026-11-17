export type DwellHandler = (
  bookId: string,
  durationMs: number,
  visitId: string,
) => void;

type Clock = () => number;

/** Tracks visible intervals without merging separate visits or emitting tiny noise. */
export function createDwellTracker(
  onDwell: DwellHandler,
  clock: Clock = () => window.performance.now(),
  minimumDurationMs = 250,
) {
  const startedAt = new Map<string, number>();
  const visitIds = new Map<string, string>();
  const visibleBooks = new Set<string>();

  const finish = (bookId: string) => {
    const start = startedAt.get(bookId);
    if (start === undefined) return;
    startedAt.delete(bookId);
    const visitId = visitIds.get(bookId);
    visitIds.delete(bookId);
    const durationMs = Math.max(0, Math.round(clock() - start));
    if (durationMs >= minimumDurationMs && visitId) {
      onDwell(bookId, durationMs, visitId);
    }
  };

  return {
    update(bookId: string, visible: boolean) {
      if (visible) {
        visibleBooks.add(bookId);
        if (!startedAt.has(bookId)) {
          startedAt.set(bookId, clock());
          visitIds.set(
            bookId,
            globalThis.crypto?.randomUUID?.() ??
              `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`,
          );
        }
      } else {
        visibleBooks.delete(bookId);
        finish(bookId);
      }
    },
    setPageVisible(pageVisible: boolean) {
      if (!pageVisible) {
        for (const bookId of visibleBooks) finish(bookId);
        return;
      }
      for (const bookId of visibleBooks) {
        if (!startedAt.has(bookId)) startedAt.set(bookId, clock());
      }
    },
    visitId(bookId: string) {
      return visitIds.get(bookId);
    },
    flush() {
      for (const bookId of startedAt.keys()) finish(bookId);
    },
  };
}
