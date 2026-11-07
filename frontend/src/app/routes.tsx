import type { RouteObject } from 'react-router'
import { AuthGate } from '@/core/auth/AuthGate'
import { LoginPage } from '@/core/auth/LoginPage'
import { DashboardPage } from '@/core/dashboard/DashboardPage'
import { AccountPage } from '@/core/account/AccountPage'
import { UsersPage } from '@/core/admin/users/UsersPage'
import { CategoriesPage } from '@/core/admin/categories/CategoriesPage'
import { AccountsPage } from '@/core/ledger/accounts/AccountsPage'
import { AccountDetailPage } from '@/core/ledger/accounts/AccountDetailPage'
import { TransactionsPage } from '@/core/ledger/transactions/TransactionsPage'
import { LegalEntitiesPage } from '@/core/ledger/legal-entities/LegalEntitiesPage'
import { PropertiesPage } from '@/core/realestate/PropertiesPage'
import { PropertyDetailPage } from '@/core/realestate/PropertyDetailPage'
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
          { path: 'posteingang', lazy: async () => ({ Component: (await import('@/core/mail/PosteingangPage')).PosteingangPage }) },
          { path: 'finanzen/konten', element: <AccountsPage /> },
          { path: 'finanzen/konten/:accountId', element: <AccountDetailPage /> },
          { path: 'finanzen/transaktionen', element: <TransactionsPage /> },
          { path: 'finanzen/eigentuemer', element: <LegalEntitiesPage /> },
          { path: 'immobilien', element: <PropertiesPage /> },
          { path: 'immobilien/:propertyId', element: <PropertyDetailPage /> },
          {
            path: 'einstellungen/kategorien',
            element: (
              <RequireAdmin>
                <CategoriesPage />
              </RequireAdmin>
            ),
          },
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
