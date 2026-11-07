import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { ChevronLeftIcon, ChevronRightIcon, PlusIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/PageHeader'
import { todayIso } from '@/core/ledger/transactions/format'
import { BookingDialog } from '../BookingDialog'
import { CalendarGrid } from '../CalendarGrid'
import { IdSelect } from '../RentalFilters'
import { rentalProperties, useBooking, useCalendar, useRentalUnits } from '../api'

const monthName = new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric', timeZone: 'UTC' })

function shift(month: string, delta: number) {
  const [y, m] = month.split('-').map(Number)
  const d = new Date(Date.UTC(y, m - 1 + delta, 1))
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
}

/** Month calendar per property; "+ Buchung" opens the same dialog as the grid. */
export function KalenderPage() {
  const [params, setParams] = useSearchParams()
  const { data: units = [], isLoading: unitsLoading } = useRentalUnits()
  const properties = useMemo(() => rentalProperties(units), [units])
  const propertyId = params.get('propertyId') ? Number(params.get('propertyId')) : (properties[0]?.id ?? null)
  const month = params.get('month') ?? todayIso().slice(0, 7)
  const { data: calendar, isLoading } = useCalendar(propertyId, month)
  const [dialog, setDialog] = useState<{ bookingId: number | null } | null>(null)
  const { data: openBooking } = useBooking(dialog?.bookingId ?? null)
  const canRecord = units.some((u) => u.propertyId === propertyId && !u.archived && u.myRole !== 'VIEWER')

  function update(changes: Record<string, string | number | undefined>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === undefined ? next.delete(k) : next.set(k, String(v))))
    setParams(next, { replace: true })
  }

  const [y, m] = month.split('-').map(Number)
  return (
    <>
      <PageHeader
        title="Kalender"
        description="Belegung der Ferienwohnungen je Monat."
        actions={
          canRecord && (
            <Button onClick={() => setDialog({ bookingId: null })}>
              <PlusIcon />
              Buchung
            </Button>
          )
        }
      />
      {!unitsLoading && properties.length === 0 ? (
        <p className="text-muted-foreground">Es gibt noch keine Ferienwohnung. Lege unter „Immobilien“ ein Objekt mit der Nutzung Ferienvermietung an.</p>
      ) : (
        <>
          <div className="mb-4 flex flex-wrap items-end gap-3">
            <div className="w-64">
              <IdSelect
                id="cal-property"
                label="Objekt"
                value={propertyId ?? undefined}
                options={properties}
                onChange={(v) => update({ propertyId: v })}
                allLabel="Objekt wählen"
              />
            </div>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="icon" aria-label="Vorheriger Monat" onClick={() => update({ month: shift(month, -1) })}>
                <ChevronLeftIcon />
              </Button>
              <span className="min-w-36 text-center font-medium" aria-live="polite">
                {monthName.format(new Date(Date.UTC(y, m - 1, 1)))}
              </span>
              <Button variant="outline" size="icon" aria-label="Nächster Monat" onClick={() => update({ month: shift(month, 1) })}>
                <ChevronRightIcon />
              </Button>
            </div>
          </div>
          {calendar ? (
            <CalendarGrid calendar={calendar} onOpen={(bookingId) => setDialog({ bookingId })} />
          ) : (
            isLoading && <p className="text-muted-foreground">Wird geladen …</p>
          )}
          <p className="mt-2 flex gap-4 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <span className="inline-block h-3 w-5 rounded border-2 border-primary" /> Geplant
            </span>
            <span className="inline-flex items-center gap-1">
              <span className="inline-block h-3 w-5 rounded bg-primary" /> Abgerechnet
            </span>
          </p>
        </>
      )}
      {dialog && (dialog.bookingId === null || openBooking) && (
        <BookingDialog
          key={dialog.bookingId ?? 'new'}
          booking={dialog.bookingId === null ? null : openBooking!}
          defaultPropertyId={propertyId ?? undefined}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  )
}
