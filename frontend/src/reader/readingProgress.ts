export type ReadingLocator = {
  href: string;
  type: string;
  locations: {
    progression: number;
    totalProgression?: number;
    [key: string]: unknown;
  };
  [key: string]: unknown;
};
type Fetcher = (url: string, init?: RequestInit) => Promise<Response>;

export async function loadReadingPosition(fetcher: Fetcher, path: string) {
  const response = await fetcher(path, { cache: "no-store" });
  if (!response.ok)
    throw new Error(
      `Não foi possível recuperar a posição de leitura (HTTP ${response.status}). Tente abrir o livro novamente.`,
    );
  const rows = await response.json();
  let locator = rows[0]?.locator;
  if (typeof locator === "string") {
    try {
      locator = JSON.parse(locator);
    } catch {
      return null;
    }
  }
  if (
    !locator ||
    typeof locator.href !== "string" ||
    typeof locator.type !== "string" ||
    !Number.isFinite(locator.locations?.progression) ||
    locator.locations.progression < 0 ||
    locator.locations.progression > 1
  )
    return null;
  return locator as ReadingLocator;
}

/** One queue per book: writes are ordered and rapid position changes coalesce. */
export function createReadingProgress({
  fetcher,
  path,
  headers = {},
  onError = () => {},
  capture,
  documentRef = document,
  windowRef = window,
  schedule = (callback: () => void) => setTimeout(callback, 300),
  cancel = (timer: ReturnType<typeof setTimeout>) => clearTimeout(timer),
}: {
  fetcher: Fetcher;
  path: string;
  headers?: Record<string, string>;
  onError?: () => void;
  capture?: () => ReadingLocator | null;
  documentRef?: Pick<
    Document,
    "visibilityState" | "addEventListener" | "removeEventListener"
  >;
  windowRef?: Pick<Window, "addEventListener" | "removeEventListener">;
  schedule?: (callback: () => void) => ReturnType<typeof setTimeout>;
  cancel?: (timer: ReturnType<typeof setTimeout>) => void;
}) {
  let pending: ReadingLocator | undefined;
  let inFlight: Promise<void> | undefined;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let closed = false;
  let warned = false;
  function checkpoint() {
    const locator = capture?.();
    if (locator) pending = JSON.parse(JSON.stringify(locator));
  }
  function flush(): Promise<void> {
    if (timer !== undefined) {
      cancel(timer);
      timer = undefined;
    }
    if (inFlight) return inFlight;
    inFlight = (async () => {
      while (pending) {
        const locator = pending;
        pending = undefined;
        const progression =
          locator.locations.totalProgression ?? locator.locations.progression;
        try {
          const response = await fetcher(path, {
            method: "PUT",
            keepalive: true,
            headers: { "Content-Type": "application/json", ...headers },
            body: JSON.stringify({
              percent: progression * 100,
              positionCodepoint: 0,
              locator,
            }),
          });
          if (!response.ok) throw new Error(`HTTP ${response.status}`);
          warned = false;
        } catch {
          // Retain the newest unsaved position for a later change/visibility/close.
          pending ??= locator;
          if (!warned) {
            warned = true;
            onError();
          }
          break;
        }
      }
    })().finally(() => {
      inFlight = undefined;
    });
    return inFlight;
  }
  const onHidden = () => {
    if (documentRef.visibilityState === "hidden") {
      checkpoint();
      void flush();
    }
  };
  const onPagehide = () => {
    checkpoint();
    void flush();
  };
  documentRef.addEventListener("visibilitychange", onHidden);
  windowRef.addEventListener("pagehide", onPagehide);
  return {
    record(locator: ReadingLocator) {
      if (closed) return;
      pending = JSON.parse(JSON.stringify(locator));
      if (timer !== undefined) cancel(timer);
      timer = schedule(() => {
        void flush();
      });
    },
    flush,
    async close() {
      checkpoint();
      closed = true;
      documentRef.removeEventListener("visibilitychange", onHidden);
      windowRef.removeEventListener("pagehide", onPagehide);
      await flush();
    },
  };
}
