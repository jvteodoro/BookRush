import type { ReactNode } from "react";
import { SearchField } from "./SearchField";
import { Icon } from "./Icon";
import { Logo } from "./Logo";

export function AppHeader({
  title,
  query,
  onQuery,
  onSearch,
  onHome,
  homeActive = false,
  actions,
  searchLabel = "Buscar livros ou pessoas",
  placeholder = "Buscar livros, pessoas…",
}: {
  title?: string;
  query?: string;
  onQuery?: (query: string) => void;
  onSearch?: () => void;
  onHome?: () => void;
  homeActive?: boolean;
  actions?: ReactNode;
  searchLabel?: string;
  placeholder?: string;
}) {
  return (
    <header
      className={`br-header${!onHome && !onSearch ? " br-header--compact" : ""}`}
    >
      <div className="br-mobile-logo">
        <Logo size={36} />
      </div>
      <span className="br-header__title">{title}</span>
      {(onHome || onSearch) && (
        <div className="br-header__center">
          {onHome && (
            <button
              type="button"
              className="br-home-shortcut"
              aria-current={homeActive ? "page" : undefined}
              aria-label="Ir para início"
              onClick={onHome}
            >
              <Icon name="home" />
            </button>
          )}
          {onSearch && (
            <form
              className="br-header-search-form"
              role="search"
              onSubmit={(event) => {
                event.preventDefault();
                onSearch();
              }}
            >
              <SearchField
                value={query}
                onChange={(event) => onQuery?.(event.target.value)}
                label={searchLabel}
                placeholder={placeholder}
                action={
                  <button type="submit" aria-label={searchLabel}>
                    <Icon name="arrow" />
                  </button>
                }
              />
            </form>
          )}
        </div>
      )}
      <div className="br-header__actions">{actions}</div>
    </header>
  );
}
