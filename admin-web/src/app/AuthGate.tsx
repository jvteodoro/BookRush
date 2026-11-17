import { Button } from "../components/ui/Button";
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
        <Button variant="primary" type="button" onClick={auth.login}>
          Sign in
        </Button>
      </div>
    );
  }
  return <RouterProvider router={router} />;
}
