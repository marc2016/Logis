import type { Location } from 'react-router'

export function bookingPath(id: number) {
  return `/ferienvermietung/buchungen/${id}`
}

export function statementPath(id: number) {
  return `/ferienvermietung/abrechnungen/${id}`
}

/** Navigation state of a record page: where the back link leads. */
export interface BackState {
  back?: string
}

/** State that lets a record page return to the current view with its filters. */
export function backTo(location: Pick<Location, 'pathname' | 'search'>): BackState {
  return { back: `${location.pathname}${location.search}` }
}

/** The text of the back link, after where it leads. */
export function backLabel(back: string) {
  if (back.startsWith('/ferienvermietung/kalender')) return 'Zurück zum Kalender'
  if (back.startsWith('/posteingang')) return 'Zurück zum Posteingang'
  if (back.startsWith('/ferienvermietung/abrechnungen/')) return 'Zurück zur Abrechnung'
  if (back.startsWith('/ferienvermietung/abrechnungen')) return 'Zurück zu Abrechnungen'
  return 'Zurück zu Buchungen'
}
