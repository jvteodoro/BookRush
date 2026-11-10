import { authClient } from "../auth";

export type BookComment = {
  id: string;
  body?: string;
  parentCommentId?: string | null;
  available?: boolean;
  subject_key?: string;
  authorName?: string;
  canDelete?: boolean;
  created_at?: string;
};
type Fetcher = (url: string, init?: RequestInit) => Promise<Response>;
export async function loadComments(
  bookId: string,
  signal?: AbortSignal,
  fetcher: Fetcher = authClient.fetch.bind(authClient),
): Promise<BookComment[]> {
  const response = await fetcher(
    `${apiRoutes.social.comments(bookId)}?includeUnavailable=true`,
    { signal },
  );
  if (!response.ok)
    throw new Error(
      response.status === 401
        ? "Sua sessão expirou. Entre novamente."
        : "Não foi possível carregar os comentários.",
    );
  return response.json();
}
export async function postComment(
  bookId: string,
  body: string,
  fetcher: Fetcher = authClient.fetch.bind(authClient),
  parentCommentId?: string | null,
): Promise<BookComment> {
  const text = body.trim();
  if (!text || text.length > 2000)
    throw new Error("Escreva um comentário com até 2000 caracteres.");
  const response = await fetcher(apiRoutes.social.comments(bookId), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      body: text,
      ...(parentCommentId ? { parentCommentId } : {}),
    }),
  });
  if (!response.ok)
    throw new Error(
      response.status === 401
        ? "Sua sessão expirou. Entre novamente."
        : response.status === 409
          ? "O comentário ao qual você respondeu não está mais disponível. Atualize a conversa; seu texto foi preservado."
          : "Não foi possível publicar o comentário. Seu texto foi preservado.",
    );
  return response.json();
}
export async function reportComment(
  bookId: string,
  commentId: string,
  reason: string,
  fetcher: Fetcher = authClient.fetch.bind(authClient),
): Promise<void> {
  const detail = reason.trim();
  if (!detail || detail.length > 120)
    throw new Error("Informe um motivo com até 120 caracteres.");
  const response = await fetcher(apiRoutes.social.reports(), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ bookId, commentId, reason: detail }),
  });
  if (!response.ok)
    throw new Error(
      response.status === 401
        ? "Sua sessão expirou. Entre novamente."
        : "Não foi possível denunciar o comentário.",
    );
}
import { apiRoutes } from "../app/apiRoutes";

export async function deleteComment(
  commentId: string,
  fetcher: Fetcher = authClient.fetch.bind(authClient),
): Promise<void> {
  const response = await fetcher(apiRoutes.social.comment(commentId), {
    method: "DELETE",
  });
  if (!response.ok) {
    const messages: Record<number, string> = {
      401: "Sua sessão expirou. Entre novamente.",
      403: "Você só pode remover os próprios comentários.",
      404: "Comentário não encontrado. Atualize a lista.",
    };
    throw new Error(
      messages[response.status] ??
        "Não foi possível remover o comentário. Tente novamente.",
    );
  }
}

export function visibleCommentCount(rows: BookComment[]): number {
  return rows.filter((row) => row.available !== false).length;
}

export function threadRows(
  rows: BookComment[],
  collapsed: Set<string>,
): Array<{ comment: BookComment; depth: number; children: number }> {
  const ids = new Set(rows.map((row) => row.id));
  const children = new Map<string | null, BookComment[]>();
  for (const row of rows) {
    const parent =
      row.parentCommentId && ids.has(row.parentCommentId)
        ? row.parentCommentId
        : null;
    const group = children.get(parent) ?? [];
    group.push(row);
    children.set(parent, group);
  }
  const result: Array<{
    comment: BookComment;
    depth: number;
    children: number;
  }> = [];
  const stack = (children.get(null) ?? [])
    .slice()
    .reverse()
    .map((comment) => ({ comment, depth: 0 }));
  const visited = new Set<string>();
  while (stack.length) {
    const item = stack.pop()!;
    if (visited.has(item.comment.id)) continue;
    visited.add(item.comment.id);
    const replies = children.get(item.comment.id) ?? [];
    result.push({ ...item, children: replies.length });
    if (!collapsed.has(item.comment.id)) {
      for (let i = replies.length - 1; i >= 0; i--)
        stack.push({ comment: replies[i], depth: item.depth + 1 });
    }
  }
  return result;
}
