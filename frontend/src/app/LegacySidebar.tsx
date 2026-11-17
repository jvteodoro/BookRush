import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Sidebar } from "@bookrush/ui/Sidebar";
import { styleNativeControls } from '@bookrush/ui/native-controls';
import { Logo } from "@bookrush/ui/Logo";
import { Icon, type IconName } from "@bookrush/ui/Icon";

const items = [
  ["feed", "Para você", "home"],
  ["recommendations", "Recomendações", "sparkle"],
  ["search", "Buscar", "search"],
  ["library", "Biblioteca", "library"],
  ["plant", "Minha planta", "leaf"],
  ["profile", "Perfil", "user"],
  ["privacy", "Privacidade", "shield"],
  ["admin", "Administração", "admin"],
  ["publisher", "Publicador", "upload"],
].map(([id, label, icon]) => ({
  id,
  label,
  icon: <Icon name={icon as IconName} />,
  separator: id === "admin" || id === "publisher",
}));

/** Adapter for the production screens still rendered by pageloop.js. */
export function LegacySidebar() {
  const [mount, setMount] = useState<HTMLElement | null>(null);
  const [active, setActive] = useState("feed");
  const [collapsed, setCollapsed] = useState(false);
  useEffect(() => {
    const update = (event?: Event) => {
      const slot = document.getElementById("bookrush-sidebar-slot");
      const app = document.getElementById('app');
      if (app) styleNativeControls(app);
      setMount(slot);
      setActive(
        event
          ? (event as CustomEvent<string>).detail
          : slot?.dataset.activeRoute || "feed",
      );
    };
    update();
    window.addEventListener("bookrush:legacy-route", update);
    return () => window.removeEventListener("bookrush:legacy-route", update);
  }, []);
  const navigate = (id: string) =>
    window.dispatchEvent(new CustomEvent("bookrush:navigate", { detail: id }));
  return mount
    ? createPortal(
        <Sidebar
          id="legacy-sidebar"
          items={items}
          active={active}
          onNavigate={navigate}
          logo={<Logo />}
          onBrandClick={() => navigate("feed")}
          collapsed={collapsed}
          onCollapsedChange={setCollapsed}
        />,
        mount,
      )
    : null;
}
