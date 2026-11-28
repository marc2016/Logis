import { createElement } from 'react'
import { Navigate } from 'react-router'
import { DoorOpenIcon, FileSignatureIcon, KeyRoundIcon } from 'lucide-react'
import type { LogisModule } from '@/app/types'
import { longTermRentalWidgets } from './dashboard/widgets'

export const longTermRentalModule: LogisModule = {
  id: 'longtermrental',
  label: 'Dauervermietung',
  icon: KeyRoundIcon,
  order: 200,
  basePath: 'dauervermietung',
  navItems: [
    { label: 'Mietverhältnisse', path: 'mietverhaeltnisse', icon: FileSignatureIcon },
    { label: 'Einheiten', path: 'einheiten', icon: DoorOpenIcon },
  ],
  // Pages load on first use, so the module does not grow the main bundle.
  routes: [
    { index: true, element: createElement(Navigate, { to: 'mietverhaeltnisse', replace: true }) },
    { path: 'mietverhaeltnisse', lazy: async () => ({ Component: (await import('./pages/MietverhaeltnissePage')).MietverhaeltnissePage }) },
    { path: 'mietverhaeltnisse/:tenancyId', lazy: async () => ({ Component: (await import('./pages/TenancyPage')).TenancyPage }) },
    { path: 'einheiten', lazy: async () => ({ Component: (await import('./pages/EinheitenPage')).EinheitenPage }) },
  ],
  dashboardWidgets: longTermRentalWidgets,
}
