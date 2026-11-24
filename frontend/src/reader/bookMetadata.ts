export type BibliographicMetadata = {
  authors?: string[] | null;
};

/** Missing metadata must never be replaced with an invented author. */
export function bookMetadata(book: BibliographicMetadata) {
  const authors = [
    ...new Set((book.authors ?? []).map((name) => name.trim()).filter(Boolean)),
  ];
  return {
    author: authors.join(" · ") || "Autor não informado",
  };
}
