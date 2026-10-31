import AssignmentOutlinedIcon from '@mui/icons-material/AssignmentOutlined';
import DynamicFormOutlinedIcon from '@mui/icons-material/DynamicFormOutlined';
import LogoutIcon from '@mui/icons-material/Logout';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline';
import {
  AppBar,
  Avatar,
  Box,
  Button,
  Container,
  Divider,
  IconButton,
  ListItemIcon,
  Menu,
  MenuItem,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { NavLink, Outlet, useMatch, useNavigate } from 'react-router-dom';
import { fetchStartableProcesses } from '../api/processes';
import { useAuth } from '../auth/AuthContext';
import { useBranding } from '../theme/BrandingContext';
import { BrandMark } from './BrandMark';

export function AppShell() {
  const { user, logout } = useAuth();
  const branding = useBranding();
  // the Start menu only appears for users who can start something
  const startable = useQuery({ queryKey: ['processes'], queryFn: fetchStartableProcesses, staleTime: 5 * 60_000 });
  const navigate = useNavigate();
  // call both hooks on every render: short-circuiting the second one changes the hook order and crashes React
  const taskPage = useMatch('/tasks/:id');
  const startFormPage = useMatch('/start/:key');
  const formPage = Boolean(taskPage) || Boolean(startFormPage);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const initials = (user?.firstName?.[0] ?? user?.id[0] ?? '?').toUpperCase() + (user?.lastName?.[0] ?? '').toUpperCase();
  const fullName = [user?.firstName, user?.lastName].filter(Boolean).join(' ') || user?.id;

  const navButton = (to: string, label: string, icon: JSX.Element) => (
    <Button
      component={NavLink}
      to={to}
      end={to === '/'}
      startIcon={icon}
      sx={{
        color: 'text.secondary',
        minHeight: 44,
        px: { xs: 1, sm: 1.5 },
        '& .MuiButton-startIcon': { mr: { xs: 0.5, sm: 1 } },
        '&:hover': { color: 'text.primary', bgcolor: 'action.hover' },
        '&.active': { color: 'primary.main', bgcolor: 'rgba(var(--mui-palette-primary-mainChannel) / 0.1)' },
      }}
    >
      {label}
    </Button>
  );

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar
        position="sticky"
        elevation={0}
        color="inherit"
        sx={{
          bgcolor: 'background.paper',
          borderBottom: 1,
          borderColor: 'divider',
          borderTop: '3px solid',
          borderTopColor: 'primary.main',
        }}
      >
        <Toolbar sx={{ gap: 1, minHeight: { xs: 60, sm: 68 } }}>
          <Box
            component={NavLink}
            to="/"
            aria-label={`${branding.name} ${branding.product}, home`}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.25,
              color: 'text.primary',
              textDecoration: 'none',
              mr: { xs: 'auto', sm: 3 },
            }}
          >
            <BrandMark size={40} />
            <Typography
              component="span"
              sx={{ fontWeight: 700, fontSize: '1.1rem', letterSpacing: '-0.01em', display: { xs: 'none', sm: 'inline' } }}
            >
              {branding.name}
              <Box component="span" sx={{ fontWeight: 500, color: 'text.secondary' }}>
                {' '}
                {branding.product}
              </Box>
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 0.5, flexGrow: { sm: 1 } }}>
            {navButton('/', 'Tasks', <AssignmentOutlinedIcon />)}
            {(startable.data?.length ?? 0) > 0 && navButton('/start', 'Start', <PlayCircleOutlineIcon />)}
            {user?.admin && navButton('/admin/forms', 'Forms', <DynamicFormOutlinedIcon />)}
          </Box>
          <Tooltip title={fullName ?? ''}>
            <IconButton onClick={(e) => setAnchor(e.currentTarget)} aria-label="Account menu" sx={{ p: 0.5 }}>
              <Avatar sx={{ width: 36, height: 36, bgcolor: 'secondary.main', fontSize: 15, fontWeight: 600 }}>{initials}</Avatar>
            </IconButton>
          </Tooltip>
          <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
            <Box sx={{ px: 2, py: 1 }}>
              <Typography variant="subtitle2">{fullName}</Typography>
              {user?.email && (
                <Typography variant="body2" color="text.secondary">
                  {user.email}
                </Typography>
              )}
            </Box>
            <Divider />
            <MenuItem
              onClick={async () => {
                setAnchor(null);
                await logout();
                navigate('/login', { replace: true });
              }}
            >
              <ListItemIcon>
                <LogoutIcon fontSize="small" />
              </ListItemIcon>
              Sign out
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>
      {/* form pages get the full width so a document preview fits beside the form */}
      <Container maxWidth={formPage ? false : 'lg'} sx={{ py: { xs: 2, sm: 3 }, px: { xs: 2, sm: 3 } }}>
        <Outlet />
      </Container>
    </Box>
  );
}
