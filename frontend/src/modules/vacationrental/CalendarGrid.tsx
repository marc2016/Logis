import { useEffect, useRef } from 'react'
import { cn } from '@/lib/utils'
import { daysIn, nightsInMonth } from './calendar'
import { kindLabel, statusLabel, type CalendarBooking } from './api'

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

/** Width of the sticky month column (5rem), which covers the days scrolled under it. */
const MONTH_COLUMN = 80

/**
 * Scrolls the calendar's own frame sideways so that the cell is centred, only if it is not fully visible; the page
 * itself does not move (design D3 of calendar-today-weekdays).
 */
function scrollSideways(frame: HTMLElement, cell: HTMLElement) {
  const f = frame.getBoundingClientRect()
  const c = cell.getBoundingClientRect()
  const left = f.left + MONTH_COLUMN
  if (c.left >= left && c.right <= f.right) return
  const centre = left + (f.right - left) / 2
  frame.scrollTo?.({ left: frame.scrollLeft + (c.left + c.width / 2 - centre) })
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
  /** Scroll today's cell into view, e.g. when the current year opens or after „Heute“. */
  scrollToToday?: boolean
  /** Stays of the shown unit over all months. */
  bookings: CalendarBooking[]
  onOpen: (bookingId: number) => void
}

/**
 * One unit over a year: one row per month, one column per day (1 to 31), one bar per stay over its nights.
 * Planned stays are outlined, settled ones filled; the neighbouring months of other years are muted.
 */
export function CalendarGrid({ months, year, currentMonth, today, scrollToToday = false, bookings, onOpen }: Props) {
  const visible = bookings.filter((b) => b.status !== 'CANCELLED')
  const frame = useRef<HTMLDivElement>(null)
  const todayCell = useRef<HTMLDivElement>(null)
  // Only when the year changes to the current one, not after every reload of its bookings (design D3).
  useEffect(() => {
    if (scrollToToday && frame.current && todayCell.current) scrollSideways(frame.current, todayCell.current)
  }, [scrollToToday, year])
  return (
    <div ref={frame} className="overflow-x-auto rounded-lg border">
      <div
        className="grid w-max min-w-full text-xs"
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
                const isToday = weekday !== null && `${month}-${String(i + 1).padStart(2, '0')}` === today
                return (
                  <div
                    key={i}
                    ref={isToday ? todayCell : undefined}
                    role={weekday === null ? undefined : 'gridcell'}
                    aria-label={weekday === null ? undefined : `${dayLong.format(monthDate(month, i + 1))}${isToday ? ', heute' : ''}`}
                    aria-current={isToday ? 'date' : undefined}
                    data-today={isToday ? 'true' : undefined}
                    className={cn(
                      'flex h-12 flex-col items-start gap-0.5 border-b border-l px-0.5 pt-0.5 text-[10px] leading-none whitespace-nowrap text-muted-foreground',
                      weekday === null ? 'bg-muted' : (weekday === 0 || weekday === 6) && 'bg-muted/50',
                      isToday && 'ring-1 ring-primary ring-inset',
                    )}
                    style={{ gridRow: row, gridColumn: i + 2 }}
                  >
                    {weekday !== null && (
                      <>
                        <span className={cn(isToday && 'rounded-sm bg-primary px-0.5 font-semibold text-primary-foreground')}>{i + 1}</span>
                        {/* Under the number, so that the columns keep their width (design D1 of calendar-today-weekdays). */}
                        <span className="text-[9px] opacity-70">{WEEKDAYS[weekday]}</span>
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
                      'z-10 mx-px mt-6 mb-0.5 truncate rounded px-1 text-left leading-5',
                      owner
                        ? 'border-2 border-muted-foreground/40 bg-muted text-muted-foreground'
                        : b.status === 'SETTLED'
                          ? 'bg-primary text-primary-foreground'
                          : 'border-2 border-primary bg-background',
                      b.reviewState !== 'NONE' && 'border-2 border-dashed border-amber-600',
                    )}
                    aria-label={owner ? label : `${label}, ${state}${b.reviewState !== 'NONE' ? ', zu prüfen' : ''}`}
                    data-review={b.reviewState}
                    data-status={b.status}
                    data-kind={b.kind}
                    title={owner ? label : `${label} (${state})`}
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
