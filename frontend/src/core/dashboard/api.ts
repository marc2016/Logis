import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'

/** One tile on the user's start page; `type` names a widget of the catalog. */
export interface Tile {
  id: string
  type: string
  settings: Record<string, unknown>
}

interface Dashboard {
  /** `null` while the user has not customised the start page. */
  tiles: Tile[] | null
}

export const dashboardKey = ['dashboard'] as const

export function useDashboard() {
  return useQuery({ queryKey: dashboardKey, queryFn: () => api<Dashboard>('/api/dashboard') })
}

/** Saves at once, showing the new tiles before the answer arrives (design D4). */
export function useSaveDashboard() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (tiles: Tile[] | null) => api<Dashboard>('/api/dashboard', { method: 'PUT', json: { tiles } }),
    onMutate: async (tiles) => {
      await client.cancelQueries({ queryKey: dashboardKey })
      const previous = client.getQueryData<Dashboard>(dashboardKey)
      client.setQueryData<Dashboard>(dashboardKey, { tiles })
      return { previous }
    },
    onError: (_error, _tiles, context) => client.setQueryData(dashboardKey, context?.previous),
    onSettled: () => client.invalidateQueries({ queryKey: dashboardKey }),
  })
}
