import { Drawer, Stack } from '@mantine/core';
import type { ReactNode } from 'react';

/**
 * Right-slide drawer for viewing a single record: title + scrollable content + an optional footer (e.g. a
 * `StateButton`). Width defaults to `xl`; the warm skin, radius and terracotta close button come from the
 * theme. Mantine ships focus-trap + return-focus, so no custom a11y code is needed.
 */
export function DetailDrawer({
  opened,
  onClose,
  title,
  children,
  footer,
  size = 'xl',
}: {
  opened: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  size?: string;
}) {
  return (
    <Drawer opened={opened} onClose={onClose} title={title} position="right" size={size}>
      <Stack>
        {children}
        {footer}
      </Stack>
    </Drawer>
  );
}
