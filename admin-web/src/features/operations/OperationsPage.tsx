import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ShieldAlert, UserCheck, UserX } from "lucide-react";
import { services } from "../../lib/services";

export function OperationsPage() {
  const queryClient = useQueryClient();
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const users = useQuery({
    queryKey: ["admin-users", search, page],
    queryFn: () => services.listAdminUsers(search, page),
  });
  const reports = useQuery({
    queryKey: ["admin-reports"],
    queryFn: services.listAdminReports,
  });
  const comments = useQuery({
    queryKey: ["admin-comments"],
    queryFn: services.listAdminComments,
  });
  const kpis = useQuery({
    queryKey: ["admin-kpis"],
    queryFn: services.adminKpis,
  });
  const userMutation = useMutation({
    mutationFn: ({
      subjectKey,
      enabled,
      reason,
    }: {
      subjectKey: string;
      enabled: boolean;
      reason: string;
    }) => services.setUserEnabled(subjectKey, enabled, reason),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ["admin-users"] }),
  });
  const moderation = useMutation({
    mutationFn: ({ id, visibility }: { id: string; visibility: string }) =>
      services.moderateAdminComment(id, visibility),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ["admin-comments"] }),
  });
  return (
    <section>
      <header className="pagehead">
        <div>
          <span className="eyebrow">PLATFORM OPERATIONS</span>
          <h1>Users & moderation</h1>
          <p>
            Dados reais do admin-service, com ações protegidas e auditáveis.
          </p>
        </div>
      </header>
      {kpis.data && (
        <div className="stats">
          {Object.entries(kpis.data).map(([key, value]) => (
            <div key={key}>
              <ShieldAlert />
              <span>{key}</span>
              <strong>{String(value)}</strong>
            </div>
          ))}
        </div>
      )}
      <div className="panel">
        <h2>Usuários</h2>
        <form
          className="toolbar"
          onSubmit={(event) => {
            event.preventDefault();
            setPage(0);
            setSearch(searchInput);
          }}
        >
          <label>
            Buscar por usuário, nome ou email
            <input
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Digite para buscar"
              maxLength={200}
            />
          </label>
          <button type="submit" className="primary-action">
            Buscar
          </button>
        </form>
        {users.isLoading ? (
          <p>Carregando…</p>
        ) : users.isError ? (
          <p role="alert">Não foi possível carregar os usuários.</p>
        ) : users.data?.items.length === 0 ? (
          <p>Nenhum usuário encontrado.</p>
        ) : (
          users.data?.items.map((user) => (
            <div className="review-asset" key={user.subjectKey}>
              <span>
                <b>
                  {[user.firstName, user.lastName].filter(Boolean).join(" ") ||
                    user.username}
                </b>
                <small>
                  {user.username}
                  {user.email ? ` · ${user.email}` : ""} ·{" "}
                  {user.enabled === false ? "desabilitado" : "ativo"}
                </small>
              </span>
              <button
                className="secondary-action"
                disabled={userMutation.isPending}
                onClick={() => {
                  const action =
                    user.enabled === false ? "habilitar" : "desabilitar";
                  const reason = window
                    .prompt(`Motivo para ${action} ${user.username}:`)
                    ?.trim();
                  if (!reason) return;
                  if (!window.confirm(`Confirma ${action} este usuário?`))
                    return;
                  userMutation.mutate({
                    subjectKey: user.subjectKey,
                    enabled: user.enabled === false,
                    reason,
                  });
                }}
              >
                {user.enabled === false ? (
                  <UserCheck size={15} />
                ) : (
                  <UserX size={15} />
                )}
                {user.enabled === false ? "Habilitar" : "Desabilitar"}
              </button>
            </div>
          ))
        )}
        {userMutation.isError && (
          <p role="alert">
            Não foi possível alterar o usuário. Verifique sua permissão e tente
            novamente.
          </p>
        )}
        {users.data && users.data.totalItems > 0 && (
          <div className="review-asset-actions">
            <button
              className="secondary-action"
              disabled={page === 0}
              onClick={() => setPage((value) => value - 1)}
            >
              Anterior
            </button>
            <span>
              Página {page + 1} · {users.data.totalItems} usuários
            </span>
            <button
              className="secondary-action"
              disabled={users.data.last}
              onClick={() => setPage((value) => value + 1)}
            >
              Próxima
            </button>
          </div>
        )}
      </div>
      <div className="panel">
        <h2>Reports</h2>
        {reports.data?.length ? (
          reports.data.map((report) => (
            <p key={report.id}>
              <b>{report.reason}</b> · {report.status} · {report.reporter}
            </p>
          ))
        ) : (
          <p>Nenhum report aberto.</p>
        )}
      </div>
      <div className="panel">
        <h2>Comentários</h2>
        {comments.data?.map((comment) => (
          <div className="review-asset" key={comment.id}>
            <span>
              <b>{comment.body}</b>
              <small>
                {comment.subjectKey} · {comment.visibility}
              </small>
            </span>
            <span className="review-asset-actions">
              <button
                className="secondary-action"
                onClick={() =>
                  moderation.mutate({ id: comment.id, visibility: "VISIBLE" })
                }
              >
                Visível
              </button>
              <button
                className="secondary-action"
                onClick={() =>
                  moderation.mutate({ id: comment.id, visibility: "HIDDEN" })
                }
              >
                Ocultar
              </button>
              <button
                className="secondary-action"
                onClick={() =>
                  moderation.mutate({ id: comment.id, visibility: "REMOVED" })
                }
              >
                Remover
              </button>
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}
