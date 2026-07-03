import { useEffect, useRef, useState } from 'react';
import {
  ActionIcon,
  Alert,
  Box,
  Drawer,
  Group,
  Paper,
  ScrollArea,
  Stack,
  Text,
  Textarea,
  Tooltip,
} from '@mantine/core';
import { IconAlertCircle, IconPlayerStop, IconSend, IconTrash } from '@tabler/icons-react';
import { useI18n } from '../../i18n';
import { ToolCard } from './ToolCard';
import { useAssistantChat } from './useAssistantChat';
import type { UiMessage } from './types';

interface AssistantDrawerProps {
  opened: boolean;
  onClose: () => void;
}

/** A single user/assistant entry: user bubbles right, assistant text + tool cards flow left. */
function MessageRow({
  message,
  pendingToolUseId,
  resolving,
  onConfirm,
  onReject,
}: {
  message: UiMessage;
  pendingToolUseId: string | null;
  resolving: boolean;
  onConfirm: () => void;
  onReject: () => void;
}) {
  if (message.kind === 'user') {
    return (
      <Group justify="flex-end">
        <Paper
          bg="var(--mantine-color-brand-light)"
          radius="md"
          p="xs"
          maw="85%"
          style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}
        >
          <Text size="sm">{message.text}</Text>
        </Paper>
      </Group>
    );
  }
  return (
    <Stack gap="xs">
      {message.text && (
        <Text size="sm" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
          {message.text}
        </Text>
      )}
      {message.tools.map((tool) => (
        <ToolCard
          key={tool.id}
          tool={tool}
          awaitingConfirmation={pendingToolUseId === tool.id}
          resolving={pendingToolUseId === tool.id && resolving}
          onConfirm={onConfirm}
          onReject={onReject}
        />
      ))}
    </Stack>
  );
}

export function AssistantDrawer({ opened, onClose }: AssistantDrawerProps) {
  const { t } = useI18n();
  const { messages, streaming, pending, error, send, resolveConfirmation, stop, reset } =
    useAssistantChat();
  const [draft, setDraft] = useState('');
  // Tracks "confirm/reject was just clicked for the current pending tool, UI update not landed yet" so the
  // ToolCard's buttons disable the instant they're clicked, not just once `pending` flips to null next render.
  const [resolving, setResolving] = useState(false);
  const viewportRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to the bottom as messages stream in.
  useEffect(() => {
    viewportRef.current?.scrollTo({ top: viewportRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  // Reset the "resolving" (buttons disabled) flag whenever a new confirmation shows up or the current one
  // is cleared, rather than clearing it manually after dispatch — this is the render-driven mirror of
  // useAssistantChat's synchronous ref guard, and stays correct regardless of how resolveConfirmation resolves.
  useEffect(() => {
    setResolving(false);
  }, [pending]);

  const submit = () => {
    if (!draft.trim() || streaming || pending) return;
    send(draft);
    setDraft('');
  };

  const confirm = () => {
    setResolving(true);
    resolveConfirmation(true);
  };
  const reject = () => {
    setResolving(true);
    resolveConfirmation(false);
  };

  const rateLimited = error?.status === 429;

  return (
    <Drawer
      opened={opened}
      onClose={onClose}
      position="right"
      size={420}
      title={t('assistant.title')}
      closeButtonProps={{ 'aria-label': t('assistant.close') }}
      // Keep the drawer (and this hook's state) mounted once opened, so closing/reopening preserves the
      // conversation and the close animation plays normally — only the initial mount is lazy (see AppLayout).
      keepMounted
      styles={{ body: { height: 'calc(100% - 60px)', display: 'flex', flexDirection: 'column' } }}
    >
      <Group justify="flex-end" mb="xs">
        <Tooltip label={t('assistant.clear')}>
          <ActionIcon
            variant="subtle"
            color="gray"
            aria-label={t('assistant.clear')}
            disabled={messages.length === 0 || streaming}
            onClick={reset}
          >
            <IconTrash size={16} />
          </ActionIcon>
        </Tooltip>
      </Group>

      <ScrollArea style={{ flex: 1 }} viewportRef={viewportRef} type="auto">
        <Stack gap="md" pb="sm">
          {messages.length === 0 && !error && (
            <Text c="dimmed" size="sm" ta="center" mt="xl">
              {t('assistant.empty')}
            </Text>
          )}
          {messages.map((message) => (
            <MessageRow
              key={message.id}
              message={message}
              pendingToolUseId={pending?.toolUseId ?? null}
              resolving={resolving}
              onConfirm={confirm}
              onReject={reject}
            />
          ))}
          {error && (
            <Alert
              color={rateLimited ? 'yellow' : 'red'}
              icon={<IconAlertCircle size={16} />}
              title={rateLimited ? t('assistant.error.rateLimited') : t('assistant.error.title')}
            >
              {rateLimited ? t('assistant.error.rateLimitedDetail') : (error.detail ?? t('assistant.error.generic'))}
            </Alert>
          )}
        </Stack>
      </ScrollArea>

      <Box mt="sm">
        <Group gap="xs" align="flex-end" wrap="nowrap">
          <Textarea
            style={{ flex: 1 }}
            autosize
            minRows={1}
            maxRows={5}
            placeholder={t('assistant.inputPlaceholder')}
            aria-label={t('assistant.inputPlaceholder')}
            value={draft}
            disabled={streaming || !!pending}
            onChange={(e) => setDraft(e.currentTarget.value)}
            onKeyDown={(e) => {
              // Enter sends; Shift+Enter inserts a newline.
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                submit();
              }
            }}
          />
          {streaming ? (
            <ActionIcon
              size="lg"
              color="red"
              variant="light"
              aria-label={t('assistant.stop')}
              onClick={stop}
            >
              <IconPlayerStop size={18} />
            </ActionIcon>
          ) : (
            <ActionIcon
              size="lg"
              color="brand"
              aria-label={t('assistant.send')}
              disabled={!draft.trim() || !!pending}
              onClick={submit}
            >
              <IconSend size={18} />
            </ActionIcon>
          )}
        </Group>
      </Box>
    </Drawer>
  );
}
