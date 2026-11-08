import type { Booking, Calendar, RentalUnit, Statement } from './api'

export const seeufer: RentalUnit = {
  id: 70,
  name: 'Haus Seeufer',
  propertyId: 7,
  propertyName: 'Haus Seeufer',
  propertyMultiUnit: false,
  archived: false,
  myRole: 'OWNER',
}

export function booking(overrides: Partial<Booking> = {}): Booking {
  return {
    id: 500,
    unit: { id: 70, name: 'Haus Seeufer', propertyId: 7, propertyName: 'Haus Seeufer', propertyMultiUnit: false },
    arrival: '2026-11-06',
    departure: '2026-11-08',
    nights: 2,
    externalRef: '275505',
    adults: 4,
    children: 0,
    expectedRent: '248.58',
    status: 'PLANNED',
    statementId: null,
    guest: { name: 'Max Gast', street: 'Seeweg 1', postalCode: '48529', city: 'Nordhorn', country: 'Deutschland', phone: '0123', email: 'gast@example.org' },
    guestDeleted: false,
    canEdit: true,
    version: 0,
    agency: null,
    pets: 0,
    reviewState: 'NONE',
    proposal: null,
    ...overrides,
  }
}

export function bookingPage(items: Booking[]) {
  return { items, page: 0, size: 50, totalItems: items.length }
}

export function statement(overrides: Partial<Statement> = {}): Statement {
  return {
    id: 900,
    unit: booking().unit,
    provider: 'Ferienwohnungen Nord',
    periodFrom: '2026-11-01',
    periodTo: '2026-11-30',
    statementDate: '2026-11-30',
    payout: '0.00',
    lineCount: 0,
    canEdit: true,
    version: 0,
    lines: [],
    ...overrides,
  }
}

export function calendar(overrides: Partial<Calendar> = {}): Calendar {
  return {
    propertyId: 7,
    propertyName: 'Haus am Deich',
    year: 2026,
    from: '2025-12',
    to: '2027-01',
    units: [
      {
        id: 81,
        name: 'Whg. 1',
        archived: false,
        canEdit: true,
        bookings: [{ id: 1, arrival: '2026-11-06', departure: '2026-11-08', status: 'PLANNED', guestName: 'Gast Geplant', externalRef: null, reviewState: 'NONE' }],
      },
      {
        id: 82,
        name: 'Whg. 2',
        archived: false,
        canEdit: true,
        bookings: [
          { id: 2, arrival: '2026-11-10', departure: '2026-11-12', status: 'SETTLED', guestName: 'Gast Abgerechnet', externalRef: null, reviewState: 'NONE' },
          { id: 3, arrival: '2026-11-20', departure: '2026-11-22', status: 'CANCELLED', guestName: 'Gast Storniert', externalRef: null, reviewState: 'NONE' },
        ],
      },
    ],
    ...overrides,
  }
}
