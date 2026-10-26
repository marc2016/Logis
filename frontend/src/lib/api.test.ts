import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './api'

function mockFetch(status: number, body?: unknown) {
  const fn = vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
  vi.stubGlobal('fetch', fn)
  return fn
}

describe('api', () => {
  const assign = vi.fn()

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=abc%2D123'
    vi.stubGlobal('location', { ...window.location, pathname: '/ferienvermietung/belegung', search: '?x=1', assign })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    assign.mockReset()
  })

  it('sends the CSRF header on POST', async () => {
    const fetchMock = mockFetch(200, { ok: true })
    await api('/api/account/recovery-codes', { method: 'POST' })
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('abc-123')
    expect(init.credentials).toBe('include')
  })

  it('does not send the CSRF header on GET', async () => {
    const fetchMock = mockFetch(200, [])
    await api('/api/modules')
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(new Headers(init.headers).has('X-XSRF-TOKEN')).toBe(false)
  })

  it('redirects to login on 401 and keeps the target', async () => {
    mockFetch(401)
    await expect(api('/api/modules')).rejects.toBeInstanceOf(ApiError)
    expect(assign).toHaveBeenCalledWith('/login?next=%2Fferienvermietung%2Fbelegung%3Fx%3D1')
  })

  it('does not redirect for the login steps themselves', async () => {
    mockFetch(401, { detail: 'Benutzername oder Passwort ist falsch' })
    await expect(api('/api/auth/login', { json: {} })).rejects.toThrow('Benutzername oder Passwort ist falsch')
    expect(assign).not.toHaveBeenCalled()
  })

  it('uses the ProblemDetail message', async () => {
    mockFetch(409, { detail: 'Benutzername ist bereits vergeben' })
    await expect(api('/api/admin/users', { json: {} })).rejects.toMatchObject({
      status: 409,
      message: 'Benutzername ist bereits vergeben',
    })
  })
})
