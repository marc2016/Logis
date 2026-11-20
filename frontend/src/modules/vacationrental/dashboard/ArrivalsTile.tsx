import { Link } from 'react-router'
import { NumberChoice } from '@/core/dashboard/SettingsFields'
import { Loading, Unavailable } from '@/core/dashboard/TileStates'
import { formatDate, todayIso } from '@/core/ledger/transactions/format'
import { unitLabel, useBookings, useRentalUnits } from '../api'
import { plusDays, stayEvents } from './figures'
import { UnitSetting } from './UnitSetting'

export interface ArrivalsSettings {
  days?: number
  unitId?: number
}

const MAX_ROWS = 10

/** „Nächste An- und Abreisen“ for 7, 14 or 30 days, of one unit or all. */
export function ArrivalsTile({ settings }: { settings: ArrivalsSettings }) {
  const days = settings.days ?? 14
  const from = todayIso()
  const to = plusDays(from, days)
  const { data: units } = useRentalUnits()
  const unavailable = settings.unitId !== undefined && units !== undefined && !units.some((u) => u.id === settings.unitId)
  const { data } = useBookings({ from, to, unitId: unavailable ? undefined : settings.unitId, size: 100 })
  if (unavailable) return <Unavailable />
  if (!data) return <Loading />
  const events = stayEvents(data.items, from, to)
  if (events.length === 0) {
    return <p className="text-sm text-muted-foreground">Keine An- oder Abreisen in den nächsten {days} Tagen.</p>
  }
  return (
    <div className="space-y-2 text-sm">
      <ul className="divide-y">
        {events.slice(0, MAX_ROWS).map((e) => (
          <li key={`${e.booking.id}-${e.arrival}`}>
            <Link to={`/ferienvermietung/buchungen/${e.booking.id}`} className="flex items-center gap-3 py-1.5 hover:underline">
              <span className="w-20 shrink-0 tabular-nums text-muted-foreground">{formatDate(e.date)}</span>
              <span className="w-16 shrink-0">{e.arrival ? 'Anreise' : 'Abreise'}</span>
              <span className="min-w-0 flex-1 truncate">
                {e.booking.kind === 'OWNER' ? 'Eigenbelegung' : (e.booking.guest?.name ?? '–')}
                <span className="text-muted-foreground"> · {unitLabel(e.booking.unit)}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
      <Link to="/ferienvermietung/kalender" className="inline-block hover:underline">
        Alle anzeigen
      </Link>
    </div>
  )
}

export function ArrivalsSettingsForm({ value, onChange }: { value: ArrivalsSettings; onChange: (s: ArrivalsSettings) => void }) {
  return (
    <div className="space-y-4">
      <NumberChoice id="arr-days" label="Zeitraum" value={value.days ?? 14} options={[7, 14, 30]} unit="Tage"
        onChange={(days) => onChange({ ...value, days })} />
      <UnitSetting id="arr-unit" value={value.unitId} onChange={(unitId) => onChange({ ...value, unitId })} />
    </div>
  )
}
