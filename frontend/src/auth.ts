import { User, UserManager, WebStorageStateStore } from 'oidc-client-ts';

const authority = import.meta.env.VITE_OIDC_AUTHORITY ?? 'https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush';
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID ?? 'bookrush-web';
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

export type AuthState = { status: 'anonymous' | 'loading' | 'authenticated' | 'error'; user?: User; error?: string };

export const authClient = {
  async currentUser(): Promise<User | null> {
    return manager.getUser();
  },
  async login(returnTo: string): Promise<void> {
    await manager.signinRedirect({ state: { returnTo: safeReturnTo(returnTo) } });
  },
  async callback(): Promise<{ user: User; returnTo: string }> {
    const user = await manager.signinRedirectCallback();
    const state = user.state as { returnTo?: unknown } | undefined;
    return { user, returnTo: safeReturnTo(state?.returnTo) };
  },
  async silentCallback(): Promise<void> {
    await manager.signinSilentCallback();
  },
  async logout(): Promise<void> {
    await manager.signoutRedirect({ id_token_hint: (await manager.getUser())?.id_token });
  },
  async accessToken(): Promise<string | undefined> {
    const user = await this.ensureFresh();
    return user?.access_token;
  },
  async ensureFresh(): Promise<User | null> {
    const current = await manager.getUser();
    if (!current || !current.expires_at || current.expires_at > Math.floor(Date.now() / 1000) + 30) return current;
    if (!refreshInFlight) {
      refreshInFlight = manager.signinSilent().catch(async () => {
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
    if (response.status === 401) await manager.removeUser();
    return response;
  },
};

export { safeReturnTo };
