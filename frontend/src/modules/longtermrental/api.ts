import { ledgerKeys } from '@/core/ledger/api'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'

export type TenancyState = 'FUTURE' | 'RUNNING' | 'ENDED'

export const stateLabel: Record<TenancyState, string> = {
  FUTURE: 'künftig',
  RUNNING: 'laufend',
  ENDED: 'beendet',
}

export const TENANCY_DOCUMENT_TYPE = 'longtermrental.tenancy'

export interface UnitRef {
  id: number
  name: string
  propertyId: number
  propertyName: string
  propertyMultiUnit: boolean
}

/** The name is visible to everyone with access; the contact fields only to editors (null otherwise). */
export interface Tenant {
  id?: number | null
  name: string | null
  street: string | null
  postalCode: string | null
  city: string | null
  country: string | null
  phone: string | null
  email: string | null
}

export const EMPTY_TENANT: Tenant = { name: null, street: null, postalCode: null, city: null, country: null, phone: null, email: null }

/** A rent valid from a date: cold rent, advance payment for utilities and further items; amounts as "650.00". */
export interface Rent {
  id: number
  validFrom: string
  coldRent: string
  utilitiesAdvance: string
  others: { label: string; amount: string }[]
  total: string
}

export type RentInput = Pick<Rent, 'coldRent' | 'utilitiesAdvance' | 'others'> & { validFrom?: string }

export interface Tenancy {
  id: number
  unit: UnitRef
  startDate: string
  /** The last day of the tenancy, or null while it has no end. */
  endDate: string | null
  state: TenancyState
  /** Empty once the tenant data is deleted. */
  tenants: Tenant[]
  tenantsDeleted: boolean
  depositAmount: string | null
  depositReceivedOn: string | null
  currentRent: Rent | null
  rentTerms: Rent[]
  canEdit: boolean
  version: number
  /** Rent due up to this month minus paid; 0.00 when paid up (spec rent-payments). */
  arrears: string
  /** More paid than due. */
  credit: string
  /** A month is not fully paid after its third working day. */
  overdue: boolean
}

export interface RentMonth {
  /** „2027-05“ */
  month: string
  due: string
  paid: string
  open: string
  overdue: boolean
}

/** A payment; account and note only for editors. */
export interface RentPayment {
  id: number
  date: string
  amount: string
  accountId: number | null
  accountName: string | null
  note: string | null
  version: number
}

/** The rent account of a tenancy, newest month and payment first. */
export interface RentAccount {
  due: string
  paid: string
  arrears: string
  credit: string
  overdue: boolean
  canEdit: boolean
  months: RentMonth[]
  payments: RentPayment[]
}

/** A long-term unit: its running tenancy, or vacant since a day (null: never rented), and the next tenancy. */
export interface RentalUnit {
  unit: UnitRef
  canEdit: boolean
  tenancy: Tenancy | null
  vacantSince: string | null
  next: Tenancy | null
}

export interface TenancyFilter {
  propertyId?: number
  unitId?: number
  states: TenancyState[]
}

export function unitLabel(unit: Pick<UnitRef, 'name' | 'propertyName' | 'propertyMultiUnit'>) {
  return unit.propertyMultiUnit ? `${unit.propertyName} · ${unit.name}` : unit.propertyName
}

/** The tenants' names, or that their data is deleted. */
export function tenantNames(tenancy: Pick<Tenancy, 'tenants' | 'tenantsDeleted'>) {
  return tenancy.tenantsDeleted ? 'Mieterdaten gelöscht' : tenancy.tenants.map((t) => t.name).join(', ')
}

export const tenancyKeys = {
  all: ['longtermrental'] as const,
  units: ['longtermrental', 'units'] as const,
  list: (filter: TenancyFilter) => ['longtermrental', 'tenancies', filter] as const,
  one: (id: number) => ['longtermrental', 'tenancy', id] as const,
  rentAccount: (id: number) => ['longtermrental', 'rent-account', id] as const,
}

export const TENANCIES = '/api/longtermrental/tenancies'

export function useLongTermUnits() {
  return useQuery({ queryKey: tenancyKeys.units, queryFn: () => api<RentalUnit[]>('/api/longtermrental/units') })
}

export function useTenancies(filter: TenancyFilter) {
  const params = new URLSearchParams()
  if (filter.propertyId !== undefined) params.set('propertyId', String(filter.propertyId))
  if (filter.unitId !== undefined) params.set('unitId', String(filter.unitId))
  filter.states.forEach((s) => params.append('state', s))
  return useQuery({
    queryKey: tenancyKeys.list(filter),
    queryFn: () => api<Tenancy[]>(`${TENANCIES}${params.size ? `?${params}` : ''}`),
  })
}

export function useTenancy(id: number | null) {
  return useQuery({
    queryKey: tenancyKeys.one(id ?? 0),
    queryFn: () => api<Tenancy>(`${TENANCIES}/${id}`),
    enabled: id !== null,
    retry: false,
  })
}

export function useInvalidateTenancies() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: tenancyKeys.all })
}

export function useRentAccount(tenancyId: number) {
  return useQuery({
    queryKey: tenancyKeys.rentAccount(tenancyId),
    queryFn: () => api<RentAccount>(`${TENANCIES}/${tenancyId}/rent-account`),
  })
}

/** Payments change the tenancies, their rent accounts and the account and transactions of the finances. */
export function useInvalidateAfterPayment() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: tenancyKeys.all }),
      queryClient.invalidateQueries({ queryKey: ledgerKeys.accounts }),
      queryClient.invalidateQueries({ queryKey: ledgerKeys.transactions }),
    ])
}
