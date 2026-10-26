import { MD3DarkTheme, MD3LightTheme, type MD3Theme } from 'react-native-paper';

// Samanvay branding, matching the web app's MUI theme.
export const lightTheme: MD3Theme = {
  ...MD3LightTheme,
  roundness: 3,
  colors: {
    ...MD3LightTheme.colors,
    primary: '#1f4e79',
    onPrimary: '#ffffff',
    primaryContainer: '#d3e4f7',
    onPrimaryContainer: '#0b2540',
    secondary: '#00796b',
    secondaryContainer: '#c8ece6',
    background: '#f5f7fa',
    surface: '#ffffff',
  },
};

export const darkTheme: MD3Theme = {
  ...MD3DarkTheme,
  roundness: 3,
  colors: {
    ...MD3DarkTheme.colors,
    primary: '#7fb2e5',
    onPrimary: '#0b2540',
    primaryContainer: '#1f4e79',
    onPrimaryContainer: '#d3e4f7',
    secondary: '#4db6ac',
    background: '#0f1720',
    surface: '#16212c',
  },
};
