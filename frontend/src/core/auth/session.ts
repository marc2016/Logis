import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'

export type AuthStage =
  | 'ANONYMOUS'
  | 'TOTP_ENROLLMENT_REQUIRED'
  | 'TOTP_REQUIRED'
  | 'PASSWORD_CHANGE_REQUIRED'
  | 'AUTHENTICATED'

export interface SessionUser {
  id: number
  username: string
  displayName: string
  role: 'ADMIN' | 'USER'
}

export interface SessionResponse {
  stage: AuthStage
  user: SessionUser | null
  recoveryCodes: string[] | null
}

export const sessionQueryKey = ['session'] as const

export function fetchSession() {
  return api<SessionResponse>('/api/auth/me')
}

export function useSession() {
  return useQuery({ queryKey: sessionQueryKey, queryFn: fetchSession, staleTime: 60_000 })
}

/** Nur innerhalb des AuthGate verwenden - dort ist der Benutzer garantiert vorhanden. */
export function useCurrentUser(): SessionUser {
  const { data } = useSession()
  if (!data?.user) throw new Error('useCurrentUser ausserhalb des AuthGate')
  return data.user
}

/** Erlaubt nur relative Ziele innerhalb der App (kein Open Redirect). */
export function safeNext(next: string | null): string {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/login')) return '/'
  return next
}
