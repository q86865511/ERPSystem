import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { ForbiddenPage } from '../pages/ForbiddenPage';
import type { Role } from './roles';
import { useAuth } from './useAuth';

/** Gate for role-restricted pages: 403 screen when authenticated but lacking the role. */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user, hasRole } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (!hasRole(role)) {
    return <ForbiddenPage />;
  }
  return <>{children}</>;
}
