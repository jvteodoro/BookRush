type SessionResponse = {
  ok: boolean;
  json: () => Promise<{ id?: string }>;
};

type SessionFetcher = (
  input: string,
  init: RequestInit,
) => Promise<SessionResponse>;

type SessionDocument = {
  visibilityState: string;
  addEventListener: (name: string, listener: () => void) => void;
  removeEventListener: (name: string, listener: () => void) => void;
};

type SessionWindow = {
  addEventListener: (name: string, listener: () => void) => void;
  removeEventListener: (name: string, listener: () => void) => void;
};

export function createReaderSessionLifecycle({
  fetcher,
  sessionsPath,
  endSessionPath,
  documentRef = document,
  windowRef = window,
  now = () => Date.now(),
}: {
  fetcher: SessionFetcher;
  sessionsPath: string;
  endSessionPath: (id: string) => string;
  documentRef?: SessionDocument;
  windowRef?: SessionWindow;
  now?: () => number;
}) {
  let sessionId: string | undefined;
  let activeSince: number | undefined;
  let activeSeconds = 0;
  let endInFlight: Promise<void> | undefined;
  let visibilityListener: (() => void) | undefined;
  let pagehideListener: (() => void) | undefined;

  function accumulateActiveTime() {
    if (activeSince === undefined) return;
    activeSeconds += Math.max(0, Math.floor((now() - activeSince) / 1000));
    activeSince = documentRef.visibilityState === "visible" ? now() : undefined;
  }

  async function start(
    bookId: string,
    originHeaders: Record<string, string> = {},
  ) {
    const response = await fetcher(sessionsPath, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...originHeaders },
      body: JSON.stringify({ bookId }),
    });
    if (!response.ok) return;
    const payload = await response.json();
    if (!payload.id) return;
    sessionId = payload.id;
    activeSeconds = 0;
    activeSince = documentRef.visibilityState === "visible" ? now() : undefined;
    visibilityListener = () => {
      accumulateActiveTime();
      if (documentRef.visibilityState === "visible") activeSince = now();
    };
    documentRef.addEventListener("visibilitychange", visibilityListener);
    pagehideListener = () => {
      void end();
    };
    windowRef.addEventListener("pagehide", pagehideListener);
  }

  function end(): Promise<void> {
    if (endInFlight) return endInFlight;
    if (!sessionId) return Promise.resolve();
    accumulateActiveTime();
    const id = sessionId;
    const seconds = activeSeconds;
    sessionId = undefined;
    if (visibilityListener)
      documentRef.removeEventListener("visibilitychange", visibilityListener);
    if (pagehideListener)
      windowRef.removeEventListener("pagehide", pagehideListener);
    visibilityListener = undefined;
    pagehideListener = undefined;
    endInFlight = fetcher(endSessionPath(id), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ activeSeconds: seconds }),
      keepalive: true,
    })
      .then(() => undefined)
      .catch(() => undefined)
      .finally(() => {
        endInFlight = undefined;
      });
    return endInFlight;
  }

  return { start, end };
}
