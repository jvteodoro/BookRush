import { useEffect, useState } from "react";
import { loadFollowerStats } from "./followers";

export function FollowerCount() {
  const [label, setLabel] = useState("Carregando seguidores…");
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLabel("Carregando seguidores…");
    setError(false);
    void loadFollowerStats(undefined, controller.signal)
      .then((stats) => {
        if (!controller.signal.aborted) setLabel(stats.label);
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setError(true);
          setLabel("Seguidores indisponíveis");
        }
      });
    return () => controller.abort();
  }, [attempt]);
  return (
    <div className="br-follower-count" aria-live="polite">
      <strong>{label}</strong>
      {error && (
        <button
          type="button"
          className="br-comment-report"
          onClick={() => setAttempt((value) => value + 1)}
        >
          Tentar novamente
        </button>
      )}
    </div>
  );
}
