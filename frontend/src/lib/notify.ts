import { notifications } from '@mantine/notifications';

/** Extracts a human message from an openapi-fetch error (RFC 9457 problem+json body) or any throwable. */
export function errorMessage(error: unknown): string {
  if (error && typeof error === 'object') {
    const e = error as { detail?: string; title?: string; message?: string };
    return e.detail ?? e.title ?? e.message ?? 'Request failed';
  }
  return 'Request failed';
}

export function notifySuccess(message: string): void {
  notifications.show({ color: 'teal', message });
}

export function notifyError(error: unknown, title = 'Error'): void {
  notifications.show({ color: 'red', title, message: errorMessage(error) });
}
