import type { ReaderPreferences } from "../app/types";

export const fonts = {
  serif: { name: "Georgia", family: 'Georgia, "Times New Roman", serif' },
  sans: { name: "Arial", family: "Arial, Helvetica, sans-serif" },
  literary: {
    name: "Palatino",
    family: 'Palatino, "Palatino Linotype", "Book Antiqua", serif',
  },
  verdana: { name: "Verdana", family: "Verdana, Geneva, sans-serif" },
  times: { name: "Times New Roman", family: '"Times New Roman", Times, serif' },
} as const;

export const themes = {
  white: { name: "Branco", background: "#ffffff", foreground: "#252525" },
  light: { name: "Claro", background: "#f7f4ee", foreground: "#29261f" },
  rose: { name: "Rosé", background: "#f5efef", foreground: "#392e32" },
  mist: { name: "Névoa", background: "#edf0f0", foreground: "#293333" },
  sepia: { name: "Sépia", background: "#f1e7c8", foreground: "#29261f" },
  graphite: { name: "Grafite", background: "#292e30", foreground: "#e5e7e7" },
  blue: { name: "Azul noite", background: "#1c2a3a", foreground: "#e4eaf2" },
  night: { name: "Noturno", background: "#1b1b1b", foreground: "#f7f4ee" },
} as const;

export const spacing = [1.5, 1.8, 2.1] as const;
export const textWidths = {
  narrow: { name: "Estreita", maxWidth: "46rem" },
  comfortable: { name: "Confortável", maxWidth: "68rem" },
  wide: { name: "Ampla", maxWidth: "none" },
} as const;
export const storageKey = "bookrush-reader-preferences";
export const defaults: ReaderPreferences = {
  fontFamily: "serif",
  fontSize: 100,
  lineHeight: 1.8,
  textWidth: "narrow",
  foreground: themes.light.foreground,
  background: themes.light.background,
};

export function normalizePreferences(value: unknown): ReaderPreferences {
  const raw =
    value && typeof value === "object"
      ? (value as Partial<ReaderPreferences>)
      : {};
  const fontFamily =
    raw.fontFamily && Object.hasOwn(fonts, raw.fontFamily)
      ? raw.fontFamily
      : "serif";
  const size = Number(raw.fontSize);
  const height = Number(raw.lineHeight);
  const lineHeight =
    Number.isFinite(height) && height > 0
      ? spacing.reduce(
          (best, option) =>
            Math.abs(option - height) < Math.abs(best - height) ? option : best,
          spacing[0],
        )
      : defaults.lineHeight;
  const theme =
    Object.values(themes).find((item) => item.background === raw.background) ??
    themes.light;
  return {
    fontFamily,
    fontSize:
      Number.isFinite(size) && size > 0
        ? Math.round(Math.min(170, Math.max(85, size)) / 5) * 5
        : 100,
    lineHeight,
    textWidth:
      raw.textWidth && Object.hasOwn(textWidths, raw.textWidth)
        ? raw.textWidth
        : defaults.textWidth,
    foreground: theme.foreground,
    background: theme.background,
  };
}

export function readPreferences(): ReaderPreferences {
  try {
    return normalizePreferences(
      JSON.parse(localStorage.getItem(storageKey) ?? "{}"),
    );
  } catch {
    return { ...defaults };
  }
}

export function savePreferences(value: ReaderPreferences): boolean {
  try {
    localStorage.setItem(
      storageKey,
      JSON.stringify(normalizePreferences(value)),
    );
    return true;
  } catch {
    return false;
  }
}
