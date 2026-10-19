import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Eye,
  EyeOff,
  Search,
  ShieldAlert,
  Trash2,
  UserCheck,
  UserX,
} from "lucide-react";
import { services, type AdminComment } from "../../lib/services";

const commentStatus = {
  VISIBLE: { label: "Visível", className: "state-visible" },
  HIDDEN: { label: "Oculto", className: "state-hidden" },
  REMOVED: { label: "Removido", className: "state-removed" },
} as const;

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
    onSuccess: (_data, variables) => {
      queryClient.setQueryData<AdminComment[]>(["admin-comments"], (current) =>
        current?.map((comment) =>
          comment.id === variables.id
            ? { ...comment, visibility: variables.visibility }
            : comment,
        ),
      );
      void queryClient.invalidateQueries({ queryKey: ["admin-comments"] });
    },
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
          className="user-search"
          onSubmit={(event) => {
            event.preventDefault();
            setPage(0);
            setSearch(searchInput);
          }}
        >
          <label className="user-search-label">
            <span>Buscar por usuário, nome ou e-mail</span>
            <span className="user-search-field">
              <Search size={18} aria-hidden="true" />
              <input
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                placeholder="Nome, usuário ou e-mail"
                maxLength={200}
              />
            </span>
          </label>
          <button type="submit" className="primary-action">
            Buscar
          </button>
        </form>
        {users.isLoading ? (
          <p>Carregando…</p>
        ) : users.isError ? (
          <div className="user-load-error" role="alert">
            <div>
              <strong>Não foi possível carregar os usuários.</strong>
              <span>
                {users.error instanceof Error
                  ? users.error.message
                  : "Verifique a integração com o Keycloak e tente novamente."}
              </span>
            </div>
            <button
              type="button"
              className="secondary-action"
              onClick={() => void users.refetch()}
            >
              Tentar novamente
            </button>
          </div>
        ) : users.data?.items.length === 0 ? (
          <p>Nenhum usuário encontrado.</p>
        ) : (
          users.data?.items.map((user) => (
            <div className="review-asset" key={user.subjectKey}>
              <span className="moderation-copy">
                <b>
                  {[user.firstName, user.lastName].filter(Boolean).join(" ") ||
                    user.username}
                </b>
                <small>
                  {user.username}
                  {user.email ? ` · ${user.email}` : ""}
                </small>
                <span
                  className={`state-badge ${user.enabled ? "state-active" : "state-banned"}`}
                >
                  {user.enabled ? "Ativo" : "Banido"}
                </span>
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
                {user.enabled === false ? "Desbanir" : "Banir"}
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
        <div className="moderation-section-heading">
          <div>
            <h2>Denúncias</h2>
            <p>Comentários e livros denunciados pelos leitores para revisão.</p>
          </div>
          {reports.data && (
            <span className="section-count">{reports.data.length}</span>
          )}
        </div>
        {reports.isLoading ? (
          <p>Carregando denúncias…</p>
        ) : reports.isError ? (
          <p role="alert" className="moderation-error">
            Não foi possível carregar as denúncias.
          </p>
        ) : reports.data?.length ? (
          reports.data.map((report) => (
            <div className="report-card" key={report.id}>
              <div className="moderation-copy">
                <b>{report.reason}</b>
                <small>Enviado por {report.reporter}</small>
                {report.commentId ? (
                  <code>Comentário: {report.commentId}</code>
                ) : (
                  <code>Livro: {report.bookId ?? "não informado"}</code>
                )}
              </div>
              <span
                className={`state-badge ${report.status === "OPEN" ? "state-open" : "state-resolved"}`}
              >
                {report.status === "OPEN"
                  ? "Aguardando revisão"
                  : report.status}
              </span>
            </div>
          ))
        ) : (
          <p>Nenhuma denúncia registrada.</p>
        )}
      </div>
      <div className="panel">
        <div className="moderation-section-heading">
          <div>
            <h2>Comentários</h2>
            <p>Altere a visibilidade; o estado atual aparece destacado.</p>
          </div>
          {comments.data && (
            <span className="section-count">{comments.data.length}</span>
          )}
        </div>
        {comments.isLoading ? (
          <p>Carregando comentários…</p>
        ) : comments.isError ? (
          <p role="alert" className="moderation-error">
            Não foi possível carregar os comentários.
          </p>
        ) : comments.data?.length === 0 ? (
          <p>Nenhum comentário publicado.</p>
        ) : (
          comments.data?.map((comment) => {
            const state =
              commentStatus[comment.visibility as keyof typeof commentStatus] ??
              commentStatus.VISIBLE;
            const pending =
              moderation.isPending && moderation.variables?.id === comment.id;
            return (
              <div className="review-asset" key={comment.id}>
                <span className="moderation-copy">
                  <b>{comment.body}</b>
                  <small>Autor: {comment.subjectKey}</small>
                  <span className={`state-badge ${state.className}`}>
                    {pending ? "Atualizando…" : state.label}
                  </span>
                </span>
                <span className="review-asset-actions">
                  <button
                    className={`secondary-action ${comment.visibility === "VISIBLE" ? "state-action-selected" : ""}`}
                    disabled={
                      moderation.isPending || comment.visibility === "VISIBLE"
                    }
                    onClick={() =>
                      moderation.mutate({
                        id: comment.id,
                        visibility: "VISIBLE",
                      })
                    }
                  >
                    <Eye size={15} /> Tornar visível
                  </button>
                  <button
                    className={`secondary-action ${comment.visibility === "HIDDEN" ? "state-action-selected" : ""}`}
                    disabled={
                      moderation.isPending || comment.visibility === "HIDDEN"
                    }
                    onClick={() =>
                      moderation.mutate({
                        id: comment.id,
                        visibility: "HIDDEN",
                      })
                    }
                  >
                    <EyeOff size={15} /> Ocultar
                  </button>
                  <button
                    className={`secondary-action danger-action ${comment.visibility === "REMOVED" ? "state-action-selected" : ""}`}
                    disabled={
                      moderation.isPending || comment.visibility === "REMOVED"
                    }
                    onClick={() =>
                      moderation.mutate({
                        id: comment.id,
                        visibility: "REMOVED",
                      })
                    }
                  >
                    <Trash2 size={15} /> Remover
                  </button>
                </span>
              </div>
            );
          })
        )}
        {moderation.isError && (
          <p role="alert" className="moderation-error">
            Não foi possível atualizar o comentário. Tente novamente.
          </p>
        )}
      </div>
    </section>
  );
}
