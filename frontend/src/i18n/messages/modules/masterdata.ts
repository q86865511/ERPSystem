/**
 * Master Data 模組訊息片段(英 / 繁中)。
 * 結構由 `masterdataEn` 定義,`masterdataZh: typeof masterdataEn` 強制兩者 key 完全一致。
 * 通用字串(create / cancel / name / code / type / vendor / customer / warehouse …)沿用 common.* / field.*,不在此重複定義。
 */
export const masterdataEn = {
  subtitle: 'Items, partners, warehouses and locations',
  tabs: {
    items: 'Items',
    partners: 'Partners',
    warehouses: 'Warehouses',
    locations: 'Locations',
  },
  validation: {
    required: 'Required',
    pickRole: 'Pick vendor and/or customer',
  },
  items: {
    new: 'New item',
    empty: 'No items yet.',
    created: 'Item {sku} created',
    th: {
      sku: 'SKU',
      uom: 'UoM',
      stocked: 'Stocked',
      stdCost: 'Std cost',
    },
    yes: 'Yes',
    no: 'No',
    form: {
      sku: 'SKU',
      uom: 'Unit of measure',
      standardCost: 'Standard cost',
      stocked: 'Stocked',
    },
  },
  partners: {
    new: 'New partner',
    empty: 'No partners yet.',
    created: 'Partner {code} created',
    th: {
      roles: 'Roles',
      taxId: 'Tax ID',
      terms: 'Terms (days)',
    },
    form: {
      taxId: 'Tax ID',
      paymentTerms: 'Payment terms (days)',
      apAccount: 'AP account (override)',
      arAccount: 'AR account (override)',
    },
  },
  warehouses: {
    new: 'New warehouse',
    empty: 'No warehouses yet.',
    created: 'Warehouse {code} created',
  },
  locations: {
    new: 'New location',
    empty: 'No locations.',
    created: 'Location {code} created',
    filterByWarehouse: 'Filter by warehouse',
    allWarehouses: 'All warehouses',
  },
};

export const masterdataZh: typeof masterdataEn = {
  subtitle: '商品、夥伴、倉庫與儲位',
  tabs: {
    items: '商品',
    partners: '夥伴',
    warehouses: '倉庫',
    locations: '儲位',
  },
  validation: {
    required: '必填',
    pickRole: '請選擇供應商與/或客戶',
  },
  items: {
    new: '新增商品',
    empty: '尚無商品。',
    created: '商品 {sku} 已建立',
    th: {
      sku: 'SKU',
      uom: '單位',
      stocked: '計庫存',
      stdCost: '標準成本',
    },
    yes: '是',
    no: '否',
    form: {
      sku: 'SKU',
      uom: '計量單位',
      standardCost: '標準成本',
      stocked: '計庫存',
    },
  },
  partners: {
    new: '新增夥伴',
    empty: '尚無夥伴。',
    created: '夥伴 {code} 已建立',
    th: {
      roles: '角色',
      taxId: '統一編號',
      terms: '帳期(天)',
    },
    form: {
      taxId: '統一編號',
      paymentTerms: '付款帳期(天)',
      apAccount: '應付科目(覆寫)',
      arAccount: '應收科目(覆寫)',
    },
  },
  warehouses: {
    new: '新增倉庫',
    empty: '尚無倉庫。',
    created: '倉庫 {code} 已建立',
  },
  locations: {
    new: '新增儲位',
    empty: '尚無儲位。',
    created: '儲位 {code} 已建立',
    filterByWarehouse: '依倉庫篩選',
    allWarehouses: '所有倉庫',
  },
};
