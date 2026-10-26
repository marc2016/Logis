import { createElement } from 'react'
import { Navigate } from 'react-router'
import { CalendarDaysIcon, HouseIcon } from 'lucide-react'
import type { LogisModule } from '@/app/types'
import { BelegungPage } from './pages/BelegungPage'

export const vacationRentalModule: LogisModule = {
  id: 'vacationrental',
  label: 'Ferienvermietung',
  icon: HouseIcon,
  order: 100,
  basePath: 'ferienvermietung',
  navItems: [{ label: 'Belegung', path: 'belegung', icon: CalendarDaysIcon }],
  routes: [
    { index: true, element: createElement(Navigate, { to: 'belegung', replace: true }) },
    { path: 'belegung', element: createElement(BelegungPage) },
  ],
}
