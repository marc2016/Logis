import { useState } from 'react'

/** The selected rows of a list page, by id; `resetKey` (filter, sort, page) clears the selection when it changes. */
export interface Selection {
  ids: ReadonlySet<string>
  isSelected: (id: string) => boolean
  /** Toggles one row; with Shift, selects the range from the last clicked row within `order`. */
  toggle: (id: string, shift: boolean, order: string[]) => void
  setAll: (ids: string[], on: boolean) => void
  /** Keeps only these ids selected, e.g. the failed entries after a bulk action. */
  keepOnly: (ids: string[]) => void
  clear: () => void
}

const EMPTY: ReadonlySet<string> = new Set()

export function useSelection(resetKey: string): Selection {
  const [state, setState] = useState<{ key: string; ids: ReadonlySet<string>; last: string | null }>({ key: resetKey, ids: EMPTY, last: null })
  const current = state.key === resetKey ? state : { key: resetKey, ids: EMPTY, last: null }
  const update = (ids: ReadonlySet<string>, last: string | null) => setState({ key: resetKey, ids, last })
  return {
    ids: current.ids,
    isSelected: (id) => current.ids.has(id),
    toggle: (id, shift, order) => {
      const next = new Set(current.ids)
      const on = !next.has(id)
      if (shift && current.last !== null && order.includes(current.last) && order.includes(id)) {
        const [a, b] = [order.indexOf(current.last), order.indexOf(id)].sort((x, y) => x - y)
        order.slice(a, b + 1).forEach((r) => (on ? next.add(r) : next.delete(r)))
      } else if (on) {
        next.add(id)
      } else {
        next.delete(id)
      }
      update(next, id)
    },
    setAll: (ids, on) => {
      const next = new Set(current.ids)
      ids.forEach((r) => (on ? next.add(r) : next.delete(r)))
      update(next, null)
    },
    keepOnly: (ids) => update(new Set(ids.filter((r) => current.ids.has(r))), null),
    clear: () => update(EMPTY, null),
  }
}
