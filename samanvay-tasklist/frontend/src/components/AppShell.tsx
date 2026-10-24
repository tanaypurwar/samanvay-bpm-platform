import AssignmentOutlinedIcon from '@mui/icons-material/AssignmentOutlined';
import DynamicFormOutlinedIcon from '@mui/icons-material/DynamicFormOutlined';
import LogoutIcon from '@mui/icons-material/Logout';
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
import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
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
            Samanvay<Box component="span" sx={{ fontWeight: 400, display: { xs: 'none', sm: 'inline' } }}> Tasklist</Box>
          </Typography>
          <Box sx={{ display: 'flex', gap: 0.5, flexGrow: { sm: 1 } }}>
            {navButton('/', 'Tasks', <AssignmentOutlinedIcon />)}
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
      <Container maxWidth="lg" sx={{ py: { xs: 2, sm: 3 }, px: { xs: 2, sm: 3 } }}>
        <Outlet />
      </Container>
    </Box>
  );
}
