import { User, UserManager, WebStorageStateStore } from 'oidc-client-ts';

const authority = import.meta.env.VITE_OIDC_AUTHORITY?.trim() || 'https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush';
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID?.trim() || 'bookrush-web';
const origin = window.location.origin;

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();
  get length(): number { return this.values.size; }
  clear(): void { this.values.clear(); }
  getItem(key: string): string | null { return this.values.get(key) ?? null; }
  key(index: number): string | null { return [...this.values.keys()][index] ?? null; }
  removeItem(key: string): void { this.values.delete(key); }
  setItem(key: string, value: string): void { this.values.set(key, value); }
}

function safeReturnTo(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return '/';
  return value;
}

const manager = new UserManager({
  authority,
  client_id: clientId,
  redirect_uri: `${origin}/auth/callback`,
  post_logout_redirect_uri: origin,
  silent_redirect_uri: `${origin}/auth/silent-callback`,
  response_type: 'code',
  scope: 'openid profile email',
  // Tokens are deliberately not persisted. The protocol transaction state is
  // short-lived and kept by oidc-client-ts only until the callback completes.
  userStore: new WebStorageStateStore({ store: new MemoryStorage() }),
  stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
  automaticSilentRenew: false,
  loadUserInfo: false,
});

let refreshInFlight: Promise<User | null> | null = null;
let activeUser: User | null = null;
// React StrictMode replays mount effects in development. A callback code is
// single-use: every caller must await the same exchange, including failures.
let callbackInFlight: Promise<{ user: User; returnTo: string }> | null = null;
let silentCallbackInFlight: Promise<void> | null = null;

export type AuthState = { status: 'anonymous' | 'loading' | 'authenticated' | 'error'; user?: User; error?: string };

export const authClient = {
  registrationUrl(): string {
    const url = new URL(`${authority}/protocol/openid-connect/registrations`);
    url.searchParams.set('client_id', clientId);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('scope', 'openid');
    // Registration is a Keycloak-managed flow. After creating the account the
    // user returns to the public app and can enter through the normal PKCE flow.
    url.searchParams.set('redirect_uri', origin);
    return url.toString();
  },
  async currentUser(): Promise<User | null> {
    if (activeUser) return activeUser;
    activeUser = await manager.getUser();
    return activeUser;
  },
  async login(returnTo: string): Promise<void> {
    await manager.signinRedirect({ state: { returnTo: safeReturnTo(returnTo) } });
  },
  callback(): Promise<{ user: User; returnTo: string }> {
    if (!callbackInFlight) {
      callbackInFlight = manager.signinRedirectCallback(window.location.href).then(user => {
        activeUser = user;
        const state = user.state as { returnTo?: unknown } | undefined;
        return { user, returnTo: safeReturnTo(state?.returnTo) };
      });
    }
    return callbackInFlight;
  },
  silentCallback(): Promise<void> {
    if (!silentCallbackInFlight) silentCallbackInFlight = manager.signinSilentCallback();
    return silentCallbackInFlight;
  },
  async logout(): Promise<void> {
    try { await manager.signoutRedirect({ id_token_hint: (await this.currentUser())?.id_token }); } finally { activeUser = null; }
  },
  async accessToken(): Promise<string | undefined> {
    const user = await this.ensureFresh();
    return user?.access_token;
  },
  async ensureFresh(): Promise<User | null> {
    const current = await this.currentUser();
    if (!current || !current.expires_at || current.expires_at > Math.floor(Date.now() / 1000) + 30) return current;
    if (!refreshInFlight) {
      refreshInFlight = manager.signinSilent().then(user => { activeUser = user; return user; }).catch(async () => {
        activeUser = null;
        await manager.removeUser();
        return null;
      }).finally(() => { refreshInFlight = null; });
    }
    return refreshInFlight;
  },
  async fetch(input: string, init?: RequestInit): Promise<Response> {
    const url = new URL(input, origin);
    if (url.origin !== origin || !url.pathname.startsWith('/api/')) throw new Error('A API deve ser same-origin e allowlisted.');
    const token = await this.accessToken();
    const headers = new Headers(init?.headers);
    if (token) headers.set('Authorization', `Bearer ${token}`);
    const response = await fetch(url, { ...init, headers });
    // A 401 de um endpoint opcional não invalida a sessão global. O token
    // permanece em memória; a renovação só remove a sessão quando o IdP falha.
    return response;
  },
  async imageUrl(input: string): Promise<string | undefined> {
    const response = await this.fetch(input, { cache: 'no-store' });
    if (!response.ok) return undefined;
    return URL.createObjectURL(await response.blob());
  },
};

export { safeReturnTo };
