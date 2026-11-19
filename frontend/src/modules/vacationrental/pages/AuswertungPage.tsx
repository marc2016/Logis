import { useSearchParams } from 'react-router'
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Money } from '@/components/Money'
import { PageHeader } from '@/components/PageHeader'
import { todayIso } from '@/core/ledger/transactions/format'
import { IdSelect } from '../RentalFilters'
import { UnitCharts, UnitsCharts } from '../OccupancyCharts'
import { unitsTotal } from '../occupancyFigures'
import { unitLabel, useOverview, useRentalUnits, useUnitsOverview, type MonthFigures } from '../api'

const MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember']
/** `unitId` in the address for „Alle Einheiten“. */
const ALL_UNITS = 'all'

function Figures({ f }: { f: MonthFigures }) {
  return (
    <>
      <TableCell className="text-right tabular-nums">{f.nights}</TableCell>
      <TableCell className="text-right tabular-nums">{f.occupancy} %</TableCell>
      <TableCell className="text-right tabular-nums text-muted-foreground">{f.ownerNights}</TableCell>
      <TableCell className="text-right">
        <Money amount={f.settledNet} />
      </TableCell>
      <TableCell className="text-right text-muted-foreground">
        <Money amount={f.plannedRent} colored={false} />
      </TableCell>
    </>
  )
}

/** The previous year's occupancy and settled net, shown when „Vorjahr einblenden“ is on. */
function PreviousFigures({ f }: { f: MonthFigures }) {
  return (
    <>
      <TableCell className="text-right tabular-nums text-muted-foreground">{f.occupancy} %</TableCell>
      <TableCell className="text-right text-muted-foreground">
        <Money amount={f.settledNet} colored={false} />
      </TableCell>
    </>
  )
}

function FigureHeads() {
  return (
    <>
      <TableHead className="text-right">Nächte</TableHead>
      <TableHead className="text-right">Belegung</TableHead>
      <TableHead className="text-right">Eigenbelegung</TableHead>
      <TableHead className="text-right">Netto abgerechnet</TableHead>
      <TableHead className="text-right">Mietpreis geplant</TableHead>
    </>
  )
}

/** Yearly overview per unit, or all units compared: charts above, the exact figures in a table below. */
export function AuswertungPage() {
  const [params, setParams] = useSearchParams()
  const { data: units = [] } = useRentalUnits()
  const allUnits = params.get('unitId') === ALL_UNITS
  const unitId = allUnits ? null : params.get('unitId') ? Number(params.get('unitId')) : (units[0]?.id ?? null)
  const year = params.get('year') ? Number(params.get('year')) : Number(todayIso().slice(0, 4))
  const showPrevious = params.get('previous') === '1'
  const { data: overview } = useOverview(allUnits ? null : unitId, year)
  const { data: previous } = useOverview(allUnits || !showPrevious ? null : unitId, year - 1)
  const { data: unitRows } = useUnitsOverview(year, allUnits)
  const previousMonths = showPrevious ? previous?.months : undefined

  function update(changes: Record<string, string | number | undefined>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === undefined ? next.delete(k) : next.set(k, String(v))))
    setParams(next, { replace: true })
  }

  return (
    <>
      <PageHeader title="Auswertung" description="Belegung und Einnahmen je Einheit und Jahr, oder alle Einheiten im Vergleich." />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="w-72 max-w-full">
          <IdSelect
            id="ov-unit"
            label="Einheit"
            value={allUnits ? undefined : (unitId ?? undefined)}
            options={units.map((u) => ({ id: u.id, name: unitLabel(u) }))}
            onChange={(v) => update({ unitId: v ?? ALL_UNITS })}
            allLabel="Alle Einheiten"
          />
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" aria-label="Vorheriges Jahr" onClick={() => update({ year: year - 1 })}>
            <ChevronLeftIcon />
          </Button>
          <span className="w-14 text-center font-medium">{year}</span>
          <Button variant="outline" size="icon" aria-label="Nächstes Jahr" onClick={() => update({ year: year + 1 })}>
            <ChevronRightIcon />
          </Button>
        </div>
        {!allUnits && (
          <div className="flex h-9 items-center gap-2">
            <Checkbox
              id="ov-previous"
              checked={showPrevious}
              onCheckedChange={(on) => update({ previous: on === true ? 1 : undefined })}
            />
            <Label htmlFor="ov-previous">Vorjahr einblenden</Label>
          </div>
        )}
      </div>

      {!allUnits && overview && (
        <>
          <UnitCharts year={year} months={overview.months} previous={previousMonths} />
          <div className="rounded-lg border">
            <Table aria-label={`Auswertung ${year}`}>
              <TableHeader>
                <TableRow>
                  <TableHead>Monat</TableHead>
                  <FigureHeads />
                  {previousMonths && (
                    <>
                      <TableHead className="text-right">Belegung {year - 1}</TableHead>
                      <TableHead className="text-right">Netto {year - 1}</TableHead>
                    </>
                  )}
                </TableRow>
              </TableHeader>
              <TableBody>
                {overview.months.map((f, i) => (
                  <TableRow key={f.month}>
                    <TableCell>{MONTHS[f.month - 1]}</TableCell>
                    <Figures f={f} />
                    {previousMonths && <PreviousFigures f={previousMonths[i]} />}
                  </TableRow>
                ))}
              </TableBody>
              <TableFooter>
                <TableRow>
                  <TableCell>Summe</TableCell>
                  <Figures f={overview.total} />
                  {previous && previousMonths && <PreviousFigures f={previous.total} />}
                </TableRow>
              </TableFooter>
            </Table>
          </div>
        </>
      )}

      {allUnits && unitRows && (
        unitRows.length === 0 ? (
          <p className="text-sm text-muted-foreground">Keine Einheiten mit Zahlen in {year}.</p>
        ) : (
          <>
            <UnitsCharts year={year} rows={unitRows} />
            <div className="rounded-lg border">
              <Table aria-label={`Alle Einheiten ${year}`}>
                <TableHeader>
                  <TableRow>
                    <TableHead>Einheit</TableHead>
                    <FigureHeads />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {unitRows.map((r) => (
                    <TableRow key={r.unitId}>
                      <TableCell>
                        {unitLabel({ name: r.unitName, propertyName: r.propertyName, propertyMultiUnit: r.propertyMultiUnit })}
                        {r.archived && <span className="ml-2 text-xs text-muted-foreground">archiviert</span>}
                      </TableCell>
                      <Figures f={r.total} />
                    </TableRow>
                  ))}
                </TableBody>
                <TableFooter>
                  <TableRow>
                    <TableCell>Summe</TableCell>
                    <Figures f={unitsTotal(unitRows, year)} />
                  </TableRow>
                </TableFooter>
              </Table>
            </div>
          </>
        )
      )}
      <p className="mt-2 text-xs text-muted-foreground">
        Nächte werden auf die Monate verteilt, Eigenbelegungen zählen nicht zur Belegung, Geld zählt im Monat des Abrechnungsdatums. Geplante Mietpreise zählen im Anreisemonat.
      </p>
    </>
  )
}
