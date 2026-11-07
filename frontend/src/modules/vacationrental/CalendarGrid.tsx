import { cn } from '@/lib/utils'
import { daysIn, nightsInMonth } from './calendar'
import { statusLabel, type Calendar } from './api'

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
  /** Loaded months in order, each already reduced to one unit. */
  months: Calendar[]
  unitId: number
  onOpen: (bookingId: number) => void
}

/**
 * One unit over many months: one row per month, one column per day (1 to 31), one bar per stay over its nights.
 * Planned stays are outlined, settled ones filled.
 */
export function CalendarGrid({ months, unitId, onOpen }: Props) {
  return (
    <div className="overflow-x-auto rounded-lg border">
      <div
        className="grid min-w-full text-xs"
        style={{ gridTemplateColumns: `5rem repeat(${MAX_DAYS}, minmax(1.75rem, 1fr))` }}
        role="grid"
        aria-label="Belegung"
      >
        {months.map((calendar, index) => {
          const row = index + 1
          const days = daysIn(calendar.month)
          const bookings = calendar.units.find((u) => u.id === unitId)?.bookings ?? []
          return (
            <div key={calendar.month} className="contents" role="row" aria-label={monthTitle(calendar.month)}>
              <div
                className="sticky left-0 z-20 flex items-center border-b bg-background px-2 font-medium whitespace-nowrap"
                style={{ gridRow: row, gridColumn: 1 }}
                role="rowheader"
              >
                {monthShort.format(monthDate(calendar.month))}
              </div>
              {Array.from({ length: MAX_DAYS }, (_, i) => {
                const weekday = i < days ? monthDate(calendar.month, i + 1).getUTCDay() : null
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
              {bookings.filter((b) => b.status !== 'CANCELLED').map((b) => {
                const span = nightsInMonth(b, calendar.month)
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
