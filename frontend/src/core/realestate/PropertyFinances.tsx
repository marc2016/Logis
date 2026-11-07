import { useState } from 'react'
import { useNavigate } from 'react-router'
import { createColumnHelper } from '@tanstack/react-table'
import { ChevronLeftIcon, ChevronRightIcon, PlusIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { DataTable, type SortState } from '@/components/DataTable'
import { Money } from '@/components/Money'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate, todayIso } from '@/core/ledger/transactions/format'
import { CostDialog } from './CostDialog'
import { COST_SOURCE, useFinances, type Cost, type FinanceItem, type FinanceKind, type FinanceMonth, type Property } from './api'

const MONTHS = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez']
const ALL = 'all'
const kindLabel: Record<FinanceKind, string> = { INCOME: 'Einnahme', EXPENSE: 'Ausgabe' }
/** Where a contributed item can be opened. */
const sourceLink: Record<string, (id: number) => string> = {
  'vacationrental.statement': (id) => `/ferienvermietung/abrechnungen?statement=${id}`,
}

const column = createColumnHelper<FinanceItem>()

function signed(item: FinanceItem) {
  return item.kind === 'EXPENSE' ? `-${item.amount}` : item.amount
}

function MonthRow({ label, m }: { label: string; m: FinanceMonth }) {
  return (
    <>
      <TableCell>{label}</TableCell>
      <TableCell className="text-right">
        <Money amount={m.income} colored={false} />
      </TableCell>
      <TableCell className="text-right">
        <Money amount={m.expenses} colored={false} />
      </TableCell>
      <TableCell className="text-right font-medium">
        <Money amount={m.result} />
      </TableCell>
    </>
  )
}

/** Income, expenses and result of a property per month, with all items and cost entry. */
export function PropertyFinances({ property }: { property: Property }) {
  const navigate = useNavigate()
  const [year, setYear] = useState(() => Number(todayIso().slice(0, 4)))
  const [unitId, setUnitId] = useState<number | undefined>()
  const [kind, setKind] = useState<FinanceKind | undefined>()
  const [sort, setSort] = useState<SortState>({ id: 'date', desc: true })
  const { data, isLoading } = useFinances(property.id, year, { unitId, kind, sort: `${sort.id},${sort.desc ? 'desc' : 'asc'}` })
  const [dialog, setDialog] = useState<{ cost: Cost | null } | null>(null)
  const showUnits = property.multiUnit || !property.myRole

  const columns = [
    column.accessor('date', { header: 'Datum', cell: (c) => formatDate(c.getValue()), meta: { label: 'Datum', sortable: true } }),
    ...(showUnits
      ? [column.accessor('unitName', { id: 'unit', header: 'Einheit', cell: (c) => c.getValue() ?? 'Ganzes Objekt', meta: { label: 'Einheit', sortable: true } })]
      : []),
    column.accessor('label', { header: 'Posten', meta: { label: 'Posten', sortable: true } }),
    column.accessor('categoryName', { header: 'Kategorie', cell: (c) => c.getValue() ?? '–', meta: { label: 'Kategorie' } }),
    column.accessor('kind', {
      header: 'Art',
      cell: (c) => <Badge variant="outline">{kindLabel[c.getValue()]}</Badge>,
      meta: { label: 'Art' },
    }),
    column.accessor((i) => signed(i), {
      id: 'amount',
      header: 'Betrag',
      cell: (c) => <Money amount={c.getValue()} />,
      meta: { label: 'Betrag', sortable: true, className: 'text-right' },
    }),
  ]

  async function open(item: FinanceItem) {
    if (item.sourceType === COST_SOURCE) {
      try {
        const cost = await api<Cost>(`/api/costs/${item.sourceId}`)
        if (item.editable) setDialog({ cost })
        else toast.info(`${item.label}: ${formatDate(cost.date)}${cost.note ? ` – ${cost.note}` : ''}`)
      } catch (err) {
        toast.error(errorMessage(err))
      }
      return
    }
    const link = sourceLink[item.sourceType]
    if (link) navigate(link(item.sourceId))
  }

  return (
    <section className="mb-8" aria-label="Finanzen">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">Finanzen</h2>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" aria-label="Vorheriges Jahr" onClick={() => setYear(year - 1)}>
            <ChevronLeftIcon />
          </Button>
          <span className="w-12 text-center font-medium">{year}</span>
          <Button variant="outline" size="icon" aria-label="Nächstes Jahr" onClick={() => setYear(year + 1)}>
            <ChevronRightIcon />
          </Button>
          {data?.canRecord && (
            <Button onClick={() => setDialog({ cost: null })}>
              <PlusIcon />
              Kosten
            </Button>
          )}
        </div>
      </div>

      {data && (
        <div className="mb-4 rounded-lg border">
          <Table aria-label="Monatsübersicht">
            <TableHeader>
              <TableRow>
                <TableHead>Monat</TableHead>
                <TableHead className="text-right">Einnahmen</TableHead>
                <TableHead className="text-right">Ausgaben</TableHead>
                <TableHead className="text-right">Ergebnis</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.months.map((m) => (
                <TableRow key={m.month}>
                  <MonthRow label={MONTHS[m.month - 1]} m={m} />
                </TableRow>
              ))}
            </TableBody>
            <TableFooter>
              <TableRow>
                <MonthRow label={`Summe ${year}`} m={data.total} />
              </TableRow>
            </TableFooter>
          </Table>
        </div>
      )}

      <div className="mb-2 flex flex-wrap gap-3">
        {showUnits && (
          <div className="w-56 space-y-1">
            <Label htmlFor="fin-unit" className="text-xs text-muted-foreground">
              Einheit
            </Label>
            <Select value={unitId === undefined ? ALL : String(unitId)} onValueChange={(v) => setUnitId(v === ALL ? undefined : Number(v))}>
              <SelectTrigger id="fin-unit" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Alle</SelectItem>
                {property.units.map((u) => (
                  <SelectItem key={u.id} value={String(u.id)}>
                    {u.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        <div className="w-40 space-y-1">
          <Label htmlFor="fin-kind" className="text-xs text-muted-foreground">
            Art
          </Label>
          <Select value={kind ?? ALL} onValueChange={(v) => setKind(v === ALL ? undefined : (v as FinanceKind))}>
            <SelectTrigger id="fin-kind" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Alle</SelectItem>
              <SelectItem value="INCOME">Einnahmen</SelectItem>
              <SelectItem value="EXPENSE">Ausgaben</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
      <DataTable
        tableId="realestate.finances"
        columns={columns}
        data={data?.items ?? []}
        getRowId={(i) => `${i.sourceType}-${i.sourceId}-${i.kind}-${i.label}-${i.unitId ?? 0}`}
        sort={sort}
        onSortChange={setSort}
        onRowClick={open}
        rowLabel={(i) => `${i.label} ${formatDate(i.date)}`}
        isLoading={isLoading}
        emptyText="Keine Einnahmen oder Ausgaben in diesem Jahr."
      />
      <p className="mt-2 text-xs text-muted-foreground">
        Abrechnungen zählen am Abrechnungsdatum. Zugeordnete Transaktionen unten sind nicht im Ergebnis enthalten.
      </p>

      {dialog && <CostDialog property={property} cost={dialog.cost} onClose={() => setDialog(null)} />}
    </section>
  )
}
