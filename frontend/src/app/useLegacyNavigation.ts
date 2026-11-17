import { useEffect, useState } from "react";
import { sharedBookPath } from "../reader/sharedBook";

export type ReaderTab = "feed" | "library" | "search" | "recommendations";

const readerTabs = new Set<ReaderTab>([
  "feed",
  "library",
  "search",
  "recommendations",
]);

export function useLegacyNavigation() {
  const [readerTab, setReaderTab] = useState<ReaderTab>("feed");
  const [legacyQuery, setLegacyQuery] = useState("");
  const [bookPath, setBookPath] = useState(() =>
    sharedBookPath(window.location.pathname),
  );
  const [legacyVisible, setLegacyVisible] = useState(
    window.location.pathname.startsWith("/usuarios/"),
  );

  useEffect(() => {
    const handleRoute = (event: Event) => {
      const route = (event as CustomEvent<string>).detail;
      if (readerTabs.has(route as ReaderTab)) {
        setReaderTab(route as ReaderTab);
      }
      setLegacyVisible(!readerTabs.has(route as ReaderTab));
    };
    const handleSearch = (event: Event) => {
      setLegacyQuery((event as CustomEvent<string>).detail);
    };

    const syncPath = () => {
      const path = sharedBookPath(window.location.pathname);
      setBookPath(path);
      if (path !== null) setLegacyVisible(false);
    };
    window.addEventListener("popstate", syncPath);
    window.addEventListener("bookrush:path-changed", syncPath);
    window.addEventListener("bookrush:legacy-route", handleRoute);
    window.addEventListener("bookrush:legacy-search", handleSearch);
    return () => {
      window.removeEventListener("popstate", syncPath);
      window.removeEventListener("bookrush:path-changed", syncPath);
      window.removeEventListener("bookrush:legacy-route", handleRoute);
      window.removeEventListener("bookrush:legacy-search", handleSearch);
    };
  }, []);

  return {
    bookPath,
    legacyQuery,
    legacyVisible,
    readerTab,
    showLegacy: () => setLegacyVisible(true),
  };
}
