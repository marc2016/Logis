import type { DashboardWidget } from '@/app/types'
import { ArrearsTile } from './ArrearsTile'

const arrears: DashboardWidget = {
  type: 'longtermrental.arrears',
  title: 'Mietrückstände',
  description: 'Mietverhältnisse mit überfälliger Miete, höchster Rückstand zuerst.',
  defaultSettings: {},
  Tile: ArrearsTile,
}

/** The long-term rental tiles of the start page (spec dashboard, „Tile catalog“). */
export const longTermRentalWidgets: DashboardWidget[] = [arrears]
