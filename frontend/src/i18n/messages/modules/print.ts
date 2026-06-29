/**
 * Print/PDF view strings (En + Zh). Merged into the core catalogues by the i18n index under `print.*`.
 * Document field labels reuse `common.*` / `field.*` and the owning module's keys.
 */
export const printEn = {
  print: 'Print',
  back: 'Back',
  documentNo: 'No.',
  salesInvoice: 'Sales Invoice',
  purchaseOrder: 'Purchase Order',
  delivery: 'Delivery Note',
  trialBalance: 'Trial Balance',
};

export const printZh: typeof printEn = {
  print: '列印',
  back: '返回',
  documentNo: '單號',
  salesInvoice: '銷售發票',
  purchaseOrder: '採購單',
  delivery: '出貨單',
  trialBalance: '試算表',
};
