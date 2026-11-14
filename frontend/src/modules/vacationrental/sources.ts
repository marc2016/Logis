import type { FieldLocation } from './api'

export const fieldLabels: Record<string, string> = {
  externalRef: 'Buchungsnummer',
  propertyName: 'Objekt',
  arrival: 'Anreise',
  departure: 'Abreise',
  adults: 'Erwachsene',
  children: 'Kinder',
  pets: 'Haustiere',
  expectedRent: 'Mietpreis',
  guestName: 'Name',
  guestStreet: 'Straße',
  guestPostalCode: 'PLZ',
  guestCity: 'Ort',
  guestCountry: 'Land',
  guestPhone: 'Telefon',
  guestEmail: 'E-Mail',
}

/** The field whose location is shown: of the booking's current values or of the proposed change. */
export interface ActiveField {
  field: string
  scope: 'current' | 'proposal'
}

export interface Mark {
  field: string
  scope: ActiveField['scope']
  location: FieldLocation
}

export interface Segment {
  start: number
  end: number
  marks: Mark[]
}

/** Splits a string at every boundary of the marks, so overlapping marks are shown as well. */
export function segments(length: number, marks: Mark[]): Segment[] {
  const cuts = new Set<number>([0, length])
  marks.forEach((m) => {
    cuts.add(Math.min(Math.max(m.location.start, 0), length))
    cuts.add(Math.min(Math.max(m.location.end, 0), length))
  })
  const sorted = [...cuts].sort((a, b) => a - b)
  const result: Segment[] = []
  for (let i = 0; i < sorted.length - 1; i++) {
    const start = sorted[i]
    const end = sorted[i + 1]
    result.push({ start, end, marks: marks.filter((m) => m.location.start <= start && m.location.end >= end && m.location.end > m.location.start) })
  }
  return result
}

