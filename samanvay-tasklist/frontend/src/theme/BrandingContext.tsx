import { CssBaseline, ThemeProvider } from '@mui/material';
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../api/client';
import type { Branding } from '../api/types';
import { createAppTheme, DEFAULT_BRANDING } from './theme';

const BrandingContext = createContext<Branding>(DEFAULT_BRANDING);

export const useBranding = () => useContext(BrandingContext);

/**
 * Loads the deployment's branding before the first render, so users never see another brand flash by. Falls
 * back to the default brand if the server can't be reached.
 */
export function BrandingProvider({ children }: { children: ReactNode }) {
  const [branding, setBranding] = useState<Branding | null>(null);

  useEffect(() => {
    let active = true;
    api<Branding>('/api/branding', { silentUnauthorized: true })
      .then((b) => active && setBranding({ ...DEFAULT_BRANDING, ...b }))
      .catch(() => active && setBranding(DEFAULT_BRANDING));
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (branding) {
      document.title = `${branding.name} ${branding.product}`;
      document.querySelector('meta[name="theme-color"]')?.setAttribute('content', branding.primaryColor);
      if (branding.logoUrl) {
        const icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
        icon?.removeAttribute('type');
        icon?.setAttribute('href', branding.logoUrl);
      }
    }
  }, [branding]);

  const theme = useMemo(() => createAppTheme(branding ?? DEFAULT_BRANDING), [branding]);

  if (!branding) {
    return null;
  }
  return (
    <BrandingContext.Provider value={branding}>
      <ThemeProvider theme={theme} defaultMode="system">
        <CssBaseline enableColorScheme />
        {children}
      </ThemeProvider>
    </BrandingContext.Provider>
  );
}
