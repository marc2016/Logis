import type { APIRequestContext } from '@playwright/test'
import { freshTotp, lastStep, rememberStep } from './totp'

/** JSON calls with the CSRF header the backend expects for changing requests. */
export async function call<T = unknown>(request: APIRequestContext, method: string, path: string, body?: unknown): Promise<T> {
  const state = await request.storageState()
  const xsrf = state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value
  const res = await request.fetch(path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(xsrf ? { 'X-XSRF-TOKEN': decodeURIComponent(xsrf) } : {}) },
    data: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  if (!res.ok()) throw new Error(`${method} ${path}: ${res.status()} ${text}`)
  return (text ? JSON.parse(text) : undefined) as T
}

export interface Credentials {
  username: string
  password: string
  /** TOTP secret, known once two-factor sign-in is set up. */
  secret?: string
  /** Last TOTP time step used, so a later run does not reuse a code. */
  totpStep?: number
}

type Stage = { stage: string }

/**
 * Signs in through the API, completing a required password change and two-factor setup.
 * Updates `credentials` with the new password and the secret.
 */
export async function signIn(request: APIRequestContext, credentials: Credentials) {
  await call(request, 'GET', '/api/auth/me').catch(() => undefined)
  let r = await call<Stage>(request, 'POST', '/api/auth/login', { username: credentials.username, password: credentials.password })
  for (let i = 0; i < 5 && r.stage !== 'AUTHENTICATED'; i++) {
    if (r.stage === 'PASSWORD_CHANGE_REQUIRED') {
      const newPassword = `${credentials.password}-neu`
      r = await call<Stage>(request, 'POST', '/api/auth/password', { newPassword })
      credentials.password = newPassword
    } else if (r.stage === 'TOTP_ENROLLMENT_REQUIRED') {
      const setup = await call<{ secret: string }>(request, 'POST', '/api/auth/totp/setup')
      credentials.secret = setup.secret
      const code = await freshTotp(setup.secret)
      credentials.totpStep = lastStep(setup.secret)
      r = await call<Stage>(request, 'POST', '/api/auth/totp/confirm', { code })
    } else if (r.stage === 'TOTP_REQUIRED') {
      rememberStep(credentials.secret!, credentials.totpStep)
      const code = await freshTotp(credentials.secret!)
      credentials.totpStep = lastStep(credentials.secret!)
      r = await call<Stage>(request, 'POST', '/api/auth/totp', { code })
    } else {
      throw new Error(`Unexpected sign-in stage ${r.stage}`)
    }
  }
  if (r.stage !== 'AUTHENTICATED') throw new Error(`Sign-in of ${credentials.username} did not finish`)
}

/** Short random suffix for names, so tests never collide. */
export function unique(prefix: string) {
  return `${prefix}${Date.now().toString(36)}${Math.floor(Math.random() * 1296).toString(36)}`
}
