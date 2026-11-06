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
