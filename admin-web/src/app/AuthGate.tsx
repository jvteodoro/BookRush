import { RouterProvider } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { createAdminRouter } from "./adminRouter";

const router = createAdminRouter();

export function AuthGate() {
  const auth = useAuth();

  if (!auth.ready) return <div className="center">Connecting to Keycloak…</div>;
  if (!auth.authenticated) {
    return (
      <div className="center">
        <button type="button" onClick={auth.login}>
          Sign in
        </button>
      </div>
    );
  }
  return <RouterProvider router={router} />;
}
