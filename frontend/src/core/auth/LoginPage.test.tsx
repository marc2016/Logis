import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { adminUser, authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'

const anonymous = { body: { stage: 'ANONYMOUS', user: null, recoveryCodes: null } }

describe('Sign-in', () => {
  it('deep link without session -> password -> TOTP -> target page', async () => {
    let loggedIn = false
    mockApi({
      'GET /api/auth/me': () => (loggedIn ? authenticated() : anonymous),
      'POST /api/auth/login': { body: { stage: 'TOTP_REQUIRED', user: null, recoveryCodes: null } },
      'POST /api/auth/totp': () => {
        loggedIn = true
        return { body: { stage: 'AUTHENTICATED', user: null, recoveryCodes: null } }
      },
      'GET /api/modules': vacationRentalActive,
    })
    const { router } = renderApp('/ferienvermietung/belegung')
    const user = userEvent.setup()

    await screen.findByLabelText('Benutzername')
    expect(router.state.location.search).toBe('?next=%2Fferienvermietung%2Fbelegung')

    await user.type(screen.getByLabelText('Benutzername'), 'admin')
    await user.type(screen.getByLabelText('Passwort'), 'ein-langes-passwort')
    await user.click(screen.getByRole('button', { name: 'Weiter' }))

    await screen.findByText('Bestätigungscode')
    await user.keyboard('123456')
    await user.click(screen.getByRole('button', { name: 'Anmelden' }))

    expect(await screen.findByRole('heading', { name: 'Belegung' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/ferienvermietung/belegung')
  })

  it('first sign-in: password -> setup -> codes -> overview', async () => {
    let loggedIn = false
    mockApi({
      'GET /api/auth/me': () => (loggedIn ? authenticated(adminUser) : anonymous),
      'POST /api/auth/login': { body: { stage: 'TOTP_ENROLLMENT_REQUIRED', user: null, recoveryCodes: null } },
      'POST /api/auth/totp/setup': { body: { secret: 'JBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://totp/Logis:admin?secret=JBSWY3DPEHPK3PXP' } },
      'POST /api/auth/totp/confirm': () => {
        loggedIn = true
        return { body: { stage: 'AUTHENTICATED', user: null, recoveryCodes: ['AAAAA-BBBBB', 'CCCCC-DDDDD'] } }
      },
      'GET /api/modules': vacationRentalActive,
    })
    renderApp('/login')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Benutzername'), 'admin')
    await user.type(screen.getByLabelText('Passwort'), 'ein-langes-passwort')
    await user.click(screen.getByRole('button', { name: 'Weiter' }))

    expect(await screen.findByTestId('totp-secret')).toHaveTextContent('JBSWY3DPEHPK3PXP')
    expect(screen.getByLabelText('QR-Code für die Authenticator-App')).toBeInTheDocument()
    await user.click(screen.getByLabelText('Code aus der Authenticator-App'))
    await user.keyboard('654321')
    await user.click(screen.getByRole('button', { name: 'Bestätigen' }))

    expect(await screen.findByText('AAAAA-BBBBB')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ich habe die Codes gesichert' }))

    expect(await screen.findByRole('heading', { name: 'Übersicht' })).toBeInTheDocument()
  })

  it('shows the backend error messages', async () => {
    let attempt = 0
    mockApi({
      'GET /api/auth/me': anonymous,
      'POST /api/auth/login': () => {
        attempt++
        return attempt === 1
          ? { status: 401, body: { detail: 'Benutzername oder Passwort ist falsch' } }
          : attempt === 2
            ? { status: 429, body: { detail: 'Zu viele Fehlversuche, bitte später erneut versuchen' } }
            : { body: { stage: 'TOTP_REQUIRED', user: null, recoveryCodes: null } }
      },
      'POST /api/auth/totp': { status: 401, body: { detail: 'Code ist ungültig' } },
    })
    renderApp('/login')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Benutzername'), 'admin')
    await user.type(screen.getByLabelText('Passwort'), 'falsch-falsch-falsch')
    await user.click(screen.getByRole('button', { name: 'Weiter' }))
    expect(await screen.findByText('Benutzername oder Passwort ist falsch')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Weiter' }))
    expect(await screen.findByText('Zu viele Fehlversuche, bitte später erneut versuchen')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Weiter' }))
    await screen.findByText('Bestätigungscode')
    await user.keyboard('000000')
    await user.click(screen.getByRole('button', { name: 'Anmelden' }))
    expect(await screen.findByText('Code ist ungültig')).toBeInTheDocument()
  })

  it('allows sign-in with a recovery code', async () => {
    let body: unknown
    mockApi({
      'GET /api/auth/me': { body: { stage: 'TOTP_REQUIRED', user: null, recoveryCodes: null } },
      'POST /api/auth/totp': (init) => {
        body = JSON.parse(String(init?.body))
        return { status: 401, body: { detail: 'Code ist ungültig' } }
      },
    })
    renderApp('/login')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Wiederherstellungscode verwenden' }))
    await user.type(screen.getByLabelText('Wiederherstellungscode'), 'AAAAA-BBBBB')
    await user.click(screen.getByRole('button', { name: 'Anmelden' }))
    await waitFor(() => expect(body).toEqual({ code: 'AAAAA-BBBBB' }))
  })

  it('ignores external redirect targets', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': vacationRentalActive })
    const { router } = renderApp('/login?next=//evil.example.com')
    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })
})
