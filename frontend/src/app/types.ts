import type { LucideIcon } from 'lucide-react'
import type { RouteObject } from 'react-router'

/** Contract that every frontend module fulfills (see design.md D11). */
export interface LogisModule {
  /** Identical to the module ID in the backend. */
  id: string
  label: string
  icon: LucideIcon
  order: number
  /** URL segment under which the module pages live, e.g. "ferienvermietung". */
  basePath: string
  /** Navigation entries; `path` relative to basePath. */
  navItems: { label: string; path: string; icon?: LucideIcon }[]
  /** Routes relative to basePath. */
  routes: RouteObject[]
}
