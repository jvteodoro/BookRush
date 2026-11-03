import { useEffect, useState } from "react";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { CarouselControl } from "../components/ui/CarouselControl";
import { searchUsers, type UserSearchResult } from "./userSearch";

type Props = {
  visible: boolean;
  query: string;
  onProfile: (subject: string) => void;
  requestVersion?: number;
};

export function UserSearchResults({
  visible,
  query,
  onProfile,
  requestVersion = 0,
}: Props) {
  const [page, setPage] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [data, setData] = useState<UserSearchResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const hasQuery = Boolean(query.trim().replace(/^@/, "").trim());
  useEffect(() => {
    if (!visible) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    const timer = window.setTimeout(
      () => {
        void searchUsers(query, page, controller.signal)
          .then((result) => {
            if (!controller.signal.aborted) setData(result);
          })
          .catch((cause: unknown) => {
            if (!controller.signal.aborted)
              setError(
                cause instanceof Error
                  ? cause.message
                  : "Não foi possível buscar as pessoas.",
              );
          })
          .finally(() => {
            if (!controller.signal.aborted) setLoading(false);
          });
      },
      hasQuery ? 300 : 0,
    );
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [query, page, attempt, visible, hasQuery, requestVersion]);
  if (!hasQuery)
    return (
      <Card className="br-live-empty">
        <h2>Encontre outros leitores</h2>
        <p>Digite um nome ou @usuário para pesquisar perfis públicos.</p>
      </Card>
    );
  return (
    <div aria-busy={loading}>
      {loading ? (
        <p role="status">Buscando pessoas…</p>
      ) : error ? (
        <Card className="br-live-empty">
          <p role="alert">{error}</p>
          <Button onClick={() => setAttempt((current) => current + 1)}>
            Tentar novamente
          </Button>
        </Card>
      ) : !data?.items.length ? (
        <Card className="br-live-empty">
          <h2>Nenhuma pessoa encontrada</h2>
          <p>
            Tente outro nome ou @usuário. Somente perfis públicos aparecem na
            busca.
          </p>
        </Card>
      ) : (
        <>
          <div className="br-search-summary">
            <p role="status">
              {data.totalItems}{" "}
              {data.totalItems === 1
                ? "pessoa encontrada"
                : "pessoas encontradas"}
            </p>
          </div>
          <div className="br-search-results">
            {data.items.map((user) => (
              <Card key={user.subject}>
                <h2>{user.displayName || user.username || "Leitor"}</h2>
                {user.username && <p className="br-muted">@{user.username}</p>}
                <Button
                  variant="secondary"
                  onClick={() => onProfile(user.subject)}
                  aria-label={`Ver perfil de ${user.displayName || user.username || "leitor"}`}
                >
                  Ver perfil
                </Button>
              </Card>
            ))}
          </div>
          {data.totalPages > 1 && (
            <nav
              className="br-search-pagination"
              aria-label="Páginas de pessoas"
            >
              <CarouselControl
                direction="previous"
                label="Página anterior de pessoas"
                disabled={page === 0}
                onClick={() => setPage((current) => current - 1)}
              />
              <span>
                Página {data.page + 1} de {data.totalPages}
              </span>
              <CarouselControl
                direction="next"
                label="Próxima página de pessoas"
                disabled={page >= data.totalPages - 1}
                onClick={() => setPage((current) => current + 1)}
              />
            </nav>
          )}
        </>
      )}
    </div>
  );
}
