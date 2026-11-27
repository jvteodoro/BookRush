import type { Request } from "./session";

/** Premium Família subscription: plan catalog, checkout and cancellation for web and mobile. */
export const subscriptionPath = "/api/v1/subscription/me";
export const checkoutPath = "/api/v1/subscription/checkout";
export const cancelSubscriptionPath = "/api/v1/subscription/cancel";
export const PREMIUM_FAMILY_PLAN_ID = "premium-family";

export type SubscriptionState =
  | "FREE"
  | "ACTIVE"
  | "CANCEL_AT_END"
  | "CANCELED"
  | "EXPIRED"
  | "SUSPENDED";

export type Subscription = {
  plan: string;
  planVersion: number;
  state: SubscriptionState;
  effectiveUntil: string | null;
  version: number;
  publishBooks: boolean;
  premiumFamily: boolean;
  additionalSeats: number;
};

export const premiumFamilyBenefits = [
  "Família com titular e até 5 membros",
  "Convites enviados pelo código da pessoa convidada",
  "Gestão de membros e convites pela própria família",
];

export function subscriptionStateLabel(subscription: Subscription): string {
  switch (subscription.state) {
    case "ACTIVE":
      return "Assinatura ativa";
    case "CANCEL_AT_END":
      return "Cancelamento agendado";
    case "SUSPENDED":
      return "Assinatura suspensa";
    default:
      return "Sem assinatura Premium Família";
  }
}

export function subscriptionMessage(status: number): string {
  switch (status) {
    case 401:
      return "Sua sessão expirou. Entre novamente.";
    case 403:
      return "Sua conta não pode assinar o plano no momento.";
    case 409:
      return "Não há uma assinatura ativa para cancelar.";
    case 400:
      return "Plano indisponível.";
    default:
      return "Não foi possível concluir a operação da assinatura.";
  }
}

async function readSubscription<T>(response: Response): Promise<T> {
  if (!response.ok) throw new Error(subscriptionMessage(response.status));
  return (response.status === 204 ? undefined : await response.json()) as T;
}

export async function loadSubscription(
  request: Request,
  init?: RequestInit,
): Promise<Subscription> {
  return readSubscription<Subscription>(
    await request(subscriptionPath, { ...init, method: "GET" }),
  );
}

/** Mock provider checkout: confirms the subscription immediately in non-real environments. */
export async function checkoutPremiumFamily(
  request: Request,
  idempotencyKey: string,
  init?: RequestInit,
): Promise<void> {
  const response = await request(checkoutPath, {
    ...init,
    method: "POST",
    headers: {
      ...(init?.headers as Record<string, string> | undefined),
      "Content-Type": "application/json",
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify({ planId: PREMIUM_FAMILY_PLAN_ID }),
  });
  if (
    response.status !== 200 &&
    response.status !== 201 &&
    response.status !== 202
  )
    throw new Error(subscriptionMessage(response.status));
}

export async function cancelSubscription(
  request: Request,
  init?: RequestInit,
): Promise<void> {
  const response = await request(cancelSubscriptionPath, {
    ...init,
    method: "POST",
  });
  if (!response.ok) throw new Error(subscriptionMessage(response.status));
}
