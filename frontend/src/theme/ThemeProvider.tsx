import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

export type Theme = 'light' | 'dark';
const storageKey = 'bookrush-theme';
const ThemeContext = createContext<{ theme: Theme; toggle: () => void; setTheme: (theme: Theme) => void } | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(() => {
    try {
      const saved = localStorage.getItem(storageKey);
      if (saved === 'light' || saved === 'dark') return saved;
    } catch { /* Theme also works when browser storage is unavailable. */ }
    return 'dark';
  });
  useEffect(() => {
    document.documentElement.dataset.bookrushTheme = theme;
    try { localStorage.setItem(storageKey, theme); } catch { /* Preference stays in memory. */ }
  }, [theme]);
  return <ThemeContext.Provider value={{ theme, setTheme, toggle: () => setTheme(current => current === 'dark' ? 'light' : 'dark') }}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme precisa estar dentro de ThemeProvider.');
  return context;
}
