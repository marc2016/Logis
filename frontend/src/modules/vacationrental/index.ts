import { createElement } from 'react'
import { Navigate } from 'react-router'
import { BarChart3Icon, CalendarDaysIcon, HouseIcon, InboxIcon, ListIcon, ReceiptTextIcon } from 'lucide-react'
import type { LogisModule } from '@/app/types'

export const vacationRentalModule: LogisModule = {
  id: 'vacationrental',
  label: 'Ferienvermietung',
  icon: HouseIcon,
  order: 100,
  basePath: 'ferienvermietung',
  navItems: [
    { label: 'Buchungen', path: 'buchungen', icon: ListIcon },
    { label: 'Kalender', path: 'kalender', icon: CalendarDaysIcon },
    { label: 'Abrechnungen', path: 'abrechnungen', icon: ReceiptTextIcon },
    { label: 'Auswertung', path: 'auswertung', icon: BarChart3Icon },
    { label: 'Posteingang', path: 'posteingang', icon: InboxIcon },
  ],
  // Pages load on first use, so the module does not grow the main bundle.
  routes: [
    { index: true, element: createElement(Navigate, { to: 'buchungen', replace: true }) },
    { path: 'buchungen', lazy: async () => ({ Component: (await import('./pages/BuchungenPage')).BuchungenPage }) },
    { path: 'kalender', lazy: async () => ({ Component: (await import('./pages/KalenderPage')).KalenderPage }) },
    { path: 'abrechnungen', lazy: async () => ({ Component: (await import('./pages/AbrechnungenPage')).AbrechnungenPage }) },
    { path: 'posteingang', lazy: async () => ({ Component: (await import('./pages/PosteingangPage')).PosteingangPage }) },
    { path: 'auswertung', lazy: async () => ({ Component: (await import('./pages/AuswertungPage')).AuswertungPage }) },
  ],
}
