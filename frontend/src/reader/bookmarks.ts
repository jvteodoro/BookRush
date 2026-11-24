export type Bookmark = {
  id: string;
  name?: string;
  locator: Record<string, unknown> | null;
  created_at?: string;
};

export function bookmarkName(value: string): string {
  const name = value.trim();
  if (!name || name.length > 120)
    throw new Error("Informe um nome de 1 a 120 caracteres.");
  return name;
}

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
      name?: string,
    ): Promise<Bookmark> {
      const response = await fetcher(path(bookId), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          locator,
          ...(name === undefined ? {} : { name: bookmarkName(name) }),
        }),
      });
      if (!response.ok)
        throw new Error(
          `Não foi possível salvar a marcação (HTTP ${response.status}).`,
        );
      return parseBookmark(await response.json());
    },
    async rename(
      bookId: string,
      bookmarkId: string,
      name: string,
    ): Promise<Bookmark> {
      const response = await fetcher(
        `${path(bookId)}/${encodeURIComponent(bookmarkId)}`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name: bookmarkName(name) }),
        },
      );
      if (!response.ok)
        throw new Error(
          `Não foi possível editar o nome (HTTP ${response.status}).`,
        );
      return parseBookmark(await response.json());
    },
    async remove(bookId: string, bookmarkId: string): Promise<void> {
      const response = await fetcher(
        `${path(bookId)}/${encodeURIComponent(bookmarkId)}`,
        { method: "DELETE" },
      );
      if (!response.ok)
        throw new Error(
          `Não foi possível excluir a marcação (HTTP ${response.status}).`,
        );
    },
  };
}
