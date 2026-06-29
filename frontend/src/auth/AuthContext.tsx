import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, setAuthErrorHandlers } from '../api/client';
import { clearAccessToken, setAccessToken } from './credentials';
import { canDo, hasRole, type Role, type WriteAction } from './roles';

export interface AuthUser {
  username: string;
  roles: string[];
}

export interface AuthContextValue {
  user: AuthUser | null;
  /** True while the on-load silent refresh is in flight; route guards wait rather than bounce to /login. */
  bootstrapping: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  hasRole: (role: Role) => boolean;
  canDo: (action: WriteAction) => boolean;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [bootstrapping, setBootstrapping] = useState(true);

  // Wire the API client's 401/403 reactions to navigation (registered once). The client refreshes the
  // access token itself on 401; onUnauthorized only fires once the refresh has also failed.
  useEffect(() => {
    setAuthErrorHandlers({
      onUnauthorized: () => {
        clearAccessToken();
        setUser(null);
        navigate('/login');
      },
      onForbidden: () => {
        // Stay on the current page; pages surface 403 via notifications at the call site.
      },
    });
  }, [navigate]);

  // Silent refresh on load: the access token lives only in memory, so a reload starts with none. If a valid
  // refresh cookie is present, mint a fresh access token and restore the session before guards run.
  useEffect(() => {
    let active = true;
    void (async () => {
      const { data, error } = await api.POST('/api/auth/refresh', {});
      if (active && !error && data) {
        setAccessToken(data.accessToken ?? null);
        setUser({ username: data.username ?? '', roles: data.roles ?? [] });
      }
      if (active) {
        setBootstrapping(false);
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const { data, error } = await api.POST('/api/auth/login', { body: { username, password } });
    if (error || !data) {
      throw new Error('Invalid username or password');
    }
    setAccessToken(data.accessToken ?? null);
    setUser({ username: data.username ?? username, roles: data.roles ?? [] });
  }, []);

  const logout = useCallback(() => {
    // Clear the server's refresh cookie, then drop local state regardless of the call's outcome.
    void api.POST('/api/auth/logout', {}).finally(() => {
      clearAccessToken();
      setUser(null);
      navigate('/login');
    });
  }, [navigate]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      bootstrapping,
      login,
      logout,
      hasRole: (role) => hasRole(user?.roles ?? [], role),
      canDo: (action) => canDo(user?.roles ?? [], action),
    }),
    [user, bootstrapping, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
