import { useQueryClient } from '@tanstack/react-query';
import * as SecureStore from 'expo-secure-store';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { getServerUrl, normalizeServerUrl, setServerUrl, setUnauthorizedHandler } from '../api/client';
import * as endpoints from '../api/endpoints';
import type { User } from '../api/types';

const SERVER_KEY = 'samanvay.serverUrl';
const USERNAME_KEY = 'samanvay.username';
/** The host machine as seen from the Android emulator. */
export const DEFAULT_SERVER = 'http://10.0.2.2:8081';

interface AuthState {
  user: User | null;
  loading: boolean;
  sessionExpired: boolean;
  serverUrl: string;
  lastUsername: string;
  login: (serverUrl: string, username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Session handling for the app. The backend session lives in an HTTP-only cookie kept by the platform's
 * cookie store, so a signed-in user stays signed in across app restarts until the session expires.
 * Only the server address and last username are stored on the device; never the password.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);
  const [serverUrl, setServerUrlState] = useState(DEFAULT_SERVER);
  const [lastUsername, setLastUsername] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const storedServer = (await SecureStore.getItemAsync(SERVER_KEY)) ?? DEFAULT_SERVER;
        setLastUsername((await SecureStore.getItemAsync(USERNAME_KEY)) ?? '');
        setServerUrlState(storedServer);
        setServerUrl(storedServer);
        setUser(await endpoints.fetchCurrentUser());
      } catch {
        setUser(null);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser((current) => {
        if (current) {
          setSessionExpired(true);
        }
        return null;
      });
      queryClient.clear();
    });
    return () => setUnauthorizedHandler(null);
  }, [queryClient]);

  const login = useCallback(async (server: string, username: string, password: string) => {
    const normalized = normalizeServerUrl(server);
    if (normalized !== getServerUrl()) {
      setServerUrl(normalized);
    }
    const signedIn = await endpoints.login(username, password);
    await SecureStore.setItemAsync(SERVER_KEY, normalized);
    await SecureStore.setItemAsync(USERNAME_KEY, username);
    setServerUrlState(normalized);
    setLastUsername(username);
    setSessionExpired(false);
    setUser(signedIn);
  }, []);

  const logout = useCallback(async () => {
    try {
      await endpoints.logout();
    } catch {
      // signed out locally even if the server cannot be reached
    } finally {
      setUser(null);
      setSessionExpired(false);
      queryClient.clear();
    }
  }, [queryClient]);

  const value = useMemo(
    () => ({ user, loading, sessionExpired, serverUrl, lastUsername, login, logout }),
    [user, loading, sessionExpired, serverUrl, lastUsername, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
