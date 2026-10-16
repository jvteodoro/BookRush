import { authClient } from "../auth";

export type BookComment = {
  id: string;
  body: string;
  subject_key?: string;
  authorName?: string;
  created_at?: string;
};
type Fetcher = (url: string, init?: RequestInit) => Promise<Response>;
export async function loadComments(
  bookId: string,
  signal?: AbortSignal,
  fetcher: Fetcher = authClient.fetch.bind(authClient),
): Promise<BookComment[]> {
  const response = await fetcher(apiRoutes.social.comments(bookId), { signal });
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
): Promise<BookComment> {
  const text = body.trim();
  if (!text || text.length > 2000)
    throw new Error("Escreva um comentário com até 2000 caracteres.");
  const response = await fetcher(apiRoutes.social.comments(bookId), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ body: text }),
  });
  if (!response.ok)
    throw new Error(
      response.status === 401
        ? "Sua sessão expirou. Entre novamente."
        : "Não foi possível publicar o comentário. Seu texto foi preservado.",
    );
  return response.json();
}
import { apiRoutes } from "../app/apiRoutes";
