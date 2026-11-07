import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { createColumnHelper } from '@tanstack/react-table'
import { PlusIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { DataTable, type SortState } from '@/components/DataTable'
import { Money } from '@/components/Money'
import { PageHeader } from '@/components/PageHeader'
import { formatDate } from '@/core/ledger/transactions/format'
import { BookingDialog } from '../BookingDialog'
import { IdSelect } from '../RentalFilters'
import { rentalProperties, channelLabel, statusLabel, unitLabel, useBookings, useRentalUnits, type Booking, type BookingStatus } from '../api'

const PAGE_SIZE = 50
const ALL = 'all'
const DEFAULT_SORT = 'arrival,desc'
const column = createColumnHelper<Booking>()

const columns = [
  column.accessor('arrival', { header: 'Anreise', cell: (c) => formatDate(c.getValue()), meta: { label: 'Anreise', sortable: true } }),
  column.accessor('departure', { header: 'Abreise', cell: (c) => formatDate(c.getValue()), meta: { label: 'Abreise', sortable: true } }),
  column.accessor('unit', { header: 'Einheit', cell: (c) => unitLabel(c.getValue()), meta: { label: 'Einheit', sortable: true } }),
  column.accessor((b) => b.guest?.name ?? null, {
    id: 'guest',
    header: 'Gast',
    cell: (c) => c.getValue() ?? (c.row.original.guestDeleted ? <span className="text-muted-foreground">gelöscht</span> : '–'),
    meta: { label: 'Gast', sortable: true },
  }),
  column.accessor('nights', { header: 'Nächte', meta: { label: 'Nächte', sortable: true, className: 'text-right' } }),
  column.accessor('expectedRent', {
    id: 'expectedRent',
    header: 'Mietpreis',
    cell: (c) => (c.getValue() ? <Money amount={c.getValue()!} colored={false} /> : '–'),
    meta: { label: 'Mietpreis', sortable: true, className: 'text-right' },
  }),
  column.accessor('status', {
    header: 'Status',
    cell: (c) => <Badge variant={c.getValue() === 'CANCELLED' ? 'secondary' : 'outline'}>{statusLabel[c.getValue()]}</Badge>,
    meta: { label: 'Status', sortable: true },
  }),
  column.accessor('externalRef', { header: 'Buchungsnr.', cell: (c) => c.getValue() ?? '–', meta: { label: 'Buchungsnummer' } }),
  column.accessor('channel', { header: 'Kanal', cell: (c) => (c.getValue() ? channelLabel[c.getValue()!] : '–'), meta: { label: 'Kanal' } }),
]

function parseSort(value: string | null): SortState {
  const [id, dir] = (value ?? DEFAULT_SORT).split(',')
  return { id, desc: dir !== 'asc' }
}

/** Grid of all visible bookings with filters, sorting and paging kept in the URL. */
export function BuchungenPage() {
  const [params, setParams] = useSearchParams()
  const { data: units = [] } = useRentalUnits()
  const [dialog, setDialog] = useState<{ booking: Booking | null } | null>(null)

  const num = (key: string) => (params.get(key) ? Number(params.get(key)) : undefined)
  const filter = {
    propertyId: num('propertyId'),
    unitId: num('unitId'),
    status: (params.get('status') as BookingStatus | null) ?? undefined,
    from: params.get('from') ?? undefined,
    to: params.get('to') ?? undefined,
    q: params.get('q') ?? undefined,
    sort: params.get('sort') ?? DEFAULT_SORT,
    page: num('page') ?? 0,
    size: PAGE_SIZE,
  }
  const { data, isLoading } = useBookings(filter)
  const properties = useMemo(() => rentalProperties(units), [units])
  const unitOptions = units
    .filter((u) => filter.propertyId === undefined || u.propertyId === filter.propertyId)
    .map((u) => ({ id: u.id, name: unitLabel(u) }))
  const canRecord = units.some((u) => !u.archived && u.myRole !== 'VIEWER')

  /** Changes URL parameters; every filter change starts again on the first page. */
  function update(changes: Record<string, string | number | undefined>, keepPage = false) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === undefined || v === '' ? next.delete(k) : next.set(k, String(v))))
    if (!keepPage) next.delete('page')
    setParams(next, { replace: true })
  }

  return (
    <>
      <PageHeader
        title="Buchungen"
        description="Alle Buchungen der Ferienwohnungen."
        actions={
          canRecord && (
            <Button onClick={() => setDialog({ booking: null })}>
              <PlusIcon />
              Buchung
            </Button>
          )
        }
      />
      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-6">
        <IdSelect id="flt-property" label="Objekt" value={filter.propertyId} options={properties} onChange={(v) => update({ propertyId: v, unitId: undefined })} />
        <IdSelect id="flt-unit" label="Einheit" value={filter.unitId} options={unitOptions} onChange={(v) => update({ unitId: v })} />
        <div className="space-y-1">
          <Label htmlFor="flt-status" className="text-xs text-muted-foreground">
            Status
          </Label>
          <Select value={filter.status ?? ALL} onValueChange={(v) => update({ status: v === ALL ? undefined : v })}>
            <SelectTrigger id="flt-status" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Alle</SelectItem>
              {(Object.keys(statusLabel) as BookingStatus[]).map((s) => (
                <SelectItem key={s} value={s}>
                  {statusLabel[s]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="flt-from" className="text-xs text-muted-foreground">
            Von
          </Label>
          <Input id="flt-from" type="date" value={filter.from ?? ''} onChange={(e) => update({ from: e.target.value })} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="flt-to" className="text-xs text-muted-foreground">
            Bis
          </Label>
          <Input id="flt-to" type="date" value={filter.to ?? ''} onChange={(e) => update({ to: e.target.value })} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="flt-q" className="text-xs text-muted-foreground">
            Suche
          </Label>
          <Input id="flt-q" type="search" placeholder="Buchungsnr. oder Gast" defaultValue={filter.q ?? ''} onChange={(e) => update({ q: e.target.value })} />
        </div>
      </div>
      <DataTable
        tableId="vacationrental.bookings"
        columns={columns}
        data={data?.items ?? []}
        getRowId={(b) => String(b.id)}
        sort={parseSort(filter.sort)}
        onSortChange={(s) => update({ sort: `${s.id},${s.desc ? 'desc' : 'asc'}` })}
        page={filter.page}
        pageSize={PAGE_SIZE}
        total={data?.totalItems ?? 0}
        onPageChange={(p) => update({ page: p || undefined }, true)}
        onRowClick={(booking) => setDialog({ booking })}
        rowLabel={(b) => `Buchung ${b.externalRef ?? ''} ${formatDate(b.arrival)}`.replace(/\s+/g, ' ')}
        rowClassName={(b) => (b.status === 'CANCELLED' ? 'text-muted-foreground' : undefined)}
        isLoading={isLoading}
        emptyText="Keine Buchungen gefunden."
      />
      {dialog && (
        <BookingDialog booking={dialog.booking} defaultPropertyId={filter.propertyId} defaultUnitId={filter.unitId} onClose={() => setDialog(null)} />
      )}
    </>
  )
}
