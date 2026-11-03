import { authClient } from "../auth";
import { apiRoutes } from "../app/apiRoutes";

/** Counts only time for which an open reader is visible. */
export function startReadingTimer(): () => void {
  let startedAt: number | undefined =
    document.visibilityState === "visible" ? Date.now() : undefined;
  let stopped = false;
  let sending: Promise<void> = Promise.resolve();

  function flush() {
    const now = Date.now();
    if (startedAt === undefined) return;
    while (now - startedAt >= 1000) {
      const seconds = Math.min(60, Math.floor((now - startedAt) / 1000));
      startedAt += seconds * 1000;
      const interval = {
        eventId: crypto.randomUUID(),
        activeSeconds: seconds,
        endedAt: new Date(startedAt).toISOString(),
      };
      sending = sending.then(async () => {
        for (let attempt = 0; attempt < 3; attempt++) {
          try {
            const response = await authClient.fetch(
              apiRoutes.reader.plantReadingTime(),
              {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(interval),
                keepalive: true,
              },
            );
            if (response.ok) {
              (
                window as Window & { __BOOKRUSH_PLANT__?: unknown }
              ).__BOOKRUSH_PLANT__ = await response.json();
              window.dispatchEvent(new Event("bookrush:plant-updated"));
              return;
            }
          } catch {
            /* retry with the same event id */
          }
        }
        (
          window as Window & { __BOOKRUSH_PLANT_SAVE_ERROR__?: boolean }
        ).__BOOKRUSH_PLANT_SAVE_ERROR__ = true;
        window.dispatchEvent(new Event("bookrush:plant-save-error"));
      });
    }
  }
  function visibility() {
    if (document.visibilityState === "visible") startedAt = Date.now();
    else {
      flush();
      startedAt = undefined;
    }
  }
  const timer = window.setInterval(flush, 15000);
  document.addEventListener("visibilitychange", visibility);
  window.addEventListener("pagehide", flush);
  return () => {
    if (stopped) return;
    stopped = true;
    flush();
    window.clearInterval(timer);
    document.removeEventListener("visibilitychange", visibility);
    window.removeEventListener("pagehide", flush);
  };
}
