import type { components } from './schema';

/** Convenience aliases for generated DTOs, so feature code doesn't reach into `components['schemas']`. */
type Schemas = components['schemas'];

export type ItemResponse = Schemas['ItemResponse'];
export type CreateItemRequest = Schemas['CreateItemRequest'];
export type ItemType = NonNullable<ItemResponse['itemType']>;

export type PartnerResponse = Schemas['PartnerResponse'];
export type CreatePartnerRequest = Schemas['CreatePartnerRequest'];

export type WarehouseResponse = Schemas['WarehouseResponse'];
export type CreateWarehouseRequest = Schemas['CreateWarehouseRequest'];

export type LocationResponse = Schemas['LocationResponse'];
export type CreateLocationRequest = Schemas['CreateLocationRequest'];
export type LocationType = NonNullable<LocationResponse['locationType']>;

// --- Purchasing / payments (Stage 4) ---
export type PurchaseOrderResponse = Schemas['PurchaseOrderResponse'];
export type CreatePoRequest = Schemas['CreatePoRequest'];
export type GoodsReceiptResponse = Schemas['GoodsReceiptResponse'];
export type CreateGrnRequest = Schemas['CreateGrnRequest'];
export type VendorBillResponse = Schemas['VendorBillResponse'];
export type CreateBillRequest = Schemas['CreateBillRequest'];
export type PaymentResponse = Schemas['PaymentResponse'];
export type PayOutRequest = Schemas['PayOutRequest'];
export type ApAgingReport = Schemas['ApAgingReport'];

// --- Sales / O2C (Stage 5) ---
export type SalesOrderResponse = Schemas['SalesOrderResponse'];
export type CreateSoRequest = Schemas['CreateSoRequest'];
export type DeliveryResponse = Schemas['DeliveryResponse'];
export type CreateDeliveryRequest = Schemas['CreateDeliveryRequest'];
export type SalesInvoiceResponse = Schemas['SalesInvoiceResponse'];
export type CreateInvoiceRequest = Schemas['CreateInvoiceRequest'];
export type CustomerReturnResponse = Schemas['CustomerReturnResponse'];
export type CreateReturnRequest = Schemas['CreateReturnRequest'];
export type ArAgingReport = Schemas['ArAgingReport'];
export type PayInRequest = Schemas['PayInRequest'];

// --- Advanced / ledger (Stage 7) ---
export type CreateAdjustmentRequest = Schemas['CreateAdjustmentRequest'];
export type AdjustmentResponse = Schemas['AdjustmentResponse'];
export type JournalEntryRequest = Schemas['JournalEntryRequest'];
export type JournalEntryResponse = Schemas['JournalEntryResponse'];
export type FiscalPeriodResponse = Schemas['FiscalPeriodResponse'];
export type YearEndCloseResult = Schemas['YearEndCloseResult'];

// --- Manufacturing (Stage 6) ---
export type BomResponse = Schemas['BomResponse'];
export type CreateBomRequest = Schemas['CreateBomRequest'];
export type WorkOrderResponse = Schemas['WorkOrderResponse'];
export type CreateWorkOrderRequest = Schemas['CreateWorkOrderRequest'];
export type ReorderReport = Schemas['ReorderReport'];

// --- HR (B1) ---
export type EmployeeResponse = Schemas['EmployeeResponse'];
export type CreateEmployeeRequest = Schemas['CreateEmployeeRequest'];
export type DepartmentResponse = Schemas['DepartmentResponse'];
export type CreateDepartmentRequest = Schemas['CreateDepartmentRequest'];
export type PositionResponse = Schemas['PositionResponse'];
export type CreatePositionRequest = Schemas['CreatePositionRequest'];
export type EmploymentStatus = NonNullable<EmployeeResponse['status']>;

// --- HR time (B2) ---
export type AttendanceResponse = Schemas['AttendanceResponse'];
export type CreateAttendanceRequest = Schemas['CreateAttendanceRequest'];
export type AttendanceStatus = NonNullable<AttendanceResponse['status']>;
export type LeaveRequestResponse = Schemas['LeaveRequestResponse'];
export type CreateLeaveRequest = Schemas['CreateLeaveRequest'];
export type LeaveType = NonNullable<LeaveRequestResponse['leaveType']>;
export type LeaveStatus = NonNullable<LeaveRequestResponse['status']>;
export type TimesheetResponse = Schemas['TimesheetResponse'];
export type CreateTimesheetRequest = Schemas['CreateTimesheetRequest'];
export type TimesheetStatus = NonNullable<TimesheetResponse['status']>;
export const ATTENDANCE_STATUSES: AttendanceStatus[] = ['PRESENT', 'ABSENT', 'LATE', 'LEAVE', 'REMOTE'];
export const LEAVE_TYPES: LeaveType[] = ['ANNUAL', 'SICK', 'PERSONAL', 'UNPAID'];

export const ITEM_TYPES: ItemType[] = ['RAW', 'WIP', 'FINISHED', 'SERVICE'];
export const LOCATION_TYPES: LocationType[] = [
  'STOCK',
  'RECEIVING',
  'SHIPPING',
  'PRODUCTION_WIP',
  'SCRAP',
  'VENDOR',
  'CUSTOMER',
  'INVENTORY_LOSS',
];
