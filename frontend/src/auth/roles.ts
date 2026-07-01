/**
 * Frontend mirror of the backend authorization matrix (SecurityConfig.java). Used only to hide/disable
 * actions the backend would reject with 403 — the SINGLE SOURCE OF TRUTH is SecurityConfig (and RbacIT
 * guards it). Keep both in sync when roles change.
 */
export const ROLES = ['ADMIN', 'ACCOUNTANT', 'WAREHOUSE', 'SALES', 'HR'] as const;
export type Role = (typeof ROLES)[number];

export type WriteAction =
  | 'masterdata.create'
  | 'purchasing.write'
  | 'purchasing.vendorBill'
  | 'sales.order'
  | 'sales.delivery'
  | 'sales.invoice'
  | 'sales.customerReturn'
  | 'inventory.adjust'
  | 'manufacturing.write'
  | 'payments.create'
  | 'ledger.post'
  | 'hr.write';

/** Which role each write action requires, mirroring the POST rules in SecurityConfig. */
export const WRITE_ACTION_ROLE: Record<WriteAction, Role> = {
  'masterdata.create': 'ADMIN',
  'purchasing.write': 'WAREHOUSE', // PO / goods receipt (vendor bill is separate)
  'purchasing.vendorBill': 'ACCOUNTANT',
  'sales.order': 'SALES',
  'sales.delivery': 'WAREHOUSE',
  'sales.invoice': 'ACCOUNTANT',
  'sales.customerReturn': 'ACCOUNTANT',
  'inventory.adjust': 'WAREHOUSE',
  'manufacturing.write': 'WAREHOUSE',
  'payments.create': 'ACCOUNTANT',
  'ledger.post': 'ACCOUNTANT', // manual entries + period close/reopen
  'hr.write': 'HR', // departments / positions / employees (admin holds HR too)
};

export function hasRole(roles: readonly string[], role: Role): boolean {
  return roles.includes(role);
}

export function canDo(roles: readonly string[], action: WriteAction): boolean {
  return hasRole(roles, WRITE_ACTION_ROLE[action]);
}
