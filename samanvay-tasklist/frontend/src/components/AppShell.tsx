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

export function AppShell() {
  const { user, logout } = useAuth();
  const branding = useBranding();
  // the Start menu only appears for users who can start something
  const startable = useQuery({ queryKey: ['processes'], queryFn: fetchStartableProcesses, staleTime: 5 * 60_000 });
  const navigate = useNavigate();
  const formPage = Boolean(useMatch('/tasks/:id')) || Boolean(useMatch('/start/:key'));
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const initials = (user?.firstName?.[0] ?? user?.id[0] ?? '?').toUpperCase() + (user?.lastName?.[0] ?? '').toUpperCase();
  const fullName = [user?.firstName, user?.lastName].filter(Boolean).join(' ') || user?.id;

  const navButton = (to: string, label: string, icon: JSX.Element) => (
    <Button
      component={NavLink}
      to={to}
      end={to === '/'}
      color="inherit"
      startIcon={icon}
      sx={{ opacity: 0.85, '&.active': { opacity: 1, bgcolor: 'rgba(255,255,255,0.14)' } }}
    >
      {label}
    </Button>
  );

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" elevation={0} color="primary" enableColorOnDark>
        <Toolbar sx={{ gap: 1 }}>
          <Typography
            variant="h6"
            component={NavLink}
            to="/"
            sx={{ color: 'inherit', textDecoration: 'none', fontWeight: 700, mr: { xs: 'auto', sm: 3 } }}
          >
            {branding.name}
            <Box component="span" sx={{ fontWeight: 400, display: { xs: 'none', sm: 'inline' } }}>
              {' '}
              {branding.product}
            </Box>
          </Typography>
          <Box sx={{ display: 'flex', gap: 0.5, flexGrow: { sm: 1 } }}>
            {navButton('/', 'Tasks', <AssignmentOutlinedIcon />)}
            {(startable.data?.length ?? 0) > 0 && navButton('/start', 'Start', <PlayCircleOutlineIcon />)}
            {user?.admin && navButton('/admin/forms', 'Forms', <DynamicFormOutlinedIcon />)}
          </Box>
          <Tooltip title={fullName ?? ''}>
            <IconButton onClick={(e) => setAnchor(e.currentTarget)} aria-label="Account menu" sx={{ p: 0.5 }}>
              <Avatar sx={{ width: 34, height: 34, bgcolor: 'secondary.main', fontSize: 15 }}>{initials}</Avatar>
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
