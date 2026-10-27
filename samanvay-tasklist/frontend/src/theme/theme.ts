import { createTheme, lighten } from '@mui/material/styles';
import type { Branding } from '../api/types';

export const DEFAULT_BRANDING: Branding = {
  name: 'Samanvay',
  product: 'Tasklist',
  primaryColor: '#1f4e79',
  secondaryColor: '#00796b',
};

/** Theme in the deployment's brand colors. Light and dark schemes follow the operating system preference. */
export function createAppTheme(branding: Branding) {
  return createTheme({
    cssVariables: true,
    colorSchemes: {
      light: {
        palette: {
          primary: { main: branding.primaryColor },
          secondary: { main: branding.secondaryColor },
          background: { default: '#f5f7fa', paper: '#ffffff' },
        },
      },
      dark: {
        palette: {
          primary: { main: lighten(branding.primaryColor, 0.45) },
          secondary: { main: lighten(branding.secondaryColor, 0.3) },
          background: { default: '#0f1720', paper: '#16212c' },
        },
      },
    },
    shape: { borderRadius: 10 },
    typography: {
      fontFamily: '"Inter", "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
      h1: { fontSize: '1.6rem', fontWeight: 600 },
      h2: { fontSize: '1.3rem', fontWeight: 600 },
      h3: { fontSize: '1.1rem', fontWeight: 600 },
    },
    components: {
      MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { textTransform: 'none' } } },
      MuiPaper: { defaultProps: { variant: 'outlined' } },
      MuiCard: { defaultProps: { variant: 'outlined' } },
    },
  });
}

export const theme = createAppTheme(DEFAULT_BRANDING);
