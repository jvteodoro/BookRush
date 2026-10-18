import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ShieldAlert, UserCheck, UserX } from "lucide-react";
import { services } from "../../lib/services";

export function OperationsPage() {
  const queryClient = useQueryClient();
  const users = useQuery({
    queryKey: ["admin-users"],
    queryFn: services.listAdminUsers,
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
    }: {
      subjectKey: string;
      enabled: boolean;
    }) => services.setUserEnabled(subjectKey, enabled),
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
        {users.isLoading ? (
          <p>Carregando…</p>
        ) : (
          users.data?.map((user) => (
            <div className="review-asset" key={user.subjectKey}>
              <span>
                <b>{user.displayName || user.subjectKey}</b>
                <small>
                  {user.subjectKey} ·{" "}
                  {user.enabled === false ? "desabilitado" : "ativo"}
                </small>
              </span>
              <button
                className="secondary-action"
                disabled={userMutation.isPending}
                onClick={() =>
                  userMutation.mutate({
                    subjectKey: user.subjectKey,
                    enabled: user.enabled === false,
                  })
                }
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
