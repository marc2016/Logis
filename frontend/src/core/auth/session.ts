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
  /** No e-mail address yet: the app shell asks for one (from `/me`). */
  emailMissing?: boolean
}

export interface SessionResponse {
  stage: AuthStage
  user: SessionUser | null
  recoveryCodes: string[] | null
  /** Whether passkey sign-in is offered (from `/me`). */
  passkeys?: boolean | null
  /** An admin session started by a remembered device: the admin area needs a fresh sign-in (from `/me`). */
  reauthenticationRequired?: boolean | null
}

export const sessionQueryKey = ['session'] as const

export function fetchSession() {
  return api<SessionResponse>('/api/auth/me')
}

export function useSession() {
  return useQuery({ queryKey: sessionQueryKey, queryFn: fetchSession, staleTime: 60_000 })
}

/** Use only inside the AuthGate - the user is guaranteed to be present there. */
export function useCurrentUser(): SessionUser {
  const { data } = useSession()
  if (!data?.user) throw new Error('useCurrentUser ausserhalb des AuthGate')
  return data.user
}

/** Allows only relative targets within the app (no open redirect). */
export function safeNext(next: string | null): string {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/login')) return '/'
  return next
}

const REMEMBER_HINT = 'logis.rememberOnNextSignIn'

/** „Erneut anmelden“: the device was remembered, so the next sign-in keeps „Angemeldet bleiben“ checked. */
export function rememberOnNextSignIn() {
  try {
    sessionStorage.setItem(REMEMBER_HINT, '1')
  } catch {
    // storage unavailable: the box simply starts unchecked
  }
}

/** Reads and clears the hint of {@link rememberOnNextSignIn}. */
export function takeRememberHint(): boolean {
  try {
    const hint = sessionStorage.getItem(REMEMBER_HINT) === '1'
    sessionStorage.removeItem(REMEMBER_HINT)
    return hint
  } catch {
    return false
  }
}
