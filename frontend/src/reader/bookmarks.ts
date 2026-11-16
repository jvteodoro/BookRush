export type Bookmark = {
  id: string;
  locator: Record<string, unknown> | null;
  created_at?: string;
};

export function parseBookmark(value: Bookmark): Bookmark {
  let locator = value.locator;
  if (typeof locator === "string") {
    try {
      locator = JSON.parse(locator);
    } catch {
      locator = null;
    }
  }
  if (
    !locator ||
    typeof locator !== "object" ||
    typeof locator.href !== "string" ||
    typeof locator.type !== "string"
  )
    locator = null;
  return { ...value, locator };
}

export function createBookmarksClient(
  fetcher: (url: string, init?: RequestInit) => Promise<Response>,
  path: (bookId: string) => string,
) {
  return {
    async list(bookId: string): Promise<Bookmark[]> {
      const response = await fetcher(path(bookId));
      if (!response.ok)
        throw new Error(
          `Não foi possível carregar as marcações (HTTP ${response.status}).`,
        );
      return (await response.json()).map(parseBookmark);
    },
    async save(
      bookId: string,
      locator: Record<string, unknown>,
    ): Promise<Bookmark> {
      const response = await fetcher(path(bookId), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ locator }),
      });
      if (!response.ok)
        throw new Error(
          `Não foi possível salvar a marcação (HTTP ${response.status}).`,
        );
      return parseBookmark(await response.json());
    },
  };
}
