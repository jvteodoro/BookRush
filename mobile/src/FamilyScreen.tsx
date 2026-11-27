import { useEffect, useState } from "react";
import {
  Alert,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
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
} from "../../packages/session/family";
import type {
  Invitation,
  InviteAddress,
  Request,
} from "../../packages/session/session";
import {
  cancelSubscription,
  checkoutPremiumFamily,
  loadSubscription,
  premiumFamilyBenefits,
  subscriptionStateLabel,
  type Subscription,
} from "../../packages/session/subscription";

/** Family tab for the mobile app: holder management, member status, and the guest flow. */
export function MobileFamily({
  request,
  onClose,
}: {
  request: Request;
  onClose: () => void;
}) {
  const [subscription, setSubscription] = useState<Subscription>();
  const [household, setHousehold] = useState<Household | null>();
  const [invitations, setInvitations] = useState<HouseholdInvitation[]>([]);
  const [received, setReceived] = useState<Invitation[]>([]);
  const [address, setAddress] = useState<InviteAddress>();
  const [recipientCode, setRecipientCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function refresh() {
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
  }

  useEffect(() => {
    refresh().catch((cause: unknown) =>
      setError(
        cause instanceof Error ? cause.message : "Falha ao carregar a família.",
      ),
    );
    // refresh only depends on the request, which is stable for the screen lifetime
  }, []);

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
    <SafeAreaView style={styles.root}>
      <View style={styles.header}>
        <Text style={styles.brand}>Minha família</Text>
        <Pressable onPress={onClose}>
          <Text style={styles.link}>Voltar</Text>
        </Pressable>
      </View>
      <ScrollView contentContainerStyle={styles.body}>
        {loading ? (
          <Text style={styles.subtitle}>Carregando família…</Text>
        ) : null}

        {member && household ? (
          <>
            <Text style={styles.subtitle}>
              Você é membro de uma família Premium.
            </Text>
            <Text style={styles.subtitle}>
              {household.occupiedOrReserved} de {household.additionalSeats}{" "}
              vagas ocupadas ou reservadas.
            </Text>
          </>
        ) : null}

        {guest ? (
          <>
            <Text style={styles.heading}>Entrar em uma família</Text>
            <Text style={styles.subtitle}>
              Envie este código ao titular da família para ser convidado.
            </Text>
            <Pressable
              style={styles.primary}
              disabled={busy}
              onPress={() =>
                void run(async () => {
                  setAddress(await createInviteAddress(request));
                }, "Código gerado.")
              }
            >
              <Text style={styles.primaryText}>Gerar código</Text>
            </Pressable>
            {address ? (
              <>
                <Text selectable style={styles.rowText}>
                  {address.code}
                </Text>
                <Text style={styles.subtitle}>
                  Válido até{" "}
                  {new Date(address.expiresAt).toLocaleString("pt-BR")}. Toque e
                  segure o código para copiar.
                </Text>
              </>
            ) : null}

            <Text style={styles.heading}>Convites recebidos</Text>
            {received.length === 0 ? (
              <Text style={styles.subtitle}>Nenhum convite recebido.</Text>
            ) : null}
            {received.map((invitation) => (
              <View key={invitation.id} style={styles.row}>
                <Text style={styles.rowText}>
                  {invitationStateLabel(invitation.state)} · válido até{" "}
                  {new Date(invitation.expiresAt).toLocaleString("pt-BR")}
                </Text>
                <Pressable
                  disabled={busy || !canRespondToInvitation(invitation)}
                  onPress={() =>
                    void run(
                      () =>
                        respondToInvitation(request, invitation.id, "accept"),
                      "Convite aceito. Você agora é membro da família.",
                    )
                  }
                >
                  <Text style={styles.link}>Aceitar</Text>
                </Pressable>
                <Pressable
                  disabled={busy || !canRespondToInvitation(invitation)}
                  onPress={() =>
                    void run(
                      () =>
                        respondToInvitation(request, invitation.id, "decline"),
                      "Convite recusado.",
                    )
                  }
                >
                  <Text style={styles.link}>Recusar</Text>
                </Pressable>
              </View>
            ))}

            <Text style={styles.heading}>Premium Família</Text>
            {premiumFamilyBenefits.map((benefit) => (
              <Text key={benefit} style={styles.subtitle}>
                • {benefit}
              </Text>
            ))}
            <Text style={styles.subtitle}>
              Assinatura simulada: nenhum pagamento real é feito neste ambiente.
            </Text>
            <Pressable
              style={styles.primary}
              disabled={busy}
              onPress={() =>
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
            >
              <Text style={styles.primaryText}>Assinar Premium Família</Text>
            </Pressable>
          </>
        ) : null}

        {!loading &&
        !member &&
        subscription.premiumFamily &&
        household === null ? (
          <>
            <Text style={styles.subtitle}>
              Crie sua família para começar a convidar membros.
            </Text>
            <Pressable
              style={styles.primary}
              disabled={busy}
              onPress={() =>
                void run(async () => {
                  await createHousehold(request);
                }, "Família criada.")
              }
            >
              <Text style={styles.primaryText}>Criar família</Text>
            </Pressable>
          </>
        ) : null}

        {owner && household ? (
          <>
            <Text style={styles.subtitle}>
              {household.occupiedOrReserved} de {household.additionalSeats}{" "}
              vagas ocupadas ou reservadas. {freeSeats(household)} livres.
            </Text>

            <Text style={styles.heading}>Membros</Text>
            {household.members.length === 0 ? (
              <Text style={styles.subtitle}>Nenhum membro ainda.</Text>
            ) : null}
            {household.members.map((item) => (
              <View key={item.id} style={styles.row}>
                <Text style={styles.rowText}>
                  Membro · {memberStateLabel(item.state)}
                </Text>
                <Pressable
                  disabled={busy || item.state !== "ACTIVE"}
                  onPress={() =>
                    Alert.alert(
                      "Remover membro",
                      "Remover este membro da família?",
                      [
                        { text: "Cancelar", style: "cancel" },
                        {
                          text: "Remover",
                          style: "destructive",
                          onPress: () =>
                            void run(
                              () => removeMember(request, household.id, item),
                              "Membro removido.",
                            ),
                        },
                      ],
                    )
                  }
                >
                  <Text style={styles.link}>Remover</Text>
                </Pressable>
              </View>
            ))}

            <Text style={styles.heading}>Convidar alguém</Text>
            <Text style={styles.subtitle}>
              Peça à pessoa o código de convite gerado na tela dela.
            </Text>
            <View style={styles.search}>
              <TextInput
                value={recipientCode}
                onChangeText={setRecipientCode}
                placeholder="Código do convidado"
                placeholderTextColor="#89978e"
                autoCapitalize="none"
                autoCorrect={false}
                style={styles.input}
              />
            </View>
            <Pressable
              style={styles.primary}
              disabled={busy || freeSeats(household) === 0}
              onPress={() => {
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
              <Text style={styles.primaryText}>Convidar</Text>
            </Pressable>

            <Text style={styles.heading}>Convites enviados</Text>
            {invitations.length === 0 ? (
              <Text style={styles.subtitle}>Nenhum convite enviado.</Text>
            ) : null}
            {invitations.map((invitation) => (
              <View key={invitation.id} style={styles.row}>
                <Text style={styles.rowText}>
                  {invitationStateLabel(invitation.state)} · válido até{" "}
                  {new Date(invitation.expiresAt).toLocaleString("pt-BR")}
                </Text>
                <Pressable
                  disabled={busy || invitation.state !== "PENDING"}
                  onPress={() =>
                    void run(
                      () =>
                        cancelInvitation(request, household.id, invitation.id),
                      "Convite cancelado.",
                    )
                  }
                >
                  <Text style={styles.link}>Cancelar</Text>
                </Pressable>
              </View>
            ))}
          </>
        ) : null}

        {!loading && subscription.premiumFamily ? (
          <>
            <Text style={styles.subtitle}>
              {subscriptionStateLabel(subscription)}
              {subscription.effectiveUntil
                ? ` · válida até ${new Date(subscription.effectiveUntil).toLocaleDateString("pt-BR")}`
                : ""}
            </Text>
            {subscription.state === "ACTIVE" ? (
              <Pressable
                disabled={busy}
                onPress={() =>
                  Alert.alert(
                    "Cancelar assinatura",
                    "Cancelar a assinatura Premium Família ao fim do período?",
                    [
                      { text: "Manter", style: "cancel" },
                      {
                        text: "Cancelar assinatura",
                        style: "destructive",
                        onPress: () =>
                          void run(
                            () => cancelSubscription(request),
                            "Cancelamento agendado.",
                          ),
                      },
                    ],
                  )
                }
              >
                <Text style={styles.link}>Cancelar assinatura</Text>
              </Pressable>
            ) : null}
          </>
        ) : null}

        {error ? <Text style={styles.error}>{error}</Text> : null}
        {notice ? <Text style={styles.subtitle}>{notice}</Text> : null}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: "#0a0a0d" },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    padding: 18,
  },
  brand: { color: "#f3f4f5", fontSize: 22, fontWeight: "700" },
  link: { color: "#356157" },
  body: { padding: 20, paddingBottom: 40, gap: 12 },
  heading: { color: "#f3f4f5", fontSize: 20, fontWeight: "700", marginTop: 12 },
  subtitle: { color: "#9ba1ae" },
  primary: {
    backgroundColor: "#d76c51",
    borderRadius: 12,
    padding: 14,
    alignItems: "center",
  },
  primaryText: { color: "#fff", fontWeight: "700" },
  row: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 12,
  },
  rowText: { color: "#f3f4f5", flex: 1 },
  search: {
    flexDirection: "row",
    borderWidth: 1,
    borderColor: "#2b2d36",
    borderRadius: 12,
    paddingHorizontal: 12,
  },
  input: { flex: 1, height: 46, color: "#f3f4f5" },
  error: { color: "#ff8f7a" },
});
