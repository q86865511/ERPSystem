import { useSearchParams } from 'react-router-dom';

/**
 * Drives a module page's active tab from the `?tab=` query param, so the collapsible sidebar sub-items can
 * deep-link to a specific tab (e.g. `/purchasing?tab=bills`). Returns `[tab, setTab]` where `tab` falls back
 * to `defaultTab` when the param is absent; `setTab` (wired to Mantine `Tabs.onChange`) updates the URL.
 */
export function useTabParam(defaultTab: string): [string, (value: string | null) => void] {
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') ?? defaultTab;
  const setTab = (value: string | null) => {
    const next = new URLSearchParams(params);
    if (value) next.set('tab', value);
    else next.delete('tab');
    setParams(next, { replace: true });
  };
  return [tab, setTab];
}
