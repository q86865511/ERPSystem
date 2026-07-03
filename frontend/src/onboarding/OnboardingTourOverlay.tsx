import { useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import { Button, FocusTrap, Group, Paper, Stack, Text } from '@mantine/core';
import { useLocation, useNavigate } from 'react-router-dom';
import { useI18n } from '../i18n';
import { useOnboardingTour } from './useOnboardingTour';

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

function isInViewport(el: Element): boolean {
  const r = el.getBoundingClientRect();
  return r.top >= 0 && r.left >= 0 && r.bottom <= window.innerHeight && r.right <= window.innerWidth;
}

const GAP = 16;
const CALLOUT_MAX_WIDTH = 320;
/** Rough callout height used only to keep the bottom edge on screen when clamping. */
const CALLOUT_EST_HEIGHT = 200;
const MARGIN = 8;

function calloutPosition(rect: Rect, position: 'top' | 'bottom' | 'left' | 'right'): { top: number; left: number } {
  switch (position) {
    case 'top':
      return { top: rect.top - GAP - CALLOUT_EST_HEIGHT, left: rect.left };
    case 'left':
      return { top: rect.top, left: rect.left - GAP - CALLOUT_MAX_WIDTH };
    case 'right':
      return { top: rect.top, left: rect.left + rect.width + GAP };
    case 'bottom':
    default:
      return { top: rect.top + rect.height + GAP, left: rect.left };
  }
}

/** Keep the callout box fully inside the viewport regardless of where its anchor sits. */
function clampToViewport(top: number, left: number): { top: number; left: number } {
  const maxLeft = Math.max(MARGIN, window.innerWidth - CALLOUT_MAX_WIDTH - MARGIN);
  const maxTop = Math.max(MARGIN, window.innerHeight - CALLOUT_EST_HEIGHT - MARGIN);
  return {
    top: Math.min(Math.max(top, MARGIN), maxTop),
    left: Math.min(Math.max(left, MARGIN), maxLeft),
  };
}

/**
 * Spotlight backdrop + callout for the current onboarding step. Route-aware, with a single fast-forward
 * invariant: a step is only skipped when it has NO route and its target is absent (e.g. the header step on a
 * page that lacks it). Every route-bound step instead WAITS:
 * - Wrong page (step.route !== pathname): navigate to step.route, then wait (render nothing) for its target.
 * - Right page but target not yet in the DOM: wait — the target is guaranteed to appear once the page's lazy
 *   chunk mounts, because each route-bound target is rendered unconditionally by its page and RBAC filtering
 *   drops any step the user can't reach. The MutationObserver re-runs the locate scan on chunk mount, so the
 *   wait always resolves and never deadlocks. This is what stops the lazy-Suspense gap from fast-forwarding
 *   to the next step (and navigating to ITS route), which would ping-pong between pages.
 * The target is scrolled into view before its rect is read so below-the-fold anchors are framed on screen.
 */
export function OnboardingTourOverlay() {
  const { active, stepIndex, totalSteps, steps, next, previous, skip, setStepIndex } = useOnboardingTour();
  const { t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const pathname = location.pathname;
  const [rect, setRect] = useState<Rect | null>(null);
  const [resolvedIndex, setResolvedIndex] = useState<number | null>(null);

  useEffect(() => {
    if (!active) {
      setResolvedIndex(null);
      return;
    }

    const locate = () => {
      for (let i = stepIndex; i < steps.length; i++) {
        const step = steps[i];
        if (!step) continue;

        // Step lives on another page: navigate there and wait for its target to mount. Don't fast-forward
        // and don't render a callout during the transition — otherwise a later every-page target could be
        // matched first and swallow this step.
        if (step.route && step.route !== pathname) {
          setResolvedIndex(null);
          setRect(null);
          navigate(step.route);
          return;
        }

        // No target (centered welcome step) — show it as-is.
        if (!step.targetSelector) {
          setResolvedIndex(i);
          setRect(null);
          if (i !== stepIndex) setStepIndex(i);
          return;
        }

        const el = document.querySelector(step.targetSelector);
        if (el) {
          if (!isInViewport(el)) el.scrollIntoView({ block: 'center' });
          setResolvedIndex(i);
          setRect(readRect(el));
          if (i !== stepIndex) setStepIndex(i);
          return;
        }

        // Target absent. If the step declares a route (and we're already on it, per the check above), the
        // target is guaranteed to appear once the page's lazy chunk mounts — so WAIT rather than fall
        // through. Falling through here would fast-forward to the next step and navigate to ITS route
        // during the Suspense gap, ping-ponging between pages. The MutationObserver re-runs locate() when
        // the chunk mounts, and RBAC filtering guarantees every route-bound target is reachable, so the
        // wait always resolves — it never deadlocks.
        if (step.route) {
          setResolvedIndex(null);
          setRect(null);
          return;
        }
        // No route + target absent (e.g. header target on a page that lacks it) — genuinely skippable, so
        // fall through to try the next step.
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
  }, [active, stepIndex, steps, pathname, navigate, setStepIndex]);

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
  const step = steps[resolvedIndex];
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
    ? {
        position: 'fixed',
        zIndex: 1001,
        maxWidth: CALLOUT_MAX_WIDTH,
        ...(() => {
          const { top, left } = calloutPosition(rect, step.position ?? 'bottom');
          return clampToViewport(top, left);
        })(),
      }
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
