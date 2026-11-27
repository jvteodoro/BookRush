import {
  canRespondToInvitation,
  familyView,
  freeSeats,
  invitationStateLabel,
  memberStateLabel,
  type Household,
  type HouseholdInvitation,
} from "../../../packages/session/family";
import {
  premiumFamilyBenefits,
  subscriptionStateLabel,
  type Subscription,
} from "../../../packages/session/subscription";
import type {
  Invitation,
  InviteAddress,
} from "../../../packages/session/session";

export type FamilyActions = {
  generateCode: () => void;
  copyCode: () => void;
  accept: (invitationId: string) => void;
  decline: (invitationId: string) => void;
  checkout: () => void;
  createFamily: () => void;
  removeMember: (member: Household["members"][number]) => void;
  invite: () => void;
  setRecipientCode: (value: string) => void;
  cancelInvitation: (invitationId: string) => void;
  cancelSubscription: () => void;
};

export type FamilyPanelsProps = {
  onClose?: () => void;
  subscription?: Subscription;
  household?: Household | null;
  invitations: HouseholdInvitation[];
  received: Invitation[];
  address?: InviteAddress;
  recipientCode: string;
  busy: boolean;
  error: string;
  notice: string;
  actions: FamilyActions;
};

/** Presentational family tab: every visible state is derived from the props only. */
export function FamilyPanels({
  onClose,
  subscription,
  household,
  invitations,
  received,
  address,
  recipientCode,
  busy,
  error,
  notice,
  actions,
}: FamilyPanelsProps) {
  const view = familyView(subscription, household);

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

      {view === "loading" && <p role="status">Carregando família…</p>}

      {view === "member" && household && (
        <div>
          <p>Você é membro de uma família Premium.</p>
          <p>
            {household.occupiedOrReserved} de {household.additionalSeats} vagas
            ocupadas ou reservadas.
          </p>
        </div>
      )}

      {view === "guest" && (
        <div>
          <h2>Entrar em uma família</h2>
          <h3>Meu código de convite</h3>
          <p>Envie este código ao titular da família para ser convidado.</p>
          <button
            className="primary-button"
            disabled={busy}
            onClick={actions.generateCode}
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
                onClick={actions.copyCode}
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
                  onClick={() => actions.accept(invitation.id)}
                  type="button"
                >
                  Aceitar
                </button>{" "}
                <button
                  className="secondary-button"
                  disabled={busy || !canRespondToInvitation(invitation)}
                  onClick={() => actions.decline(invitation.id)}
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
            onClick={actions.checkout}
            type="button"
          >
            Assinar Premium Família
          </button>
        </div>
      )}

      {view === "premium-without-family" && (
        <div>
          <p>Crie sua família para começar a convidar membros.</p>
          <button
            className="primary-button"
            disabled={busy}
            onClick={actions.createFamily}
            type="button"
          >
            Criar família
          </button>
        </div>
      )}

      {view === "owner" && household && (
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
                  onClick={() => actions.removeMember(item)}
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
              actions.invite();
            }}
          >
            <label>
              Código do convidado{" "}
              <input
                value={recipientCode}
                onChange={(event) =>
                  actions.setRecipientCode(event.target.value)
                }
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
                  onClick={() => actions.cancelInvitation(invitation.id)}
                  type="button"
                >
                  Cancelar convite
                </button>
              </li>
            ))}
          </ul>
        </>
      )}

      {subscription?.premiumFamily && (
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
              onClick={actions.cancelSubscription}
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
