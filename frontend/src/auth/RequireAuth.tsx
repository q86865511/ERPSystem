import type { ReactNode } from 'react';
import { Center, Loader } from '@mantine/core';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './useAuth';

/** Gate for authenticated areas: waits out the on-load silent refresh, then redirects to /login if no session. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, bootstrapping } = useAuth();
  const location = useLocation();
  if (bootstrapping) {
    return (
      <Center mih="100vh">
        <Loader />
      </Center>
    );
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <>{children}</>;
}
