import type { RouteObject } from 'react-router'
import { AuthGate } from '@/core/auth/AuthGate'
import { LoginPage } from '@/core/auth/LoginPage'
import { DashboardPage } from '@/core/dashboard/DashboardPage'
import { AccountPage } from '@/core/account/AccountPage'
import { UsersPage } from '@/core/admin/users/UsersPage'
import { AppShell } from './layout/AppShell'
import { ModuleGuard, RequireAdmin } from './guards'
import { NotFound } from './NotFound'
import { moduleRegistry } from './registry'

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <AuthGate />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: 'konto', element: <AccountPage /> },
          {
            path: 'einstellungen/benutzer',
            element: (
              <RequireAdmin>
                <UsersPage />
              </RequireAdmin>
            ),
          },
          ...moduleRegistry.map((module) => ({
            path: module.basePath,
            element: <ModuleGuard moduleId={module.id} />,
            children: module.routes,
          })),
          { path: '*', element: <NotFound /> },
        ],
      },
    ],
  },
]
