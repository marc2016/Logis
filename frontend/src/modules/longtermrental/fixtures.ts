import type { Rent, RentAccount, RentalUnit, Tenancy, UnitRef } from './api'

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
    arrears: '0.00',
    credit: '0.00',
    overdue: false,
    ...overrides,
  }
}

/** March to May 2027 with 800,00 each, March paid. */
export function rentAccount(overrides: Partial<RentAccount> = {}): RentAccount {
  return {
    due: '2400.00',
    paid: '800.00',
    arrears: '1600.00',
    credit: '0.00',
    overdue: true,
    canEdit: true,
    months: [
      { month: '2027-05', due: '800.00', paid: '0.00', open: '800.00', overdue: true },
      { month: '2027-04', due: '800.00', paid: '0.00', open: '800.00', overdue: true },
      { month: '2027-03', due: '800.00', paid: '800.00', open: '0.00', overdue: false },
    ],
    payments: [{ id: 31, date: '2027-03-03', amount: '800.00', accountId: 5, accountName: 'Giro', note: 'Überweisung', version: 0 }],
    ...overrides,
  }
}

export function rentalUnit(overrides: Partial<RentalUnit> = {}): RentalUnit {
  return { unit: whg1, canEdit: true, tenancy: null, vacantSince: null, next: null, ...overrides }
}
