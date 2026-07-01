import { lazy, Suspense } from 'react';
import { createBrowserRouter, Outlet } from 'react-router-dom';
import { Center, Loader } from '@mantine/core';
import { AuthProvider } from '../auth/AuthContext';
import { RequireAuth } from '../auth/RequireAuth';
import { RequireRole } from '../auth/RequireRole';
import { AppLayout } from '../components/AppLayout';

// Lazy-loaded so each page (and the @tabler icons it pulls) becomes its own chunk, instead of one ~890 KB
// monolith. The auth/layout/print shells stay eager (tiny, always needed). Named exports need the adapter.
const DashboardPage = lazy(() => import('../pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const LoginPage = lazy(() => import('../pages/LoginPage').then((m) => ({ default: m.LoginPage })));
const NotFoundPage = lazy(() => import('../pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })));
const MasterDataPage = lazy(() => import('../features/masterdata/MasterDataPage').then((m) => ({ default: m.MasterDataPage })));
const PurchasingPage = lazy(() => import('../features/purchasing/PurchasingPage').then((m) => ({ default: m.PurchasingPage })));
const SalesPage = lazy(() => import('../features/sales/SalesPage').then((m) => ({ default: m.SalesPage })));
const ManufacturingPage = lazy(() => import('../features/manufacturing/ManufacturingPage').then((m) => ({ default: m.ManufacturingPage })));
const InventoryPage = lazy(() => import('../features/inventory/InventoryPage').then((m) => ({ default: m.InventoryPage })));
const ReportsPage = lazy(() => import('../features/reporting/ReportsPage').then((m) => ({ default: m.ReportsPage })));
const LedgerPage = lazy(() => import('../features/ledger/LedgerPage').then((m) => ({ default: m.LedgerPage })));
const AuditPage = lazy(() => import('../features/audit/AuditPage').then((m) => ({ default: m.AuditPage })));
const HRPage = lazy(() => import('../features/hr/HRPage').then((m) => ({ default: m.HRPage })));
const SalesInvoicePrint = lazy(() => import('../features/print/SalesInvoicePrint').then((m) => ({ default: m.SalesInvoicePrint })));
const PurchaseOrderPrint = lazy(() => import('../features/print/PurchaseOrderPrint').then((m) => ({ default: m.PurchaseOrderPrint })));
const DeliveryPrint = lazy(() => import('../features/print/DeliveryPrint').then((m) => ({ default: m.DeliveryPrint })));
const TrialBalancePrint = lazy(() => import('../features/print/TrialBalancePrint').then((m) => ({ default: m.TrialBalancePrint })));

const routeFallback = (
  <Center h="100vh">
    <Loader color="brand" />
  </Center>
);

// AuthProvider sits inside the router tree (it uses useNavigate). /login is public; everything else is
// gated by RequireAuth and rendered inside the AppLayout shell. Pages are lazy — each route group wraps
// its outlet in Suspense.
export const router = createBrowserRouter([
  {
    element: (
      <AuthProvider>
        <Outlet />
      </AuthProvider>
    ),
    children: [
      { path: '/login', element: <Suspense fallback={routeFallback}><LoginPage /></Suspense> },
      {
        // Printable document views: authenticated, but rendered without the AppShell chrome.
        element: (
          <RequireAuth>
            <Suspense fallback={routeFallback}>
              <Outlet />
            </Suspense>
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
        // AppLayout is eager; it wraps its own <Outlet> in Suspense so the nav/header stay put while a
        // lazy page chunk loads (only the content area shows the loader).
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
          { path: '/hr', element: <HRPage /> },
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
