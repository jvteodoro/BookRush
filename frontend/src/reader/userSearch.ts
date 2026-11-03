import { authClient } from "../auth";
import { apiRoutes } from "../app/apiRoutes";

export type SearchUser = {
  subject: string;
  displayName: string | null;
  username: string | null;
};
export type UserSearchResult = {
  items: SearchUser[];
  totalItems: number;
  totalPages: number;
  page: number;
};
type Fetcher = (url: string, init?: RequestInit) => Promise<Response>;

export async function searchUsers(
  query: string,
  page = 0,
  signal?: AbortSignal,
  fetcher: Fetcher = (url, init) => authClient.fetch(url, init),
): Promise<UserSearchResult> {
  const normalized = query.trim().replace(/^@/, "").trim();
  if (!normalized) return { items: [], totalItems: 0, totalPages: 0, page: 0 };
  const params = new URLSearchParams({
    q: normalized,
    page: String(Math.max(0, page)),
    size: "20",
  });
  const response = await fetcher(apiRoutes.profile.search(params.toString()), {
    signal,
  });
  if (!response.ok) {
    throw new Error(
      response.status === 401
        ? "Sua sessão expirou. Entre novamente."
        : response.status === 403
          ? "Você não tem permissão para pesquisar pessoas."
          : "Não foi possível buscar as pessoas. Tente novamente.",
    );
  }
  return (await response.json()) as UserSearchResult;
}
