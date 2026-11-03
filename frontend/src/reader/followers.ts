import { authClient } from "../auth";
import { apiRoutes } from "../app/apiRoutes";

export type FollowerStats = {
  followerCount: number | null;
  visible: boolean;
  following: boolean;
  label: string;
};
type Fetcher = (url: string, init?: RequestInit) => Promise<Response>;

export async function loadFollowerStats(
  subject?: string,
  signal?: AbortSignal,
  fetcher: Fetcher = authClient.fetch.bind(authClient),
): Promise<FollowerStats> {
  const response = await fetcher(apiRoutes.social.followers(subject), {
    signal,
  });
  if (!response.ok) {
    throw new Error(
      response.status === 401
        ? "Sua sessão expirou. Entre novamente."
        : "Não foi possível carregar os seguidores.",
    );
  }
  const data = await response.json();
  if (
    typeof data.visible !== "boolean" ||
    typeof data.following !== "boolean" ||
    (data.visible &&
      (!Number.isSafeInteger(data.followerCount) || data.followerCount < 0))
  ) {
    throw new Error("Não foi possível carregar os seguidores.");
  }
  const count = data.visible ? data.followerCount : null;
  return {
    followerCount: count,
    visible: data.visible,
    following: data.following,
    label:
      count === null
        ? "Seguidores privados"
        : `${count.toLocaleString("pt-BR")} ${count === 1 ? "seguidor" : "seguidores"}`,
  };
}
