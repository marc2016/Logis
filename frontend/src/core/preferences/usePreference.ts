import { useCallback } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'

/** All settings of the signed-in user, loaded once (spec user-preferences; design D2 of booking-compact-view). */
export const preferencesKey = ['preferences'] as const

type Preferences = Record<string, unknown>

export function usePreferences() {
  return useQuery({
    queryKey: preferencesKey,
    queryFn: () => api<Preferences>('/api/preferences'),
    staleTime: Infinity,
    retry: false,
  })
}

/**
 * A setting of the user: the stored value, or `fallback` while there is none or the settings could not be loaded.
 * Setting it applies at once and saves in the background; a failed save keeps the value for this session.
 */
export function usePreference<T>(key: string, fallback: T): [T, (value: T) => void, boolean] {
  const client = useQueryClient()
  const { data, isSuccess, isError } = usePreferences()
  const value = data && key in data ? (data[key] as T) : fallback
  const set = useCallback(
    (next: T) => {
      client.setQueryData<Preferences>(preferencesKey, (current) => ({ ...(current ?? {}), [key]: next }))
      api(`/api/preferences/${encodeURIComponent(key)}`, { method: 'PUT', json: next }).catch(() => {
        // Not remembered, but the page keeps the new value (spec user-preferences, „Settings per user“).
      })
    },
    [client, key],
  )
  // `loaded`: the stored settings are known (or failed to load), so a missing key really means „not set“.
  return [value, set, isSuccess || isError]
}
