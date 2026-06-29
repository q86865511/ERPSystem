import { createBrowserRouter, Outlet } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';
import { RequireAuth } from '../auth/RequireAuth';
import { RequireRole } from '../auth/RequireRole';
import { AppLayout } from '../components/AppLayout';
import { AuditPage } from '../features/audit/AuditPage';
import { InventoryPage } from '../features/inventory/InventoryPage';
import { LedgerPage } from '../features/ledger/LedgerPage';
import { ManufacturingPage } from '../features/manufacturing/ManufacturingPage';
import { MasterDataPage } from '../features/masterdata/MasterDataPage';
import { PurchasingPage } from '../features/purchasing/PurchasingPage';
import { ReportsPage } from '../features/reporting/ReportsPage';
import { SalesPage } from '../features/sales/SalesPage';
import { DeliveryPrint } from '../features/print/DeliveryPrint';
import { PurchaseOrderPrint } from '../features/print/PurchaseOrderPrint';
import { SalesInvoicePrint } from '../features/print/SalesInvoicePrint';
import { TrialBalancePrint } from '../features/print/TrialBalancePrint';
import { DashboardPage } from '../pages/DashboardPage';
import { LoginPage } from '../pages/LoginPage';
import { NotFoundPage } from '../pages/NotFoundPage';

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
        // Printable document views: authenticated, but rendered without the AppShell chrome.
        element: (
          <RequireAuth>
            <Outlet />
          </RequireAuth>
        ),
        children: [
          { path: '/print/sales-invoice/:id', element: <SalesInvoicePrint /> },
          { path: '/print/purchase-order/:id', element: <PurchaseOrderPrint /> },
          { path: '/print/delivery/:id', element: <DeliveryPrint /> },
          { path: '/print/trial-balance', element: <TrialBalancePrint /> },
        ],
      },
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
          { path: '/manufacturing', element: <ManufacturingPage /> },
          { path: '/inventory', element: <InventoryPage /> },
          { path: '/reporting', element: <ReportsPage /> },
          { path: '/ledger', element: <LedgerPage /> },
          {
            path: '/audit',
            element: (
              <RequireRole role="ADMIN">
                <AuditPage />
              </RequireRole>
            ),
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]);
