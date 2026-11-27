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
import {
  cancelSubscription,
  checkoutPremiumFamily,
  loadSubscription,
  premiumFamilyBenefits,
  subscriptionStateLabel,
  type Subscription,
} from "../../../packages/session/subscription";
import { authClient } from "../auth";

/** Premium family management: seats, members, invitations sent by the holder. */
export function FamilyScreen({ onClose }: { onClose?: () => void }) {
  const request = useMemo(() => authClient.fetch.bind(authClient), []);
  const [subscription, setSubscription] = useState<Subscription>();
  const [household, setHousehold] = useState<Household | null>();
  const [invitations, setInvitations] = useState<HouseholdInvitation[]>([]);
  const [recipientCode, setRecipientCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const refresh = useCallback(async () => {
    const current = await loadSubscription(request);
    setSubscription(current);
    if (!current.premiumFamily) {
      setHousehold(null);
      setInvitations([]);
      return;
    }
    const family = await loadHousehold(request);
    setHousehold(family);
    setInvitations(
      family ? await loadHouseholdInvitations(request, family.id) : [],
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

  const loading = subscription === undefined;

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

      {!loading && !subscription.premiumFamily && (
        <div>
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

      {!loading && subscription.premiumFamily && household === null && (
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
