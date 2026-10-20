import { authClient } from "../auth";
import type { CatalogBook, RecommendationItem } from "./types";
import { apiRoutes } from "./apiRoutes";

export async function loadCatalog(): Promise<CatalogBook[]> {
  const response = await fetch(apiRoutes.catalog.list(), {
    credentials: "same-origin",
  });
  if (!response.ok) {
    throw new Error(
      response.status === 401
        ? "Sua sessão expirou. Entre novamente."
        : `Catálogo indisponível (HTTP ${response.status}).`,
    );
  }
  const page = (await response.json()) as { items: CatalogBook[] };
  return page.items;
}

export async function loadLibrary(): Promise<string[]> {
  const response = await authClient.fetch(apiRoutes.reader.library());
  if (!response.ok) return [];
  const rows = (await response.json()) as Array<{
    book_id?: string;
    bookId?: string;
  }>;
  return rows
    .map((row) => row.book_id ?? row.bookId)
    .filter((id): id is string => Boolean(id));
}

export async function loadRecent(): Promise<string[]> {
  const response = await authClient.fetch(apiRoutes.reader.recent());
  if (!response.ok) return [];
  const rows = (await response.json()) as Array<{
    book_id?: string;
    bookId?: string;
  }>;
  return rows
    .map((row) => row.book_id ?? row.bookId)
    .filter((id): id is string => Boolean(id));
}

export async function loadRecommendationFeed(): Promise<RecommendationItem[]> {
  const response = await authClient.fetch(apiRoutes.reader.feed());
  if (!response.ok) return [];
  const payload = (await response.json()) as { items?: RecommendationItem[] };
  return payload.items ?? [];
}

export async function loadSocialCounts(
  bookId: string,
): Promise<{ likes: number; comments: number; liked: boolean }> {
  const [likesResponse, commentsResponse] = await Promise.all([
    authClient.fetch(apiRoutes.social.likes(bookId)),
    authClient.fetch(apiRoutes.social.comments(bookId)),
  ]);
  let likes = 0;
  let liked = false;
  if (likesResponse.ok) {
    const payload = (await likesResponse.json()) as {
      count?: number;
      liked?: boolean;
    };
    likes = Number(payload.count ?? 0);
    liked = payload.liked === true;
  }
  const commentsPayload = commentsResponse.ok
    ? await commentsResponse.json()
    : [];
  const comments = Array.isArray(commentsPayload) ? commentsPayload.length : 0;
  return { likes, comments, liked };
}
