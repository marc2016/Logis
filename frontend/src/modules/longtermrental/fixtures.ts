import type { Rent, RentalUnit, Tenancy, UnitRef } from './api'

export const whg1: UnitRef = { id: 81, name: 'Whg. 1', propertyId: 8, propertyName: 'Haus Talblick', propertyMultiUnit: true }
export const whg2: UnitRef = { id: 82, name: 'Whg. 2', propertyId: 8, propertyName: 'Haus Talblick', propertyMultiUnit: true }

export function rent(overrides: Partial<Rent> = {}): Rent {
  return { id: 900, validFrom: '2026-03-01', coldRent: '650.00', utilitiesAdvance: '150.00', others: [], total: '800.00', ...overrides }
}

export function tenancy(overrides: Partial<Tenancy> = {}): Tenancy {
  const first = rent()
  return {
    id: 600,
    unit: whg1,
    startDate: '2026-03-01',
    endDate: null,
    state: 'RUNNING',
    tenants: [
      { id: 1, name: 'Erika Musterfrau', street: 'Musterweg 1', postalCode: '00000', city: 'Talstadt', country: null, phone: '+49 000 0000', email: 'erika@example.org' },
    ],
    tenantsDeleted: false,
    depositAmount: null,
    depositReceivedOn: null,
    currentRent: first,
    rentTerms: [first],
    canEdit: true,
    version: 0,
    ...overrides,
  }
}

export function rentalUnit(overrides: Partial<RentalUnit> = {}): RentalUnit {
  return { unit: whg1, canEdit: true, tenancy: null, vacantSince: null, next: null, ...overrides }
}
