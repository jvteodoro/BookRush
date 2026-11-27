import { useCallback, useEffect, useMemo, useState } from "react";
import {
  cancelInvitation,
  createHousehold,
  freeSeats,
  inviteMember,
  invitationStateLabel,
  isRecipientCode,
  loadHousehold,
  loadHouseholdInvitations,
  memberStateLabel,
  newIdempotencyKey,
  removeMember,
  type Household,
  type HouseholdInvitation,
} from "../../../packages/session/family";
import { authClient } from "../auth";

/** Premium family management: seats, members, invitations sent by the holder. */
export function FamilyScreen({ onClose }: { onClose: () => void }) {
  const request = useMemo(() => authClient.fetch.bind(authClient), []);
  const [household, setHousehold] = useState<Household | null>();
  const [invitations, setInvitations] = useState<HouseholdInvitation[]>([]);
  const [recipientCode, setRecipientCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const refresh = useCallback(async () => {
    const current = await loadHousehold(request);
    setHousehold(current);
    setInvitations(
      current ? await loadHouseholdInvitations(request, current.id) : [],
    );
  }, [request]);

  useEffect(() => {
    refresh().catch((cause: unknown) =>
      setError(
        cause instanceof Error ? cause.message : "Falha ao carregar a família.",
      ),
    );
  }, [refresh]);

  async function run(action: () => Promise<void>, success: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      await refresh();
      setNotice(success);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Falha na operação.");
    } finally {
      setBusy(false);
    }
  }

  const loading = household === undefined;

  return (
    <section
      aria-label="Gestão da família"
      className="landing restricted-session"
      style={{
        position: "fixed",
        inset: 0,
        overflowY: "auto",
        zIndex: 1000,
        padding: "32px 24px",
      }}
    >
      <header
        style={{ display: "flex", justifyContent: "space-between", gap: 16 }}
      >
        <h1>Minha família</h1>
        <button className="secondary-button" onClick={onClose} type="button">
          Voltar ao feed
        </button>
      </header>

      {loading && <p role="status">Carregando família…</p>}

      {!loading && household === null && (
        <div>
          <p>Você ainda não tem uma família Premium.</p>
          <button
            className="primary-button"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await createHousehold(request);
              }, "Família criada.")
            }
            type="button"
          >
            Criar família
          </button>
        </div>
      )}

      {household && (
        <>
          <p>
            {household.occupiedOrReserved} de {household.additionalSeats} vagas
            ocupadas ou reservadas. {freeSeats(household)} livres.
          </p>

          <h2>Membros</h2>
          {household.members.length === 0 && <p>Nenhum membro ainda.</p>}
          <ul>
            {household.members.map((member) => (
              <li key={member.id}>
                <span>Membro · {memberStateLabel(member.state)}</span>{" "}
                <button
                  className="secondary-button"
                  disabled={busy || member.state !== "ACTIVE"}
                  onClick={() => {
                    if (!window.confirm("Remover este membro da família?"))
                      return;
                    void run(
                      () => removeMember(request, household.id, member),
                      "Membro removido.",
                    );
                  }}
                  type="button"
                >
                  Remover
                </button>
              </li>
            ))}
          </ul>

          <h2>Convidar alguém</h2>
          <p>Peça à pessoa o código de convite gerado na tela dela.</p>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              const code = recipientCode.trim();
              if (!isRecipientCode(code)) {
                setError("Confira o código do convidado.");
                return;
              }
              void run(async () => {
                await inviteMember(
                  request,
                  household.id,
                  code,
                  newIdempotencyKey(),
                );
                setRecipientCode("");
              }, "Convite enviado.");
            }}
          >
            <label>
              Código do convidado{" "}
              <input
                value={recipientCode}
                onChange={(event) => setRecipientCode(event.target.value)}
                placeholder="00000000-0000-0000-0000-000000000000"
                autoComplete="off"
              />
            </label>{" "}
            <button
              className="primary-button"
              disabled={busy || freeSeats(household) === 0}
              type="submit"
            >
              Convidar
            </button>
          </form>

          <h2>Convites enviados</h2>
          {invitations.length === 0 && <p>Nenhum convite enviado.</p>}
          <ul>
            {invitations.map((invitation) => (
              <li key={invitation.id}>
                <span>
                  {invitationStateLabel(invitation.state)} · válido até{" "}
                  {new Date(invitation.expiresAt).toLocaleString("pt-BR")}
                </span>{" "}
                <button
                  className="secondary-button"
                  disabled={busy || invitation.state !== "PENDING"}
                  onClick={() =>
                    void run(
                      () =>
                        cancelInvitation(request, household.id, invitation.id),
                      "Convite cancelado.",
                    )
                  }
                  type="button"
                >
                  Cancelar convite
                </button>
              </li>
            ))}
          </ul>
        </>
      )}

      {error && <p role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}
    </section>
  );
}
