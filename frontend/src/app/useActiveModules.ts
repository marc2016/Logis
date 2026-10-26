import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { moduleRegistry } from './registry'
import type { LogisModule } from './types'

interface ModuleDescriptor {
  id: string
  label: string
  order: number
}

export function filterActiveModules(registry: LogisModule[], active: ModuleDescriptor[]): LogisModule[] {
  const ids = new Set(active.map((m) => m.id))
  return registry.filter((m) => ids.has(m.id)).sort((a, b) => a.order - b.order)
}

export function useActiveModules() {
  const query = useQuery({
    queryKey: ['modules'],
    queryFn: () => api<ModuleDescriptor[]>('/api/modules'),
    staleTime: Infinity,
  })
  return {
    modules: query.data ? filterActiveModules(moduleRegistry, query.data) : [],
    isLoading: query.isLoading,
  }
}
