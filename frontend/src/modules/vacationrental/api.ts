import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { ShareRole } from '@/core/ledger/api'

export type Channel = 'AIRBNB' | 'BOOKING_COM' | 'DIRECT' | 'OTHER'
export type BookingStatus = 'PLANNED' | 'SETTLED' | 'CANCELLED'
export type ReviewState = 'NONE' | 'NEW' | 'CHANGE'

export const channelLabel: Record<Channel, string> = {
  AIRBNB: 'Airbnb',
  BOOKING_COM: 'Booking.com',
  DIRECT: 'Direkt',
  OTHER: 'Sonstige',
}

export const statusLabel: Record<BookingStatus, string> = {
  PLANNED: 'Geplant',
  SETTLED: 'Abgerechnet',
  CANCELLED: 'Storniert',
}

/** Name is visible to everyone with access; the other fields only to editors (null otherwise). */
export interface Guest {
  name: string | null
  street: string | null
  postalCode: string | null
  city: string | null
  country: string | null
  phone: string | null
  email: string | null
}

export const EMPTY_GUEST: Guest = { name: null, street: null, postalCode: null, city: null, country: null, phone: null, email: null }

export interface UnitRef {
  id: number
  name: string
  propertyId: number
  propertyName: string
  propertyMultiUnit: boolean
}

/** A change proposed by a mail; null fields stay as they are. */
export interface BookingProposal {
  type: 'CHANGE' | 'CANCELLATION'
  arrival: string | null
  departure: string | null
  adults: number | null
  children: number | null
  pets: number | null
  expectedRent: string | null
  guest: Guest | null
}

export interface Booking {
  id: number
  unit: UnitRef
  arrival: string
  departure: string
  nights: number
  externalRef: string | null
  channel: Channel | null
  adults: number
  children: number
  expectedRent: string | null
  status: BookingStatus
  statementId: number | null
  guest: Guest | null
  guestDeleted: boolean
  canEdit: boolean
  version: number
  agency: string | null
  pets: number
  reviewState: ReviewState
  proposal: BookingProposal | null
}

export interface BookingPage {
  items: Booking[]
  page: number
  size: number
  totalItems: number
}

export interface RentalUnit {
  id: number
  name: string
  propertyId: number
  propertyName: string
  propertyMultiUnit: boolean
  archived: boolean
  myRole: ShareRole
}

export interface StatementLine {
  booking: Booking
  gross: string
  commission: string
  cleaning: string
  net: string
}

export interface Statement {
  id: number
  unit: UnitRef
  provider: string
  periodFrom: string
  periodTo: string
  statementDate: string
  payout: string
  lineCount: number
  canEdit: boolean
  version: number
  lines: StatementLine[]
}

export interface CalendarBooking {
  id: number
  arrival: string
  departure: string
  status: BookingStatus
  guestName: string | null
  externalRef: string | null
  reviewState: ReviewState
}

export interface CalendarUnit {
  id: number
  name: string
  archived: boolean
  canEdit: boolean
  bookings: CalendarBooking[]
}

export interface Calendar {
  propertyId: number
  propertyName: string
  month: string
  units: CalendarUnit[]
}

export interface MonthFigures {
  month: number
  nights: number
  occupancy: number
  settledNet: string
  plannedRent: string
}

export interface Overview {
  unitId: number
  year: number
  months: MonthFigures[]
  total: MonthFigures
}

export interface BookingFilter {
  propertyId?: number
  unitId?: number
  status?: BookingStatus
  from?: string
  to?: string
  q?: string
  /** Only bookings and changes awaiting review. */
  review?: boolean
  sort?: string
  page?: number
  size?: number
}

// --- Booking mails (labels for the core inbox) ----------------------------------------------------

export const mailKindLabel: Record<string, string> = {
  CONFIRMATION: 'Bestätigung',
  SHORT_NOTICE: 'Last-Minute',
  CHANGE: 'Änderung',
  CANCELLATION: 'Stornierung',
}

export const mailResultLabel: Record<string, string> = {
  BOOKING_CREATED: 'Buchung angelegt',
  CHANGE_PROPOSED: 'Änderung vorgeschlagen',
  ATTACHED: 'Angehängt',
  UNASSIGNED_UNIT: 'Einheit zuordnen',
  NOT_MATCHED: 'Nicht zugeordnet',
  PERIOD_TAKEN: 'Zeitraum belegt',
  DISCARDED: 'Verworfen',
}

/** Display name of a unit: the property alone for single-unit properties. */
export function unitLabel(unit: Pick<UnitRef, 'name' | 'propertyName' | 'propertyMultiUnit'>) {
  return unit.propertyMultiUnit ? `${unit.propertyName} · ${unit.name}` : unit.propertyName
}

/** Distinct properties of the rental units, sorted by name. */
export function rentalProperties(units: RentalUnit[]) {
  const map = new Map<number, string>()
  units.forEach((u) => map.set(u.propertyId, u.propertyName))
  return [...map.entries()].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name, 'de'))
}

export const rentalKeys = {
  all: ['vacationrental'] as const,
  units: ['vacationrental', 'units'] as const,
  bookings: (filter: BookingFilter) => ['vacationrental', 'bookings', filter] as const,
  booking: (id: number) => ['vacationrental', 'booking', id] as const,
  statements: (unitId?: number) => ['vacationrental', 'statements', unitId ?? null] as const,
  statement: (id: number) => ['vacationrental', 'statement', id] as const,
  providers: (unitId: number) => ['vacationrental', 'providers', unitId] as const,
  calendar: (propertyId: number, month: string) => ['vacationrental', 'calendar', propertyId, month] as const,
  overview: (unitId: number, year: number) => ['vacationrental', 'overview', unitId, year] as const,
}

function query(params: Record<string, string | number | boolean | undefined | null>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '' && v !== false) search.set(k, String(v))
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

export function useRentalUnits() {
  return useQuery({ queryKey: rentalKeys.units, queryFn: () => api<RentalUnit[]>('/api/vacationrental/units') })
}

export function useBookings(filter: BookingFilter) {
  return useQuery({
    queryKey: rentalKeys.bookings(filter),
    queryFn: () => api<BookingPage>(`/api/vacationrental/bookings${query({ ...filter })}`),
  })
}

export function useBooking(id: number | null) {
  return useQuery({
    queryKey: rentalKeys.booking(id ?? 0),
    queryFn: () => api<Booking>(`/api/vacationrental/bookings/${id}`),
    enabled: id !== null,
  })
}

export function useStatements(unitId?: number) {
  return useQuery({
    queryKey: rentalKeys.statements(unitId),
    queryFn: () => api<Statement[]>(`/api/vacationrental/statements${query({ unitId })}`),
  })
}

export function useStatement(id: number | null) {
  return useQuery({
    queryKey: rentalKeys.statement(id ?? 0),
    queryFn: () => api<Statement>(`/api/vacationrental/statements/${id}`),
    enabled: id !== null,
  })
}

export function useProviders(unitId: number | null) {
  return useQuery({
    queryKey: rentalKeys.providers(unitId ?? 0),
    queryFn: () => api<string[]>(`/api/vacationrental/statements/providers?unitId=${unitId}`),
    enabled: unitId !== null,
  })
}

export function useCalendar(propertyId: number | null, month: string) {
  return useQuery({
    queryKey: rentalKeys.calendar(propertyId ?? 0, month),
    queryFn: () => api<Calendar>(`/api/vacationrental/occupancy/calendar?propertyId=${propertyId}&month=${month}`),
    enabled: propertyId !== null,
  })
}

export function useOverview(unitId: number | null, year: number) {
  return useQuery({
    queryKey: rentalKeys.overview(unitId ?? 0, year),
    queryFn: () => api<Overview>(`/api/vacationrental/occupancy/overview?unitId=${unitId}&year=${year}`),
    enabled: unitId !== null,
  })
}

/** Bookings, calendar, statements and figures depend on each other: refresh the whole module. */
export function useInvalidateRental() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: rentalKeys.all })
}
