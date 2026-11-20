import type { Booking } from '../api'

/** Pure helpers of the start page tiles (kept apart from the components for fast refresh). */

export function plusDays(iso: string, days: number) {
  const [y, m, d] = iso.split('-').map(Number)
  const date = new Date(Date.UTC(y, m - 1, d + days))
  return date.toISOString().slice(0, 10)
}

export interface StayEvent {
  date: string
  arrival: boolean
  booking: Booking
}

/** Arrivals and departures in the window, by date; departures first on the same day (design D7). */
export function stayEvents(bookings: Booking[], from: string, to: string): StayEvent[] {
  const events: StayEvent[] = []
  bookings
    .filter((b) => b.status !== 'CANCELLED')
    .forEach((b) => {
      if (b.arrival >= from && b.arrival <= to) events.push({ date: b.arrival, arrival: true, booking: b })
      if (b.departure >= from && b.departure <= to) events.push({ date: b.departure, arrival: false, booking: b })
    })
  return events.sort((a, b) => a.date.localeCompare(b.date) || Number(a.arrival) - Number(b.arrival))
}

/** Change in percent, or null when the previous year had nothing to compare with. */
export function change(current: string, previous: string): number | null {
  const before = Number(previous)
  if (before === 0) return null
  return Math.round(((Number(current) - before) / Math.abs(before)) * 100)
}
