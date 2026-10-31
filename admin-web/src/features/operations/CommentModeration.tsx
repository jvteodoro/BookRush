import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Eye, EyeOff, History, Search, Trash2 } from "lucide-react";
import { services, type AdminComment } from "../../lib/services";

const states = {
  VISIBLE: {
    label: "Visível",
    action: "Restaurar",
    className: "state-visible",
    icon: Eye,
  },
  HIDDEN: {
    label: "Oculto",
    action: "Ocultar",
    className: "state-hidden",
    icon: EyeOff,
  },
  REMOVED: {
    label: "Removido",
    action: "Remover",
    className: "state-removed",
    icon: Trash2,
  },
} as const;
type Visibility = keyof typeof states;

function date(value?: string) {
  if (!value) return "";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "" : parsed.toLocaleString("pt-BR");
}

function CommentCard({
  comment,
  busy,
  onConfirm,
}: {
  comment: AdminComment;
  busy: boolean;
  onConfirm: (
    id: string,
    visibility: Visibility,
    reason: string,
    done: () => void,
  ) => void;
}) {
  const [decision, setDecision] = useState<Visibility>();
  const [reason, setReason] = useState("");
  const [showHistory, setShowHistory] = useState(false);
  const history = useQuery({
    queryKey: ["comment-history", comment.id],
    queryFn: () => services.commentHistory(comment.id),
    enabled: showHistory,
  });
  const state = states[comment.visibility as Visibility] ?? states.VISIBLE;
  return (
    <article className="comment-card">
      <div className="review-asset">
        <div className="moderation-copy">
          <b>{comment.body}</b>
          <small>
            Autor: <strong>{comment.authorName}</strong>
          </small>
          <code>Livro: {comment.bookId}</code>
          <small>Publicado: {date(comment.createdAt)}</small>
          <span className={`state-badge ${state.className}`}>
            {state.label}
          </span>
          {comment.reasonCode && (
            <small>Último motivo: {comment.reasonCode}</small>
          )}
          {comment.updatedBy && (
            <small>
              Moderado por {comment.updatedBy} · {date(comment.updatedAt)}
            </small>
          )}
        </div>
        <div className="review-asset-actions">
          {(
            Object.entries(states) as [
              Visibility,
              (typeof states)[Visibility],
            ][]
          ).map(([visibility, action]) => {
            const Icon = action.icon;
            return (
              <button
                type="button"
                key={visibility}
                className={`secondary-action ${visibility === "REMOVED" ? "danger-action" : ""}`}
                disabled={busy || comment.visibility === visibility}
                onClick={() => {
                  setDecision(visibility);
                  setReason("");
                }}
              >
                <Icon size={15} /> {action.action}
              </button>
            );
          })}
          <button
            type="button"
            className="secondary-action"
            aria-expanded={showHistory}
            onClick={() => setShowHistory((value) => !value)}
          >
            <History size={15} /> Histórico
          </button>
        </div>
      </div>
      {decision && (
        <form
          className="comment-decision"
          onSubmit={(event) => {
            event.preventDefault();
            if (reason.trim().length < 3 || reason.trim().length > 80 || busy)
              return;
            onConfirm(comment.id, decision, reason.trim(), () =>
              setDecision(undefined),
            );
          }}
        >
          <strong>
            Confirmar: {states[decision].action.toLowerCase()} comentário
          </strong>
          <p>
            {decision === "VISIBLE"
              ? "O comentário voltará a aparecer para os leitores."
              : "O comentário deixará de aparecer para os leitores. O conteúdo será preservado para revisão."}
          </p>
          <label>
            Justificativa (3 a 80 caracteres)
            <textarea
              value={reason}
              required
              minLength={3}
              maxLength={80}
              rows={2}
              disabled={busy}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
          <div className="review-asset-actions">
            <button
              type="submit"
              className="primary-action"
              disabled={busy || reason.trim().length < 3}
            >
              {busy ? "Salvando…" : "Confirmar decisão"}
            </button>
            <button
              type="button"
              className="secondary-action"
              disabled={busy}
              onClick={() => setDecision(undefined)}
            >
              Cancelar
            </button>
          </div>
        </form>
      )}
      {showHistory && (
        <div className="comment-history">
          <h3>Histórico de moderação</h3>
          {history.isLoading ? (
            <p>Carregando histórico…</p>
          ) : history.isError ? (
            <div role="alert">
              <p>Não foi possível carregar o histórico.</p>
              <button
                type="button"
                className="secondary-action"
                onClick={() => void history.refetch()}
              >
                Tentar novamente
              </button>
            </div>
          ) : history.data?.length ? (
            <ol>
              {history.data.map((entry) => (
                <li key={entry.id}>
                  <strong>
                    {states[entry.previousVisibility as Visibility]?.label ??
                      entry.previousVisibility}{" "}
                    →{" "}
                    {states[entry.visibility as Visibility]?.label ??
                      entry.visibility}
                  </strong>
                  <p>{entry.reason}</p>
                  <small>
                    {entry.actor} · {date(entry.createdAt)}
                  </small>
                </li>
              ))}
            </ol>
          ) : (
            <p>Nenhuma decisão registrada.</p>
          )}
        </div>
      )}
    </article>
  );
}

export function CommentModeration({
  initialSearch = "",
}: {
  initialSearch?: string;
}) {
  const client = useQueryClient();
  const [input, setInput] = useState(initialSearch);
  const [search, setSearch] = useState(initialSearch);
  const [visibility, setVisibility] = useState("");
  const [page, setPage] = useState(0);
  const comments = useQuery({
    queryKey: ["admin-comments", search, visibility, page],
    queryFn: () => services.listAdminComments(search, visibility, page),
  });
  const moderation = useMutation({
    mutationFn: ({
      id,
      visibility,
      reason,
    }: {
      id: string;
      visibility: Visibility;
      reason: string;
    }) => services.moderateAdminComment(id, visibility, reason),
    onSuccess: (_data, variables) => {
      void client.invalidateQueries({ queryKey: ["admin-comments"] });
      void client.invalidateQueries({
        queryKey: ["comment-history", variables.id],
      });
      void client.invalidateQueries({ queryKey: ["admin-kpis"] });
    },
  });
  return (
    <div className="panel" id="comment-moderation">
      <div className="moderation-section-heading">
        <div>
          <h2>Comentários</h2>
          <p>
            Revise, oculte, remova ou restaure comentários com justificativa e
            histórico.
          </p>
        </div>
        {comments.data && (
          <span className="section-count">{comments.data.totalItems}</span>
        )}
      </div>
      <form
        className="user-search"
        onSubmit={(event) => {
          event.preventDefault();
          setSearch(input.trim());
          setPage(0);
          moderation.reset();
        }}
      >
        <label className="user-search-label">
          <span>Buscar por comentário, autor ou livro</span>
          <span className="user-search-field">
            <Search size={18} aria-hidden="true" />
            <input
              value={input}
              onChange={(event) => setInput(event.target.value)}
              maxLength={200}
              placeholder="Texto, autor ou ID do livro"
            />
          </span>
        </label>
        <label className="user-search-label">
          <span>Visibilidade</span>
          <select
            value={visibility}
            onChange={(event) => {
              setVisibility(event.target.value);
              setPage(0);
            }}
          >
            <option value="">Todas</option>
            {Object.entries(states).map(([value, state]) => (
              <option key={value} value={value}>
                {state.label}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="primary-action">
          Buscar comentários
        </button>
      </form>
      {moderation.isError && (
        <p role="alert" className="moderation-error">
          Não foi possível atualizar o comentário. Verifique sua permissão e
          tente novamente.
        </p>
      )}
      {moderation.isSuccess && (
        <p role="status">Decisão registrada com sucesso.</p>
      )}
      {comments.isLoading ? (
        <p>Carregando comentários…</p>
      ) : comments.isError ? (
        <div role="alert" className="user-load-error">
          <p>Não foi possível carregar os comentários.</p>
          <button
            type="button"
            className="secondary-action"
            onClick={() => void comments.refetch()}
          >
            Tentar novamente
          </button>
        </div>
      ) : comments.data?.items.length ? (
        comments.data.items.map((comment) => (
          <CommentCard
            key={comment.id}
            comment={comment}
            busy={moderation.isPending}
            onConfirm={(id, visibility, reason, done) =>
              moderation.mutate({ id, visibility, reason }, { onSuccess: done })
            }
          />
        ))
      ) : (
        <p>Nenhum comentário encontrado.</p>
      )}
      {comments.data && comments.data.totalItems > 0 && (
        <div className="review-asset-actions">
          <button
            type="button"
            className="secondary-action"
            disabled={page === 0 || comments.isFetching || moderation.isPending}
            onClick={() => setPage((value) => value - 1)}
          >
            Anterior
          </button>
          <span>
            Página {page + 1} · {comments.data.totalItems} comentários
          </span>
          <button
            type="button"
            className="secondary-action"
            disabled={
              comments.data.last || comments.isFetching || moderation.isPending
            }
            onClick={() => setPage((value) => value + 1)}
          >
            Próxima
          </button>
        </div>
      )}
    </div>
  );
}
