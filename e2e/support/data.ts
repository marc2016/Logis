import type { APIRequestContext } from '@playwright/test'
import { call, unique } from './api'

export interface RentalUnit {
  propertyId: number
  unitId: number
  name: string
}

/** A single-unit holiday home owned by the signed-in user. */
export async function holidayHome(api: APIRequestContext, prefix = 'Haus'): Promise<RentalUnit> {
  const name = unique(`${prefix} `)
  const entity = await call<{ id: number }>(api, 'POST', '/api/legal-entities', { name: unique('Test GbR '), kind: 'GBR' })
  const property = await call<{ id: number; units: { id: number }[] }>(api, 'POST', '/api/properties', {
    legalEntityId: entity.id,
    name,
    city: 'Talstadt',
    multiUnit: false,
    firstUnit: { usage: 'VACATION_RENTAL' },
  })
  return { propertyId: property.id, unitId: property.units[0].id, name }
}

export async function booking(api: APIRequestContext, unitId: number, arrival: string, departure: string, guest: string, ref?: string, expectedRent?: string) {
  return call<{ id: number }>(api, 'POST', '/api/vacationrental/bookings', {
    unitId,
    arrival,
    departure,
    adults: 2,
    guest: { name: guest },
    ...(ref ? { externalRef: ref } : {}),
    ...(expectedRent ? { expectedRent } : {}),
  })
}

/** An ISO date as typed into the date fields: TT.MM.JJJJ. */
export function germanDate(iso: string) {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

/** ISO date `days` from today (local time). */
export function inDays(days: number) {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** An owner (legal entity) of the signed-in user. */
export async function owner(api: APIRequestContext, name = unique('Test GbR ')) {
  return call<{ id: number; name: string }>(api, 'POST', '/api/legal-entities', { name, kind: 'GBR' })
}

/** A checking account of an owner. */
export async function account(api: APIRequestContext, legalEntityId: number, name = unique('Konto '), openingBalance = '0.00') {
  return call<{ id: number; name: string }>(api, 'POST', '/api/accounts', { legalEntityId, name, kind: 'CHECKING', openingBalance })
}

/** Shares a property, unit or account with another user. */
export async function share(api: APIRequestContext, type: 'property' | 'unit' | 'account', objectId: number, userId: number, role: 'VIEWER' | 'EDITOR' | 'OWNER') {
  return call(api, 'POST', `/api/shares/${type}/${objectId}`, { userId, role })
}

/** A statement of a unit for a period. */
export async function statement(api: APIRequestContext, unitId: number, provider: string, periodFrom: string, periodTo: string) {
  return call<{ id: number }>(api, 'POST', '/api/vacationrental/statements', { unitId, provider, periodFrom, periodTo, statementDate: periodTo })
}

/** A single-unit property in long-term rental owned by the signed-in user. */
export async function longTermUnit(api: APIRequestContext, prefix = 'Haus'): Promise<RentalUnit> {
  const name = unique(`${prefix} `)
  const entity = await owner(api)
  const property = await call<{ id: number; units: { id: number }[] }>(api, 'POST', '/api/properties', {
    legalEntityId: entity.id,
    name,
    city: 'Talstadt',
    multiUnit: false,
    firstUnit: { usage: 'LONG_TERM_RENTAL' },
  })
  return { propertyId: property.id, unitId: property.units[0].id, name }
}

/** A tenancy of a long-term unit with one tenant and a rent of 650,00 € plus 150,00 € advance payment. */
export async function tenancy(api: APIRequestContext, unitId: number, startDate: string, tenant: string, endDate: string | null = null) {
  return call<{ id: number }>(api, 'POST', '/api/longtermrental/tenancies', {
    unitId,
    startDate,
    endDate,
    tenants: [{ name: tenant, street: 'Musterweg 1', postalCode: '00000', city: 'Talstadt', phone: '+49 000 0000', email: 'mieter@example.org' }],
    rent: { coldRent: '650.00', utilitiesAdvance: '150.00', others: [{ label: 'Stellplatz', amount: '40.00' }] },
    depositAmount: '1950.00',
  })
}
