import { Select, type SelectProps } from '@mantine/core';
import { useItems, useLocations, usePartners, useWarehouses } from '../features/masterdata/api';
import type { ItemType, LocationType } from '../api/types';
import { useI18n } from '../i18n';
import type { TranslationKey } from '../i18n';

type BaseProps = Omit<SelectProps, 'data' | 'value' | 'onChange'>;
type IdProps = { value: number | null; onChange: (id: number | null) => void };

const toIdValue = (value: number | null) => (value != null ? String(value) : null);
const fromIdValue = (v: string | null) => (v ? Number(v) : null);

/** Item picker (searchable), optionally restricted to a single item type. */
export function ItemSelect({
  value,
  onChange,
  itemType,
  ...props
}: BaseProps & IdProps & { itemType?: ItemType }) {
  const { t } = useI18n();
  const { data, isLoading } = useItems();
  const options = (data ?? [])
    .filter((i) => !itemType || i.itemType === itemType)
    .map((i) => ({ value: String(i.id), label: `${i.sku} — ${i.name}` }));
  return (
    <Select
      searchable
      nothingFoundMessage={t('select.noItems')}
      data={options}
      disabled={isLoading}
      value={toIdValue(value)}
      onChange={(v) => onChange(fromIdValue(v))}
      {...props}
    />
  );
}

/** Partner picker, optionally narrowed to vendors or customers. */
export function PartnerSelect({
  value,
  onChange,
  vendor,
  customer,
  ...props
}: BaseProps & IdProps & { vendor?: boolean; customer?: boolean }) {
  const { t } = useI18n();
  const { data, isLoading } = usePartners({ vendor, customer });
  const options = (data ?? []).map((p) => ({ value: String(p.id), label: `${p.code} — ${p.name}` }));
  return (
    <Select
      searchable
      nothingFoundMessage={t('select.noPartners')}
      data={options}
      disabled={isLoading}
      value={toIdValue(value)}
      onChange={(v) => onChange(fromIdValue(v))}
      {...props}
    />
  );
}

export function WarehouseSelect({ value, onChange, ...props }: BaseProps & IdProps) {
  const { t } = useI18n();
  const { data, isLoading } = useWarehouses();
  const options = (data ?? []).map((w) => ({ value: String(w.id), label: `${w.code} — ${w.name}` }));
  return (
    <Select
      searchable
      nothingFoundMessage={t('select.noWarehouses')}
      data={options}
      disabled={isLoading}
      value={toIdValue(value)}
      onChange={(v) => onChange(fromIdValue(v))}
      {...props}
    />
  );
}

/** Location picker, optionally scoped to a warehouse and/or a location type. */
export function LocationSelect({
  value,
  onChange,
  warehouseId,
  locationType,
  ...props
}: BaseProps & IdProps & { warehouseId?: number; locationType?: LocationType }) {
  const { t } = useI18n();
  const { data, isLoading } = useLocations(warehouseId);
  const options = (data ?? [])
    .filter((l) => !locationType || l.locationType === locationType)
    .map((l) => ({
      value: String(l.id),
      label: `${l.code} (${t(`locationType.${l.locationType}` as TranslationKey)})`,
    }));
  return (
    <Select
      searchable
      nothingFoundMessage={t('select.noLocations')}
      data={options}
      disabled={isLoading}
      value={toIdValue(value)}
      onChange={(v) => onChange(fromIdValue(v))}
      {...props}
    />
  );
}
