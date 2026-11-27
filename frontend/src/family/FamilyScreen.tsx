import { useCallback, useEffect, useMemo, useState } from "react";
import {
  canRespondToInvitation,
  cancelInvitation,
  createHousehold,
  createInviteAddress,
  freeSeats,
  inviteMember,
  invitationStateLabel,
  isMemberHousehold,
  isRecipientCode,
  loadHousehold,
  loadHouseholdInvitations,
  loadReceivedInvitations,
  memberStateLabel,
  newIdempotencyKey,
  removeMember,
  respondToInvitation,
  type Household,
  type HouseholdInvitation,
} from "../../../packages/session/family";
import {
  cancelSubscription,
  checkoutPremiumFamily,
  loadSubscription,
  premiumFamilyBenefits,
  subscriptionStateLabel,
  type Subscription,
} from "../../../packages/session/subscription";
import type {
  Invitation,
  InviteAddress,
} from "../../../packages/session/session";
import { authClient } from "../auth";

/** Family tab: holder management, member status, and the guest flow (code, invitations, plan). */
export function FamilyScreen({ onClose }: { onClose?: () => void }) {
  const request = useMemo(() => authClient.fetch.bind(authClient), []);
  const [subscription, setSubscription] = useState<Subscription>();
  const [household, setHousehold] = useState<Household | null>();
  const [invitations, setInvitations] = useState<HouseholdInvitation[]>([]);
  const [received, setReceived] = useState<Invitation[]>([]);
  const [address, setAddress] = useState<InviteAddress>();
  const [recipientCode, setRecipientCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const refresh = useCallback(async () => {
    const current = await loadSubscription(request);
    setSubscription(current);
    const family = await loadHousehold(request);
    setHousehold(family);
    if (family && !isMemberHousehold(family)) {
      setInvitations(await loadHouseholdInvitations(request, family.id));
    } else {
      setInvitations([]);
    }
    setReceived(family ? [] : await loadReceivedInvitations(request));
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

  const loading = subscription === undefined || household === undefined;
  const member = household ? isMemberHousehold(household) : false;
  const owner = household !== null && household !== undefined && !member;
  const guest = !loading && household === null && !subscription.premiumFamily;

  return (
    <section
      aria-label="Gestão da família"
      style={{ padding: "24px", maxWidth: 880, margin: "0 auto" }}
    >
      <header
        style={{ display: "flex", justifyContent: "space-between", gap: 16 }}
      >
        <h1>Minha família</h1>
        {onClose && (
          <button className="secondary-button" onClick={onClose} type="button">
            Voltar ao feed
          </button>
        )}
      </header>

      {loading && <p role="status">Carregando família…</p>}

      {member && household && (
        <div>
          <p>Você é membro de uma família Premium.</p>
          <p>
            {household.occupiedOrReserved} de {household.additionalSeats} vagas
            ocupadas ou reservadas.
          </p>
        </div>
      )}

      {guest && (
        <div>
          <h2>Entrar em uma família</h2>
          <h3>Meu código de convite</h3>
          <p>Envie este código ao titular da família para ser convidado.</p>
          <button
            className="primary-button"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                setAddress(await createInviteAddress(request));
              }, "Código gerado.")
            }
            type="button"
          >
            Gerar código
          </button>
          {address && (
            <div>
              <p>
                <strong>{address.code}</strong> · válido até{" "}
                {new Date(address.expiresAt).toLocaleString("pt-BR")}
              </p>
              <button
                className="secondary-button"
                onClick={() => {
                  void navigator.clipboard?.writeText(address.code);
                  setNotice("Código copiado.");
                }}
                type="button"
              >
                Copiar código
              </button>
            </div>
          )}

          <h3>Convites recebidos</h3>
          {received.length === 0 && <p>Nenhum convite recebido.</p>}
          <ul>
            {received.map((invitation) => (
              <li key={invitation.id}>
                <span>
                  {invitationStateLabel(invitation.state)} · válido até{" "}
                  {new Date(invitation.expiresAt).toLocaleString("pt-BR")}
                </span>{" "}
                <button
                  className="primary-button"
                  disabled={busy || !canRespondToInvitation(invitation)}
                  onClick={() =>
                    void run(
                      () =>
                        respondToInvitation(request, invitation.id, "accept"),
                      "Convite aceito. Você agora é membro da família.",
                    )
                  }
                  type="button"
                >
                  Aceitar
                </button>{" "}
                <button
                  className="secondary-button"
                  disabled={busy || !canRespondToInvitation(invitation)}
                  onClick={() =>
                    void run(
                      () =>
                        respondToInvitation(request, invitation.id, "decline"),
                      "Convite recusado.",
                    )
                  }
                  type="button"
                >
                  Recusar
                </button>
              </li>
            ))}
          </ul>

          <h2>Premium Família</h2>
          <ul>
            {premiumFamilyBenefits.map((benefit) => (
              <li key={benefit}>{benefit}</li>
            ))}
          </ul>
          <p>
            Assinatura simulada: nenhum pagamento real é feito neste ambiente.
          </p>
          <button
            className="primary-button"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await checkoutPremiumFamily(request, newIdempotencyKey());
                const current = await loadSubscription(request);
                if (
                  current.premiumFamily &&
                  (await loadHousehold(request)) === null
                ) {
                  await createHousehold(request);
                }
              }, "Assinatura confirmada. Bem-vindo ao Premium Família.")
            }
            type="button"
          >
            Assinar Premium Família
          </button>
        </div>
      )}

      {!loading &&
        !member &&
        subscription.premiumFamily &&
        household === null && (
          <div>
            <p>Crie sua família para começar a convidar membros.</p>
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

      {owner && household && (
        <>
          <p>
            {household.occupiedOrReserved} de {household.additionalSeats} vagas
            ocupadas ou reservadas. {freeSeats(household)} livres.
          </p>

          <h2>Membros</h2>
          {household.members.length === 0 && <p>Nenhum membro ainda.</p>}
          <ul>
            {household.members.map((item) => (
              <li key={item.id}>
                <span>Membro · {memberStateLabel(item.state)}</span>{" "}
                <button
                  className="secondary-button"
                  disabled={busy || item.state !== "ACTIVE"}
                  onClick={() => {
                    if (!window.confirm("Remover este membro da família?"))
                      return;
                    void run(
                      () => removeMember(request, household.id, item),
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

      {!loading && subscription.premiumFamily && (
        <div>
          <p>
            {subscriptionStateLabel(subscription)}
            {subscription.effectiveUntil
              ? ` · válida até ${new Date(subscription.effectiveUntil).toLocaleDateString("pt-BR")}`
              : ""}
          </p>
          {subscription.state === "ACTIVE" && (
            <button
              className="secondary-button"
              disabled={busy}
              onClick={() => {
                if (
                  !window.confirm(
                    "Cancelar a assinatura Premium Família ao fim do período?",
                  )
                )
                  return;
                void run(
                  () => cancelSubscription(request),
                  "Cancelamento agendado.",
                );
              }}
              type="button"
            >
              Cancelar assinatura
            </button>
          )}
        </div>
      )}

      {error && <p role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}
    </section>
  );
}
