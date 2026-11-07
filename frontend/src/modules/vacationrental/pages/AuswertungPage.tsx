import { useSearchParams } from 'react-router'
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Money } from '@/components/Money'
import { PageHeader } from '@/components/PageHeader'
import { todayIso } from '@/core/ledger/transactions/format'
import { IdSelect } from '../RentalFilters'
import { unitLabel, useOverview, useRentalUnits, type MonthFigures } from '../api'

const MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember']

function Row({ label, f }: { label: string; f: MonthFigures }) {
  return (
    <>
      <TableCell>{label}</TableCell>
      <TableCell className="text-right tabular-nums">{f.nights}</TableCell>
      <TableCell className="text-right tabular-nums">{f.occupancy} %</TableCell>
      <TableCell className="text-right">
        <Money amount={f.settledNet} />
      </TableCell>
      <TableCell className="text-right text-muted-foreground">
        <Money amount={f.plannedRent} colored={false} />
      </TableCell>
    </>
  )
}

/** Yearly overview per unit: nights split by month, money on the statement date. */
export function AuswertungPage() {
  const [params, setParams] = useSearchParams()
  const { data: units = [] } = useRentalUnits()
  const unitId = params.get('unitId') ? Number(params.get('unitId')) : (units[0]?.id ?? null)
  const year = params.get('year') ? Number(params.get('year')) : Number(todayIso().slice(0, 4))
  const { data: overview } = useOverview(unitId, year)

  function update(changes: Record<string, number | undefined>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === undefined ? next.delete(k) : next.set(k, String(v))))
    setParams(next, { replace: true })
  }

  return (
    <>
      <PageHeader title="Auswertung" description="Belegung und Einnahmen je Einheit und Jahr." />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="w-72">
          <IdSelect
            id="ov-unit"
            label="Einheit"
            value={unitId ?? undefined}
            options={units.map((u) => ({ id: u.id, name: unitLabel(u) }))}
            onChange={(v) => update({ unitId: v })}
            allLabel="Einheit wählen"
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
      </div>
      {overview && (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Monat</TableHead>
                <TableHead className="text-right">Nächte</TableHead>
                <TableHead className="text-right">Belegung</TableHead>
                <TableHead className="text-right">Netto abgerechnet</TableHead>
                <TableHead className="text-right">Mietpreis geplant</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {overview.months.map((f) => (
                <TableRow key={f.month}>
                  <Row label={MONTHS[f.month - 1]} f={f} />
                </TableRow>
              ))}
            </TableBody>
            <TableFooter>
              <TableRow>
                <Row label="Summe" f={overview.total} />
              </TableRow>
            </TableFooter>
          </Table>
        </div>
      )}
      <p className="mt-2 text-xs text-muted-foreground">
        Nächte werden auf die Monate verteilt, Geld zählt im Monat des Abrechnungsdatums. Geplante Mietpreise zählen im Anreisemonat.
      </p>
    </>
  )
}
