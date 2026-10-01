import { createContext } from 'react';

export interface AuthState {
  ready: boolean;
  authenticated: boolean;
  token?: string;
  userId: string;
  username: string;
  roles: string[];
  login: () => void;
  logout: () => void;
}

export const AuthContext = createContext<AuthState | null>(null);
