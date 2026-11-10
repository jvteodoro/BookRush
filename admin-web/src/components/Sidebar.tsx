import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Sidebar as BookRushSidebar } from "@bookrush/ui/Sidebar";
import { Logo } from "@bookrush/ui/Logo";
import { Icon } from "@bookrush/ui/Icon";
import { Button } from "@bookrush/ui/Button";
import {
  LayoutDashboard,
  LibraryBig,
  ClipboardCheck,
  ChartNoAxesCombined,
  FlaskConical,
  GraduationCap,
  UsersRound,
} from "lucide-react";
import { useAuth } from "../auth/useAuth";
import { adminNavigation } from "../app/navigation";

const icons = {
  "/": LayoutDashboard,
  "/catalog": LibraryBig,
  "/review": ClipboardCheck,
  "/analytics": ChartNoAxesCombined,
  "/feed-lab": FlaskConical,
  "/training": GraduationCap,
  "/operations": UsersRound,
};
const items = adminNavigation.map(({ to, label }) => {
  const MenuIcon = icons[to];
  return {
    id: to,
    label,
    href: to,
    icon: <MenuIcon size={22} strokeWidth={1.8} aria-hidden="true" />,
  };
});

export function Sidebar() {
  const auth = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const active =
    adminNavigation.find(({ to }) =>
      to === "/" ? location.pathname === "/" : location.pathname.startsWith(to),
    )?.to ?? "/";
  return (
    <BookRushSidebar
      id="admin-sidebar"
      items={items.map((item) => ({
        ...item,
        href: `${import.meta.env.BASE_URL.replace(/\/$/, "")}${item.href}`,
      }))}
      active={active}
      onNavigate={navigate}
      logo={<Logo />}
      subtitle="Administração"
      navigationLabel="Administração"
      onBrandClick={() => navigate("/")}
      collapsed={collapsed}
      onCollapsedChange={setCollapsed}
      footer={
        <div className="admin-session">
          <span className="admin-session-avatar">
            {auth.username.slice(0, 2).toUpperCase()}
          </span>
          <div>
            <strong>{auth.username}</strong>
            <small>{auth.roles.slice(0, 2).join(" · ")}</small>
          </div>
          <Button
            variant="ghost"
            size="sm"
            aria-label="Sair"
            onClick={auth.logout}
          >
            <Icon name="arrow" width={16} height={16} />
          </Button>
        </div>
      }
    />
  );
}
