import { describe, expect, it } from 'vitest'
import { Home } from 'lucide-react'
import { filterActiveModules } from './useActiveModules'
import type { LogisModule } from './types'

const mod = (id: string, order: number): LogisModule => ({
  id,
  label: id,
  icon: Home,
  order,
  basePath: id,
  navItems: [],
  routes: [],
})

describe('filterActiveModules', () => {
  it('returns only modules reported active by the API, ordered', () => {
    const registry = [mod('b', 2), mod('a', 1), mod('off', 3)]
    const result = filterActiveModules(registry, [
      { id: 'b', label: 'B', order: 2 },
      { id: 'a', label: 'A', order: 1 },
      { id: 'unknown', label: 'X', order: 0 },
    ])
    expect(result.map((m) => m.id)).toEqual(['a', 'b'])
  })

  it('returns nothing when no module is active', () => {
    expect(filterActiveModules([mod('a', 1)], [])).toEqual([])
  })
})
