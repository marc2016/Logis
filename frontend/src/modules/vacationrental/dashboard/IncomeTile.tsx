import { Link } from 'react-router'
import { Money } from '@/components/Money'
import { Loading, Unavailable } from '@/core/dashboard/TileStates'
import { todayIso } from '@/core/ledger/transactions/format'
import { netUpTo } from '../occupancyFigures'
import { change } from './figures'
import { UnitSetting } from './UnitSetting'
import { useYearFigures } from './useYearFigures'

export interface IncomeSettings {
  unitId?: number
}

const MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember']

/** „Einnahmen“: settled net this year up to the current month, against the same months of the previous year. */
export function IncomeTile({ settings }: { settings: IncomeSettings }) {
  const today = todayIso()
  const year = Number(today.slice(0, 4))
  const month = Number(today.slice(5, 7))
  const current = useYearFigures(settings.unitId, year)
  const previous = useYearFigures(settings.unitId, year - 1)
  if (current.unavailable) return <Unavailable />
  if (!current.months || !previous.months) return <Loading />
  const now = netUpTo(current.months, month)
  const before = netUpTo(previous.months, month)
  const diff = change(now, before)
  return (
    <div className="space-y-2 text-sm">
      <p className="text-muted-foreground">Netto abgerechnet Januar bis {MONTHS[month - 1]}</p>
      <dl className="grid grid-cols-[auto_1fr] items-baseline gap-x-4 gap-y-1">
        <dt>{year}</dt>
        <dd className="text-right text-lg font-semibold">
          <Money amount={now} colored={false} />
        </dd>
        <dt className="text-muted-foreground">{year - 1}</dt>
        <dd className="text-right text-muted-foreground">
          <Money amount={before} colored={false} />
        </dd>
      </dl>
      {diff !== null && (
        <p className={diff < 0 ? 'text-red-700' : 'text-emerald-700'}>
          {diff > 0 ? '+' : ''}
          {diff} % gegenüber Vorjahr
        </p>
      )}
      <Link to={`/ferienvermietung/auswertung?unitId=${settings.unitId ?? 'all'}&year=${year}`} className="inline-block hover:underline">
        Zur Auswertung
      </Link>
    </div>
  )
}

export function IncomeSettingsForm({ value, onChange }: { value: IncomeSettings; onChange: (s: IncomeSettings) => void }) {
  return <UnitSetting id="inc-unit" value={value.unitId} onChange={(unitId) => onChange({ ...value, unitId })} />
}
