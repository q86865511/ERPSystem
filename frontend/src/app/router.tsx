import { createBrowserRouter, Outlet } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';
import { RequireAuth } from '../auth/RequireAuth';
import { AppLayout } from '../components/AppLayout';
import { InventoryPage } from '../features/inventory/InventoryPage';
import { MasterDataPage } from '../features/masterdata/MasterDataPage';
import { PurchasingPage } from '../features/purchasing/PurchasingPage';
import { ReportsPage } from '../features/reporting/ReportsPage';
import { SalesPage } from '../features/sales/SalesPage';
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
          { path: '/masterdata', element: <MasterDataPage /> },
          { path: '/purchasing', element: <PurchasingPage /> },
          { path: '/sales', element: <SalesPage /> },
          { path: '/manufacturing', element: <PlaceholderPage title="Manufacturing" /> },
          { path: '/inventory', element: <InventoryPage /> },
          { path: '/reporting', element: <ReportsPage /> },
          { path: '/ledger', element: <PlaceholderPage title="Ledger" /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]);
