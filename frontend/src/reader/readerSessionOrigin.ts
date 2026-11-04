export type ReaderSessionOrigin = {
  bookId: string;
  headers: Record<string, string>;
};

/**
 * Keeps the requested origin separate from the origin of the active reader.
 * The requested value survives only until readiumBridge captures it; the
 * active value is installed after the previous reader has been unmounted.
 */
export function createReaderSessionOriginStore() {
  let requested: ReaderSessionOrigin | null = null;
  let active: ReaderSessionOrigin | null = null;

  return {
    request(origin: ReaderSessionOrigin | null) {
      requested = origin
        ? { bookId: origin.bookId, headers: { ...origin.headers } }
        : null;
    },
    requestedFor(bookId: string) {
      return requested?.bookId === bookId ? { ...requested.headers } : {};
    },
    activate(bookId: string, headers: Record<string, string>) {
      active = { bookId, headers: { ...headers } };
    },
    activeFor(bookId: string) {
      return active?.bookId === bookId ? { ...active.headers } : {};
    },
    clear() {
      requested = null;
      active = null;
    },
  };
}

export const readerSessionOrigin = createReaderSessionOriginStore();
