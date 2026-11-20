import type { DashboardWidget, LogisModule } from '@/app/types'
import type { Tile } from './api'

/** The default tiles for users who never customised the start page (design D3). */
export const DEFAULT_TILES: Tile[] = [
  { id: 'default-todo', type: 'core.todo', settings: {} },
  { id: 'default-arrivals', type: 'vacationrental.arrivals', settings: { days: 14 } },
  { id: 'default-occupancy', type: 'vacationrental.occupancy', settings: {} },
  { id: 'default-balances', type: 'core.balances', settings: {} },
]

/** The tile types of the core and of the active modules, by type. */
export function catalogOf(core: DashboardWidget[], modules: LogisModule[]): Map<string, DashboardWidget> {
  const catalog = new Map<string, DashboardWidget>()
  ;[...core, ...modules.flatMap((m) => m.dashboardWidgets ?? [])].forEach((w) => catalog.set(w.type, w))
  return catalog
}

/** The user's tiles, or the default; `shown` leaves out tiles whose type is not in the catalog (inactive module). */
export function tilesOf(saved: Tile[] | null | undefined, catalog: Map<string, DashboardWidget>) {
  const all = saved ?? DEFAULT_TILES
  return { all, shown: all.filter((t) => catalog.has(t.type)), customised: saved != null }
}

export function newTile(widget: DashboardWidget): Tile {
  return { id: crypto.randomUUID(), type: widget.type, settings: { ...widget.defaultSettings } }
}

/** Moves the tile one place among the shown tiles; hidden tiles keep their place in the stored list. */
export function move(all: Tile[], shown: Tile[], id: string, step: -1 | 1): Tile[] {
  const index = shown.findIndex((t) => t.id === id)
  const other = shown[index + step]
  if (index < 0 || !other) return all
  const a = all.findIndex((t) => t.id === id)
  const b = all.findIndex((t) => t.id === other.id)
  const next = [...all]
  ;[next[a], next[b]] = [next[b], next[a]]
  return next
}
