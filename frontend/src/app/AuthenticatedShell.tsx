import type { User } from "oidc-client-ts";
import { LiveReader } from "../reader/LiveReader";
import { LegacySidebar } from "./LegacySidebar";
import type { ReaderTab } from "./useLegacyNavigation";

type AuthenticatedShellProps = {
  error: string;
  bookPath: string | null;
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
  bookPath,
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
      <LegacySidebar />
      {!feedReady && (
        <div className="splash">
          <p>Preparando seu feed…</p>
        </div>
      )}
      {feedReady && (
        <div hidden={legacyVisible}>
          <LiveReader
            visible={!legacyVisible}
            bookPath={bookPath}
            initialTab={readerTab}
            legacyQuery={legacyQuery}
            userName={String(
              user?.profile.name ??
                user?.profile.preferred_username ??
                "Leitor",
            )}
            onLegacy={onShowLegacy}
          />
        </div>
      )}
    </>
  );
}
