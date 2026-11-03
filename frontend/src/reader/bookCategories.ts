export type BookCategory = { id: string; name: string; scheme: string };

const mimeType =
  /^(application|audio|font|image|message|model|multipart|text|video)\/[a-z0-9.+_-]+(?:\s*;.*)?$/i;

/** Keep legacy RDF representation/type values out of reader-facing categories. */
function displayName(category: BookCategory): string {
  const name = category.name
    .trim()
    .replace(/^Category:\s*/i, "")
    .trim();
  if (mimeType.test(name)) return "";
  const scheme = category.scheme.trim().toUpperCase();
  if (scheme === "LCC" || scheme === "DDC") return "";
  if (scheme === "GUTENBERG" && (name === "Text" || /^[A-Z]{1,3}$/.test(name)))
    return "";
  return name;
}

/** Categories are bibliographic classifications, never a language or a guessed genre. */
export function categoryNames(categories?: BookCategory[] | null): string[] {
  return [...new Set((categories ?? []).map(displayName).filter(Boolean))];
}

export function categoryLabel(categories?: BookCategory[] | null): string {
  return categoryNames(categories).join(" · ") || "Sem categoria";
}
