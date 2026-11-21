import type { Location } from 'react-router'

export const TENANCY_LIST = '/dauervermietung/mietverhaeltnisse'
export const UNIT_LIST = '/dauervermietung/einheiten'

export function tenancyPath(id: number) {
  return `${TENANCY_LIST}/${id}`
}

/** The list with the dialog for a new tenancy open, the unit preselected. */
export function newTenancyPath(unitId: number) {
  return `${TENANCY_LIST}?neu=${unitId}`
}

/** Navigation state of the tenancy page: where the back link leads. */
export interface BackState {
  back?: string
}

export function backTo(location: Pick<Location, 'pathname' | 'search'>): BackState {
  return { back: `${location.pathname}${location.search}` }
}

export function backLabel(back: string) {
  return back.startsWith(UNIT_LIST) ? 'Zurück zu Einheiten' : 'Zurück zu Mietverhältnissen'
}
