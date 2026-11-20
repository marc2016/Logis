import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Booking, BookingFilter, BookingPage } from './api'

/** Address parameter of the review mode on the booking page (design D1 of booking-review-queue). */
export const REVIEW_PARAM = 'pruefen'
/** The list's filters that also limit the queue. */
const FILTER_KEYS = ['propertyId', 'unitId', 'status', 'from', 'to', 'q', 'agencyId', 'kind'] as const
/** The API's largest page; more bookings to review are counted but not reached by „Weiter“. */
const QUEUE_SIZE = 100

/** Only the list's filters, without paging, sorting or the review flag. */
export function queueParams(params: URLSearchParams): URLSearchParams {
  const result = new URLSearchParams()
  FILTER_KEYS.forEach((k) => {
    const v = params.get(k)
    if (v) result.set(k, v)
  })
  return result
}

/** Address of a booking in the review mode, keeping the queue's filters. */
export function reviewPath(id: number, filters: URLSearchParams) {
  const params = new URLSearchParams(filters)
  params.set(REVIEW_PARAM, '1')
  return `/ferienvermietung/buchungen/${id}?${params}`
}

/** Start of the review mode for these filters. */
export function reviewStartPath(filters: URLSearchParams) {
  const search = filters.toString()
  return `/ferienvermietung/pruefen${search ? `?${search}` : ''}`
}

/** The booking list with these filters. */
export function listPath(filters: URLSearchParams) {
  const search = filters.toString()
  return `/ferienvermietung/buchungen${search ? `?${search}` : ''}`
}

function filterOf(filters: URLSearchParams, size: number): BookingFilter {
  const num = (k: string) => (filters.get(k) ? Number(filters.get(k)) : undefined)
  return {
    propertyId: num('propertyId'),
    unitId: num('unitId'),
    agencyId: num('agencyId'),
    status: (filters.get('status') ?? undefined) as BookingFilter['status'],
    kind: (filters.get('kind') ?? undefined) as BookingFilter['kind'],
    from: filters.get('from') ?? undefined,
    to: filters.get('to') ?? undefined,
    q: filters.get('q') ?? undefined,
    review: true,
    sort: 'arrival,asc',
    size,
  }
}

function query(filter: BookingFilter) {
  const params = new URLSearchParams()
  Object.entries(filter).forEach(([k, v]) => {
    if (v !== undefined && v !== '' && v !== false) params.set(k, String(v))
  })
  return `/api/vacationrental/bookings?${params}`
}

/** Queue order: arrival, then id, so that the order is the same however the API breaks ties. */
function compare(a: Pick<Booking, 'arrival' | 'id'>, b: Pick<Booking, 'arrival' | 'id'>) {
  return a.arrival.localeCompare(b.arrival) || a.id - b.id
}

export function sortQueue(items: Booking[]) {
  return [...items].sort(compare)
}

/** The first booking after `current` in queue order, also when `current` has left the queue. */
export function nextAfter(items: Booking[], current: Pick<Booking, 'arrival' | 'id'>) {
  return sortQueue(items).find((b) => compare(b, current) > 0)
}

export function previousBefore(items: Booking[], current: Pick<Booking, 'arrival' | 'id'>) {
  return sortQueue(items)
    .reverse()
    .find((b) => compare(b, current) < 0)
}

/** The bookings to review that match the list's filters, live (design D1). */
export function useReviewQueue(filters: URLSearchParams, enabled = true) {
  const filter = filterOf(filters, QUEUE_SIZE)
  return useQuery({
    queryKey: ['vacationrental', 'bookings', 'review-queue', filter] as const,
    queryFn: async () => {
      const page = await api<BookingPage>(query(filter))
      return { items: sortQueue(page.items), total: page.totalItems }
    },
    enabled,
  })
}

/** Only the number of bookings to review for these filters, for „Prüfen (n)“. */
export function useReviewCount(filters: URLSearchParams) {
  const filter = filterOf(filters, 1)
  return useQuery({
    queryKey: ['vacationrental', 'bookings', 'review-count', filter] as const,
    queryFn: async () => (await api<BookingPage>(query(filter))).totalItems,
  })
}
