import { useEffect, useRef, useState } from "react";
import {
  accessMessage,
  invitationRequest,
  type Invitation,
  type InviteAddress,
  type Session,
} from "../../../packages/session/session";
import { authClient } from "../auth";
import { apiRoutes } from "./apiRoutes";

export function RestrictedSession({
  session,
  onRefresh,
  onLogout,
}: {
  session: Session;
  onRefresh: () => void;
  onLogout: () => void;
}) {
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [address, setAddress] = useState<InviteAddress>();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const operations = useRef<AbortController>();
  const permitted = session.capabilities.includes("INVITE_SELF");

  useEffect(() => {
    const controller = new AbortController();
    operations.current = controller;
    setBusy(false);
    if (permitted) {
      void invitationRequest<Invitation[]>(
        authClient.fetch.bind(authClient),
        apiRoutes.family.invitations(),
        { signal: controller.signal },
      )
        .then((result) => {
          if (!controller.signal.aborted) {
            setInvitations(result);
            setLoaded(true);
          }
        })
        .catch((cause) => {
          if (!controller.signal.aborted) setError(cause.message);
        });
    }
    return () => controller.abort();
  }, [permitted, session.validUntil]);

  async function act(path: string, code = false) {
    const controller = operations.current;
    if (
      !controller ||
      controller.signal.aborted ||
      Date.parse(session.validUntil) <= Date.now()
    )
      return;
    setBusy(true);
    setError("");
    try {
      const result = await invitationRequest<InviteAddress>(
        authClient.fetch.bind(authClient),
        path,
        { method: "POST", signal: controller.signal },
      );
      if (controller.signal.aborted) return;
      if (code) setAddress(result);
      else onRefresh();
    } catch (cause) {
      if (!controller.signal.aborted)
        setError(cause instanceof Error ? cause.message : "Falha no convite.");
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  return (
    <main className="landing restricted-session">
      <span className="eyebrow">BOOKRUSH</span>
      <h1>Olá, {session.username}</h1>
      <p>{accessMessage(session)}</p>
      <button className="primary-button" onClick={onRefresh}>
        Atualizar acesso
      </button>
      {permitted && (
        <section aria-label="Convites familiares">
          <h2>Convites familiares</h2>
          {loaded && invitations.length === 0 && (
            <p>Nenhum convite recebido.</p>
          )}
          <ul>
            {invitations.map((invite) => (
              <li key={invite.id}>
                <p>
                  Convite válido até{" "}
                  {new Date(invite.expiresAt).toLocaleString("pt-BR")}
                </p>
                <button
                  disabled={busy || invite.state !== "PENDING"}
                  onClick={() =>
                    void act(
                      apiRoutes.family.invitationAction(invite.id, "accept"),
                    )
                  }
                >
                  Aceitar
                </button>
                <button
                  disabled={busy || invite.state !== "PENDING"}
                  onClick={() =>
                    void act(
                      apiRoutes.family.invitationAction(invite.id, "decline"),
                    )
                  }
                >
                  Recusar
                </button>
              </li>
            ))}
          </ul>
          <button
            className="primary-button"
            disabled={busy}
            onClick={() => void act(apiRoutes.family.inviteAddress(), true)}
          >
            Gerar código de convite
          </button>
          {address && (
            <p>
              <strong>{address.code}</strong> · expira em{" "}
              {new Date(address.expiresAt).toLocaleString("pt-BR")}
            </p>
          )}
        </section>
      )}
      {error && <p role="alert">{error}</p>}
      <button onClick={onLogout}>Sair da conta</button>
    </main>
  );
}
