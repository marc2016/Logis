import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { CalendarPlusIcon, ChevronLeftIcon, ChevronRightIcon, PlusIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/PageHeader'
import { todayIso } from '@/core/ledger/transactions/format'
import { BookingDialog } from '../BookingDialog'
import { CalendarGrid } from '../CalendarGrid'
import { monthRange } from '../calendar'
import { IdSelect } from '../RentalFilters'
import { SubscriptionDialog } from '../SubscriptionDialog'
import { unitLabel, useBooking, useCalendarYear, useRentalUnits } from '../api'

/** The year from the address: `year`, or the year of an old `month` link, otherwise the current year. */
function yearFrom(params: URLSearchParams, currentYear: number) {
  const raw = params.get('year') ?? params.get('month')?.slice(0, 4)
  const year = Number(raw)
  return Number.isInteger(year) && year >= 1900 && year <= 2999 ? year : currentYear
}

/** One unit, one row per month of a year plus its neighbouring months, paged by year. */
export function KalenderPage() {
  const [params, setParams] = useSearchParams()
  const { data: units = [], isLoading: unitsLoading } = useRentalUnits()
  const options = useMemo(
    () => [...units].sort((a, b) => Number(a.archived) - Number(b.archived) || unitLabel(a).localeCompare(unitLabel(b), 'de')),
    [units],
  )
  const unit = options.find((u) => u.id === Number(params.get('unitId'))) ?? options[0]
  const propertyId = unit?.propertyId ?? null
  const currentMonth = todayIso().slice(0, 7)
  const currentYear = Number(currentMonth.slice(0, 4))
  const year = yearFrom(params, currentYear)
  const calendar = useCalendarYear(propertyId, year)
  const [dialog, setDialog] = useState<{ bookingId: number | null } | null>(null)
  const [subscribing, setSubscribing] = useState(false)
  const { data: openBooking } = useBooking(dialog?.bookingId ?? null)
  const canRecord = !!unit && !unit.archived && unit.myRole !== 'VIEWER'
  const data = calendar.data
  const bookings = data?.units.find((u) => u.id === unit?.id)?.bookings ?? []

  function update(changes: Record<string, string | number | undefined>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === undefined ? next.delete(k) : next.set(k, String(v))))
    setParams(next, { replace: true })
  }

  /** A year in the address; the current year needs none. An old `month` parameter is dropped. */
  function showYear(next: number) {
    update({ year: next === currentYear ? undefined : next, month: undefined })
  }

  return (
    <>
      <PageHeader
        title="Kalender"
        description="Belegung der Ferienwohnungen, Monat für Monat."
        actions={
          canRecord && (
            <Button onClick={() => setDialog({ bookingId: null })}>
              <PlusIcon />
              Buchung
            </Button>
          )
        }
      />
      {!unitsLoading && options.length === 0 ? (
        <p className="text-muted-foreground">Es gibt noch keine Ferienwohnung. Lege unter „Immobilien“ ein Objekt mit der Nutzung Ferienvermietung an.</p>
      ) : (
        <>
          <div className="mb-4 flex flex-wrap items-end gap-3">
            <div className="w-64">
              <IdSelect
                id="cal-unit"
                label="Einheit"
                value={unit?.id}
                options={options.map((u) => ({ id: u.id, name: unitLabel(u) }))}
                onChange={(v) => update({ unitId: v })}
                allLabel="Einheit wählen"
              />
            </div>
            <div className="flex items-center gap-1" role="group" aria-label="Jahr">
              <Button variant="outline" size="icon" aria-label={`Jahr ${year - 1}`} onClick={() => showYear(year - 1)}>
                <ChevronLeftIcon />
              </Button>
              <span className="min-w-14 text-center font-medium tabular-nums" aria-live="polite">
                {year}
              </span>
              <Button variant="outline" size="icon" aria-label={`Jahr ${year + 1}`} onClick={() => showYear(year + 1)}>
                <ChevronRightIcon />
              </Button>
            </div>
            <Button variant="outline" onClick={() => showYear(currentYear)} disabled={year === currentYear}>
              Heute
            </Button>
            {unit && (
              <Button variant="outline" onClick={() => setSubscribing(true)}>
                <CalendarPlusIcon />
                Abonnieren
              </Button>
            )}
          </div>
          <p className="mb-3 flex gap-4 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <span className="inline-block h-3 w-5 rounded border-2 border-primary" /> Geplant
            </span>
            <span className="inline-flex items-center gap-1">
              <span className="inline-block h-3 w-5 rounded bg-primary" /> Abgerechnet
            </span>
          </p>

          {calendar.isLoading && <p className="text-muted-foreground">Wird geladen …</p>}
          {calendar.isError && <p className="text-destructive">Der Kalender konnte nicht geladen werden.</p>}
          {unit && data && (
            <CalendarGrid
              months={monthRange(data.from, data.to)}
              year={data.year}
              currentMonth={currentMonth}
              bookings={bookings}
              onOpen={(bookingId) => setDialog({ bookingId })}
            />
          )}
        </>
      )}
      {subscribing && unit && (
        <SubscriptionDialog unitId={unit.id} unitName={unitLabel(unit)} onClose={() => setSubscribing(false)} />
      )}
      {dialog && (dialog.bookingId === null || openBooking) && (
        <BookingDialog
          key={dialog.bookingId ?? 'new'}
          booking={dialog.bookingId === null ? null : openBooking!}
          defaultUnitId={unit?.id}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  )
}
