import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, setAuthErrorHandlers } from '../api/client';
import { clearCredentials, loadCredentials, saveCredentials } from './credentials';
import { canDo, hasRole, type Role, type WriteAction } from './roles';

export interface AuthUser {
  username: string;
  roles: string[];
}

export interface AuthContextValue {
  user: AuthUser | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  hasRole: (role: Role) => boolean;
  canDo: (action: WriteAction) => boolean;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [user, setUser] = useState<AuthUser | null>(() => {
    const c = loadCredentials();
    return c ? { username: c.username, roles: c.roles } : null;
  });

  // Wire the API client's 401/403 reactions to navigation (registered once).
  useEffect(() => {
    setAuthErrorHandlers({
      onUnauthorized: () => {
        clearCredentials();
        setUser(null);
        navigate('/login');
      },
      onForbidden: () => {
        // Stay on the current page; pages surface 403 via notifications at the call site.
      },
    });
  }, [navigate]);

  const login = useCallback(async (username: string, password: string) => {
    // Save first so the request middleware attaches the Basic header to the probe call.
    saveCredentials({ username, password, roles: [] });
    const { data, error } = await api.GET('/api/auth/me');
    if (error || !data) {
      clearCredentials();
      throw new Error('Invalid username or password');
    }
    const roles = data.roles ?? [];
    saveCredentials({ username, password, roles });
    setUser({ username: data.username ?? username, roles });
  }, []);

  const logout = useCallback(() => {
    clearCredentials();
    setUser(null);
    navigate('/login');
  }, [navigate]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      login,
      logout,
      hasRole: (role) => hasRole(user?.roles ?? [], role),
      canDo: (action) => canDo(user?.roles ?? [], action),
    }),
    [user, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
