export type DwellHandler = (bookId: string, durationMs: number) => void;

type Clock = () => number;

/** Tracks visible intervals without merging separate visits or emitting tiny noise. */
export function createDwellTracker(
  onDwell: DwellHandler,
  clock: Clock = () => window.performance.now(),
  minimumDurationMs = 250,
) {
  const startedAt = new Map<string, number>();
  const visibleBooks = new Set<string>();

  const finish = (bookId: string) => {
    const start = startedAt.get(bookId);
    if (start === undefined) return;
    startedAt.delete(bookId);
    const durationMs = Math.max(0, Math.round(clock() - start));
    if (durationMs >= minimumDurationMs) onDwell(bookId, durationMs);
  };

  return {
    update(bookId: string, visible: boolean) {
      if (visible) {
        visibleBooks.add(bookId);
        if (!startedAt.has(bookId)) startedAt.set(bookId, clock());
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
    flush() {
      for (const bookId of startedAt.keys()) finish(bookId);
    },
  };
}
