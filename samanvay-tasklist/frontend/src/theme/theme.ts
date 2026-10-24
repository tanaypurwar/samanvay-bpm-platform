import { createTheme } from '@mui/material/styles';

/** Samanvay branding. Light and dark schemes follow the operating system preference. */
export const theme = createTheme({
  cssVariables: true,
  colorSchemes: {
    light: {
      palette: {
        primary: { main: '#1f4e79' },
        secondary: { main: '#00796b' },
        background: { default: '#f5f7fa', paper: '#ffffff' },
      },
    },
    dark: {
      palette: {
        primary: { main: '#7fb2e5' },
        secondary: { main: '#4db6ac' },
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
