import { Group, Stack, Text, Title } from '@mantine/core';
import type { ReactNode } from 'react';

/**
 * Standard page header: optional breadcrumb + title (with an optional status badge beside it) + optional
 * subtitle on the left, actions on the right. `breadcrumb`, `status` and `onboardingId` are additive —
 * existing callers that pass only title/subtitle/actions are unaffected.
 */
export function PageHeader({
  title,
  subtitle,
  breadcrumb,
  status,
  actions,
  onboardingId,
}: {
  title: string;
  subtitle?: string;
  breadcrumb?: ReactNode;
  status?: ReactNode;
  actions?: ReactNode;
  /** Sets `data-onboarding` on the root so the onboarding tour can target this page's header. */
  onboardingId?: string;
}) {
  return (
    <Group justify="space-between" align="flex-end" mb="md">
      <Stack gap={2}>
        {breadcrumb}
        <Group gap="sm" align="center">
          {/* `data-onboarding` sits on the (narrow) title text, not the full-width row, so a `position:
              'right'` tour callout floats in the page's open space instead of covering the tabs below. */}
          <Title order={2} data-onboarding={onboardingId}>
            {title}
          </Title>
          {status}
        </Group>
        {subtitle && (
          <Text size="sm" c="dimmed">
            {subtitle}
          </Text>
        )}
      </Stack>
      {actions}
    </Group>
  );
}
