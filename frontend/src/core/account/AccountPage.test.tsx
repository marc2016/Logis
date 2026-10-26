import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp } from '@/test/renderApp'

async function fillPasswordForm(current: string) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Aktuelles Passwort'), current)
  await user.type(screen.getByLabelText('Neues Passwort'), 'mein-neues-passwort')
  await user.type(screen.getByLabelText('Neues Passwort wiederholen'), 'mein-neues-passwort')
  await user.click(screen.getByRole('button', { name: 'Passwort ändern' }))
}

describe('Mein Konto', () => {
  it('ändert das Passwort', async () => {
    let body: unknown
    mockApi({
      'GET /api/auth/me': authenticated(),
      'GET /api/modules': { body: [] },
      'POST /api/account/password': (init) => {
        body = JSON.parse(String(init?.body))
        return { status: 200 }
      },
    })
    renderApp('/konto')
    await fillPasswordForm('altes-passwort-123')
    expect(await screen.findByText('Passwort geändert. Andere Sitzungen wurden beendet.')).toBeInTheDocument()
    expect(body).toEqual({ currentPassword: 'altes-passwort-123', newPassword: 'mein-neues-passwort' })
  })

  it('zeigt die Meldung bei falschem aktuellem Passwort', async () => {
    mockApi({
      'GET /api/auth/me': authenticated(),
      'GET /api/modules': { body: [] },
      'POST /api/account/password': { status: 400, body: { detail: 'Das aktuelle Passwort ist falsch.' } },
    })
    renderApp('/konto')
    await fillPasswordForm('falsch-falsch-falsch')
    expect(await screen.findByText('Das aktuelle Passwort ist falsch.')).toBeInTheDocument()
  })

  it('erzeugt neue Wiederherstellungscodes nach Bestätigung', async () => {
    mockApi({
      'GET /api/auth/me': authenticated(),
      'GET /api/modules': { body: [] },
      'POST /api/account/recovery-codes': { body: { recoveryCodes: ['AAAAA-BBBBB', 'CCCCC-DDDDD'] } },
    })
    renderApp('/konto')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Neue Codes erzeugen' }))
    expect(screen.getByText('Alle bisherigen Codes werden dabei ungültig.')).toBeInTheDocument()
    await user.click(screen.getAllByRole('button', { name: 'Neue Codes erzeugen' }).at(-1)!)
    await waitFor(() => expect(screen.getByText('AAAAA-BBBBB')).toBeInTheDocument())
  })
})
