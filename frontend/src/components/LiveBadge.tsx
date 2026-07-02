import { Badge } from '@mantine/core';
import { useI18n } from '../i18n';

/**
 * The "live data" pill shown on dashboard cards. Uses the semantic positive tokens (index.css) so the
 * label clears WCAG AA — the previous `color="teal" variant="light"` pairing measured 4.32:1 (ERP-015).
 */
export function LiveBadge() {
  const { t } = useI18n();
  return (
    <Badge
      size="sm"
      variant="light"
      styles={{
        root: { backgroundColor: 'var(--erp-positive-bg)' },
        label: { color: 'var(--erp-positive-text)' },
      }}
    >
      {t('reporting.overview.liveData')}
    </Badge>
  );
}
