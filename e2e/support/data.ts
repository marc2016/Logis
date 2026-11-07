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
    city: 'Nordhorn',
    multiUnit: false,
    firstUnit: { usage: 'VACATION_RENTAL' },
  })
  return { propertyId: property.id, unitId: property.units[0].id, name }
}

export async function booking(api: APIRequestContext, unitId: number, arrival: string, departure: string, guest: string, ref?: string) {
  return call<{ id: number }>(api, 'POST', '/api/vacationrental/bookings', {
    unitId,
    arrival,
    departure,
    adults: 2,
    guest: { name: guest },
    ...(ref ? { externalRef: ref } : {}),
  })
}

/** ISO date `days` from today (local time). */
export function inDays(days: number) {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
