import { createBrowserRouter, Outlet } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';
import { RequireAuth } from '../auth/RequireAuth';
import { AppLayout } from '../components/AppLayout';
import { DashboardPage } from '../pages/DashboardPage';
import { LoginPage } from '../pages/LoginPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { PlaceholderPage } from '../pages/PlaceholderPage';

// AuthProvider sits inside the router tree (it uses useNavigate). /login is public; everything else is
// gated by RequireAuth and rendered inside the AppLayout shell. Module screens are placeholders until
// their stages land.
export const router = createBrowserRouter([
  {
    element: (
      <AuthProvider>
        <Outlet />
      </AuthProvider>
    ),
    children: [
      { path: '/login', element: <LoginPage /> },
      {
        element: (
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        ),
        children: [
          { path: '/', element: <DashboardPage /> },
          { path: '/masterdata', element: <PlaceholderPage title="Master Data" /> },
          { path: '/purchasing', element: <PlaceholderPage title="Purchasing" /> },
          { path: '/sales', element: <PlaceholderPage title="Sales" /> },
          { path: '/manufacturing', element: <PlaceholderPage title="Manufacturing" /> },
          { path: '/inventory', element: <PlaceholderPage title="Inventory" /> },
          { path: '/reporting', element: <PlaceholderPage title="Reporting" /> },
          { path: '/ledger', element: <PlaceholderPage title="Ledger" /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]);
