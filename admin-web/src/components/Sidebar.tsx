import {
  BarChart3,
  BookOpen,
  ClipboardCheck,
  FlaskConical,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  ShieldCheck,
  Users,
} from "lucide-react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { adminNavigation } from "../app/navigation";

const icons = {
  "/": LayoutDashboard,
  "/catalog": BookOpen,
  "/review": ClipboardCheck,
  "/analytics": BarChart3,
  "/feed-lab": FlaskConical,
  "/training": GraduationCap,
  "/operations": Users,
} as const;

export function Sidebar() {
  const auth = useAuth();

  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brandmark">L</div>
        <div>
          <strong>LabSoft</strong>
          <span>Admin Console</span>
        </div>
      </div>
      <nav aria-label="Administração">
        {adminNavigation.map(({ to, label }) => {
          const Icon = icons[to];
          return (
            <NavLink
              key={to}
              to={to}
              end={to === "/"}
              className={({ isActive }) => (isActive ? "active" : undefined)}
            >
              <Icon size={18} />
              {label}
            </NavLink>
          );
        })}
      </nav>
      <div className="identity">
        <div className="avatar">{auth.username.slice(0, 2).toUpperCase()}</div>
        <div>
          <strong>{auth.username}</strong>
          <span>
            <ShieldCheck size={12} /> {auth.roles.slice(0, 2).join(" · ")}
          </span>
        </div>
        <button type="button" aria-label="Sair" onClick={auth.logout}>
          <LogOut size={16} />
        </button>
      </div>
    </aside>
  );
}
