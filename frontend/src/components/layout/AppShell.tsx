import { ThemeToggle } from "@bookrush/ui/ThemeToggle";
import { AppFrame } from "@bookrush/ui/AppFrame";
import { AppHeader } from "@bookrush/ui/AppHeader";
import { Avatar } from "@bookrush/ui/Avatar";
import { Sidebar } from "@bookrush/ui/Sidebar";
import { useState, type ReactNode } from "react";
import { Logo } from "../ui/Logo";
import { Icon, type IconName } from "../ui/Icon";

export type ReaderTab =
  | "feed"
  | "recommendations"
  | "search"
  | "library"
  | "plant"
  | "profile"
  | "privacy"
  | "admin"
  | "publisher";
const tabs: {
  id: ReaderTab;
  label: string;
  icon: IconName;
  separator?: boolean;
}[] = [
  { id: "feed", label: "Para você", icon: "home" },
  { id: "recommendations", label: "Recomendações", icon: "sparkle" },
  { id: "search", label: "Buscar", icon: "search" },
  { id: "library", label: "Biblioteca", icon: "library" },
  { id: "plant", label: "Minha planta", icon: "leaf" },
  { id: "profile", label: "Perfil", icon: "user" },
  { id: "privacy", label: "Privacidade", icon: "shield" },
  { id: "admin", label: "Administração", icon: "admin", separator: true },
  { id: "publisher", label: "Publicador", icon: "upload", separator: true },
];

type Props = {
  children: ReactNode;
  active: ReaderTab;
  onNavigate: (tab: ReaderTab) => void;
  query: string;
  onSearch: (query: string) => void;
  userName?: string;
};
export function AppShell({
  children,
  active,
  onNavigate,
  query,
  onSearch,
  userName = "Ana",
}: Props) {
  const [collapsed, setCollapsed] = useState(false);
  return (
    <AppFrame
      className={active === "feed" ? "br-shell--feed" : ""}
      mainId="reader-main"
      sidebar={
        <Sidebar
          id="reader-sidebar"
          items={tabs.map((tab) => ({
            ...tab,
            icon: <Icon name={tab.icon} />,
          }))}
          active={active}
          onNavigate={(id) => onNavigate(id as ReaderTab)}
          logo={<Logo />}
          brandLabel="BookRush — Para você"
          onBrandClick={() => onNavigate("feed")}
          collapsed={collapsed}
          onCollapsedChange={setCollapsed}
        />
      }
      header={
        <AppHeader
          title={tabs.find((tab) => tab.id === active)?.label}
          query={query}
          onQuery={onSearch}
          onSearch={() => onNavigate("search")}
          onHome={() => onNavigate("feed")}
          homeActive={active === "feed"}
          actions={
            <>
              <ThemeToggle />
              <Avatar name={userName} onClick={() => onNavigate("profile")} />
            </>
          }
        />
      }
    >
      {children}
    </AppFrame>
  );
}
