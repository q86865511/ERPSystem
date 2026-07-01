import { useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import { Button, FocusTrap, Group, Paper, Stack, Text } from '@mantine/core';
import { useI18n } from '../i18n';
import { useOnboardingTour } from './useOnboardingTour';
import { ONBOARDING_STEPS } from './steps';

interface Rect {
  top: number;
  left: number;
  width: number;
  height: number;
}

function readRect(el: Element): Rect {
  const r = el.getBoundingClientRect();
  return { top: r.top, left: r.left, width: r.width, height: r.height };
}

const GAP = 16;

function calloutPosition(rect: Rect, position: 'top' | 'bottom' | 'left' | 'right'): CSSProperties {
  switch (position) {
    case 'top':
      return { left: rect.left, bottom: window.innerHeight - rect.top + GAP };
    case 'left':
      return { top: rect.top, right: window.innerWidth - rect.left + GAP };
    case 'right':
      return { top: rect.top, left: rect.left + rect.width + GAP };
    case 'bottom':
    default:
      return { top: rect.top + rect.height + GAP, left: rect.left };
  }
}

/**
 * Spotlight backdrop + callout for the current onboarding step. Route-aware: on every DOM mutation (which
 * fires on route changes) it scans forward from the persisted step index for the first step whose target
 * is present (or has none — the centered welcome step), silently advancing past steps whose target isn't
 * on the current page. Renders nothing while no step in range has a satisfied target.
 */
export function OnboardingTourOverlay() {
  const { active, stepIndex, totalSteps, next, previous, skip, setStepIndex } = useOnboardingTour();
  const { t } = useI18n();
  const [rect, setRect] = useState<Rect | null>(null);
  const [resolvedIndex, setResolvedIndex] = useState<number | null>(null);

  useEffect(() => {
    if (!active) {
      setResolvedIndex(null);
      return;
    }

    const locate = () => {
      for (let i = stepIndex; i < ONBOARDING_STEPS.length; i++) {
        const step = ONBOARDING_STEPS[i];
        if (!step) continue;
        if (!step.targetSelector) {
          setResolvedIndex(i);
          setRect(null);
          if (i !== stepIndex) setStepIndex(i);
          return;
        }
        const el = document.querySelector(step.targetSelector);
        if (el) {
          setResolvedIndex(i);
          setRect(readRect(el));
          if (i !== stepIndex) setStepIndex(i);
          return;
        }
      }
      setResolvedIndex(null);
      setRect(null);
    };

    locate();
    const observer = new MutationObserver(locate);
    observer.observe(document.body, { childList: true, subtree: true });
    window.addEventListener('resize', locate);
    window.addEventListener('scroll', locate, true);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', locate);
      window.removeEventListener('scroll', locate, true);
    };
  }, [active, stepIndex, setStepIndex]);

  useEffect(() => {
    if (resolvedIndex == null) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        skip();
      } else if (e.key === 'Enter') {
        e.preventDefault();
        next();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [resolvedIndex, skip, next]);

  if (!active || resolvedIndex == null) return null;
  const step = ONBOARDING_STEPS[resolvedIndex];
  if (!step) return null;
  const stepLabel = t('onboarding.stepAnnouncement', { current: resolvedIndex + 1, total: totalSteps });

  // Spotlight cutout via box-shadow (a border-radius box with a huge shadow covering everything else) when
  // a target rect exists; a uniform dim backdrop for the centered (no-target) step.
  const PAD = 8;
  const spotlightStyle: CSSProperties = rect
    ? {
        position: 'fixed',
        top: rect.top - PAD,
        left: rect.left - PAD,
        width: rect.width + PAD * 2,
        height: rect.height + PAD * 2,
        borderRadius: 10,
        boxShadow: '0 0 0 9999px rgba(26, 20, 16, 0.55)',
        border: '2px solid var(--mantine-color-brand-6)',
        pointerEvents: 'none',
        zIndex: 1000,
        transition: 'top 150ms ease, left 150ms ease, width 150ms ease, height 150ms ease',
      }
    : { position: 'fixed', inset: 0, background: 'rgba(26, 20, 16, 0.55)', zIndex: 1000 };

  const calloutStyle: CSSProperties = rect
    ? { position: 'fixed', zIndex: 1001, maxWidth: 320, ...calloutPosition(rect, step.position ?? 'bottom') }
    : { position: 'fixed', inset: 0, zIndex: 1001, display: 'flex', alignItems: 'center', justifyContent: 'center' };

  return (
    <>
      <div style={spotlightStyle} aria-hidden />
      <div style={calloutStyle}>
        <FocusTrap active>
          <Paper withBorder shadow="lg" radius="lg" p="md" role="dialog" aria-label={stepLabel} aria-live="polite" maw={320}>
            <Stack gap="xs">
              <Text size="xs" c="dimmed">
                {stepLabel}
              </Text>
              <Text fw={600}>{t(step.titleKey)}</Text>
              <Text size="sm" c="dimmed">
                {t(step.descriptionKey)}
              </Text>
              {step.actionKey && (
                <Text size="sm" fw={500} c="brand">
                  {t(step.actionKey)}
                </Text>
              )}
              <Group justify="space-between" mt="xs">
                <Button variant="subtle" size="xs" onClick={skip}>
                  {t('onboarding.skipTour')}
                </Button>
                <Group gap="xs">
                  {resolvedIndex > 0 && (
                    <Button variant="default" size="xs" onClick={previous}>
                      {t('onboarding.previousStep')}
                    </Button>
                  )}
                  <Button size="xs" onClick={next}>
                    {t('onboarding.nextStep')}
                  </Button>
                </Group>
              </Group>
            </Stack>
          </Paper>
        </FocusTrap>
      </div>
    </>
  );
}
