/**
 * English message catalogue — the SOURCE OF TRUTH for the translation key shape. `types.ts` derives
 * `Messages = typeof en` and the dotted-path `TranslationKey` union from it, and `zh-TW.ts` is constrained
 * to `: Messages`, so any missing/renamed key fails `tsc`. Do NOT add `as const` here: leaves must widen to
 * `string` so the zh-TW catalogue (different literal strings) stays assignable.
 *
 * Key convention: `<area>.<element>`. Shared labels live under `common.*`; nav under `nav.*`; enum tokens
 * mirror the backend codes under `status.*` / `itemType.*` / `locationType.*` (value stays the code, only the
 * label is translated).
 */
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
    create: 'Create',
    cancel: 'Cancel',
    confirm: 'Confirm',
    save: 'Save',
    view: 'View',
    addLine: 'Add line',
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
  },
  login: {
    subtitle: 'Sign in to continue',
    username: 'Username',
    password: 'Password',
    signIn: 'Sign in',
    invalidCredentials: 'Invalid username or password',
    demoAccounts: 'Demo accounts',
    demoHint: "Each demo account's password equals its username.",
  },
  dashboard: {
    signedInAs: 'Signed in as',
    totalAssets: 'Total assets',
    totalLiabilities: 'Total liabilities',
    netIncome: 'Net income',
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
};
