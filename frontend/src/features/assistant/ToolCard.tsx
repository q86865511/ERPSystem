import { useState } from 'react';
import {
  ActionIcon,
  Badge,
  Box,
  Button,
  Code,
  Collapse,
  Group,
  Loader,
  Paper,
  Table,
  Text,
} from '@mantine/core';
import {
  IconAlertTriangle,
  IconCheck,
  IconChevronDown,
  IconTool,
  IconX,
} from '@tabler/icons-react';
import { useI18n } from '../../i18n';
import type { UiToolCall } from './types';

/** Renders a value cell for the write-tool input summary table (objects/arrays are JSON-stringified). */
function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

/** Flattens the top level of a tool input object into [key, value] rows for the confirmation summary. */
function inputRows(input: unknown): Array<[string, string]> {
  if (!input || typeof input !== 'object') return [];
  return Object.entries(input as Record<string, unknown>).map(([k, v]) => [k, formatValue(v)]);
}

interface ToolCardProps {
  tool: UiToolCall;
  /** True when this write tool is the pending confirmation (shows confirm/reject buttons). */
  awaitingConfirmation: boolean;
  /**
   * True once confirm/reject has been clicked for this card but the resulting state update hasn't landed
   * yet — disables both buttons immediately so a fast double-click can't fire onConfirm/onReject twice
   * before `awaitingConfirmation` flips to false on the next render.
   */
  resolving?: boolean;
  onConfirm: () => void;
  onReject: () => void;
}

/**
 * Tool-call card with a status machine driven by the reducer:
 *  - read:  running → success (collapsible result JSON) / error
 *  - write: proposed (input summary + confirm/reject) → running → success / error
 */
export function ToolCard({ tool, awaitingConfirmation, resolving = false, onConfirm, onReject }: ToolCardProps) {
  const { t } = useI18n();
  const [resultOpen, setResultOpen] = useState(false);

  const statusColor =
    tool.status === 'success'
      ? 'teal'
      : tool.status === 'error'
        ? 'red'
        : tool.status === 'proposed'
          ? 'yellow'
          : 'blue';

  return (
    <Paper withBorder radius="md" p="xs" data-tool-status={tool.status} data-tool-kind={tool.kind}>
      <Group justify="space-between" wrap="nowrap" gap="xs">
        <Group gap={6} wrap="nowrap">
          <IconTool size={15} aria-hidden />
          <Text size="sm" fw={600} style={{ fontFamily: 'var(--mantine-font-family-monospace)' }}>
            {tool.name}
          </Text>
        </Group>
        <Group gap={6} wrap="nowrap">
          {tool.status === 'running' && <Loader size="xs" aria-label={t('assistant.tool.running')} />}
          <Badge size="sm" color={statusColor} variant="light">
            {t(`assistant.tool.status.${tool.status}` as 'assistant.tool.status.running')}
          </Badge>
        </Group>
      </Group>

      {/* Write proposal: input summary + confirm/reject. */}
      {tool.status === 'proposed' && (
        <Box mt="xs">
          <Table withRowBorders={false} verticalSpacing={2} fz="xs">
            <Table.Tbody>
              {inputRows(tool.input).map(([key, value]) => (
                <Table.Tr key={key}>
                  <Table.Td style={{ fontWeight: 600, whiteSpace: 'nowrap', verticalAlign: 'top' }}>
                    {key}
                  </Table.Td>
                  <Table.Td style={{ wordBreak: 'break-word' }}>{value}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          {awaitingConfirmation && (
            <Group gap="xs" mt="xs">
              <Button
                size="xs"
                color="teal"
                leftSection={<IconCheck size={14} />}
                disabled={resolving}
                onClick={onConfirm}
              >
                {t('assistant.tool.confirm')}
              </Button>
              <Button
                size="xs"
                variant="default"
                leftSection={<IconX size={14} />}
                disabled={resolving}
                onClick={onReject}
              >
                {t('assistant.tool.reject')}
              </Button>
            </Group>
          )}
        </Box>
      )}

      {/* Error result is always shown; success result is collapsible. */}
      {tool.status === 'error' && tool.result && (
        <Group gap={6} mt="xs" wrap="nowrap" align="flex-start">
          <IconAlertTriangle size={14} color="var(--mantine-color-red-6)" aria-hidden />
          <Text size="xs" c="red" style={{ wordBreak: 'break-word' }}>
            {tool.result}
          </Text>
        </Group>
      )}

      {tool.status === 'success' && tool.result && (
        <Box mt="xs">
          <ActionIcon
            variant="subtle"
            color="gray"
            size="sm"
            aria-expanded={resultOpen}
            aria-label={t('assistant.tool.toggleResult')}
            onClick={() => setResultOpen((o) => !o)}
          >
            <IconChevronDown
              size={16}
              style={{ transform: resultOpen ? 'rotate(180deg)' : undefined, transition: 'transform 150ms' }}
            />
          </ActionIcon>
          <Collapse expanded={resultOpen}>
            <Code block fz="xs" mt={4} style={{ maxHeight: 220, overflow: 'auto' }}>
              {tool.result}
            </Code>
          </Collapse>
        </Box>
      )}
    </Paper>
  );
}
