/**
 * English message catalogue — the SOURCE OF TRUTH for the translation key shape. `types.ts` derives
 * `Messages = typeof en` and the dotted-path `TranslationKey` union from it, and `zh-TW.ts` is constrained
 * to `: Messages`, so any missing/renamed key fails `tsc`. Do NOT add `as const` here: leaves must widen to
 * `string` so the zh-TW catalogue (different literal strings) stays assignable.
 *
 * Key convention: `<area>.<element>`. Shared labels live under `common.*`; nav under `nav.*`; enum tokens
 * mirror the backend codes under `status.*` / `itemType.*` / `locationType.*` (value stays the code, only the
 * label is translated).
 *
 * Per-module namespaces live in `./modules/*` fragments (one file per feature module) and are composed in
 * below. Each fragment's `*Zh` is typed `: typeof *En`, so a module's zh/en mismatch fails to compile.
 */
import { auditEn } from './modules/audit';
import { hrEn } from './modules/hr';
import { inventoryEn } from './modules/inventory';
import { ledgerEn } from './modules/ledger';
import { manufacturingEn } from './modules/manufacturing';
import { masterdataEn } from './modules/masterdata';
import { onboardingEn } from './modules/onboarding';
import { paymentsEn } from './modules/payments';
import { printEn } from './modules/print';
import { purchasingEn } from './modules/purchasing';
import { reportingEn } from './modules/reporting';
import { salesEn } from './modules/sales';

export const en = {
  app: {
    title: 'Manufacturing ERP',
  },
  common: {
    signOut: 'Sign out',
    backToDashboard: 'Back to dashboard',
    error: 'Error',
    requestFailed: 'Request failed',
    noRoles: 'No roles',
    language: 'Language',
    colorScheme: 'Color scheme',
    create: 'Create',
    cancel: 'Cancel',
    confirm: 'Confirm',
    save: 'Save',
    view: 'View',
    addLine: 'Add line',
    removeLine: 'Remove line',
    submit: 'Submit',
    close: 'Close',
    details: 'Details',
    actions: 'Actions',
    all: 'All',
    loading: 'Loading…',
    noData: 'No data yet',
    search: 'Search…',
    noResults: 'No matching results',
    rowsTotal: '{total} rows',
    showingRange: '{from}–{to} of {total}',
  },
  // Shared column / form-field labels reused across modules (keep wording consistent via the glossary).
  field: {
    date: 'Date',
    status: 'Status',
    type: 'Type',
    name: 'Name',
    code: 'Code',
    quantity: 'Quantity',
    unitPrice: 'Unit price',
    unitCost: 'Unit cost',
    amount: 'Amount',
    total: 'Total',
    net: 'Net',
    vat: 'VAT',
    gross: 'Gross',
    memo: 'Memo',
    item: 'Item',
    partner: 'Partner',
    vendor: 'Vendor',
    customer: 'Customer',
    warehouse: 'Warehouse',
    location: 'Location',
    dueDate: 'Due date',
    asOf: 'As of',
    openBalance: 'Open balance',
    debit: 'Debit',
    credit: 'Credit',
    reason: 'Reason',
  },
  theme: {
    light: 'Light',
    dark: 'Dark',
    auto: 'Auto',
  },
  nav: {
    dashboard: 'Dashboard',
    masterData: 'Master Data',
    purchasing: 'Purchasing',
    sales: 'Sales',
    manufacturing: 'Manufacturing',
    inventory: 'Inventory',
    reporting: 'Reporting',
    ledger: 'Ledger',
    humanResources: 'Human Resources',
    audit: 'Audit Trail',
    toggleSection: 'Toggle {module} section',
  },
  login: {
    subtitle: 'Sign in to continue',
    username: 'Username',
    password: 'Password',
    signIn: 'Sign in',
    invalidCredentials: 'Invalid username or password',
    demoAccounts: 'Demo accounts',
    guestEntry: 'Browse as guest (read-only)',
    demoHint: "Each demo account's password equals its username.",
  },
  dashboard: {
    signedInAs: 'Signed in as',
    totalAssets: 'Total assets',
    totalLiabilities: 'Total liabilities',
    netIncome: 'Net income',
    kpiBasis: 'vs same period last month',
    overview: {
      revenue: 'Revenue',
      orders: 'Orders',
      receivables: 'Receivables',
      inventoryValue: 'Inventory value',
      orderPipeline: 'Order pipeline',
      inventoryStatus: 'Inventory status',
      salesTrend: 'Sales trend',
      alerts: 'Alerts',
      lowStock: 'Low stock items',
      overdueAr: 'Overdue receivables (90+ days)',
      stageCreated: 'Created',
      stageConfirmed: 'Confirmed',
      stageShipped: 'Shipped',
      stageClosed: 'Closed',
      raw: 'Raw materials',
      wip: 'Work in process',
      finished: 'Finished goods',
    },
  },
  notFound: {
    message: "That page doesn't exist.",
  },
  forbidden: {
    message: "You don't have the role required for this action.",
  },
  select: {
    noItems: 'No items',
    noPartners: 'No partners',
    noWarehouses: 'No warehouses',
    noLocations: 'No locations',
  },
  status: {
    DRAFT: 'Draft',
    CONFIRMED: 'Confirmed',
    PARTIALLY_RECEIVED: 'Partially received',
    RECEIVED: 'Received',
    PARTIALLY_SHIPPED: 'Partially shipped',
    SHIPPED: 'Shipped',
    PARTIALLY_PAID: 'Partially settled',
    PAID: 'Settled',
    POSTED: 'Posted',
    RETURNED: 'Returned',
    CLOSED: 'Closed',
    CANCELLED: 'Cancelled',
    IN_PROGRESS: 'In progress',
    RELEASED: 'Released',
    DONE: 'Done',
    MATCHED: 'Matched',
    PARTIAL: 'Partial',
    UNMATCHED: 'Unmatched',
    OPEN: 'Open',
    LOCKED: 'Locked',
    PENDING: 'Pending',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
    SUBMITTED: 'Submitted',
    PRESENT: 'Present',
    ABSENT: 'Absent',
    LATE: 'Late',
    LEAVE: 'On leave',
    REMOTE: 'Remote',
  },
  itemType: {
    RAW: 'Raw material',
    WIP: 'WIP',
    FINISHED: 'Finished goods',
    SERVICE: 'Service',
  },
  locationType: {
    STOCK: 'Stock',
    RECEIVING: 'Receiving',
    SHIPPING: 'Shipping',
    PRODUCTION_WIP: 'Production WIP',
    SCRAP: 'Scrap',
    VENDOR: 'Vendor',
    CUSTOMER: 'Customer',
    INVENTORY_LOSS: 'Inventory loss',
  },
  masterdata: masterdataEn,
  purchasing: purchasingEn,
  sales: salesEn,
  manufacturing: manufacturingEn,
  inventory: inventoryEn,
  reporting: reportingEn,
  ledger: ledgerEn,
  hr: hrEn,
  payments: paymentsEn,
  print: printEn,
  audit: auditEn,
  onboarding: onboardingEn,
};
