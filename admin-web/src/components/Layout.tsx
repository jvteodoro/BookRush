import { Outlet, useLocation } from "react-router-dom";
import { AppFrame } from "@bookrush/ui/AppFrame";
import { AppHeader } from "@bookrush/ui/AppHeader";
import { ThemeToggle } from "@bookrush/ui/ThemeToggle";
import { adminNavigation } from "../app/navigation";
import { Sidebar } from "./Sidebar";
import { config } from "../lib/config";

export function Layout() {
  const location = useLocation();
  return (
    <AppFrame
      sidebar={<Sidebar />}
      mainId="admin-main"
      header={
        <AppHeader
          title={
            adminNavigation.find((item) =>
              item.to === "/"
                ? location.pathname === "/"
                : location.pathname.startsWith(item.to),
            )?.label
          }
          actions={<ThemeToggle />}
        />
      }
    >
      {config.useMocks && (
        <p className="sessionbadge" role="status">
          Demonstração local · dados simulados
        </p>
      )}
      <Outlet />
    </AppFrame>
  );
}
