import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { startAuthentication, startRegistration } from '@simplewebauthn/browser'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'

vi.mock('@simplewebauthn/browser', () => ({ startAuthentication: vi.fn(), startRegistration: vi.fn() }))

const anonymous = (passkeys: boolean) => ({ body: { stage: 'ANONYMOUS', user: null, recoveryCodes: null, passkeys } })
const signedIn = () => {
  const me = authenticated()
  return { ...me, body: { ...me.body, passkeys: true } }
}

function cancelled() {
  const error = new Error('The operation either timed out or was not allowed.')
  error.name = 'NotAllowedError'
  return error
}

describe('Passkeys', () => {
  beforeEach(() => vi.mocked(startAuthentication).mockReset())

  it('signs in with a passkey and opens the overview', async () => {
    let loggedIn = false
    let sent: unknown
    vi.mocked(startAuthentication).mockResolvedValue({ id: 'cred', rawId: 'cred' } as never)
    mockApi({
      'GET /api/auth/me': () => (loggedIn ? signedIn() : anonymous(true)),
      'GET /api/modules': vacationRentalActive,
      'POST /api/auth/passkey/options': { body: { challenge: 'abc', rpId: 'localhost' } },
      'POST /api/auth/passkey': (init) => {
        sent = JSON.parse(String(init?.body))
        loggedIn = true
        return { body: { stage: 'AUTHENTICATED', user: null, recoveryCodes: null } }
      },
    })
    const { router } = renderApp('/login')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Mit Passkey anmelden' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(vi.mocked(startAuthentication)).toHaveBeenCalledWith({ optionsJSON: { challenge: 'abc', rpId: 'localhost' } })
    expect(sent).toEqual({ credential: { id: 'cred', rawId: 'cred' } })
  })

  it('shows nothing when the prompt is cancelled, and the server message when it is rejected', async () => {
    mockApi({
      'GET /api/auth/me': anonymous(true),
      'POST /api/auth/passkey/options': { body: { challenge: 'abc' } },
      'POST /api/auth/passkey': { status: 401, body: { detail: 'Anmeldung mit Passkey fehlgeschlagen' } },
    })
    renderApp('/login')
    const user = userEvent.setup()
    vi.mocked(startAuthentication).mockRejectedValueOnce(cancelled())
    await user.click(await screen.findByRole('button', { name: 'Mit Passkey anmelden' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Mit Passkey anmelden' })).toBeEnabled())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    vi.mocked(startAuthentication).mockResolvedValueOnce({ id: 'cred' } as never)
    await user.click(screen.getByRole('button', { name: 'Mit Passkey anmelden' }))
    expect(await screen.findByText('Anmeldung mit Passkey fehlgeschlagen')).toBeInTheDocument()
  })

  it('offers no passkey sign-in when passkeys are not configured', async () => {
    mockApi({ 'GET /api/auth/me': anonymous(false) })
    renderApp('/login')
    expect(await screen.findByLabelText('Benutzername')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mit Passkey anmelden' })).not.toBeInTheDocument()
  })

  it('adds, renames and deletes passkeys under „Mein Konto“', async () => {
    let list = [{ id: 1, name: 'Altes iPhone', createdAt: '2026-10-01T10:00:00Z', lastUsedAt: null }]
    let sent: unknown
    vi.mocked(startRegistration).mockResolvedValue({ id: 'new' } as never)
    mockApi({
      'GET /api/auth/me': signedIn(),
      'GET /api/modules': { body: [] },
      'GET /api/account/passkeys': () => ({ body: list }),
      'POST /api/account/passkeys/registration/options': (init) => {
        const { password } = JSON.parse(String(init?.body))
        return password === 'richtig-richtig' ? { body: { challenge: 'xyz' } } : { status: 400, body: { detail: 'Das aktuelle Passwort ist falsch.' } }
      },
      'POST /api/account/passkeys/registration': (init) => {
        sent = JSON.parse(String(init?.body))
        const added = { id: 2, name: 'MacBook', createdAt: '2026-11-15T09:00:00Z', lastUsedAt: null }
        list = [...list, added]
        return { body: added }
      },
      'PUT /api/account/passkeys/1': () => {
        list = list.map((p) => (p.id === 1 ? { ...p, name: 'iPhone 12' } : p))
        return { body: list[0] }
      },
      'DELETE /api/account/passkeys/1': () => {
        list = list.filter((p) => p.id !== 1)
        return { status: 204 }
      },
    })
    renderApp('/konto')
    const user = userEvent.setup()
    const passkeys = await screen.findByRole('list', { name: 'Meine Passkeys' })
    expect(within(passkeys).getByText('Altes iPhone')).toBeInTheDocument()
    expect(within(passkeys).getByText(/Erstellt am 01\.10\.2026 · Noch nicht verwendet/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Passkey hinzufügen' }))
    let dialog = await screen.findByRole('dialog', { name: 'Passkey hinzufügen' })
    await user.type(within(dialog).getByLabelText('Aktuelles Passwort'), 'falsch-falsch')
    await user.type(within(dialog).getByLabelText('Name'), 'MacBook')
    await user.click(within(dialog).getByRole('button', { name: 'Hinzufügen' }))
    expect(await within(dialog).findByText('Das aktuelle Passwort ist falsch.')).toBeInTheDocument()
    await user.clear(within(dialog).getByLabelText('Aktuelles Passwort'))
    await user.type(within(dialog).getByLabelText('Aktuelles Passwort'), 'richtig-richtig')
    await user.click(within(dialog).getByRole('button', { name: 'Hinzufügen' }))
    expect(await within(screen.getByRole('list', { name: 'Meine Passkeys' })).findByText('MacBook')).toBeInTheDocument()
    expect(vi.mocked(startRegistration)).toHaveBeenCalledWith({ optionsJSON: { challenge: 'xyz' } })
    expect(sent).toEqual({ name: 'MacBook', credential: { id: 'new' } })

    await user.click(screen.getByRole('button', { name: 'Altes iPhone umbenennen' }))
    dialog = await screen.findByRole('dialog', { name: 'Passkey umbenennen' })
    await user.clear(within(dialog).getByLabelText('Name'))
    await user.type(within(dialog).getByLabelText('Name'), 'iPhone 12')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('iPhone 12')).toBeInTheDocument()

    const row = screen.getByText('iPhone 12').closest('li') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Löschen' }))
    await user.click(within(row).getByRole('button', { name: 'Ja' }))
    await waitFor(() => expect(screen.queryByText('iPhone 12')).not.toBeInTheDocument())
  })

  it('hides the passkey card when passkeys are not configured', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': { body: [] } })
    renderApp('/konto')
    expect(await screen.findByText('Wiederherstellungscodes')).toBeInTheDocument()
    expect(screen.queryByText('Passkeys')).not.toBeInTheDocument()
  })
})
