import { Button, Tooltip } from '@mantine/core';
import type { ButtonProps, MantineColor } from '@mantine/core';

type StateButtonProps = Omit<ButtonProps, 'children' | 'color'> & {
  label: string;
  onClick: () => void | Promise<void>;
  disabled?: boolean;
  /** When the button is disabled, this reason is shown in a tooltip so the guard is discoverable. */
  disabledReason?: string;
  color?: MantineColor;
};

/**
 * Action button for state-machine transitions (e.g. confirm a draft order). When `disabled` with a
 * `disabledReason`, it is wrapped in a tooltip explaining why — the disabled button is made hover-reachable
 * via a wrapping span so the reason is visible.
 */
export function StateButton({ label, onClick, disabled, disabledReason, color, ...props }: StateButtonProps) {
  const button = (
    <Button {...props} color={color} disabled={disabled} onClick={() => onClick()}>
      {label}
    </Button>
  );
  if (disabled && disabledReason) {
    return (
      <Tooltip label={disabledReason} withArrow>
        <span style={{ display: 'inline-block' }}>{button}</span>
      </Tooltip>
    );
  }
  return button;
}
