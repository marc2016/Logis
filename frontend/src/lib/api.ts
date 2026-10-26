/**
 * Fetch-Wrapper fuer die Logis-API.
 * - sendet das Sitzungs-Cookie mit (Same-Origin)
 * - setzt bei zustandsaendernden Aufrufen den CSRF-Header aus dem Cookie XSRF-TOKEN
 * - leitet bei 401 auf die Anmeldung um (ausser bei den Anmeldeschritten selbst)
 * - liefert Fehler als ApiError mit deutscher Meldung aus dem ProblemDetail
 */

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

export function redirectToLogin() {
  const { pathname, search } = window.location
  if (pathname === '/login') return
  const next = encodeURIComponent(pathname + search)
  window.location.assign(`/login?next=${next}`)
}

function defaultMessage(status: number): string {
  if (status === 403) return 'Dafür fehlt die Berechtigung.'
  if (status === 404) return 'Nicht gefunden.'
  if (status === 429) return 'Zu viele Anfragen, bitte später erneut versuchen.'
  return 'Ein unerwarteter Fehler ist aufgetreten.'
}

export async function api<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const method = (init.method ?? (init.json !== undefined ? 'POST' : 'GET')).toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.json !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  if (UNSAFE_METHODS.has(method)) {
    const token = readCookie('XSRF-TOKEN')
    if (token) headers.set('X-XSRF-TOKEN', decodeURIComponent(token))
  }

  const response = await fetch(path, {
    ...init,
    method,
    headers,
    credentials: 'include',
    body: init.json !== undefined ? JSON.stringify(init.json) : init.body,
  })

  if (response.status === 401 && !path.startsWith('/api/auth/')) {
    redirectToLogin()
  }

  if (!response.ok) {
    let message = defaultMessage(response.status)
    try {
      const problem = await response.json()
      if (typeof problem?.detail === 'string' && problem.detail) message = problem.detail
    } catch {
      // kein JSON-Body
    }
    throw new ApiError(response.status, message)
  }

  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
