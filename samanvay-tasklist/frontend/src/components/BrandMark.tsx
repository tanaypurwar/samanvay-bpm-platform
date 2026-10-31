import { Box } from '@mui/material';
import { useState } from 'react';
import { useBranding } from '../theme/BrandingContext';

/**
 * The deployment's logo, or a monogram in the brand color when it has none (or the image can't be loaded).
 * Decorative: the brand name is always shown as text next to it.
 */
export function BrandMark({ size = 40 }: { size?: number }) {
  const branding = useBranding();
  const [failed, setFailed] = useState(false);

  if (branding.logoUrl && !failed) {
    return (
      <Box
        component="img"
        src={branding.logoUrl}
        alt=""
        width={size}
        height={size}
        onError={() => setFailed(true)}
        sx={{ width: size, height: size, objectFit: 'cover', borderRadius: 1.5, flexShrink: 0 }}
      />
    );
  }
  return (
    <Box
      aria-hidden
      sx={{
        width: size,
        height: size,
        borderRadius: 1.5,
        flexShrink: 0,
        display: 'grid',
        placeItems: 'center',
        bgcolor: 'primary.main',
        color: 'primary.contrastText',
        fontWeight: 700,
        fontSize: size * 0.45,
      }}
    >
      {branding.name.charAt(0).toUpperCase()}
    </Box>
  );
}
