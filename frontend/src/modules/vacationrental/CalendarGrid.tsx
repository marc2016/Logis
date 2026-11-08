import { cn } from '@/lib/utils'
import { daysIn, nightsInMonth } from './calendar'
import { statusLabel, type CalendarBooking } from './api'

const MAX_DAYS = 31
const monthShort = new Intl.DateTimeFormat('de-DE', { month: 'short', year: 'numeric', timeZone: 'UTC' })
const monthLong = new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric', timeZone: 'UTC' })

function monthDate(month: string, day = 1) {
  const [y, m] = month.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, day))
}

function monthTitle(month: string) {
  return monthLong.format(monthDate(month))
}

interface Props {
  /** Months to show in order, e.g. December of the previous year to January of the next. */
  months: string[]
  /** The year the calendar shows; months of other years are set apart. */
  year: number
  /** Current month ("2026-11"), marked in its row header. */
  currentMonth: string
  /** Stays of the shown unit over all months. */
  bookings: CalendarBooking[]
  onOpen: (bookingId: number) => void
}

/**
 * One unit over a year: one row per month, one column per day (1 to 31), one bar per stay over its nights.
 * Planned stays are outlined, settled ones filled; the neighbouring months of other years are muted.
 */
export function CalendarGrid({ months, year, currentMonth, bookings, onOpen }: Props) {
  const visible = bookings.filter((b) => b.status !== 'CANCELLED')
  return (
    <div className="overflow-x-auto rounded-lg border">
      <div
        className="grid min-w-full text-xs"
        style={{ gridTemplateColumns: `5rem repeat(${MAX_DAYS}, minmax(1.75rem, 1fr))` }}
        role="grid"
        aria-label="Belegung"
      >
        {months.map((month, index) => {
          const row = index + 1
          const days = daysIn(month)
          const outside = !month.startsWith(`${year}-`)
          const current = month === currentMonth
          return (
            <div
              key={month}
              className={cn('contents', outside && '[&>*]:opacity-60')}
              role="row"
              aria-label={monthTitle(month)}
              data-outside={outside ? 'true' : undefined}
              aria-current={current ? 'date' : undefined}
            >
              <div
                className={cn(
                  'sticky left-0 z-20 flex items-center border-b bg-background px-2 font-medium whitespace-nowrap',
                  outside && 'text-muted-foreground italic',
                  current && 'border-l-4 border-l-primary text-primary',
                )}
                style={{ gridRow: row, gridColumn: 1 }}
                role="rowheader"
              >
                {monthShort.format(monthDate(month))}
              </div>
              {Array.from({ length: MAX_DAYS }, (_, i) => {
                const weekday = i < days ? monthDate(month, i + 1).getUTCDay() : null
                return (
                  <div
                    key={i}
                    className={cn(
                      'h-9 border-b border-l px-0.5 text-[10px] leading-none text-muted-foreground',
                      weekday === null ? 'bg-muted' : (weekday === 0 || weekday === 6) && 'bg-muted/50',
                    )}
                    style={{ gridRow: row, gridColumn: i + 2 }}
                  >
                    {weekday !== null && i + 1}
                  </div>
                )
              })}
              {visible.map((b) => {
                const span = nightsInMonth(b, month)
                if (!span) return null
                const label = b.guestName ?? b.externalRef ?? 'Buchung'
                return (
                  <button
                    key={b.id}
                    type="button"
                    onClick={() => onOpen(b.id)}
                    style={{ gridRow: row, gridColumn: `${span[0] + 1} / ${span[1] + 2}` }}
                    className={cn(
                      'z-10 mx-px mt-2.5 mb-0.5 truncate rounded px-1 text-left leading-5',
                      b.status === 'SETTLED' ? 'bg-primary text-primary-foreground' : 'border-2 border-primary bg-background',
                      b.reviewState !== 'NONE' && 'border-2 border-dashed border-amber-600',
                    )}
                    aria-label={`${label}, ${statusLabel[b.status]}${b.reviewState !== 'NONE' ? ', zu prüfen' : ''}`}
                    data-review={b.reviewState}
                    data-status={b.status}
                    title={`${label} (${statusLabel[b.status]})`}
                  >
                    {label}
                  </button>
                )
              })}
            </div>
          )
        })}
      </div>
    </div>
  )
}
