import { cn } from '@/lib/utils'
import { daysIn, nightsInMonth } from './calendar'
import { statusLabel, type Calendar } from './api'

const WEEKDAYS = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa']

interface Props {
  calendar: Calendar
  onOpen: (bookingId: number) => void
}

/** Month view: one row per unit, one bar per stay over its nights. Planned stays are outlined, settled ones filled. */
export function CalendarGrid({ calendar, onOpen }: Props) {
  const days = daysIn(calendar.month)
  const [y, m] = calendar.month.split('-').map(Number)
  const template = `minmax(7rem, 10rem) repeat(${days}, minmax(1.75rem, 1fr))`

  return (
    <div className="overflow-x-auto rounded-lg border">
      <div className="grid min-w-max text-xs" style={{ gridTemplateColumns: template }} role="grid" aria-label={`Belegung ${calendar.propertyName}`}>
        <div className="sticky left-0 z-10 border-b bg-background p-2 font-medium" role="columnheader">
          Einheit
        </div>
        {Array.from({ length: days }, (_, i) => {
          const weekday = new Date(Date.UTC(y, m - 1, i + 1)).getUTCDay()
          return (
            <div
              key={i}
              role="columnheader"
              className={cn('border-b border-l p-1 text-center', (weekday === 0 || weekday === 6) && 'bg-muted/50')}
            >
              <div className="font-medium">{i + 1}</div>
              <div className="text-muted-foreground">{WEEKDAYS[weekday]}</div>
            </div>
          )
        })}
        {calendar.units.map((unit, row) => (
          <div key={unit.id} className="contents" role="row" aria-label={unit.name}>
            <div
              className="sticky left-0 z-10 truncate border-b bg-background p-2 font-medium"
              style={{ gridRow: row + 2, gridColumn: 1 }}
              role="rowheader"
            >
              {unit.name}
            </div>
            {Array.from({ length: days }, (_, i) => (
              <div key={i} className="border-b border-l" style={{ gridRow: row + 2, gridColumn: i + 2 }} />
            ))}
            {unit.bookings.filter((b) => b.status !== 'CANCELLED').map((b) => {
              const span = nightsInMonth(b, calendar.month)
              if (!span) return null
              const label = b.guestName ?? b.externalRef ?? 'Buchung'
              return (
                <button
                  key={b.id}
                  type="button"
                  onClick={() => onOpen(b.id)}
                  style={{ gridRow: row + 2, gridColumn: `${span[0] + 1} / ${span[1] + 2}` }}
                  className={cn(
                    'z-0 my-1 truncate rounded px-1.5 text-left leading-6',
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
        ))}
      </div>
    </div>
  )
}
