export const sessionPath = "/api/v1/session";
export const invitationsPath = "/api/v1/family/invitations/me";
export const inviteAddressPath = "/api/v1/family/invite-address";
export const invitationAction = (id: string, action: "accept" | "decline") =>
  `/api/v1/family/invitations/${encodeURIComponent(id)}/${action}`;

const states = [
  "ADULT_ELIGIBLE",
  "MINOR_AUTHORIZED",
  "MINOR_AWAITING_AUTHORIZATION",
  "MINOR_FAMILY_INACTIVE",
  "AGE_PROFILE_INCOMPLETE",
  "SUSPENDED",
] as const;
type AccessState = (typeof states)[number];
type Capability = "SESSION_VIEW" | "PRODUCT_USE" | "INVITE_SELF";
type ReasonCode =
  | "ACCOUNT_RESTRICTED"
  | "ACCOUNT_SUSPENDED"
  | "SUPERVISION_REQUIRED";

export type Session = {
  username: string;
  accessState: AccessState;
  reasonCode?: ReasonCode | null;
  capabilities: Capability[];
  validUntil: string;
};

export type SessionView =
  | { status: "loading" }
  | { status: "ready"; session: Session }
  | { status: "error"; authenticate: boolean };

export type Invitation = { id: string; state: string; expiresAt: string };
export type InviteAddress = { code: string; expiresAt: string };
export type Request = (path: string, init?: RequestInit) => Promise<Response>;

export function allowsProduct(view: SessionView, now = Date.now()) {
  return (
    view.status === "ready" &&
    Date.parse(view.session.validUntil) > now &&
    view.session.capabilities.includes("PRODUCT_USE")
  );
}

export function accessMessage(session: Session) {
  switch (session.reasonCode) {
    case "ACCOUNT_SUSPENDED":
      return "O acesso desta conta está suspenso. Procure o suporte do BookRush.";
    case "SUPERVISION_REQUIRED":
      return "Seu responsável precisa autorizar seu acesso ao BookRush.";
    case "ACCOUNT_RESTRICTED":
      return session.accessState === "AGE_PROFILE_INCOMPLETE"
        ? "Seu cadastro etário precisa ser regularizado. Procure um operador do BookRush para atualizar seus dados."
        : "Para acessar o BookRush, você precisa de um vínculo familiar ativo e da autorização de um responsável.";
    default:
      return "Consulte o estado da sua conta.";
  }
}

function decodeSession(value: unknown): Session {
  if (typeof value !== "object" || value === null)
    throw new Error("Invalid session");
  const snapshot = value as Session;
  if (
    typeof snapshot.username !== "string" ||
    !snapshot.username.trim() ||
    !states.includes(snapshot.accessState) ||
    typeof snapshot.validUntil !== "string" ||
    !(Date.parse(snapshot.validUntil) > Date.now()) ||
    !Array.isArray(snapshot.capabilities) ||
    snapshot.capabilities.some(
      (item) => !["SESSION_VIEW", "PRODUCT_USE", "INVITE_SELF"].includes(item),
    )
  ) {
    throw new Error("Invalid session");
  }
  const eligible =
    snapshot.accessState === "ADULT_ELIGIBLE" ||
    snapshot.accessState === "MINOR_AUTHORIZED";
  if (
    eligible !== snapshot.capabilities.includes("PRODUCT_USE") ||
    (snapshot.accessState === "SUSPENDED" && snapshot.capabilities.length)
  )
    throw new Error("Invalid capability");
  return snapshot;
}

export function createSessionGate(
  request: Request,
  update: (view: SessionView) => void,
  active = () => true,
) {
  let view: SessionView = { status: "loading" };
  let pending: AbortController | undefined;
  let renew: ReturnType<typeof setTimeout> | undefined;
  let expiry: ReturnType<typeof setTimeout> | undefined;
  let closed = false;

  function set(next: SessionView) {
    view = next;
    update(next);
  }
  function refresh(invalidate = false) {
    if (closed || !active()) return;
    if (invalidate) {
      pending?.abort();
      pending = undefined;
      set({ status: "loading" });
    }
    if (pending) return;
    const current = new AbortController();
    pending = current;
    const timeout = setTimeout(() => {
      if (pending !== current) return;
      current.abort();
      pending = undefined;
      set({ status: "error", authenticate: false });
    }, 10_000);
    void (async () => {
      try {
        const response = await request(sessionPath, {
          signal: current.signal,
          cache: "no-store",
        });
        if (pending !== current || closed) return;
        if (!response.ok) {
          set({
            status: "error",
            authenticate: response.status === 401 || response.status === 403,
          });
          return;
        }
        const snapshot = decodeSession(await response.json());
        if (pending !== current || closed) return;
        if (expiry !== undefined) clearTimeout(expiry);
        if (renew !== undefined) clearTimeout(renew);
        set({ status: "ready", session: snapshot });
        const ttl = Date.parse(snapshot.validUntil) - Date.now();
        if (ttl > 10_000)
          renew = setTimeout(() => {
            if (view.status === "ready" && view.session === snapshot) refresh();
          }, ttl - 5_000);
        expiry = setTimeout(
          () => {
            if (view.status !== "ready" || view.session !== snapshot) return;
            set({ status: "loading" });
            refresh();
          },
          Math.max(0, ttl),
        );
      } catch {
        if (pending === current && !closed)
          set({ status: "error", authenticate: false });
      } finally {
        clearTimeout(timeout);
        if (pending === current) pending = undefined;
      }
    })();
  }
  return {
    refresh,
    allowsProduct: () => allowsProduct(view),
    dispose() {
      closed = true;
      pending?.abort();
      pending = undefined;
      if (expiry !== undefined) clearTimeout(expiry);
      if (renew !== undefined) clearTimeout(renew);
    },
  };
}

export async function invitationRequest<T>(
  request: Request,
  path: string,
  init?: RequestInit,
): Promise<T> {
  const response = await request(path, init);
  if (!response.ok)
    throw new Error(
      response.status === 409
        ? "Este convite mudou de estado. Consulte a lista novamente."
        : "Não foi possível concluir a operação com o convite.",
    );
  return (response.status === 204 ? undefined : await response.json()) as T;
}
