import { cn } from '@/lib/utils'
import { daysIn, nightsInMonth } from './calendar'
import { CANCELLATION_REPORTED, kindLabel, statusLabel, type CalendarBooking } from './api'

const MAX_DAYS = 31
const monthShort = new Intl.DateTimeFormat('de-DE', { month: 'short', year: 'numeric', timeZone: 'UTC' })
const monthLong = new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric', timeZone: 'UTC' })
const dayLong = new Intl.DateTimeFormat('de-DE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
/** Two letters, by `getUTCDay()`; Intl's short weekdays carry a dot („Fr.“) (design D1 of calendar-today-weekdays). */
const WEEKDAYS = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa']

function monthDate(month: string, day = 1) {
  const [y, m] = month.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, day))
}

function monthTitle(month: string) {
  return monthLong.format(monthDate(month))
}

const monthName = new Intl.DateTimeFormat('de-DE', { month: 'short', timeZone: 'UTC' })

/** „Nov 26“ for narrow calendars (design D2 of calendar-fit-width). */
function monthTiny(month: string) {
  return `${monthName.format(monthDate(month)).replace('.', '')} ${month.slice(2, 4)}`
}

interface Props {
  /** Months to show in order, e.g. December of the previous year to January of the next. */
  months: string[]
  /** The year the calendar shows; months of other years are set apart. */
  year: number
  /** Current month ("2026-11"), marked in its row header. */
  currentMonth: string
  /** Today ("2026-11-20"), marked in its cell. */
  today: string
  /** Stays of the shown unit over all months. */
  bookings: CalendarBooking[]
  onOpen: (bookingId: number) => void
}

/**
 * One unit over a year: one row per month, one column per day (1 to 31), one bar per stay over its nights.
 * Planned stays are outlined, settled ones filled; the neighbouring months of other years are muted.
 */
export function CalendarGrid({ months, year, currentMonth, today, bookings, onOpen }: Props) {
  const visible = bookings.filter((b) => b.status !== 'CANCELLED')
  // Fits the width and never scrolls sideways; narrow calendars show less in each day (design of calendar-fit-width).
  return (
    <div className="@container/calendar overflow-hidden rounded-lg border" data-testid="calendar-frame">
      <div
        className="grid w-full text-xs [--month-column:2.75rem] @min-[46rem]/calendar:[--month-column:5rem]"
        style={{ gridTemplateColumns: `var(--month-column) repeat(${MAX_DAYS}, minmax(0, 1fr))` }}
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
                  'flex items-center border-b bg-background px-1 text-[10px] font-medium whitespace-nowrap @min-[46rem]/calendar:px-2 @min-[46rem]/calendar:text-xs',
                  outside && 'text-muted-foreground italic',
                  current && 'border-l-4 border-l-primary text-primary',
                )}
                style={{ gridRow: row, gridColumn: 1 }}
                role="rowheader"
                aria-label={monthTitle(month)}
              >
                <span className="@min-[46rem]/calendar:hidden" aria-hidden="true">
                  {monthTiny(month)}
                </span>
                <span className="hidden @min-[46rem]/calendar:inline" aria-hidden="true">
                  {monthShort.format(monthDate(month))}
                </span>
              </div>
              {Array.from({ length: MAX_DAYS }, (_, i) => {
                const weekday = i < days ? monthDate(month, i + 1).getUTCDay() : null
                const isToday = weekday !== null && `${month}-${String(i + 1).padStart(2, '0')}` === today
                return (
                  <div
                    key={i}
                    role={weekday === null ? undefined : 'gridcell'}
                    aria-label={weekday === null ? undefined : `${dayLong.format(monthDate(month, i + 1))}${isToday ? ', heute' : ''}`}
                    aria-current={isToday ? 'date' : undefined}
                    data-today={isToday ? 'true' : undefined}
                    className={cn(
                      'flex h-12 min-w-0 flex-col items-center gap-0.5 overflow-hidden border-b border-l pt-0.5 text-[8px] leading-none tracking-[-0.06em] whitespace-nowrap text-muted-foreground @min-[46rem]/calendar:items-start @min-[46rem]/calendar:px-0.5 @min-[46rem]/calendar:text-[10px] @min-[46rem]/calendar:tracking-normal',
                      weekday === null ? 'bg-muted' : (weekday === 0 || weekday === 6) && 'bg-muted/50',
                      isToday && 'ring-1 ring-primary ring-inset',
                    )}
                    style={{ gridRow: row, gridColumn: i + 2 }}
                  >
                    {weekday !== null && (
                      <>
                        <span className={cn(isToday && 'rounded-sm bg-primary px-0.5 font-semibold text-primary-foreground')}>{i + 1}</span>
                        {/* Under the number, so that the columns keep their width (design D1 of calendar-today-weekdays). */}
                        {/* Narrow calendars show the weekday only by the weekend background (design D2 of calendar-fit-width). */}
                        <span className="hidden text-[9px] opacity-70 @min-[46rem]/calendar:inline" data-weekday>
                          {WEEKDAYS[weekday]}
                        </span>
                      </>
                    )}
                  </div>
                )
              })}
              {visible.map((b) => {
                const span = nightsInMonth(b, month)
                if (!span) return null
                const owner = b.kind === 'OWNER'
                const label = owner ? kindLabel.OWNER : (b.guestName ?? b.externalRef ?? 'Buchung')
                const state = owner ? kindLabel.OWNER : statusLabel[b.status]
                return (
                  <button
                    key={b.id}
                    type="button"
                    onClick={() => onOpen(b.id)}
                    style={{ gridRow: row, gridColumn: `${span[0] + 1} / ${span[1] + 2}` }}
                    className={cn(
                      'z-10 mx-px mt-6 mb-0.5 min-w-0 truncate rounded px-px text-left text-[10px] leading-5 @min-[46rem]/calendar:px-1 @min-[46rem]/calendar:text-xs',
                      owner
                        ? 'border-2 border-muted-foreground/40 bg-muted text-muted-foreground'
                        : b.status === 'SETTLED'
                          ? 'bg-primary text-primary-foreground'
                          : 'border-2 border-primary bg-background',
                      b.reviewState !== 'NONE' && 'border-2 border-dashed border-amber-600',
                      b.cancellationReported && 'line-through',
                    )}
                    aria-label={
                      owner
                        ? label
                        : `${label}, ${state}${b.reviewState !== 'NONE' ? ', zu prüfen' : ''}${b.cancellationReported ? `, ${CANCELLATION_REPORTED}` : ''}`
                    }
                    data-review={b.reviewState}
                    data-cancellation-reported={b.cancellationReported ? 'true' : undefined}
                    data-status={b.status}
                    data-kind={b.kind}
                    title={owner ? label : `${label} (${b.cancellationReported ? `${state}, ${CANCELLATION_REPORTED}` : state})`}
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
