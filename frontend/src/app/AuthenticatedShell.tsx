import type { User } from "oidc-client-ts";
import { LiveReader } from "../reader/LiveReader";
import type { ReaderTab } from "./useLegacyNavigation";

type AuthenticatedShellProps = {
  error: string;
  feedReady: boolean;
  legacyQuery: string;
  legacyVisible: boolean;
  onRetry: () => void;
  onShowLegacy: () => void;
  readerTab: ReaderTab;
  user?: User;
};

export function AuthenticatedShell({
  error,
  feedReady,
  legacyQuery,
  legacyVisible,
  onRetry,
  onShowLegacy,
  readerTab,
  user,
}: AuthenticatedShellProps) {
  if (error) {
    return (
      <main className="landing">
        <span className="eyebrow">BOOKRUSH</span>
        <h1>Não conseguimos abrir seu feed.</h1>
        <p>{error}</p>
        <button className="primary-button" onClick={onRetry}>
          Tentar novamente
        </button>
      </main>
    );
  }

  return (
    <>
      <div id="app" hidden={!legacyVisible} />
      {!feedReady && (
        <div className="splash">
          <p>Preparando seu feed…</p>
        </div>
      )}
      {feedReady && (
        <div hidden={legacyVisible}>
          <LiveReader
            visible={!legacyVisible}
            initialTab={readerTab}
            legacyQuery={legacyQuery}
            userName={String(
              user?.profile.name ?? user?.profile.preferred_username ?? "Leitor",
            )}
            onLegacy={onShowLegacy}
          />
        </div>
      )}
    </>
  );
}
