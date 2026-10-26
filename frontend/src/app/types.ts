import type { LucideIcon } from 'lucide-react'
import type { RouteObject } from 'react-router'

/** Vertrag, den jedes Frontend-Modul erfuellt (siehe design.md D11). */
export interface LogisModule {
  /** Identisch mit der Modul-ID im Backend. */
  id: string
  label: string
  icon: LucideIcon
  order: number
  /** URL-Segment, unter dem die Modulseiten haengen, z. B. "ferienvermietung". */
  basePath: string
  /** Navigationseintraege; `path` relativ zu basePath. */
  navItems: { label: string; path: string; icon?: LucideIcon }[]
  /** Routen relativ zu basePath. */
  routes: RouteObject[]
}
