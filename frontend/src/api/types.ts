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
