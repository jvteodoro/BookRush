import Keycloak from 'keycloak-js';
import { PropsWithChildren, useEffect, useMemo, useState } from 'react';
import { config } from '../lib/config';
import { setAccessToken } from '../lib/http';
import { AuthContext, AuthState } from './auth-context';

const kc = new Keycloak({
  url: config.keycloak.url,
  realm: config.keycloak.realm,
  clientId: config.keycloak.clientId,
});

export function AuthProvider({ children }: PropsWithChildren) {
  const [state, setState] = useState({ ready: config.useMocks, authenticated: config.useMocks });
  const [profile, setProfile] = useState({ userId: 'mock-admin-001', username: 'admin.labsoft', roles: ['admin', 'analytics', 'trainer'] });

  useEffect(() => {
    if (config.useMocks) return;
    let mounted = true;
    kc.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false })
      .then(async authenticated => {
        if (!mounted) return;
        const parsed = kc.tokenParsed as Record<string, unknown> | undefined;
        const realmAccess = (parsed?.realm_access as { roles?: string[] } | undefined)?.roles ?? [];
        setProfile({
          userId: (parsed?.sub as string) ?? 'unknown',
          username: (parsed?.preferred_username as string) ?? 'admin',
          roles: realmAccess,
        });
        setAccessToken(kc.token);
        setState({ ready: true, authenticated });
      });
    const timer = window.setInterval(() => kc.updateToken(45).then(() => setAccessToken(kc.token)).catch(() => kc.login()), 30000);
    return () => { mounted = false; window.clearInterval(timer); };
  }, []);

  const value = useMemo<AuthState>(() => ({
    ready: state.ready,
    authenticated: state.authenticated,
    token: config.useMocks ? 'mock-token' : kc.token,
    userId: profile.userId,
    username: profile.username,
    roles: profile.roles,
    login: () => { void kc.login(); },
    logout: () => { setAccessToken(undefined); if (config.useMocks) location.reload(); else void kc.logout({ redirectUri: location.origin }); },
  }), [state, profile]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
