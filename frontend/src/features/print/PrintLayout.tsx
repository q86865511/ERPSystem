import { useEffect, useRef, type ReactNode } from 'react';
import { Button, Center, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { useI18n } from '../../i18n';
import './print.css';

/**
 * Shared skeleton for the printable document routes. These render outside the AppShell (auth-gated but no
 * nav chrome). It waits out the on-load silent refresh (`bootstrapping`) and the document's queries, then
 * auto-opens the browser print dialog once (Save as PDF). On-screen Back/Print buttons are hidden in print.
 */
export function PrintLayout({ ready, children }: { ready: boolean; children: ReactNode }) {
  const navigate = useNavigate();
  const { t } = useI18n();
  const { bootstrapping } = useAuth();
  const printed = useRef(false);
  const allReady = ready && !bootstrapping;

  useEffect(() => {
    if (allReady && !printed.current) {
      printed.current = true;
      // Defer to the next frame so the document has painted before the print dialog snapshots it.
      const id = window.setTimeout(() => window.print(), 300);
      return () => window.clearTimeout(id);
    }
    return undefined;
  }, [allReady]);

  if (!allReady) {
    return (
      <Center mih="100vh">
        <Loader />
      </Center>
    );
  }

  return (
    <div className="print-page">
      <Group className="print-actions" justify="flex-end" gap="xs" mb="lg">
        <Button variant="default" size="xs" onClick={() => navigate(-1)}>
          {t('print.back')}
        </Button>
        <Button size="xs" onClick={() => window.print()}>
          {t('print.print')}
        </Button>
      </Group>
      {children}
    </div>
  );
}

/** Standard letterhead for a printed document: company name + document type, then number/date and party. */
export function PrintHeader({
  docType,
  number,
  date,
  party,
  partyLabel,
}: {
  docType: string;
  number?: string;
  date?: string;
  party?: string;
  partyLabel?: string;
}) {
  const { t } = useI18n();
  return (
    <Stack gap={4} mb="lg">
      <Group justify="space-between" align="flex-end">
        <Title order={3}>{t('app.title')}</Title>
        <Title order={4} c="dimmed">
          {docType}
        </Title>
      </Group>
      <Group justify="space-between">
        <Text size="sm">{number ? `${t('print.documentNo')} ${number}` : ''}</Text>
        <Text size="sm">{date ? `${t('field.date')}: ${date}` : ''}</Text>
      </Group>
      {party && (
        <Text size="sm">
          {partyLabel}: {party}
        </Text>
      )}
    </Stack>
  );
}
