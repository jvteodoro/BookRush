import { useCallback, useEffect, useMemo, useState } from "react";
import {
  cancelInvitation,
  createHousehold,
  createInviteAddress,
  inviteMember,
  isMemberHousehold,
  isRecipientCode,
  loadHousehold,
  loadHouseholdInvitations,
  loadReceivedInvitations,
  newIdempotencyKey,
  removeMember,
  respondToInvitation,
  authorizeSupervision,
  type Household,
  type HouseholdInvitation,
} from "../../../packages/session/family";
import {
  cancelSubscription,
  checkoutPremiumFamily,
  loadSubscription,
  type Subscription,
} from "../../../packages/session/subscription";
import type {
  Invitation,
  InviteAddress,
} from "../../../packages/session/session";
import { authClient } from "../auth";
import { FamilyPanels } from "./FamilyPanels";

/** Family tab container: loads the shared state and runs the family actions. */
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

  const actions = {
    generateCode: () =>
      void run(async () => {
        setAddress(await createInviteAddress(request));
      }, "Código gerado."),
    copyCode: () => {
      if (!address) return;
      void navigator.clipboard?.writeText(address.code);
      setNotice("Código copiado.");
    },
    accept: (invitationId: string) =>
      void run(
        () => respondToInvitation(request, invitationId, "accept"),
        "Convite aceito. Você agora é membro da família.",
      ),
    decline: (invitationId: string) =>
      void run(
        () => respondToInvitation(request, invitationId, "decline"),
        "Convite recusado.",
      ),
    checkout: () =>
      void run(async () => {
        await checkoutPremiumFamily(request, newIdempotencyKey());
        const current = await loadSubscription(request);
        if (current.premiumFamily && (await loadHousehold(request)) === null) {
          await createHousehold(request);
        }
      }, "Assinatura confirmada. Bem-vindo ao Premium Família."),
    createFamily: () =>
      void run(async () => {
        await createHousehold(request);
      }, "Família criada."),
    removeMember: (member: Household["members"][number]) => {
      if (!household) return;
      if (!window.confirm("Remover este membro da família?")) return;
      void run(
        () => removeMember(request, household.id, member),
        "Membro removido.",
      );
    },
    authorizeSupervision: (member: Household["members"][number]) => {
      if (!household) return;
      void run(
        () => authorizeSupervision(request, household.id, member.id),
        "Acompanhamento autorizado.",
      );
    },
    invite: () => {
      if (!household) return;
      const code = recipientCode.trim();
      if (!isRecipientCode(code)) {
        setError("Confira o código do convidado.");
        return;
      }
      void run(async () => {
        await inviteMember(request, household.id, code, newIdempotencyKey());
        setRecipientCode("");
      }, "Convite enviado.");
    },
    setRecipientCode,
    cancelInvitation: (invitationId: string) => {
      if (!household) return;
      void run(
        () => cancelInvitation(request, household.id, invitationId),
        "Convite cancelado.",
      );
    },
    cancelSubscription: () => {
      if (
        !window.confirm(
          "Cancelar a assinatura Premium Família ao fim do período?",
        )
      )
        return;
      void run(() => cancelSubscription(request), "Cancelamento agendado.");
    },
  };

  return (
    <FamilyPanels
      onClose={onClose}
      subscription={subscription}
      household={household}
      invitations={invitations}
      received={received}
      address={address}
      recipientCode={recipientCode}
      busy={busy}
      error={error}
      notice={notice}
      actions={actions}
    />
  );
}
