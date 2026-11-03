export type BookCategory = { id: string; name: string; scheme: string };

/** Categories are bibliographic classifications, never a language or a guessed genre. */
export function categoryNames(categories?: BookCategory[] | null): string[] {
  return [
    ...new Set(
      (categories ?? [])
        .map((category) => category.name.trim())
        .filter(Boolean),
    ),
  ];
}

export function categoryLabel(categories?: BookCategory[] | null): string {
  return categoryNames(categories).join(" · ") || "Sem categoria";
}
