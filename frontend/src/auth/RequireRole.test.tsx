import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { renderWithProviders, screen } from '../test/test-utils';
import { AuthContext, type AuthContextValue, type AuthUser } from './AuthContext';
import { RequireRole } from './RequireRole';
import type { Role } from './roles';

function authValue(user: AuthUser | null): AuthContextValue {
  return {
    user,
    bootstrapping: false,
    login: async () => {},
    logout: () => {},
    hasRole: (role: Role) => (user?.roles ?? []).includes(role),
    canDo: () => false,
  };
}

function renderGuard(user: AuthUser | null) {
  renderWithProviders(
    <AuthContext.Provider value={authValue(user)}>
      <Routes>
        <Route
          path="/"
          element={
            <RequireRole role="ADMIN">
              <div>secret-content</div>
            </RequireRole>
          }
        />
        <Route path="/login" element={<div>login-page</div>} />
      </Routes>
    </AuthContext.Provider>,
    { route: '/' },
  );
}

describe('RequireRole', () => {
  it('redirects an unauthenticated user to /login', () => {
    renderGuard(null);
    expect(screen.getByText('login-page')).toBeInTheDocument();
    expect(screen.queryByText('secret-content')).not.toBeInTheDocument();
  });

  it('shows the 403 page when authenticated but lacking the role', () => {
    renderGuard({ username: 'sales', roles: ['SALES'] });
    expect(screen.getByText('403')).toBeInTheDocument();
    expect(screen.queryByText('secret-content')).not.toBeInTheDocument();
  });

  it('renders children when the user has the required role', () => {
    renderGuard({ username: 'admin', roles: ['ADMIN'] });
    expect(screen.getByText('secret-content')).toBeInTheDocument();
  });
});
