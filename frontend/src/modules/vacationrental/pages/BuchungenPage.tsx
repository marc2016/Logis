import { useMemo, useState } from 'react'
import { DateField } from '@/components/DateField'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { createColumnHelper } from '@tanstack/react-table'
import { ListChecksIcon, PlusIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { DataTable, type SortState } from '@/components/DataTable'
import { Money } from '@/components/Money'
import { PageHeader } from '@/components/PageHeader'
import { formatDate } from '@/core/ledger/transactions/format'
import { BookingDialog } from '../BookingDialog'
import { AssignToStatementDialog } from '../AssignToStatementDialog'
import { assignLine } from '../statementAssign'
import { BulkActionBar } from '@/components/BulkActionBar'
import { ConfirmButton } from '@/components/ConfirmButton'
import { useSelection } from '@/components/useSelection'
import { useBulk } from '@/components/useBulk'
import { api } from '@/lib/api'
import { SkipError } from '@/lib/bulk'
import { backTo, bookingPath } from '../BookingPage.paths'
import { IdSelect } from '../RentalFilters'
import { queueParams, reviewStartPath, useReviewCount } from '../reviewQueue'
import { CANCELLATION_REPORTED, cancellationReported, kindLabel, rentalProperties, statusLabel, unitLabel, useAgencies, useBookings, useInvalidateRental, useRentalUnits, type Booking, type BookingKind, type BookingStatus } from '../api'

const PAGE_SIZE = 50
const ALL = 'all'
const DEFAULT_SORT = 'arrival,desc'
const column = createColumnHelper<Booking>()

const columns = [
  column.accessor('arrival', { header: 'Anreise', cell: (c) => formatDate(c.getValue()), meta: { label: 'Anreise', sortable: true } }),
  column.accessor('departure', { header: 'Abreise', cell: (c) => formatDate(c.getValue()), meta: { label: 'Abreise', sortable: true } }),
  column.accessor('kind', {
    header: 'Art',
    cell: (c) => (c.getValue() === 'OWNER' ? <Badge variant="secondary">{kindLabel.OWNER}</Badge> : kindLabel.GUEST),
    meta: { label: 'Art' },
  }),
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
    cell: (c) => (
      <span className="inline-flex flex-wrap gap-1">
        <Badge variant={c.getValue() === 'CANCELLED' ? 'secondary' : 'outline'}>{statusLabel[c.getValue()]}</Badge>
        {c.row.original.reviewState !== 'NONE' && <Badge>zu prüfen</Badge>}
        {cancellationReported(c.row.original) && <Badge variant="destructive">{CANCELLATION_REPORTED}</Badge>}
      </span>
    ),
    meta: { label: 'Status', sortable: true },
  }),
  column.accessor('agency', { header: 'Vermittler', cell: (c) => c.getValue()?.name ?? '–', meta: { label: 'Vermittler', sortable: true } }),
  column.accessor('externalRef', { header: 'Buchungsnr.', cell: (c) => c.getValue() ?? '–', meta: { label: 'Buchungsnummer' } }),
]

function parseSort(value: string | null): SortState {
  const [id, dir] = (value ?? DEFAULT_SORT).split(',')
  return { id, desc: dir !== 'asc' }
}

/** Grid of all visible bookings with filters, sorting and paging kept in the URL. */
export function BuchungenPage() {
  const [params, setParams] = useSearchParams()
  const { data: units = [] } = useRentalUnits()
  const { data: agencies = [] } = useAgencies()
  const [recording, setRecording] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()

  const num = (key: string) => (params.get(key) ? Number(params.get(key)) : undefined)
  const filter = {
    propertyId: num('propertyId'),
    unitId: num('unitId'),
    status: (params.get('status') as BookingStatus | null) ?? undefined,
    from: params.get('from') ?? undefined,
    to: params.get('to') ?? undefined,
    q: params.get('q') ?? undefined,
    review: params.get('review') === 'true',
    agencyId: num('agencyId'),
    kind: (params.get('kind') as BookingKind | null) ?? undefined,
    sort: params.get('sort') ?? DEFAULT_SORT,
    page: num('page') ?? 0,
    size: PAGE_SIZE,
  }
  const { data, isLoading } = useBookings(filter)
  // „Prüfen (n)“: the bookings to review that match the current filters (spec bookings, „Review mode“).
  const reviewFilters = queueParams(params)
  const { data: toReview = 0 } = useReviewCount(reviewFilters)
  const properties = useMemo(() => rentalProperties(units), [units])
  const unitOptions = units
    .filter((u) => filter.propertyId === undefined || u.propertyId === filter.propertyId)
    .map((u) => ({ id: u.id, name: unitLabel(u) }))
  const canRecord = units.some((u) => !u.archived && u.myRole !== 'VIEWER')
  // Filters, sorting and paging all live in the URL: any change there clears the selection.
  const selection = useSelection(params.toString())
  const invalidate = useInvalidateRental()
  const bulk = useBulk(selection, invalidate)
  const [assigning, setAssigning] = useState(false)
  const selected = (data?.items ?? []).filter((b) => selection.isSelected(String(b.id)))
  const bookingId = (b: Booking) => String(b.id)
  const base = (b: Booking) => `/api/vacationrental/bookings/${b.id}`

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
          <div className="flex flex-wrap gap-2">
            {toReview > 0 && (
              <Button variant="outline" onClick={() => navigate(reviewStartPath(reviewFilters))}>
                <ListChecksIcon />
                Prüfen ({toReview})
              </Button>
            )}
            {canRecord && (
              <Button onClick={() => setRecording(true)}>
                <PlusIcon />
                Buchung
              </Button>
            )}
          </div>
        }
      />
      <label className="mb-2 inline-flex items-center gap-2 text-sm">
        <Checkbox checked={filter.review} onCheckedChange={(v) => update({ review: v === true ? 'true' : undefined })} aria-label="Nur zu prüfen" />
        Nur „zu prüfen“
      </label>
      <div className="mb-4 grid grid-cols-[repeat(auto-fill,minmax(11rem,1fr))] gap-3">
        <IdSelect id="flt-property" label="Objekt" value={filter.propertyId} options={properties} onChange={(v) => update({ propertyId: v, unitId: undefined })} />
        <IdSelect id="flt-unit" label="Einheit" value={filter.unitId} options={unitOptions} onChange={(v) => update({ unitId: v })} />
        <IdSelect id="flt-agency" label="Vermittler" value={filter.agencyId} options={agencies} onChange={(v) => update({ agencyId: v })} />
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
          <Label htmlFor="flt-kind" className="text-xs text-muted-foreground">
            Art
          </Label>
          <Select value={filter.kind ?? ALL} onValueChange={(v) => update({ kind: v === ALL ? undefined : v })}>
            <SelectTrigger id="flt-kind" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Alle</SelectItem>
              {(Object.keys(kindLabel) as BookingKind[]).map((k) => (
                <SelectItem key={k} value={k}>
                  {kindLabel[k]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="flt-from" className="text-xs text-muted-foreground">
            Von
          </Label>
          <DateField id="flt-from" label="Von" value={filter.from ?? ''} onChange={(from) => update({ from: from || undefined })} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="flt-to" className="text-xs text-muted-foreground">
            Bis
          </Label>
          <DateField id="flt-to" label="Bis" value={filter.to ?? ''} onChange={(to) => update({ to: to || undefined })} />
        </div>
        <div className="space-y-1 @2xl:col-span-2">
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
        onRowClick={(booking) => navigate(bookingPath(booking.id), { state: backTo(location) })}
        rowLabel={(b) => `Buchung ${b.externalRef ?? ''} ${formatDate(b.arrival)}`.replace(/\s+/g, ' ')}
        rowClassName={(b) => (b.status === 'CANCELLED' ? 'text-muted-foreground' : undefined)}
        isLoading={isLoading}
        emptyText="Keine Buchungen gefunden."
        selection={selection}
        selectable={(b) => b.canEdit}
      />
      <BulkActionBar selection={selection} busy={bulk.busy}>
        <Button
          size="sm"
          disabled={bulk.busy}
          onClick={() =>
            bulk.run(selected, bookingId, (b) => {
              if (b.reviewState === 'CHANGE' || cancellationReported(b)) throw new SkipError('Änderungsvorschläge bitte einzeln prüfen')
              if (b.reviewState !== 'NEW') throw new SkipError('Die Buchung ist nicht „zu prüfen“')
              return api(`${base(b)}/confirm`, { method: 'POST' })
            }, 'bestätigt')
          }
        >
          Bestätigen
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={bulk.busy}
          onClick={() =>
            bulk.run(selected, bookingId, (b) => {
              if (b.status === 'CANCELLED') throw new SkipError('Die Buchung ist bereits storniert')
              return api(`${base(b)}/cancel`, { method: 'POST' })
            }, 'storniert')
          }
        >
          Stornieren
        </Button>
        <Button size="sm" variant="outline" disabled={bulk.busy} onClick={() => setAssigning(true)}>
          Abrechnung zuordnen
        </Button>
        <ConfirmButton
          question={`${selection.ids.size} ${selection.ids.size === 1 ? 'Eintrag' : 'Einträge'} löschen?`}
          disabled={bulk.busy}
          onConfirm={() => bulk.run(selected, bookingId, (b) => api(base(b), { method: 'DELETE' }), 'gelöscht')}
        >
          Löschen
        </ConfirmButton>
      </BulkActionBar>
      {assigning && (
        <AssignToStatementDialog
          unitIds={[...new Set(selected.map((b) => b.unit.id))]}
          count={selected.length}
          onClose={() => setAssigning(false)}
          onAssign={(statement, amounts) => {
            setAssigning(false)
            bulk.run(selected, bookingId, (b) => assignLine(statement.id, b, amounts.rate, amounts.cleaning), 'zugeordnet')
          }}
        />
      )}
      {recording && <BookingDialog defaultPropertyId={filter.propertyId} defaultUnitId={filter.unitId} onClose={() => setRecording(false)} />}
    </>
  )
}
