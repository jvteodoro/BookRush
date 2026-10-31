import { CommentModeration } from "./CommentModeration";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Search, ShieldAlert, UserCheck, UserX } from "lucide-react";
import { services, type AdminReport } from "../../lib/services";

const reportStatus = {
  OPEN: { label: "Aguardando revisão", className: "state-open" },
  IN_REVIEW: { label: "Em análise", className: "state-hidden" },
  RESOLVED: { label: "Resolvida", className: "state-resolved" },
  DISMISSED: { label: "Ignorada", className: "state-dismissed" },
} as const;

export function OperationsPage() {
  const queryClient = useQueryClient();
  const [commentNavigation, setCommentNavigation] = useState({
    id: "",
    revision: 0,
  });
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
  const reportMutation = useMutation({
    mutationFn: ({
      id,
      status,
      note,
    }: {
      id: string;
      status: string;
      note: string;
    }) => services.updateAdminReportStatus(id, status, note),
    onSuccess: (_data, variables) => {
      queryClient.setQueryData<AdminReport[]>(["admin-reports"], (current) =>
        current?.map((report) =>
          report.id === variables.id
            ? {
                ...report,
                status: variables.status,
                resolutionNote: variables.note,
              }
            : report,
        ),
      );
      void queryClient.invalidateQueries({ queryKey: ["admin-reports"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-kpis"] });
    },
  });
  const changeReportStatus = (id: string, status: string) => {
    const terminal = status === "RESOLVED" || status === "DISMISSED";
    const note = terminal
      ? window
          .prompt(
            status === "RESOLVED"
              ? "Como esta denúncia foi resolvida?"
              : "Por que esta denúncia deve ser ignorada?",
          )
          ?.trim()
      : "";
    if (terminal && !note) return;
    reportMutation.mutate({ id, status, note: note ?? "" });
  };
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
          reports.data.map((report) => {
            const state =
              reportStatus[report.status as keyof typeof reportStatus] ??
              reportStatus.OPEN;
            const pending =
              reportMutation.isPending &&
              reportMutation.variables?.id === report.id;
            const actionable =
              report.status === "OPEN" || report.status === "IN_REVIEW";
            return (
              <div className="report-card" key={report.id}>
                <div className="moderation-copy">
                  <b>{report.reason}</b>
                  <small>
                    Denunciado por: <strong>{report.reporterName}</strong>
                  </small>
                  {report.commentId ? (
                    <>
                      <small>
                        Autor do comentário:{" "}
                        <strong>
                          {report.commentAuthorName ?? "Não identificado"}
                        </strong>
                      </small>
                      {report.commentBody && (
                        <blockquote>{report.commentBody}</blockquote>
                      )}
                      <code>Comentário: {report.commentId}</code>
                    </>
                  ) : (
                    <code>Livro: {report.bookId ?? "não informado"}</code>
                  )}
                  {report.resolutionNote && (
                    <small>Decisão: {report.resolutionNote}</small>
                  )}
                </div>
                <div className="report-actions">
                  <span className={`state-badge ${state.className}`}>
                    {pending ? "Atualizando…" : state.label}
                  </span>
                  {report.commentId && (
                    <button
                      type="button"
                      className="secondary-action"
                      onClick={() => {
                        setCommentNavigation((current) => ({
                          id: report.commentId ?? "",
                          revision: current.revision + 1,
                        }));
                        document
                          .getElementById("comment-moderation")
                          ?.scrollIntoView({ behavior: "smooth" });
                      }}
                    >
                      Moderar comentário
                    </button>
                  )}
                  {report.status === "OPEN" && (
                    <button
                      className="secondary-action"
                      disabled={reportMutation.isPending}
                      onClick={() => changeReportStatus(report.id, "IN_REVIEW")}
                    >
                      Analisar
                    </button>
                  )}
                  {actionable && (
                    <button
                      className="secondary-action"
                      disabled={reportMutation.isPending}
                      onClick={() => changeReportStatus(report.id, "RESOLVED")}
                    >
                      Resolver
                    </button>
                  )}
                  {actionable && (
                    <button
                      className="secondary-action"
                      disabled={reportMutation.isPending}
                      onClick={() => changeReportStatus(report.id, "DISMISSED")}
                    >
                      Ignorar
                    </button>
                  )}
                  {(report.status === "RESOLVED" ||
                    report.status === "DISMISSED") && (
                    <button
                      className="secondary-action"
                      disabled={reportMutation.isPending}
                      onClick={() => changeReportStatus(report.id, "OPEN")}
                    >
                      Reabrir
                    </button>
                  )}
                </div>
              </div>
            );
          })
        ) : (
          <p>Nenhuma denúncia registrada.</p>
        )}
        {reportMutation.isError && (
          <p role="alert" className="moderation-error">
            Não foi possível atualizar a denúncia. Informe uma justificativa
            válida e tente novamente.
          </p>
        )}
      </div>
      <CommentModeration
        key={commentNavigation.revision}
        initialSearch={commentNavigation.id}
      />
    </section>
  );
}
