import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { ChevronDownIcon, ChevronUpIcon, PlusIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/PageHeader'
import { todayIso } from '@/core/ledger/transactions/format'
import { BookingDialog } from '../BookingDialog'
import { CalendarGrid } from '../CalendarGrid'
import { IdSelect } from '../RentalFilters'
import { rentalProperties, useBooking, useCalendarMonths, useRentalUnits } from '../api'

const monthName = new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric', timeZone: 'UTC' })
/** Months shown right away: the start month and the two after it. */
const INITIAL_MONTHS = 3

function monthTitle(month: string) {
  const [y, m] = month.split('-').map(Number)
  return monthName.format(new Date(Date.UTC(y, m - 1, 1)))
}

/**
 * Whether the element is near the viewport (400px margin); always false without IntersectionObserver.
 * A new `version` (the number of loaded months) observes afresh, so a result from before the last
 * render never counts and loading stops as soon as the element has moved out of reach.
 */
function useNearViewport(version: number) {
  // A callback ref: the element only appears once the first month has loaded.
  const [element, setElement] = useState<HTMLDivElement | null>(null)
  const [nearAt, setNearAt] = useState<number | null>(null)
  useEffect(() => {
    if (!element || typeof IntersectionObserver === 'undefined') return
    const observer = new IntersectionObserver((entries) => setNearAt(entries.some((e) => e.isIntersecting) ? version : null), {
      rootMargin: '400px',
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [element, version])
  return [setElement, nearAt === version] as const
}

/** Months of one property one below the other, loading further months while scrolling in both directions. */
export function KalenderPage() {
  const [params, setParams] = useSearchParams()
  const { data: units = [], isLoading: unitsLoading } = useRentalUnits()
  const properties = useMemo(() => rentalProperties(units), [units])
  const propertyId = params.get('propertyId') ? Number(params.get('propertyId')) : (properties[0]?.id ?? null)
  const currentMonth = todayIso().slice(0, 7)
  const startMonth = params.get('month') ?? currentMonth
  const months = useCalendarMonths(propertyId, startMonth)
  const [dialog, setDialog] = useState<{ bookingId: number | null } | null>(null)
  const { data: openBooking } = useBooking(dialog?.bookingId ?? null)
  const canRecord = units.some((u) => u.propertyId === propertyId && !u.archived && u.myRole !== 'VIEWER')
  const pages = months.data?.pages ?? []

  // Fill the screen: the start month and the next two.
  useEffect(() => {
    if (months.hasNextPage && !months.isFetching && pages.length > 0 && pages.length < INITIAL_MONTHS) {
      void months.fetchNextPage()
    }
  }, [months, pages.length])

  // Earlier months are inserted above: keep what the user looks at in place.
  const heightBeforePrevious = useRef<number | null>(null)
  const firstMonth = pages[0]?.month
  useLayoutEffect(() => {
    const before = heightBeforePrevious.current
    if (before !== null) {
      heightBeforePrevious.current = null
      const scroller = document.scrollingElement
      if (scroller) window.scrollBy(0, scroller.scrollHeight - before)
    }
  }, [firstMonth])

  function loadEarlier() {
    if (months.isFetchingPreviousPage || !months.hasPreviousPage) return
    heightBeforePrevious.current = document.scrollingElement?.scrollHeight ?? null
    void months.fetchPreviousPage()
  }

  function loadLater() {
    if (months.isFetchingNextPage || !months.hasNextPage) return
    void months.fetchNextPage()
  }

  // Keep loading while the end is in view; the start only after the user scrolled, so the start month stays on top.
  const [topRef, topNear] = useNearViewport(pages.length)
  const [bottomRef, bottomNear] = useNearViewport(pages.length)
  const [scrolled, setScrolled] = useState(false)
  useEffect(() => {
    const onScroll = () => setScrolled(true)
    window.addEventListener('scroll', onScroll, { passive: true, once: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [startMonth])
  // A failed request stops automatic loading; the buttons still allow retrying.
  const ready = pages.length >= INITIAL_MONTHS && !months.isFetching && !months.isError
  useEffect(() => {
    if (ready && bottomNear) loadLater()
    else if (ready && topNear && scrolled) loadEarlier()
  })

  function update(changes: Record<string, string | number | undefined>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === undefined ? next.delete(k) : next.set(k, String(v))))
    setParams(next, { replace: true })
  }

  function today() {
    update({ month: undefined })
    window.scrollTo({ top: 0 })
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
            <Button variant="outline" onClick={today} disabled={startMonth === currentMonth && pages[0]?.month === currentMonth}>
              Heute
            </Button>
          </div>
          <p className="mb-3 flex gap-4 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <span className="inline-block h-3 w-5 rounded border-2 border-primary" /> Geplant
            </span>
            <span className="inline-flex items-center gap-1">
              <span className="inline-block h-3 w-5 rounded bg-primary" /> Abgerechnet
            </span>
          </p>

          {pages.length > 0 && (
            <div ref={topRef} className="mb-4">
              <Button variant="ghost" size="sm" disabled={months.isFetchingPreviousPage} onClick={loadEarlier}>
                <ChevronUpIcon />
                Frühere Monate
              </Button>
            </div>
          )}
          {months.isLoading && <p className="text-muted-foreground">Wird geladen …</p>}
          <div className="space-y-8">
            {pages.map((calendar) => (
              <section key={calendar.month} aria-label={monthTitle(calendar.month)}>
                <h2 className="mb-2 text-lg font-semibold">{monthTitle(calendar.month)}</h2>
                <CalendarGrid calendar={calendar} onOpen={(bookingId) => setDialog({ bookingId })} />
              </section>
            ))}
          </div>
          {pages.length > 0 && (
            <div ref={bottomRef} className="mt-4">
              <Button variant="ghost" size="sm" disabled={months.isFetchingNextPage} onClick={loadLater}>
                <ChevronDownIcon />
                Spätere Monate
              </Button>
            </div>
          )}
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
