import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { LegalEntityKind, ShareRole } from '@/core/ledger/api'

export type UnitUsage = 'VACATION_RENTAL' | 'LONG_TERM_RENTAL' | 'OWN_USE' | 'OTHER'

export const unitUsageLabel: Record<UnitUsage, string> = {
  VACATION_RENTAL: 'Ferienvermietung',
  LONG_TERM_RENTAL: 'Dauermiete',
  OWN_USE: 'Eigennutzung',
  OTHER: 'Sonstiges',
}

export interface Unit {
  id: number
  name: string
  usage: UnitUsage
  archived: boolean
  /** Effective role: the higher of property and unit role. */
  myRole: ShareRole
}

export interface Property {
  id: number
  name: string
  street: string | null
  postalCode: string | null
  city: string | null
  legalEntity: { id: number; name: string; kind: LegalEntityKind }
  multiUnit: boolean
  archived: boolean
  /** Role on the property itself; null if the user only has shares on units. */
  myRole: ShareRole | null
  /** Only the units visible to the user. */
  units: Unit[]
}

/** Target of a transaction: a property, a unit, or none. */
export interface Target {
  propertyId: number | null
  unitId: number | null
}

export const NO_TARGET: Target = { propertyId: null, unitId: null }

export const realestateKeys = {
  properties: ['properties'] as const,
  property: (id: number) => ['properties', id] as const,
}

export function useProperties(includeArchived = false) {
  return useQuery({
    queryKey: [...realestateKeys.properties, { includeArchived }],
    queryFn: () => api<Property[]>(`/api/properties${includeArchived ? '?includeArchived=true' : ''}`),
  })
}

export function useProperty(id: number) {
  return useQuery({ queryKey: realestateKeys.property(id), queryFn: () => api<Property>(`/api/properties/${id}`) })
}

export function useInvalidateProperties() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: realestateKeys.properties })
}

export function formatAddress(p: Pick<Property, 'street' | 'postalCode' | 'city'>) {
  const place = [p.postalCode, p.city].filter(Boolean).join(' ')
  return [p.street, place].filter(Boolean).join(', ')
}

export function atLeast(role: ShareRole | null | undefined, required: ShareRole) {
  const order: ShareRole[] = ['VIEWER', 'EDITOR', 'OWNER']
  return role != null && order.indexOf(role) >= order.indexOf(required)
}

// --- Finances ------------------------------------------------------------------------------------

export type FinanceKind = 'INCOME' | 'EXPENSE'

export const COST_SOURCE = 'realestate.cost'

export interface FinanceItem {
  date: string
  /** null for items on the property itself. */
  unitId: number | null
  unitName: string | null
  kind: FinanceKind
  label: string
  amount: string
  /** e.g. "realestate.cost" or "vacationrental.statement". */
  sourceType: string
  sourceId: number
  categoryName: string | null
  counterparty: string | null
  editable: boolean
}

export interface FinanceMonth {
  month: number
  income: string
  expenses: string
  result: string
}

export interface Finances {
  year: number
  months: FinanceMonth[]
  total: FinanceMonth
  items: FinanceItem[]
  canRecord: boolean
}

export interface Cost {
  id: number
  propertyId: number
  unitId: number | null
  date: string
  amount: string
  categoryId: number | null
  counterparty: string | null
  note: string | null
  version: number
}

export interface FinanceFilter {
  unitId?: number
  kind?: FinanceKind
  sort?: string
}

export const financeKeys = {
  all: (propertyId: number) => ['properties', propertyId, 'finances'] as const,
  year: (propertyId: number, year: number, filter: FinanceFilter) => ['properties', propertyId, 'finances', year, filter] as const,
}

export function useFinances(propertyId: number, year: number, filter: FinanceFilter) {
  const search = new URLSearchParams({ year: String(year) })
  if (filter.unitId) search.set('unitId', String(filter.unitId))
  if (filter.kind) search.set('kind', filter.kind)
  if (filter.sort) search.set('sort', filter.sort)
  return useQuery({
    queryKey: financeKeys.year(propertyId, year, filter),
    queryFn: () => api<Finances>(`/api/properties/${propertyId}/finances?${search}`),
  })
}

export function useInvalidateFinances(propertyId: number) {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: financeKeys.all(propertyId) })
}
