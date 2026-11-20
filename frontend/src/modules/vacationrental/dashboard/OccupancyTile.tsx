import { Link } from 'react-router'
import { Bar, CartesianGrid, ComposedChart, Line, XAxis, YAxis } from 'recharts'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from '@/components/ui/chart'
import { Loading, Unavailable } from '@/core/dashboard/TileStates'
import { todayIso } from '@/core/ledger/transactions/format'
import { UnitSetting } from './UnitSetting'
import { useYearFigures } from './useYearFigures'
import type { MonthFigures } from '../api'

export interface OccupancySettings {
  unitId?: number
  previous?: boolean
}

const LETTERS = ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D']
const MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember']

/** Occupancy of the year: nights over the days of the year (× units). */
function yearRate(months: MonthFigures[], year: number, units: number) {
  const days = (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0 ? 366 : 365
  const nights = months.reduce((n, m) => n + m.nights, 0)
  return units === 0 ? 0 : Math.floor((nights * 100) / (days * units) + 0.5)
}

/** „Belegung“ of the current year per month, of one unit or all; optionally the previous year as a line. */
export function OccupancyTile({ settings }: { settings: OccupancySettings }) {
  const year = Number(todayIso().slice(0, 4))
  const current = useYearFigures(settings.unitId, year)
  const previous = useYearFigures(settings.unitId, year - 1, settings.previous === true)
  if (current.unavailable) return <Unavailable />
  if (!current.months || (settings.previous && previous.loading)) return <Loading />
  const prev = settings.previous ? previous.months : undefined
  const data = current.months.map((m, i) => ({ month: LETTERS[i], name: MONTHS[i], booked: m.occupancy, ...(prev ? { previous: prev[i].occupancy } : {}) }))
  const config = {
    booked: { label: `Belegung ${year}`, color: 'var(--chart-1)' },
    ...(prev ? { previous: { label: `Belegung ${year - 1}`, color: 'var(--chart-3)' } } : {}),
  } satisfies ChartConfig
  const link = `/ferienvermietung/auswertung?unitId=${settings.unitId ?? 'all'}&year=${year}${settings.previous && settings.unitId !== undefined ? '&previous=1' : ''}`
  return (
    <div className="space-y-2 text-sm">
      <p>
        <span className="text-2xl font-semibold tabular-nums">{yearRate(current.months, year, current.unitCount)} %</span>{' '}
        <span className="text-muted-foreground">belegt in {year}</span>
        {prev && (
          <span className="text-muted-foreground"> · {year - 1}: {yearRate(prev, year - 1, previous.unitCount)} %</span>
        )}
      </p>
      <figure aria-label={`Belegung je Monat ${year}${prev ? ` mit Vorjahr ${year - 1}` : ''}`} className="m-0">
        <ChartContainer config={config} className="aspect-auto h-36 w-full">
          <ComposedChart data={data} margin={{ left: -20, right: 4, top: 4 }}>
            <CartesianGrid vertical={false} />
            <XAxis dataKey="month" tickLine={false} axisLine={false} interval={0} />
            <YAxis domain={[0, 100]} ticks={[0, 50, 100]} tickFormatter={(v) => `${v}`} tickLine={false} axisLine={false} />
            <ChartTooltip content={<ChartTooltipContent labelFormatter={(_, p) => p?.[0]?.payload?.name} />} />
            <Bar dataKey="booked" fill="var(--color-booked)" radius={[3, 3, 0, 0]} isAnimationActive={false} />
            {prev && (
              <Line dataKey="previous" type="linear" stroke="var(--color-previous)" strokeWidth={2} strokeDasharray="5 4"
                dot={false} isAnimationActive={false} />
            )}
          </ComposedChart>
        </ChartContainer>
      </figure>
      <Link to={link} className="inline-block hover:underline">
        Zur Auswertung
      </Link>
    </div>
  )
}

export function OccupancySettingsForm({ value, onChange }: { value: OccupancySettings; onChange: (s: OccupancySettings) => void }) {
  return (
    <div className="space-y-4">
      <UnitSetting id="occ-unit" value={value.unitId} onChange={(unitId) => onChange({ ...value, unitId })} />
      <div className="flex items-center gap-2">
        <Checkbox id="occ-previous" checked={value.previous === true} onCheckedChange={(on) => onChange({ ...value, previous: on === true })} />
        <Label htmlFor="occ-previous">Vorjahr einblenden</Label>
      </div>
    </div>
  )
}
