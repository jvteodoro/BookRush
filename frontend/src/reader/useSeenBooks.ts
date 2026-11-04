import { useEffect, useRef, useState } from "react";
import { bookOpenedEvent, loadSeenBooks } from "./seen";

export function useSeenBooks(bookIds: string[], active = true) {
  const key = [...new Set(bookIds)].sort().join(",");
  const [seen, setSeen] = useState<Set<string>>(new Set());
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const confirmed = useRef(new Set<string>());
  useEffect(() => {
    const opened = (event: Event) => {
      const id = (event as CustomEvent<string>).detail;
      if (typeof id !== "string") return;
      confirmed.current.add(id);
      setSeen((current) => new Set([...current, id]));
    };
    window.addEventListener(bookOpenedEvent, opened);
    return () => window.removeEventListener(bookOpenedEvent, opened);
  }, []);
  useEffect(() => {
    if (!active) return;
    const controller = new AbortController();
    setError("");
    void loadSeenBooks(key ? key.split(",") : [], controller.signal)
      .then((result) => {
        if (!controller.signal.aborted)
          setSeen(new Set([...result, ...confirmed.current]));
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted)
          setError(
            cause instanceof Error
              ? cause.message
              : "Não foi possível consultar os livros vistos.",
          );
      });
    return () => controller.abort();
  }, [key, active, attempt]);
  return { seen, error, retry: () => setAttempt((value) => value + 1) };
}
