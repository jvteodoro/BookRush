import { authClient } from "../auth";
import { apiRoutes } from "../app/apiRoutes";

export type RatingSummary = {
  bookId: string;
  totalRatings: number;
  averageRating?: number | null;
  myRating?: number | null;
};
type Fetcher = (url: string, init?: RequestInit) => Promise<Response>;
const authenticated: Fetcher = (url, init) => authClient.fetch(url, init);

async function result(response: Response): Promise<RatingSummary> {
  if (!response.ok)
    throw new Error(
      response.status === 401
        ? "Sua sessão expirou. Entre novamente."
        : response.status === 403
          ? "Você não tem permissão para acessar as avaliações."
          : response.status === 404
            ? "Este livro não está mais disponível."
            : "Não foi possível atualizar as avaliações. Tente novamente.",
    );
  return (await response.json()) as RatingSummary;
}
export async function loadRatings(
  bookId: string,
  signal?: AbortSignal,
  fetcher: Fetcher = authenticated,
) {
  return result(await fetcher(apiRoutes.social.ratings(bookId), { signal }));
}
export async function saveRating(
  bookId: string,
  stars: number,
  fetcher: Fetcher = authenticated,
) {
  if (!Number.isInteger(stars) || stars < 1 || stars > 5)
    throw new Error("Escolha de 1 a 5 estrelas.");
  return result(
    await fetcher(apiRoutes.social.ratings(bookId), {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ stars }),
    }),
  );
}
export async function removeRating(
  bookId: string,
  fetcher: Fetcher = authenticated,
) {
  return result(
    await fetcher(apiRoutes.social.ratings(bookId), { method: "DELETE" }),
  );
}
export function ratingLabel(summary: RatingSummary) {
  if (!summary.totalRatings)
    return "Ainda não há avaliações. Seja a primeira pessoa a avaliar!";
  const average = new Intl.NumberFormat("pt-BR", {
    maximumFractionDigits: 2,
  }).format(summary.averageRating ?? 0);
  return `${average} de 5 estrelas · ${summary.totalRatings} ${summary.totalRatings === 1 ? "avaliação" : "avaliações"}`;
}
