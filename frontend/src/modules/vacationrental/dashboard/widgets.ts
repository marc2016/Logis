import type { DashboardWidget, TodoSource } from '@/app/types'
import { api } from '@/lib/api'
import type { BookingPage } from '../api'
import { ArrivalsSettingsForm, ArrivalsTile, type ArrivalsSettings } from './ArrivalsTile'
import { IncomeSettingsForm, IncomeTile, type IncomeSettings } from './IncomeTile'
import { OccupancySettingsForm, OccupancyTile, type OccupancySettings } from './OccupancyTile'

const arrivals: DashboardWidget<ArrivalsSettings> = {
  type: 'vacationrental.arrivals',
  title: 'Nächste An- und Abreisen',
  description: 'An- und Abreisen der nächsten Tage.',
  defaultSettings: { days: 14 },
  Tile: ArrivalsTile,
  Settings: ArrivalsSettingsForm,
}

const occupancy: DashboardWidget<OccupancySettings> = {
  type: 'vacationrental.occupancy',
  title: 'Belegung',
  description: 'Belegung des laufenden Jahres je Monat.',
  defaultSettings: {},
  Tile: OccupancyTile,
  Settings: OccupancySettingsForm,
  wide: true,
}

const income: DashboardWidget<IncomeSettings> = {
  type: 'vacationrental.income',
  title: 'Einnahmen',
  description: 'Netto abgerechnet bis heute, verglichen mit dem Vorjahr.',
  defaultSettings: {},
  Tile: IncomeTile,
  Settings: IncomeSettingsForm,
}

/** The vacation rental tiles of the start page (spec dashboard, „Tile catalog“). */
export const rentalWidgets = [arrivals, occupancy, income] as unknown as DashboardWidget[]

/** „Buchungen prüfen“ in „Zu erledigen“: bookings and changes awaiting review. */
export const rentalTodoSources: TodoSource[] = [
  {
    label: 'Buchungen prüfen',
    to: '/ferienvermietung/buchungen?review=true',
    query: () => ({
      queryKey: ['vacationrental', 'bookings', 'review-count'] as const,
      queryFn: async () => (await api<BookingPage>('/api/vacationrental/bookings?review=true&size=1')).totalItems,
    }),
  },
]
