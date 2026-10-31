import { alpha, createTheme, darken, lighten } from '@mui/material/styles';
import type { Branding } from '../api/types';

export const DEFAULT_BRANDING: Branding = {
  name: 'Samanvay',
  product: 'Tasklist',
  primaryColor: '#1f4e79',
  secondaryColor: '#00796b',
  logoUrl: null,
};

const FONT = '"Plus Jakarta Sans", "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';

/**
 * Theme in the deployment's brand colors on calm slate neutrals. Light and dark schemes follow the operating
 * system preference.
 */
export function createAppTheme(branding: Branding) {
  return createTheme({
    cssVariables: true,
    colorSchemes: {
      light: {
        palette: {
          primary: { main: branding.primaryColor, dark: darken(branding.primaryColor, 0.2) },
          secondary: { main: branding.secondaryColor },
          background: { default: '#f8fafc', paper: '#ffffff' },
          text: { primary: '#0f172a', secondary: '#475569' },
          divider: '#e2e8f0',
        },
      },
      dark: {
        palette: {
          primary: { main: lighten(branding.primaryColor, 0.45) },
          secondary: { main: lighten(branding.secondaryColor, 0.35) },
          background: { default: '#0b1120', paper: '#131c2e' },
          text: { primary: '#e2e8f0', secondary: '#94a3b8' },
          divider: '#263248',
        },
      },
    },
    shape: { borderRadius: 10 },
    typography: {
      fontFamily: FONT,
      h1: { fontSize: '1.65rem', fontWeight: 700, letterSpacing: '-0.02em', lineHeight: 1.25 },
      h2: { fontSize: '1.3rem', fontWeight: 700, letterSpacing: '-0.01em' },
      h3: { fontSize: '1.1rem', fontWeight: 600 },
      subtitle2: { fontWeight: 600 },
      button: { fontWeight: 600 },
      overline: { fontWeight: 700, letterSpacing: '0.08em' },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: { WebkitFontSmoothing: 'antialiased' },
          '@media (prefers-reduced-motion: reduce)': {
            '*, *::before, *::after': { transitionDuration: '0.01ms !important', animationDuration: '0.01ms !important' },
          },
        },
      },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { textTransform: 'none', borderRadius: 8, transition: 'background-color 200ms, border-color 200ms' },
          sizeLarge: { minHeight: 48 },
        },
      },
      MuiButtonBase: {
        styleOverrides: {
          root: ({ theme }) => ({
            '&.Mui-focusVisible': {
              outline: `3px solid ${alpha(theme.palette.primary.main, 0.45)}`,
              outlineOffset: 2,
            },
          }),
        },
      },
      MuiPaper: {
        defaultProps: { variant: 'outlined' },
        styleOverrides: {
          outlined: ({ theme }) => ({
            borderColor: theme.vars?.palette.divider,
            boxShadow: '0 1px 2px rgba(15, 23, 42, 0.04)',
          }),
        },
      },
      MuiCard: {
        defaultProps: { variant: 'outlined' },
        styleOverrides: {
          root: ({ theme }) => ({
            transition: 'border-color 200ms, box-shadow 200ms',
            '&:hover': {
              borderColor: theme.vars?.palette.primary.main,
              boxShadow: '0 4px 14px rgba(15, 23, 42, 0.08)',
            },
          }),
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: { transition: 'box-shadow 200ms' },
        },
      },
      MuiTab: {
        styleOverrides: { root: { textTransform: 'none', fontWeight: 600, minHeight: 52 } },
      },
      MuiTableCell: {
        styleOverrides: {
          head: ({ theme }) => ({
            fontSize: '0.75rem',
            fontWeight: 700,
            letterSpacing: '0.06em',
            textTransform: 'uppercase',
            color: theme.vars?.palette.text.secondary,
            backgroundColor: theme.vars?.palette.background.default,
          }),
        },
      },
      MuiChip: {
        styleOverrides: { root: { fontWeight: 600 } },
      },
      MuiAlert: {
        styleOverrides: { root: { borderRadius: 10 } },
      },
    },
  });
}

export const theme = createAppTheme(DEFAULT_BRANDING);
