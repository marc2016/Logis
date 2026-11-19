import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, ComposedChart, Line, XAxis, YAxis } from 'recharts'
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/components/ui/chart'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { unitLabel, type MonthFigures, type UnitTotal } from './api'
import { ownerRate } from './occupancyFigures'

const SHORT_MONTHS = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez']
const MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember']
const euro = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })
const euroShort = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })

/** Below this chart width, month labels shrink to their first letter. */
const NARROW = 480

/** Width of an element, for choosing month labels. */
function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    const el = ref.current
    if (!el || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width))
    observer.observe(el)
    return () => observer.disconnect()
  }, [])
  return { ref, width }
}

/** A chart in a card, named for screen readers; the table below holds the exact figures. */
function ChartCard({ title, description, label, children }: { title: string; description: string; label: string; children: ReactNode }) {
  return (
    <Card className="min-w-0">
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        <figure aria-label={label} className="m-0">
          {children}
        </figure>
      </CardContent>
    </Card>
  )
}

interface UnitChartsProps {
  year: number
  months: MonthFigures[]
  /** The previous year's months when „Vorjahr einblenden“ is on. */
  previous?: MonthFigures[]
}

/** „Belegung“ and „Einnahmen“ of one unit and year (spec occupancy, „Charts of the yearly overview“). */
export function UnitCharts({ year, months, previous }: UnitChartsProps) {
  const { ref, width } = useWidth<HTMLDivElement>()
  const monthLabel = (i: number) => (width > 0 && width < NARROW ? SHORT_MONTHS[i][0] : SHORT_MONTHS[i])
  const prev = previous ? String(year - 1) : null

  const occupancyData = months.map((f, i) => ({
    month: monthLabel(i),
    name: MONTHS[i],
    booked: f.occupancy,
    owner: ownerRate(f, year),
    ...(previous ? { previous: previous[i].occupancy } : {}),
  }))
  const moneyData = months.map((f, i) => ({
    month: monthLabel(i),
    name: MONTHS[i],
    settled: Number(f.settledNet),
    planned: Number(f.plannedRent),
    ...(previous ? { previous: Number(previous[i].settledNet) } : {}),
  }))

  const occupancyConfig = {
    booked: { label: `Belegung ${year}`, color: 'var(--chart-1)' },
    owner: { label: 'Eigenbelegung', color: 'var(--chart-2)' },
    ...(prev ? { previous: { label: `Belegung ${prev}`, color: 'var(--chart-3)' } } : {}),
  } satisfies ChartConfig
  const moneyConfig = {
    settled: { label: `Netto abgerechnet ${year}`, color: 'var(--chart-1)' },
    planned: { label: 'Mietpreis geplant', color: 'var(--chart-2)' },
    ...(prev ? { previous: { label: `Netto abgerechnet ${prev}`, color: 'var(--chart-3)' } } : {}),
  } satisfies ChartConfig

  return (
    <div ref={ref} className="mb-6 grid gap-6 @4xl:grid-cols-2">
      <ChartCard
        title="Belegung"
        description="Gebuchte Nächte und Eigenbelegung je Monat, in Prozent der Tage."
        label={`Belegung je Monat ${year}${prev ? ` mit Vorjahr ${prev}` : ''}`}
      >
        <ChartContainer config={occupancyConfig} className="aspect-auto h-64 w-full">
          <ComposedChart data={occupancyData} margin={{ left: -12, right: 4 }}>
            <CartesianGrid vertical={false} />
            <XAxis dataKey="month" tickLine={false} axisLine={false} interval={0} />
            <YAxis domain={[0, 100]} ticks={[0, 25, 50, 75, 100]} tickFormatter={(v) => `${v} %`} tickLine={false} axisLine={false} />
            <ChartTooltip
              content={
                <ChartTooltipContent
                  labelFormatter={(_, payload) => payload?.[0]?.payload?.name}
                  formatter={(value, name) => (
                    <TooltipRow color={`var(--color-${name})`} label={occupancyConfig[name as keyof typeof occupancyConfig]?.label} value={`${value} %`} />
                  )}
                />
              }
            />
            <ChartLegend content={<ChartLegendContent />} itemSorter={null} />
            <Bar dataKey="booked" stackId="occupancy" fill="var(--color-booked)" stroke="var(--card)" strokeWidth={2} />
            <Bar dataKey="owner" stackId="occupancy" fill="var(--color-owner)" stroke="var(--card)" strokeWidth={2} radius={[4, 4, 0, 0]} />
            {prev && (
              <Line dataKey="previous" type="linear" stroke="var(--color-previous)" strokeWidth={2} strokeDasharray="5 4" dot={false} activeDot={{ r: 4 }} isAnimationActive={false} />
            )}
          </ComposedChart>
        </ChartContainer>
      </ChartCard>
      <ChartCard
        title="Einnahmen"
        description="Netto abgerechnet im Monat des Abrechnungsdatums, geplanter Mietpreis im Anreisemonat. Beide werden nicht addiert."
        label={`Einnahmen je Monat ${year}${prev ? ` mit Vorjahr ${prev}` : ''}`}
      >
        <ChartContainer config={moneyConfig} className="aspect-auto h-64 w-full">
          <ComposedChart data={moneyData} margin={{ left: 4, right: 4 }} barGap={2}>
            <CartesianGrid vertical={false} />
            <XAxis dataKey="month" tickLine={false} axisLine={false} interval={0} />
            <YAxis tickFormatter={(v) => euroShort.format(v)} tickLine={false} axisLine={false} width={72} />
            <ChartTooltip
              content={
                <ChartTooltipContent
                  labelFormatter={(_, payload) => payload?.[0]?.payload?.name}
                  formatter={(value, name) => (
                    <TooltipRow color={`var(--color-${name})`} label={moneyConfig[name as keyof typeof moneyConfig]?.label} value={euro.format(Number(value))} />
                  )}
                />
              }
            />
            <ChartLegend content={<ChartLegendContent />} itemSorter={null} />
            <Bar dataKey="settled" fill="var(--color-settled)" radius={[4, 4, 0, 0]} />
            <Bar dataKey="planned" fill="var(--color-planned)" radius={[4, 4, 0, 0]} />
            {prev && (
              <Line dataKey="previous" type="linear" stroke="var(--color-previous)" strokeWidth={2} strokeDasharray="5 4" dot={false} activeDot={{ r: 4 }} isAnimationActive={false} />
            )}
          </ComposedChart>
        </ChartContainer>
      </ChartCard>
    </div>
  )
}

/** One line of a tooltip: colour mark, series name in text colour, value. */
function TooltipRow({ color, label, value }: { color: string; label?: ReactNode; value: string }) {
  return (
    <div className="flex w-full items-center gap-2">
      <span className="size-2.5 shrink-0 rounded-[2px]" style={{ backgroundColor: color }} />
      <span className="text-muted-foreground">{label}</span>
      <span className="ml-auto font-medium tabular-nums text-foreground">{value}</span>
    </div>
  )
}

/** Height of a horizontal bar chart with one row per unit. */
function rowsHeight(count: number) {
  return Math.max(120, count * 40 + 48)
}

/** „Alle Einheiten“: occupancy and money per unit for a year (spec occupancy, „Overview of all units“). */
export function UnitsCharts({ year, rows }: { year: number; rows: UnitTotal[] }) {
  const data = rows.map((r) => ({
    unit: unitLabel({ name: r.unitName, propertyName: r.propertyName, propertyMultiUnit: r.propertyMultiUnit }),
    booked: r.total.occupancy,
    settled: Number(r.total.settledNet),
    planned: Number(r.total.plannedRent),
  }))
  const occupancyConfig = { booked: { label: `Belegung ${year}`, color: 'var(--chart-1)' } } satisfies ChartConfig
  const moneyConfig = {
    settled: { label: 'Netto abgerechnet', color: 'var(--chart-1)' },
    planned: { label: 'Mietpreis geplant', color: 'var(--chart-2)' },
  } satisfies ChartConfig
  const height = rowsHeight(rows.length)
  const axisWidth = 132

  return (
    <div className="mb-6 grid gap-6 @4xl:grid-cols-2">
      <ChartCard title="Belegung je Einheit" description={`Gebuchte Nächte ${year} in Prozent der Tage.`} label={`Belegung je Einheit ${year}`}>
        <ChartContainer config={occupancyConfig} className="aspect-auto w-full" style={{ height }}>
          <BarChart data={data} layout="vertical" margin={{ left: 0, right: 12 }}>
            <CartesianGrid horizontal={false} />
            <XAxis type="number" domain={[0, 100]} tickFormatter={(v) => `${v} %`} tickLine={false} axisLine={false} />
            <YAxis type="category" dataKey="unit" width={axisWidth} tickLine={false} axisLine={false} />
            <ChartTooltip
              content={
                <ChartTooltipContent
                  formatter={(value) => <TooltipRow color="var(--color-booked)" label={occupancyConfig.booked.label} value={`${value} %`} />}
                />
              }
            />
            <Bar dataKey="booked" fill="var(--color-booked)" radius={[0, 4, 4, 0]} barSize={20} />
          </BarChart>
        </ChartContainer>
      </ChartCard>
      <ChartCard
        title="Einnahmen je Einheit"
        description={`Netto abgerechnet und geplanter Mietpreis ${year}; beide werden nicht addiert.`}
        label={`Einnahmen je Einheit ${year}`}
      >
        <ChartContainer config={moneyConfig} className="aspect-auto w-full" style={{ height: height + 24 }}>
          <BarChart data={data} layout="vertical" margin={{ left: 0, right: 12 }} barGap={2}>
            <CartesianGrid horizontal={false} />
            <XAxis type="number" tickFormatter={(v) => euroShort.format(v)} tickLine={false} axisLine={false} />
            <YAxis type="category" dataKey="unit" width={axisWidth} tickLine={false} axisLine={false} />
            <ChartTooltip
              content={
                <ChartTooltipContent
                  formatter={(value, name) => (
                    <TooltipRow color={`var(--color-${name})`} label={moneyConfig[name as keyof typeof moneyConfig]?.label} value={euro.format(Number(value))} />
                  )}
                />
              }
            />
            <ChartLegend content={<ChartLegendContent />} itemSorter={null} />
            <Bar dataKey="settled" fill="var(--color-settled)" radius={[0, 4, 4, 0]} barSize={12} />
            <Bar dataKey="planned" fill="var(--color-planned)" radius={[0, 4, 4, 0]} barSize={12} />
          </BarChart>
        </ChartContainer>
      </ChartCard>
    </div>
  )
}
