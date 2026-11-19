import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp } from '@/test/renderApp'

async function fillPasswordForm(current: string) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Aktuelles Passwort'), current)
  await user.type(screen.getByLabelText('Neues Passwort'), 'mein-neues-passwort')
  await user.type(screen.getByLabelText('Neues Passwort wiederholen'), 'mein-neues-passwort')
  await user.click(screen.getByRole('button', { name: 'Passwort ändern' }))
}

describe('My account', () => {
  it('changes the password', async () => {
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

  it('shows the message for a wrong current password', async () => {
    mockApi({
      'GET /api/auth/me': authenticated(),
      'GET /api/modules': { body: [] },
      'POST /api/account/password': { status: 400, body: { detail: 'Das aktuelle Passwort ist falsch.' } },
    })
    renderApp('/konto')
    await fillPasswordForm('falsch-falsch-falsch')
    expect(await screen.findByText('Das aktuelle Passwort ist falsch.')).toBeInTheDocument()
  })

  it('generates new recovery codes after confirmation', async () => {
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

  it('lists remembered devices and signs them out', async () => {
    let devices = [
      { id: 1, browser: 'Firefox', os: 'Linux', createdAt: '2026-11-01T08:00:00Z', lastUsedAt: '2026-11-18T08:00:00Z', current: true },
      { id: 2, browser: 'Chrome', os: 'Android', createdAt: '2026-11-02T08:00:00Z', lastUsedAt: '2026-11-17T08:00:00Z', current: false },
      { id: 3, browser: 'Other', os: 'Other', createdAt: '2026-11-03T08:00:00Z', lastUsedAt: '2026-11-03T08:00:00Z', current: false },
    ]
    const calls: string[] = []
    mockApi({
      'GET /api/auth/me': authenticated(),
      'GET /api/modules': { body: [] },
      'GET /api/account/devices': () => ({ body: devices }),
      'DELETE /api/account/devices/2': () => {
        calls.push('delete 2')
        devices = devices.filter((d) => d.id !== 2)
        return { status: 204 }
      },
      'POST /api/account/devices/sign-out-others': () => {
        calls.push('others')
        devices = devices.filter((d) => d.current)
        return { status: 204 }
      },
    })
    renderApp('/konto')
    const user = userEvent.setup()

    const list = await screen.findByRole('list', { name: 'Angemeldete Geräte' })
    const items = within(list).getAllByRole('listitem')
    expect(items).toHaveLength(3)
    expect(items[0]).toHaveTextContent('Firefox auf Linux')
    expect(items[0]).toHaveTextContent('Dieses Gerät')
    expect(items[1]).toHaveTextContent('Chrome auf Android')
    expect(items[1]).not.toHaveTextContent('Dieses Gerät')
    expect(items[2]).toHaveTextContent('Unbekannter Browser auf unbekanntes System')

    await user.click(screen.getByRole('button', { name: 'Chrome auf Android abmelden' }))
    await waitFor(() => expect(within(list).getAllByRole('listitem')).toHaveLength(2))

    await user.click(screen.getByRole('button', { name: 'Alle anderen Geräte abmelden' }))
    await waitFor(() => expect(within(list).getAllByRole('listitem')).toHaveLength(1))
    expect(calls).toEqual(['delete 2', 'others'])
  })
})
