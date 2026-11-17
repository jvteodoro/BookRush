export type BibliographicMetadata = {
  authors?: string[] | null;
  firstPublicationYear?: number | null;
  publicationYear?: number | null;
};

/** Missing metadata must never be replaced with an invented author or date. */
export function bookMetadata(book: BibliographicMetadata) {
  const authors = [
    ...new Set((book.authors ?? []).map((name) => name.trim()).filter(Boolean)),
  ];
  return {
    author: authors.join(" · ") || "Autor não informado",
    publicationYear: Number.isInteger(book.firstPublicationYear)
      ? book.firstPublicationYear
      : Number.isInteger(book.publicationYear) ? book.publicationYear : null,
  };
}
