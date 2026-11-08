import type { CalendarBooking } from './api'

export function daysIn(month: string) {
  const [y, m] = month.split('-').map(Number)
  return new Date(Date.UTC(y, m, 0)).getUTCDate()
}

function dayOf(iso: string) {
  return Number(iso.slice(8, 10))
}

/** First and last booked night of the stay within the month (1-based days), or null without a night in it. */
export function nightsInMonth(b: Pick<CalendarBooking, 'arrival' | 'departure'>, month: string): [number, number] | null {
  const first = `${month}-01`
  const last = `${month}-${String(daysIn(month)).padStart(2, '0')}`
  if (b.departure <= first || b.arrival > last) return null
  const start = b.arrival < first ? 1 : dayOf(b.arrival)
  const end = b.departure > last ? daysIn(month) : dayOf(b.departure) - 1
  return end >= start ? [start, end] : null
}

/** "2026-11" plus delta months, e.g. shiftMonth("2026-12", 1) = "2027-01". */
export function shiftMonth(month: string, delta: number) {
  const [y, m] = month.split('-').map(Number)
  const d = new Date(Date.UTC(y, m - 1 + delta, 1))
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
}

/** Months from `from` to `to`, both inclusive, as "2026-11". */
export function monthRange(from: string, to: string) {
  const months: string[] = []
  for (let m = from; m <= to; m = shiftMonth(m, 1)) months.push(m)
  return months
}
